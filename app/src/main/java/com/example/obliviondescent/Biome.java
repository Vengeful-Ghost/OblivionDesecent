package com.example.obliviondescent;

import android.graphics.Color;

/**
 * Dungeon biomes: every five floors, right after each boss, the dungeon changes its stone, its fire,
 * which enemies crawl out of it and one rule. Floors 1-5 are the Crypt, 6-10 the Flooded Catacombs,
 * 11-15 the Infernal Forge and 16-20 the Hollow Void; then the cycle starts again, one ascension deeper.
 *
 * FloorLayout carries the floor's biome, GameDisplay paints its floors and walls, Props lights its torches
 * and places its hazards (puddles, vents, rifts), and GameActivity runs those hazards.
 */
enum Biome {
    CRYPT("The Crypt", "Where the dead were laid to rest",
            Color.rgb(42, 36, 33), Color.rgb(32, 28, 26), Color.rgb(104, 92, 84), Color.rgb(62, 53, 46),
            Color.rgb(26, 21, 17), Color.rgb(74, 62, 48), Color.rgb(62, 52, 40),
            Color.rgb(230, 110, 30), Color.rgb(255, 214, 120), 0xFF9A40, 150,
            Enemy.Type.ARCHER),
    // Water pools on the floor slow anyone who wades through them; bats and archers thrive here
    CATACOMBS("Flooded Catacombs", "Black water slows every step",
            Color.rgb(30, 38, 40), Color.rgb(24, 30, 32), Color.rgb(84, 100, 102), Color.rgb(44, 56, 58),
            Color.rgb(18, 24, 25), Color.rgb(48, 66, 70), Color.rgb(40, 54, 58),
            Color.rgb(110, 200, 170), Color.rgb(210, 255, 230), 0x70E0C0, 160,
            Enemy.Type.BAT, Enemy.Type.ARCHER, Enemy.Type.SPEEDER),
    // Lava vents rumble and erupt, burning whoever stands on them, enemies included
    FORGE("Infernal Forge", "The vents erupt. Do not stand on them",
            Color.rgb(44, 28, 24), Color.rgb(34, 22, 19), Color.rgb(112, 78, 62), Color.rgb(70, 42, 32),
            Color.rgb(30, 16, 12), Color.rgb(86, 48, 36), Color.rgb(70, 40, 30),
            Color.rgb(255, 130, 40), Color.rgb(255, 230, 150), 0xFF8030, 130,
            Enemy.Type.BOMBER, Enemy.Type.CHARGER, Enemy.Type.BRUTE, Enemy.Type.TANK),
    // The dark closes in: torchlight and your own light reach less far
    VOID("The Hollow Void", "The light reaches less far here",
            Color.rgb(30, 26, 40), Color.rgb(22, 19, 30), Color.rgb(90, 80, 112), Color.rgb(46, 38, 62),
            Color.rgb(18, 14, 26), Color.rgb(58, 48, 80), Color.rgb(46, 38, 64),
            Color.rgb(170, 110, 240), Color.rgb(230, 210, 255), 0x8B5FBF, 195,
            Enemy.Type.NECROMANCER, Enemy.Type.ARCHER, Enemy.Type.SPEEDER);

    final String title, rule;
    final int floor, corridor, wall, wallFace, mortar; // world colours
    final int mapRoom, mapCorridor;                    // minimap colours
    final int flame, flameCore, light;                 // torch fire and the RGB of its light pool
    final int darkness;                                // alpha of the dungeon's ambient dark (0-255)
    private final Enemy.Type[] favoured;

    Biome(String title, String rule, int floor, int corridor, int wall, int wallFace, int mortar,
          int mapRoom, int mapCorridor, int flame, int flameCore, int light, int darkness,
          Enemy.Type... favoured) {
        this.title = title;
        this.rule = rule;
        this.floor = floor;
        this.corridor = corridor;
        this.wall = wall;
        this.wallFace = wallFace;
        this.mortar = mortar;
        this.mapRoom = mapRoom;
        this.mapCorridor = mapCorridor;
        this.flame = flame;
        this.flameCore = flameCore;
        this.light = light;
        this.darkness = darkness;
        this.favoured = favoured;
    }

    /** The biome a floor belongs to: one per five floors, cycling. */
    static Biome forFloor(int floor) {
        return values()[(Math.max(1, floor) - 1) / 5 % values().length];
    }

    /** True for a floor that opens a new biome (6, 11, 16 ...), where the transition names it. */
    static boolean startsAt(int floor) {
        return floor > 1 && (floor - 1) % 5 == 0;
    }

    /** Enemies native to this biome turn up half again as often. */
    boolean favours(Enemy.Type type) {
        for (Enemy.Type t : favoured) if (t == type) return true;
        return false;
    }
}
