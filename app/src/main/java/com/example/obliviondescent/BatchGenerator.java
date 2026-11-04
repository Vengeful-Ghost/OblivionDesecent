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
        sb.append("You are the content designer for Oblivion Descent, a hard dark fantasy roguelike for mobile.\n");
        sb.append("Generate content for floors ").append(rangeStart).append(" to ").append(rangeEnd).append(".\n\n");

        sb.append("DESIGN GOALS (most important):\n");
        sb.append("- The run must feel dangerous. Progress is earned through fights, levels and careful shopping,\n");
        sb.append("  never handed out by the shop. Every floor should be a little harder than the one before.\n");
        sb.append("- The player starts with 100 HP and fists dealing 7 damage every 250 ms, and gains +10 HP and\n");
        sb.append("  +5% damage per level. Gold is scarce: about 40-55 gold per floor.\n");
        sb.append("- Shop items are modest upgrades, never a power spike. A COMMON is a small step up; a LEGENDARY\n");
        sb.append("  is strong but costs several floors of saved gold. Never exceed the budget below.\n\n");

        sb.append("PER-FLOOR BUDGET (pick values inside these ranges and vary them so no two items match):\n");
        for (int f = rangeStart; f <= rangeEnd; f++) {
            sb.append("floor ").append(f).append(":\n  weapon damage at 600 ms cooldown:");
            for (int t = 0; t < 4; t++) sb.append(' ').append(TIERS[t]).append(' ').append(span(weaponBase(f), STAT_MULT[t]));
            sb.append("\n  armor defense per piece:");
            for (int t = 0; t < 4; t++) sb.append(' ').append(TIERS[t]).append(' ').append(span(armorBase(f), STAT_MULT[t]));
            sb.append("\n  price (gold):");
            for (int t = 0; t < 4; t++) sb.append(' ').append(TIERS[t]).append(' ').append(span(priceBase(f), PRICE_MULT[t]));
            sb.append("\n  consumables: price ").append(span(priceBase(f), CONSUMABLE_PRICE))
                    .append(", heal ").append(span(healBase(f), STAT_MULT[0][0], STAT_MULT[3][1]))
                    .append(", armor_restore ").append(span(armorBase(f), 2f, 5f)).append("\n");
        }

        sb.append("\nWEAPONS: final damage = budget value * cooldown / 600, so fast weapons (250-400 ms) hit soft\n");
        sb.append("  and slow ones (900-1400 ms) hit hard. Projectile weapons: damage x0.8, range 450-900.\n");
        sb.append("  Melee range 40-140. knockback 0.05-0.4 (heavier weapons push more).\n");
        sb.append("ARMOR: maxDurability 40-120 hits (rarer lasts longer). armorType HEAD, CHEST, LEGS or BOOTS; vary them.\n");
        sb.append("SHOP: exactly 6 items per floor: 2 WEAPON, 2 ARMOR, 2 CONSUMABLE (one \"heal\", one \"armor_restore\").\n");
        sb.append("  Rarity: mostly COMMON and RARE; at most ONE item EPIC or better per floor; LEGENDARY only on\n");
        sb.append("  floor 4 or deeper and at most one LEGENDARY in this whole response.\n");
        sb.append("ENEMIES: exactly 4 types shared by every floor in this range, each with a clear role:\n");
        sb.append("  one glass cannon (low hp, high damage), one juggernaut (high hp, lower damage), two skirmishers.\n");
        sb.append("  hpMultiplier ").append(fmt(enemyHpMin(rangeStart))).append("-").append(fmt(enemyHpMax(rangeStart)))
                .append(", dmgMultiplier ").append(fmt(enemyDmgMin(rangeStart))).append("-").append(fmt(enemyDmgMax(rangeStart)))
                .append(". Distinct colours that stand out on a dark stone floor.\n");
        sb.append("BOSS for floor ").append(bossFloor).append(": the hardest fight of the range, with a name and a title.\n");
        sb.append("  hpMultiplier ").append(fmt(bossHpMultiplier(bossFloor) * 0.9f)).append("-").append(fmt(bossHpMultiplier(bossFloor) * 1.1f))
                .append(", dmgMultiplier ").append(fmt(bossDmgMultiplier(bossFloor) * 0.9f)).append("-").append(fmt(bossDmgMultiplier(bossFloor) * 1.1f)).append(".\n\n");

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
        sb.append("    \"colorHex\": \"#FF0000\",\n");
        sb.append("    \"hpMultiplier\": 3.0,\n");
        sb.append("    \"dmgMultiplier\": 1.4\n");
        sb.append("  }\n");
        sb.append("}\n");

        return sb.toString();
    }

    // ============================================================
    // BALANCE BUDGET — shared by the prompt, the reply check and the offline fallback
    // ============================================================

    private static final String[] TIERS = {"COMMON", "RARE", "EPIC", "LEGENDARY"};
    private static final float[][] STAT_MULT  = {{0.85f, 1.15f}, {1.15f, 1.4f}, {1.4f, 1.75f}, {1.75f, 2.2f}};
    private static final float[][] PRICE_MULT = {{1.0f, 1.4f}, {2.0f, 2.8f}, {3.5f, 5.0f}, {7.0f, 10.0f}};
    private static final float[] CONSUMABLE_PRICE = {0.4f, 0.7f};

    static float weaponBase(int floor) { return 6f + floor * 2.5f; }  // damage at a 600 ms cooldown
    static float armorBase(int floor)  { return 3f + floor * 1.5f; }  // defense per piece
    static float priceBase(int floor)  { return 13f + floor * 8.5f; }
    static float healBase(int floor)   { return 15f + floor * 3f; }
    static float enemyHpMin(int rangeStart)  { return 1.0f + rangeStart * 0.10f; }
    static float enemyHpMax(int rangeStart)  { return 1.6f + rangeStart * 0.15f; }
    static float enemyDmgMin(int rangeStart) { return 1.0f + rangeStart * 0.06f; }
    static float enemyDmgMax(int rangeStart) { return 1.4f + rangeStart * 0.08f; }
    static float bossHpMultiplier(int floor)  { return 1.6f + floor * 0.10f; }
    static float bossDmgMultiplier(int floor) { return 1.0f + floor * 0.04f; }

    private static String span(float base, float[] mult) { return span(base, mult[0], mult[1]); }

    private static String span(float base, float lo, float hi) {
        return Math.max(1, Math.round(base * lo)) + "-" + Math.max(1, Math.round(base * hi));
    }

    private static String fmt(float v) { return String.format(java.util.Locale.US, "%.2f", v); }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static int clampInt(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static int tierOf(String rarity) {
        int t = java.util.Arrays.asList(TIERS).indexOf(String.valueOf(rarity).toUpperCase());
        return Math.max(0, t);
    }

    // Pulls an AI item back inside the floor's budget, so a generous reply can't trivialise the run
    private static void capToFloor(ShopItem item, int floor) {
        float maxMult = STAT_MULT[tierOf(item.rarity)][1];
        float pace = clamp(item.cooldown / 600f, 0.4f, 2.4f);
        float maxDamage = weaponBase(floor) * maxMult * pace * (item.isProjectile ? 0.8f : 1f);
        item.damage  = Math.max(1, Math.min(item.damage, Math.round(maxDamage)));
        item.defense = Math.max(1, Math.min(item.defense, Math.round(armorBase(floor) * maxMult)));
        float[] priceMult = item.type == ShopItem.ItemType.CONSUMABLE ? CONSUMABLE_PRICE : PRICE_MULT[tierOf(item.rarity)];
        item.price = clampInt(item.price, Math.round(priceBase(floor) * priceMult[0]), Math.round(priceBase(floor) * priceMult[1]));
        item.effectValue = Math.min(item.effectValue, "heal".equals(item.effect)
                ? Math.round(healBase(floor) * STAT_MULT[3][1]) : Math.round(armorBase(floor) * 5f));
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
        for (EnemyTemplate t : enemyPool) {
            t.hpMultiplier  = clamp(t.hpMultiplier, enemyHpMin(rangeStart), enemyHpMax(rangeStart));
            t.dmgMultiplier = clamp(t.dmgMultiplier, enemyDmgMin(rangeStart), enemyDmgMax(rangeStart));
        }

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
                    if (item == null) continue;
                    capToFloor(item, f);
                    items.add(item);
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
        float bossHp = bossHpMultiplier(rangeEnd), bossDmg = bossDmgMultiplier(rangeEnd);
        result.bossHpMultiplier  = clamp((float) bossObj.optDouble("hpMultiplier", bossHp), bossHp * 0.9f, bossHp * 1.1f);
        result.bossDmgMultiplier = clamp((float) bossObj.optDouble("dmgMultiplier", bossDmg), bossDmg * 0.9f, bossDmg * 1.1f);
        // Seeded by the reply, so every co-op phone fills the same gaps the same way
        fillGaps(result, rangeStart, rangeEnd, new java.util.Random(jsonText.hashCode()));
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

    /** A complete offline batch for a range; the same seed gives the same content. Not saved to disk. */
    static BatchResult fallbackBatch(int rangeStart, int rangeEnd, long seed) {
        BatchResult result = new BatchResult(new ArrayList<>(), new HashMap<>(), null, null, 0);
        fillGaps(result, rangeStart, rangeEnd, new java.util.Random(seed));
        return result;
    }

    /** Fills whatever the AI reply left out (enemies, a floor's shop, the boss) with offline content. */
    private static void fillGaps(BatchResult result, int rangeStart, int rangeEnd, java.util.Random rng) {
        if (result.enemyPool.isEmpty()) {
            float hpMin = enemyHpMin(rangeStart), dmgMin = enemyDmgMin(rangeStart);
            for (int i = 0; i < 4; i++) {
                result.enemyPool.add(new EnemyTemplate(
                        pick(rng, ENEMY_ADJ) + " " + pick(rng, ENEMY_NOUN), "",
                        Color.parseColor(pick(rng, COLORS)),
                        hpMin + rng.nextFloat() * (enemyHpMax(rangeStart) - hpMin),
                        dmgMin + rng.nextFloat() * (enemyDmgMax(rangeStart) - dmgMin)));
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
        if (result.bossHpMultiplier <= 0) {
            result.bossHpMultiplier  = bossHpMultiplier(rangeEnd);
            result.bossDmgMultiplier = bossDmgMultiplier(rangeEnd);
        }
    }

    private static ShopItem fallbackItem(int floor, int kind, java.util.Random rng) {
        // Rarity: 60% common, 28% rare, 10% epic, 2% legendary (legendary from floor 4)
        float roll = rng.nextFloat();
        int tier = roll < 0.60f ? 0 : roll < 0.88f ? 1 : roll < 0.98f || floor < 4 ? 2 : 3;
        float stat  = STAT_MULT[tier][0] + rng.nextFloat() * (STAT_MULT[tier][1] - STAT_MULT[tier][0]);
        float price = PRICE_MULT[tier][0] + rng.nextFloat() * (PRICE_MULT[tier][1] - PRICE_MULT[tier][0]);

        ShopItem item = new ShopItem();
        item.rarity   = TIERS[tier];
        item.colorHex = new String[]{"#B0B0B0", "#4FA3FF", "#B060FF", "#FFB020"}[tier];
        item.color    = Color.parseColor(item.colorHex);
        item.price    = Math.max(1, Math.round(priceBase(floor) * price));
        item.description = "Scavenged from floor " + floor + ".";

        if (kind == 0) {
            item.type = ShopItem.ItemType.WEAPON;
            item.isProjectile = rng.nextFloat() < 0.35f;
            item.name     = pick(rng, ITEM_ADJ) + " " + pick(rng, item.isProjectile ? RANGED : MELEE);
            item.cooldown = item.isProjectile ? 450 + rng.nextInt(650) : 250 + rng.nextInt(650);
            item.damage   = Math.max(1, Math.round(weaponBase(floor) * stat * item.cooldown / 600f
                    * (item.isProjectile ? 0.8f : 1f)));
            item.range    = item.isProjectile ? 450 + rng.nextInt(450) : 40 + rng.nextInt(100);
            item.knockback = 0.05f + rng.nextFloat() * 0.35f;
        } else if (kind == 1) {
            String[] piece = ARMOR[rng.nextInt(ARMOR.length)];
            item.type      = ShopItem.ItemType.ARMOR;
            item.armorSlot = piece[0];
            item.name      = pick(rng, ITEM_ADJ) + " " + piece[1];
            item.defense   = Math.max(1, Math.round(armorBase(floor) * stat));
            item.maxDurability = 40 + tier * 20 + rng.nextInt(40);
        } else {
            boolean heal = rng.nextBoolean();
            item.type   = ShopItem.ItemType.CONSUMABLE;
            item.effect = heal ? "heal" : "armor_restore";
            item.name   = heal ? "Healing Draught" : "Ironbark Tonic";
            item.price  = Math.max(1, Math.round(priceBase(floor)
                    * (CONSUMABLE_PRICE[0] + rng.nextFloat() * (CONSUMABLE_PRICE[1] - CONSUMABLE_PRICE[0]))));
            item.effectValue = heal ? Math.min(100, Math.round(healBase(floor) * stat))
                    : Math.round(armorBase(floor) * (2f + rng.nextFloat() * 3f));
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