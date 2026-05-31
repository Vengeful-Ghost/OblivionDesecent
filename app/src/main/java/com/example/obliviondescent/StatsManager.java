package com.example.obliviondescent;

/**
 * Tracks all current-session statistics in memory.
 * GameActivity calls the increment methods throughout gameplay.
 * flush() is called on player death or floor transition to persist data to Firestore.
 *
 * Two scopes:
 *   - Session stats: reset after every flush to prevent double-counting.
 *   - Lifetime stats: accumulated in Firestore permanently.
 */
public class StatsManager {

    /* ============================================================
       SESSION STATS — reset each flush
       ============================================================ */
    private int floorsCleared   = 0;
    private int enemiesKilled   = 0;
    private int timesDied       = 0;
    private int bossesKilled    = 0;
    private int coinsCollected  = 0;
    private int coinsSpent      = 0;
    private int damageGiven     = 0;
    private int damageTaken     = 0;
    private long sessionStartMs = System.currentTimeMillis();

    /* ============================================================
       DEPENDENCIES
       ============================================================ */
    private final DBManager db;
    private final String    userId;
    private final String    gameId;

    /**
     * Creates a new stats manager for the current session.
     * @param db database manager
     * @param userId the user ID
     * @param gameId the current game save ID
     */
    public StatsManager(DBManager db, String userId, String gameId) {
        this.db     = db;
        this.userId = userId;
        this.gameId = gameId;
    }

    /* ============================================================
       INCREMENT — called by GameActivity during gameplay
       ============================================================ */

    /**
     * Called on every floor transition; also updates the highest floor record in Firestore.
     * @param newFloor the floor number that was just completed
     */
    public void onFloorCleared(int newFloor) {
        floorsCleared++;
        db.updateHighestFloor(userId, newFloor);
    }

    public void onEnemyKilled()            { enemiesKilled++; }
    public void onBossKilled()             { bossesKilled++; enemiesKilled++; }
    public void onPlayerDied()             { timesDied++; }
    public void onCoinCollected()          { coinsCollected++; }
    public void onCoinsSpent(int amount)   { coinsSpent += amount; }
    public void onDamageGiven(int amount)  { damageGiven += amount; }
    public void onDamageTaken(int amount)  { damageTaken += amount; }

    /* ============================================================
       GETTERS
       ============================================================ */

    public int  getFloorsCleared()  { return floorsCleared; }
    public int  getEnemiesKilled()  { return enemiesKilled; }
    public int  getTimesDied()      { return timesDied; }
    public int  getBossesKilled()   { return bossesKilled; }
    public int  getCoinsCollected() { return coinsCollected; }
    public int  getCoinsSpent()     { return coinsSpent; }
    public int  getDamageGiven()    { return damageGiven; }
    public int  getDamageTaken()    { return damageTaken; }

    public long getSessionPlaytimeSeconds() {
        return (System.currentTimeMillis() - sessionStartMs) / 1000;
    }

    /** Converts total seconds to hours (floored). */
    public static int secondsToHours(long seconds) {
        return (int)(seconds / 3600);
    }

    /* ============================================================
       FLUSH — push session stats to Firestore
       ============================================================ */

    /**
     * Persists all session statistics to Firestore and resets the in-memory counters.
     * Should be called at the end of a session (player death or floor transition).
     */
    public void flush() {
        long playtime = getSessionPlaytimeSeconds();

        db.incrementPlayerStats(
                userId,
                floorsCleared,
                enemiesKilled,
                timesDied,
                bossesKilled,
                coinsCollected,
                coinsSpent,
                damageGiven,
                damageTaken,
                playtime
        );

        // Reset session counters
        floorsCleared  = 0;
        enemiesKilled  = 0;
        timesDied      = 0;
        bossesKilled   = 0;
        coinsCollected = 0;
        coinsSpent     = 0;
        damageGiven    = 0;
        damageTaken    = 0;
        sessionStartMs = System.currentTimeMillis();
    }

    /* ============================================================
       STATIC CONVERTERS — used by StatsActivity and leaderboard
       ============================================================ */

    /**
     * Converts a raw Firestore data map to a Weapon object.
     * @param map raw data map from Firestore
     * @return a Weapon object, or null if the data is missing or invalid
     */
    public static Weapon weaponFromMap(java.util.Map<String, Object> map) {
        if (map == null) return null;
        try {
            String  name      = (String)  map.get("name");
            int     damage    = ((Number) map.get("damage")).intValue();
            float   range     = ((Number) map.get("range")).floatValue();
            long    cooldown  = ((Number) map.get("cooldown")).longValue();
            float   knockback = ((Number) map.get("knockback")).floatValue();
            boolean isProj    = map.get("isProjectile") != null
                    && (Boolean) map.get("isProjectile");
            int     color     = ((Number) map.get("color")).intValue();
            String  rarityStr = (String)  map.get("rarity");
            Weapon.Rarity rarity = Weapon.Rarity.valueOf(
                    rarityStr != null ? rarityStr.toUpperCase() : "COMMON");
            return new Weapon(name, damage, range, cooldown,
                    rarity, color, knockback, isProj);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Converts a raw Firestore data map to an Armor object.
     * @param armorId the armour document ID
     * @param map raw data map from Firestore
     * @return an Armor object, or null if the data is missing or invalid
     */
    public static Armor armorFromMap(String armorId, java.util.Map<String, Object> map) {
        if (map == null) return null;
        try {
            String name          = (String)  map.get("name");
            int    defense       = ((Number) map.get("defense")).intValue();
            float  maxDurability = ((Number) map.get("maxDurability")).floatValue();
            int    color         = ((Number) map.get("color")).intValue();
            String rarityStr     = (String)  map.get("rarity");
            String slotStr       = (String)  map.get("armorType");
            String description   = (String)  map.get("description");

            Armor.Rarity rarity = Armor.Rarity.valueOf(rarityStr);
            Armor.Slot   slot   = Armor.Slot.valueOf(slotStr);

            return new Armor(armorId, name, defense, maxDurability,
                    rarity, color, slot, description);
        } catch (Exception e) {
            return null;
        }
    }
}