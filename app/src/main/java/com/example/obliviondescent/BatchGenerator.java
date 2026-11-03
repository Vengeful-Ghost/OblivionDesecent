package com.example.obliviondescent;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
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

/**
 * Generates dynamic game content using the Gemini AI API.
 * Sends a single batch request for a floor range (5 floors) and receives back:
 * unique enemy templates, per-floor shop items and a unique boss.
 * Results are cached locally so subsequent uses require no network call.
 */
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
        void onSuccess(BatchResult result, String jsonText);
        void onFailure(String error);
    }

    /**
     * Sends a batch request to the Gemini API for the given floor range.
     * Any failure (network, HTTP error, bad JSON) is reported to onFailure; the caller handles retries.
     * @param rangeStart first floor in the range
     * @param rangeEnd last floor in the range
     * @param listener result listener (onSuccess / onFailure)
     */
    public void generateBatch(int rangeStart, int rangeEnd, OnBatchGeneratedListener listener) {
        int bossFloor = rangeEnd; // boss is always the last floor of the range

        String prompt = buildPrompt(rangeStart, rangeEnd, bossFloor);
        Log.d(TAG, "=== Generating batch for floors " + rangeStart + "-" + rangeEnd + " ===");

        try {
            JSONObject textPart = new JSONObject().put("text", prompt);
            JSONObject content  = new JSONObject()
                    .put("parts", new JSONArray().put(textPart));
            // Thinking tokens count toward maxOutputTokens, so leave plenty of room for the JSON itself
            JSONObject genCfg   = new JSONObject()
                    .put("maxOutputTokens", 32768)
                    .put("temperature", 0.9)
                    .put("responseMimeType", "application/json");
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
                    listener.onFailure("network: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
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
                        String jsonText = extractJsonText(bodyStr);
                        BatchResult result = parseBatch(jsonText, rangeStart, rangeEnd);
                        listener.onSuccess(result, jsonText);
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error: " + e.getMessage(), e);
                        listener.onFailure(e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Request build failed: " + e.getMessage());
            listener.onFailure(e.getMessage());
        }
    }

    /**
     * Builds the full prompt string for the Gemini API covering the given floor range.
     * @param rangeStart first floor in the range
     * @param rangeEnd last floor in the range
     * @param bossFloor boss floor within the range (always the last floor)
     * @return the complete prompt string
     */
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

    /**
     * Extracts the inner JSON text from the Gemini API response wrapper.
     * Joins all answer parts, strips markdown fences and reports blocked or cut-off replies.
     */
    private static String extractJsonText(String rawResponse) throws Exception {
        JSONObject apiResp = new JSONObject(rawResponse);
        JSONArray candidates = apiResp.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            JSONObject feedback = apiResp.optJSONObject("promptFeedback");
            throw new Exception("no reply" + (feedback != null ? " (" + feedback.optString("blockReason") + ")" : ""));
        }
        JSONObject candidate = candidates.getJSONObject(0);
        String finishReason = candidate.optString("finishReason", "");
        Log.d(TAG, "finishReason: " + finishReason);

        StringBuilder sb = new StringBuilder();
        JSONObject content = candidate.optJSONObject("content");
        JSONArray parts = content != null ? content.optJSONArray("parts") : null;
        if (parts != null) {
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                if (!part.optBoolean("thought", false)) sb.append(part.optString("text", ""));
            }
        }
        String text = sb.toString().trim();
        if (text.isEmpty() || "MAX_TOKENS".equals(finishReason)) {
            throw new Exception("reply cut off (" + finishReason + ")");
        }
        if (text.startsWith("```")) {
            text = text.replaceAll("```json", "").replaceAll("```", "").trim();
        }
        return text;
    }

    /**
     * Parses a pre-extracted JSON text string into a BatchResult.
     * Package-private so GameActivity can call it when loading from local storage.
     *
     * @param jsonText cleaned JSON (without the API response wrapper)
     * @param rangeStart first floor in the range
     * @param rangeEnd last floor in the range
     * @return the parsed BatchResult
     * @throws Exception if parsing fails
     */
    BatchResult parseBatch(String jsonText, int rangeStart, int rangeEnd) throws Exception {
        JSONObject root = new JSONObject(jsonText);

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

        // Parse shop items per floor; a missing floor is filled with offline items below
        Map<Integer, List<ShopItem>> shopByFloor = new HashMap<>();
        JSONObject shopObj = root.optJSONObject("shop");
        for (int f = rangeStart; f <= rangeEnd; f++) {
            String key = "floor" + f;
            List<ShopItem> items = new ArrayList<>();
            JSONArray arr = shopObj != null ? shopObj.optJSONArray(key) : null;
            if (arr != null) {
                for (int j = 0; j < arr.length(); j++) {
                    ShopItem item = parseShopItem(arr.getJSONObject(j));
                    if (item != null) items.add(item);
                }
            }
            shopByFloor.put(f, items);
        }

        // Parse boss
        JSONObject bossObj = root.optJSONObject("boss");
        if (bossObj == null) bossObj = new JSONObject();
        String bossName  = bossObj.optString("name", "The Ancient One");
        String bossTitle = bossObj.optString("title", "Ruler of the Void");
        int bossColor    = parseColor(bossObj.optString("colorHex", "#FF4444"));

        BatchResult result = new BatchResult(enemyPool, shopByFloor, bossName, bossTitle, bossColor);
        fillGaps(result, rangeStart, rangeEnd);
        return result;
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

    // ============================================================
    // OFFLINE FALLBACK — same formulas as the prompt, used when Gemini can't deliver
    // ============================================================

    private static final String[] ENEMY_ADJ  = {"Hollow", "Ashen", "Rotting", "Gloom", "Bone", "Blight", "Cinder", "Void"};
    private static final String[] ENEMY_NOUN = {"Ghoul", "Wretch", "Stalker", "Husk", "Shade", "Fiend", "Revenant", "Crawler"};
    private static final String[] BOSS_NAMES  = {"Morvath", "Xal'zur", "Veyra", "Grimhold", "Ossmaw", "Nhalek"};
    private static final String[] BOSS_TITLES = {"the Hollow Crown", "Warden of Ash", "Mother of Rot",
            "the Last Bell", "Eater of Light", "Lord of the Deep"};
    private static final String[] COLORS = {"#C0392B", "#8E44AD", "#16A085", "#D35400", "#7F8C8D", "#2E86C1", "#B7950B"};
    private static final String[] ITEM_ADJ = {"Rusted", "Ashen", "Grim", "Cursed", "Hallowed", "Bloodforged", "Duskwoven"};
    private static final String[] MELEE   = {"Blade", "Cleaver", "Mace", "Glaive", "Dagger", "Warhammer"};
    private static final String[] RANGED  = {"Wand", "Staff", "Crossbow", "Sling", "Tome"};
    private static final String[][] ARMOR = {
            {"HEAD", "Helm"}, {"HEAD", "Hood"}, {"CHEST", "Cuirass"}, {"CHEST", "Robe"},
            {"LEGS", "Greaves"}, {"LEGS", "Leggings"}, {"BOOTS", "Boots"}, {"BOOTS", "Sabatons"}};

    /** A complete offline batch for a range. Not saved to disk, so a later session retries Gemini. */
    static BatchResult fallbackBatch(int rangeStart, int rangeEnd) {
        BatchResult result = new BatchResult(new ArrayList<>(), new HashMap<>(), null, null, 0);
        fillGaps(result, rangeStart, rangeEnd);
        return result;
    }

    /** Fills whatever the AI reply left out (enemies, a floor's shop, the boss) with offline content. */
    private static void fillGaps(BatchResult result, int rangeStart, int rangeEnd) {
        java.util.Random rng = new java.util.Random();
        if (result.enemyPool.isEmpty()) {
            for (int i = 0; i < 4; i++) {
                result.enemyPool.add(new EnemyTemplate(
                        pick(rng, ENEMY_ADJ) + " " + pick(rng, ENEMY_NOUN), "",
                        Color.parseColor(pick(rng, COLORS)),
                        Math.min(3f, 0.8f + rng.nextFloat() * (0.6f + rangeStart * 0.05f)),
                        0.6f + rng.nextFloat() * 0.4f));
            }
        }
        for (int f = rangeStart; f <= rangeEnd; f++) {
            List<ShopItem> items = result.shopByFloor.get(f);
            if (items != null && !items.isEmpty()) continue;
            items = new ArrayList<>();
            for (int i = 0; i < 6; i++) items.add(fallbackItem(f, i / 2, rng)); // 2 weapons, 2 armor, 2 consumables
            result.shopByFloor.put(f, items);
        }
        if (result.bossName == null) {
            result.bossName  = pick(rng, BOSS_NAMES);
            result.bossTitle = pick(rng, BOSS_TITLES);
            result.bossColor = Color.parseColor(pick(rng, COLORS));
        }
    }

    private static ShopItem fallbackItem(int floor, int kind, java.util.Random rng) {
        // Rarity: 55% common, 28% rare, 13% epic, 4% legendary
        float roll = rng.nextFloat();
        int tier = roll < 0.55f ? 0 : roll < 0.83f ? 1 : roll < 0.96f ? 2 : 3;
        float[][] statRange  = {{0.7f, 1.3f}, {1.4f, 2.2f}, {2.0f, 3.5f}, {3.0f, 7.0f}};
        float[][] priceRange = {{0.5f, 1.0f}, {1.0f, 2.0f}, {2.0f, 4.0f}, {4.0f, 9.0f}};
        float stat = statRange[tier][0] + rng.nextFloat() * (statRange[tier][1] - statRange[tier][0]);
        float price = priceRange[tier][0] + rng.nextFloat() * (priceRange[tier][1] - priceRange[tier][0]);

        ShopItem item = new ShopItem();
        item.rarity   = new String[]{"COMMON", "RARE", "EPIC", "LEGENDARY"}[tier];
        item.colorHex = new String[]{"#B0B0B0", "#4FA3FF", "#B060FF", "#FFB020"}[tier];
        item.color    = Color.parseColor(item.colorHex);
        item.price    = Math.max(1, Math.round(floor * 5 * price));
        item.description = "Scavenged from floor " + floor + ".";

        if (kind == 0) {
            item.type = ShopItem.ItemType.WEAPON;
            item.isProjectile = rng.nextFloat() < 0.35f;
            item.name     = pick(rng, ITEM_ADJ) + " " + pick(rng, item.isProjectile ? RANGED : MELEE);
            item.damage   = Math.max(1, Math.round(floor * 3 * stat));
            item.range    = item.isProjectile ? 500 + rng.nextInt(700) : 40 + rng.nextInt(110);
            item.cooldown = item.isProjectile ? 450 + rng.nextInt(650) : 250 + rng.nextInt(650);
            item.knockback = 0.05f + rng.nextFloat() * 0.35f;
        } else if (kind == 1) {
            String[] piece = ARMOR[rng.nextInt(ARMOR.length)];
            item.type      = ShopItem.ItemType.ARMOR;
            item.armorSlot = piece[0];
            item.name      = pick(rng, ITEM_ADJ) + " " + piece[1];
            item.defense   = Math.max(1, Math.round(floor * 8 * stat));
            item.maxDurability = 60 + rng.nextInt(100);
        } else {
            boolean heal = rng.nextBoolean();
            item.type   = ShopItem.ItemType.CONSUMABLE;
            item.effect = heal ? "heal" : "armor_restore";
            item.name   = heal ? "Healing Draught" : "Ironbark Tonic";
            item.effectValue = Math.min(heal ? 100 : 80, Math.round(20 * stat));
        }
        return item;
    }

    private static String pick(java.util.Random rng, String[] options) {
        return options[rng.nextInt(options.length)];
    }

    // ============================================================
    // LOCAL STORAGE — persistent batch cache across sessions
    // ============================================================

    private static String batchFileName(int rangeStart) {
        return "batch_" + rangeStart + ".json";
    }

    /** Saves the inner JSON text for a batch range to the app's private files directory. */
    static void saveRawBatch(Context ctx, int rangeStart, String jsonText) {
        try {
            File file = new File(ctx.getFilesDir(), batchFileName(rangeStart));
            FileWriter fw = new FileWriter(file);
            fw.write(jsonText);
            fw.close();
            Log.d(TAG, "Saved batch " + rangeStart + " → " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "saveRawBatch failed: " + e.getMessage());
        }
    }

    /**
     * Loads the inner JSON text for a batch range from local storage.
     * Returns null if the file does not exist or cannot be read.
     */
    static String loadRawBatch(Context ctx, int rangeStart) {
        try {
            File file = new File(ctx.getFilesDir(), batchFileName(rangeStart));
            if (!file.exists()) return null;
            BufferedReader br = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            Log.d(TAG, "Loaded batch " + rangeStart + " from local storage");
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "loadRawBatch failed: " + e.getMessage());
            return null;
        }
    }

    /** Deletes all saved batch files. Call this when the player starts a New Game. */
    static void clearAllBatches(Context ctx) {
        File dir = ctx.getFilesDir();
        File[] files = dir.listFiles((d, name) ->
                name.startsWith("batch_") && name.endsWith(".json"));
        if (files != null) {
            for (File f : files) f.delete();
        }
        Log.d(TAG, "Cleared all local batch files");
    }
}