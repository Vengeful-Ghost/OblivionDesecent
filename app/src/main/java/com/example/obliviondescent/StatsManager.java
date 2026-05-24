package com.example.obliviondescent;

/**
 * StatsManager
 *
 * Tracks all stats for the current play session in memory.
 * GameActivity calls the increment methods throughout gameplay.
 * Call flush() on game over or floor transition to persist to Firestore.
 *
 * Two scopes:
 *   - Session stats: reset after every flush (avoid double-counting)
 *   - Lifetime stats: accumulated in Firestore forever
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

    public StatsManager(DBManager db, String userId, String gameId) {
        this.db     = db;
        this.userId = userId;
        this.gameId = gameId;
    }

    /* ============================================================
       INCREMENT — called by GameActivity during gameplay
       ============================================================ */

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