package com.example.obliviondescent;

import android.graphics.RectF;
import java.util.Random;

/**
 * Represents a single room in the procedural floor layout.
 * Stores geometry, enemy capacity, exploration state for the minimap,
 * and corridor-connection flags for all four cardinal directions.
 */
public class Room {

    public enum Type { ENTRY, EXIT, SMALL, MEDIUM, LARGE, HALLWAY, BOSS, EVENT }

    // Direction indices shared across the layout system
    public static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;

    // 1 tile = 200 world pixels
    public static final float TILE = 200f;

    // ── Identity ──────────────────────────────────────────────────────────────
    public final int  id;
    public final Type type;

    // ── World-space rectangle (top-left origin) ───────────────────────────────
    public float x, y, width, height;

    // ── Enemy capacity ────────────────────────────────────────────────────────
    public int possibleEnemyCount;   // max enemies that can be placed here
    public int assignedEnemyCount;   // enemies actually assigned during generation

    // ── Exploration state (used by minimap) ───────────────────────────────────
    public boolean explored = false;

    // ── Room event (EVENT rooms only) ─────────────────────────────────────────
    public RoomEvent event;               // what waits here, or null
    public volatile boolean eventDone;    // answered, freed, drunk, won: the map icon dims

    // ── Connection state — one corridor per cardinal side ─────────────────────
    public final boolean[] sideUsed = new boolean[4]; // [N, E, S, W]

    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Creates a new room with the given geometry and type.
     * @param id unique room identifier within the layout
     * @param x top-left X position in world space
     * @param y top-left Y position in world space
     * @param width room width in world units
     * @param height room height in world units
     * @param type room type
     */
    public Room(int id, float x, float y, float width, float height, Type type) {
        this.id     = id;
        this.x      = x;
        this.y      = y;
        this.width  = width;
        this.height = height;
        this.type   = type;
        this.possibleEnemyCount = defaultCapacity(type);
    }

    private static int defaultCapacity(Type type) {
        switch (type) {
            case ENTRY:   return 0; // always safe
            case EXIT:    return 0; // always safe
            case BOSS:    return 0; // boss handled separately
            case EVENT:   return 0; // room events are never fought over (the Trial calls its own waves)
            case HALLWAY: return 2;
            case SMALL:   return 3;
            case MEDIUM:  return 5;
            case LARGE:   return 8;
            default:      return 3;
        }
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    /** @return centre X of the room */
    public float centerX() { return x + width  * 0.5f; }
    /** @return centre Y of the room */
    public float centerY() { return y + height * 0.5f; }

    public RectF getBounds() { return new RectF(x, y, x + width, y + height); }

    public boolean contains(float px, float py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    /**
     * Returns a random spawn point inside the room, keeping at least {@code margin}
     * pixels away from every wall so enemies don't clip the boundary on spawn.
     */
    public float[] randomSpawnPoint(Random rng, float margin) {
        float usableW = Math.max(1f, width  - 2f * margin);
        float usableH = Math.max(1f, height - 2f * margin);
        float rx = x + margin + rng.nextFloat() * usableW;
        float ry = y + margin + rng.nextFloat() * usableH;
        return new float[]{rx, ry};
    }
}
