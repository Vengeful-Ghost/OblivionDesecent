package com.example.obliviondescent;

/**
 * Soul Altar: permanent upgrades bought with Souls, which every run earns even when it ends in death.
 * Ranks live in Firestore players/{uid}.altar.{name}; Souls in players/{uid}.souls.
 */
enum Altar {
    VITALITY("Vitality", "+5 max HP", 10, 20, R.drawable.ic_heart),
    STRENGTH("Strength", "+2% damage", 10, 30, R.drawable.ic_crossed_swords),
    FORTUNE("Fortune", "+5% gold", 10, 20, R.drawable.ic_coin),
    WISDOM("Wisdom", "+5% XP", 10, 20, R.drawable.ic_level),
    LOCKSMITH("Locksmith", "+2% key drops", 5, 40, R.drawable.ic_key),
    SECOND_WIND("Second Wind", "Once a life, survive a killing blow", 1, 300, R.drawable.ic_flag);

    final String title, effect;
    final int maxRank, baseCost, icon;

    Altar(String title, String effect, int maxRank, int baseCost, int icon) {
        this.title = title;
        this.effect = effect;
        this.maxRank = maxRank;
        this.baseCost = baseCost;
        this.icon = icon;
    }

    /** Souls for the next rank; each rank costs more than the last. */
    int cost(int rank) {
        return baseCost * (rank + 1);
    }

    /** Souls a run earns: deep floors and bosses count most. */
    static int souls(int floors, int kills, int bosses) {
        return floors * 6 + kills + bosses * 30;
    }

    /** Ranks by ordinal from the player document's "altar" map (missing = 0). */
    static int[] ranks(java.util.Map<String, Object> playerDoc) {
        int[] ranks = new int[values().length];
        Object map = playerDoc != null ? playerDoc.get("altar") : null;
        if (map instanceof java.util.Map) {
            for (Altar a : values()) {
                Object v = ((java.util.Map<?, ?>) map).get(a.name());
                if (v instanceof Number) ranks[a.ordinal()] = Math.min(a.maxRank, ((Number) v).intValue());
            }
        }
        return ranks;
    }
}
