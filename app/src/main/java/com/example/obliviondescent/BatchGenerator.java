package com.example.obliviondescent;

import android.graphics.Color;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BatchGenerator {

    private static final String TAG = "BatchGenerator";
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-3.1-flash-lite:generateContent?key=" + BuildConfig.GEMINI_API_KEY;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    public interface OnBatchGeneratedListener {
        void onSuccess(BatchResult result);
        void onFailure(String error);
    }

    public void generateBatch(int rangeStart, int rangeEnd, OnBatchGeneratedListener listener) {
        int bossFloor = rangeEnd; // boss is always the last floor of the range

        String prompt = buildPrompt(rangeStart, rangeEnd, bossFloor);
        Log.d(TAG, "=== Generating batch for floors " + rangeStart + "-" + rangeEnd + " ===");

        try {
            JSONObject textPart = new JSONObject().put("text", prompt);
            JSONObject content  = new JSONObject()
                    .put("parts", new JSONArray().put(textPart));
            JSONObject genCfg   = new JSONObject()
                    .put("maxOutputTokens", 16384)
                    .put("temperature", 0.9);
            JSONObject body     = new JSONObject()
                    .put("contents", new JSONArray().put(content))
                    .put("generationConfig", genCfg);

            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(RequestBody.create(
                            body.toString(),
                            MediaType.parse("application/json")))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Network failure: " + e.getMessage());
                    listener.onFailure(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.code() == 503) {
                        Log.d(TAG, "503 — retrying in 3s");
                        response.close();
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed(() ->
                                        generateBatch(rangeStart, rangeEnd, listener), 3000);
                        return;
                    }

                    if (!response.isSuccessful()) {
                        String err = response.body() != null
                                ? response.body().string() : "unknown";
                        Log.e(TAG, "HTTP error: " + err);
                        listener.onFailure("HTTP " + response.code());
                        return;
                    }

                    String bodyStr = response.body().string();
                    Log.d(TAG, "=== Raw batch response received ===");

                    try {
                        BatchResult result = parseBatch(bodyStr, rangeStart, rangeEnd);
                        listener.onSuccess(result);
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error: " + e.getMessage(), e);
                        listener.onFailure("Parse error: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Request build failed: " + e.getMessage());
            listener.onFailure(e.getMessage());
        }
    }

    private String buildPrompt(int rangeStart, int rangeEnd, int bossFloor) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a dark fantasy game content generator for a roguelike called Oblivion Descent.\n");
        sb.append("Generate content for floors ").append(rangeStart).append(" to ").append(rangeEnd).append(".\n\n");

        sb.append("RULES:\n");
        sb.append("- All item stats use a continuous floor-based formula, NOT fixed brackets.\n");
        sb.append("- Rarity is the main driver of stat magnitude. A LEGENDARY on floor 3 can\n");
        sb.append("  legitimately outperform a COMMON on floor 10.\n");
        sb.append("- Within each rarity tier add random variance (+/-30%) so no two items are identical.\n");
        sb.append("- When you generate Armor pieces, Make sure to add variations of Head, Chest, Legs, Boots.\n\n");

        sb.append("WEAPON damage (integer): base = floor * 3.\n");
        sb.append("  COMMON 0.7-1.3x, RARE 1.4-2.2x, EPIC 2.0-3.5x, LEGENDARY 3.0-7.0x base.\n");
        sb.append("ARMOR defense (integer): base = floor * 8.\n");
        sb.append("  COMMON 0.7-1.3x, RARE 1.4-2.2x, EPIC 2.0-3.5x, LEGENDARY 3.0-7.0x base.\n");
        sb.append("PRICE (gold): base = floor * 5. COMMON 0.5-1.0x, RARE 1.0-2.0x, EPIC 2.0-4.0x, LEGENDARY 4.0-9.0x.\n\n");

        sb.append("ENEMIES: Exactly 4 unique enemy types for the ENTIRE range (shared across all floors).\n");
        sb.append("  hpMultiplier: 0.8 to 3.0. dmgMultiplier: 0.6 to 1.0. Scale with floor ").append(rangeStart).append(".\n");
        sb.append("SHOP: Exactly 6 items per floor (mix of WEAPON, ARMOR, CONSUMABLE).\n");
        sb.append("  Weapon range (pixels): melee 30-150, projectile 500-1200.\n");
        sb.append("  Weapon cooldown: integer milliseconds 250-1500.\n");
        sb.append("  CONSUMABLE effectValue: heal 10-100 (player max HP is 100), armor_restore 10-80.\n");
        sb.append("BOSS: One named boss for floor ").append(bossFloor).append(".\n\n");

        sb.append("IMPORTANT: Return ONLY a valid JSON object. No markdown fences, no explanation.\n");
        sb.append("The \"enemies\" field MUST be a JSON array with EXACTLY 4 objects.\n\n");

        sb.append("{\n");
        sb.append("  \"enemies\": [\n");
        sb.append("    {\"name\": \"string\", \"colorHex\": \"#RRGGBB\", \"hpMultiplier\": 1.0, \"dmgMultiplier\": 1.0},\n");
        sb.append("    {\"name\": \"string\", \"colorHex\": \"#RRGGBB\", \"hpMultiplier\": 1.0, \"dmgMultiplier\": 1.0},\n");
        sb.append("    {\"name\": \"string\", \"colorHex\": \"#RRGGBB\", \"hpMultiplier\": 1.0, \"dmgMultiplier\": 1.0},\n");
        sb.append("    {\"name\": \"string\", \"colorHex\": \"#RRGGBB\", \"hpMultiplier\": 1.0, \"dmgMultiplier\": 1.0}\n");
        sb.append("  ],\n");
        sb.append("  \"shop\": {\n");

        for (int f = rangeStart; f <= rangeEnd; f++) {
            sb.append("    \"floor").append(f).append("\": [\n");
            sb.append("      {\"name\": \"string\", \"type\": \"WEAPON\", \"price\": 0, \"rarity\": \"COMMON\",\n");
            sb.append("       \"colorHex\": \"#FFFFFF\", \"description\": \"string\",\n");
            sb.append("       \"damage\": 0, \"range\": 80.0, \"cooldown\": 500, \"knockback\": 0.1, \"isProjectile\": false,\n");
            sb.append("       \"defense\": 0, \"maxDurability\": 100.0, \"armorType\": \"CHEST\",\n");
            sb.append("       \"effect\": \"heal\", \"effectValue\": 20}\n");
            sb.append("    ]").append(f < rangeEnd ? "," : "").append("\n");
        }

        sb.append("  },\n");
        sb.append("  \"boss\": {\n");
        sb.append("    \"name\": \"string\",\n");
        sb.append("    \"title\": \"string\",\n");
        sb.append("    \"colorHex\": \"#FF0000\"\n");
        sb.append("  }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private BatchResult parseBatch(String rawResponse,
                                   int rangeStart, int rangeEnd) throws Exception {
        // Extract JSON from Gemini wrapper
        JSONObject apiResp = new JSONObject(rawResponse);
        String text = apiResp
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text");

        // Strip markdown fences if present
        text = text.trim();
        if (text.startsWith("```")) {
            text = text.replaceAll("```json", "").replaceAll("```", "").trim();
        }

        JSONObject root = new JSONObject(text);

        // Parse enemies — expected as a JSON array of 4 objects
        List<EnemyTemplate> enemyPool = new ArrayList<>();
        try {
            // Support both array format (new) and keyed-object format (legacy fallback)
            if (root.get("enemies") instanceof JSONArray) {
                JSONArray arr = root.getJSONArray("enemies");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject e = arr.getJSONObject(i);
                    int color = parseColor(e.optString("colorHex", "#FF4444"));
                    enemyPool.add(new EnemyTemplate(
                            e.optString("name", "Unknown"),
                            e.optString("description", ""),
                            color,
                            (float) e.optDouble("hpMultiplier", 1.0),
                            (float) e.optDouble("dmgMultiplier", 1.0)
                    ));
                }
            } else {
                // Legacy: keyed object {"enemy1": {...}, ...}
                JSONObject enemiesObj = root.getJSONObject("enemies");
                for (int i = 1; i <= 5; i++) {
                    String key = "enemy" + i;
                    if (!enemiesObj.has(key)) continue;
                    JSONObject e = enemiesObj.getJSONObject(key);
                    int color = parseColor(e.optString("colorHex", "#FF4444"));
                    enemyPool.add(new EnemyTemplate(
                            e.optString("name", "Unknown"),
                            e.optString("description", ""),
                            color,
                            (float) e.optDouble("hpMultiplier", 1.0),
                            (float) e.optDouble("dmgMultiplier", 1.0)
                    ));
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "Enemy parse failed: " + ex.getMessage());
        }
        Log.d(TAG, "Parsed " + enemyPool.size() + " enemy templates");

        // Parse shop items per floor
        Map<Integer, List<ShopItem>> shopByFloor = new HashMap<>();
        JSONObject shopObj = root.getJSONObject("shop");
        for (int f = rangeStart; f <= rangeEnd; f++) {
            String key = "floor" + f;
            List<ShopItem> items = new ArrayList<>();
            if (shopObj.has(key)) {
                JSONArray arr = shopObj.getJSONArray(key);
                for (int j = 0; j < arr.length(); j++) {
                    ShopItem item = parseShopItem(arr.getJSONObject(j));
                    if (item != null) items.add(item);
                }
            }
            shopByFloor.put(f, items);
        }

        // Parse boss
        JSONObject bossObj = root.getJSONObject("boss");
        String bossName  = bossObj.optString("name", "The Ancient One");
        String bossTitle = bossObj.optString("title", "Ruler of the Void");
        int bossColor    = parseColor(bossObj.optString("colorHex", "#FF4444"));

        return new BatchResult(enemyPool, shopByFloor, bossName, bossTitle, bossColor);
    }

    private ShopItem parseShopItem(JSONObject obj) {
        try {
            ShopItem item = new ShopItem();
            item.name        = obj.optString("name", "Unknown Item");
            item.description = obj.optString("description", "");
            item.price       = obj.optInt("price", 20);
            item.rarity      = obj.optString("rarity", "COMMON");
            item.colorHex    = obj.optString("colorHex", "#FFFFFF");
            item.color       = parseColor(item.colorHex);

            String typeStr = obj.optString("type", "CONSUMABLE");
            try { item.type = ShopItem.ItemType.valueOf(typeStr); }
            catch (Exception e) { item.type = ShopItem.ItemType.CONSUMABLE; }

            item.damage      = obj.optInt("damage", 5);
            // Gemini sometimes returns range as a small normalized float (e.g. 1.6) instead of
            // game-world pixels. Values under 10 are scaled up to usable distances.
            double rangeRaw  = obj.optDouble("range", 80);
            item.range       = (float)(rangeRaw < 10 ? rangeRaw * 100 : rangeRaw);
            // Gemini sometimes returns cooldown in seconds (e.g. 1.3) instead of milliseconds.
            double cooldownRaw = obj.optDouble("cooldown", 400);
            item.cooldown    = (long)(cooldownRaw < 10 ? cooldownRaw * 1000 : cooldownRaw);
            item.knockback   = (float) obj.optDouble("knockback", 0.1);
            item.isProjectile = obj.optBoolean("isProjectile", false);
            item.defense     = obj.optInt("defense", 10);
            item.maxDurability = (float) obj.optDouble("maxDurability", 100);
            item.armorSlot   = obj.optString("armorType", "CHEST");
            item.effect      = obj.optString("effect", "heal");
            int rawEffect    = obj.optInt("effectValue", 20);
            // Player max health is 100; cap heal effectValue there. Armor restore is uncapped.
            item.effectValue = "heal".equals(item.effect) ? Math.min(100, rawEffect) : rawEffect;
            return item;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse shop item: " + e.getMessage());
            return null;
        }
    }

    private int parseColor(String hex) {
        try { return Color.parseColor(hex); }
        catch (Exception e) { return Color.parseColor("#FF4444"); }
    }
}