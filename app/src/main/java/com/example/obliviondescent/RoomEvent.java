package com.example.obliviondescent;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Room events: each one gets a dead-end room of its own (a single doorway in and out), sized for what
 * happens there and dressed to match by Props. No enemies are ever placed in an event room; only the
 * Trial calls up its waves once its gong is struck. Solo runs only, as a co-op phone would answer alone.
 *
 * FloorLayout grows the rooms, Props decorates them around a centrepiece, GameActivity answers them
 * (checkRoomEvents) and GameDisplay gives them their own floor and map colour.
 */
enum RoomEvent {
    // A small blood-soaked chapel: a quarter of your max HP for a blessing (a level's worth of stat points)
    SHRINE("Blood Shrine", 4, 4, 1, 4, Color.rgb(58, 18, 18), Color.rgb(176, 36, 36)),
    // A smoky den of card tables: gold for a mystery item of any rarity
    GAMBLER("Gambler's Den", 5, 4, 1, 4, Color.rgb(46, 34, 26), Color.rgb(212, 175, 55)),
    // A crypt of coffins round a cursed chest: the rarest loot, but two elites rise when it opens
    CURSED("Cursed Crypt", 4, 5, 2, 3, Color.rgb(34, 24, 44), Color.rgb(176, 60, 220)),
    // A sand arena: the gong seals the door and three waves rise; clear them for a vault-grade chest
    TRIAL("Trial Arena", 8, 8, 3, 3, Color.rgb(62, 50, 36), Color.rgb(224, 72, 72)),
    // A cramped cell: spend a key to free the prisoner, who pays you with a relic
    PRISONER("Prison Cell", 4, 3, 2, 2, Color.rgb(38, 34, 28), Color.rgb(140, 142, 150)),
    // Shelves of rotting books round a lectern: read the tome for a level, cursed for the rest of the floor
    LIBRARY("Forgotten Library", 6, 5, 2, 2, Color.rgb(44, 32, 24), Color.rgb(181, 140, 224)),
    // A mossy grotto round a spring: one drink restores two fifths of your health
    FOUNTAIN("Healing Spring", 5, 5, 1, 3, Color.rgb(28, 40, 42), Color.rgb(96, 170, 200));

    final String title;
    final int tilesW, tilesH;    // fixed room size in tiles (Room.TILE)
    final int minFloor, weight;  // first floor it can appear on, and how often it is picked
    final int floorColor, mapColor;

    RoomEvent(String title, int tilesW, int tilesH, int minFloor, int weight, int floorColor, int mapColor) {
        this.title = title;
        this.tilesW = tilesW;
        this.tilesH = tilesH;
        this.minFloor = minFloor;
        this.weight = weight;
        this.floorColor = floorColor;
        this.mapColor = mapColor;
    }

    /** A weighted pick among the events open on this floor that aren't on it already; null if none are. */
    static RoomEvent pick(Random rng, int floor, Set<RoomEvent> taken) {
        List<RoomEvent> open = new ArrayList<>();
        int total = 0;
        for (RoomEvent e : values()) {
            if (floor < e.minFloor || taken.contains(e)) continue;
            open.add(e);
            total += e.weight;
        }
        if (total == 0) return null;
        int roll = rng.nextInt(total);
        for (RoomEvent e : open) {
            roll -= e.weight;
            if (roll < 0) return e;
        }
        return open.get(open.size() - 1);
    }
}
