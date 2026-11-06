package com.example.obliviondescent;

import android.graphics.RectF;
import java.util.Random;

/**
 * Represents a single room in the procedural floor layout.
 * Stores geometry, enemy capacity, exploration state for the minimap,
 * and corridor-connection flags for all four cardinal directions.
 */
public class Room {

    // JUNCTION: a small landing where a corridor turns a corner (FloorLayout.growWinding)
    public enum Type { ENTRY, EXIT, SMALL, MEDIUM, LARGE, HALLWAY, BOSS, EVENT, JUNCTION }

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

    // ── Shape ─────────────────────────────────────────────────────────────────
    /**
     * Solid wall inside the room's rectangle, in world space: cut corners, a missing wing (an L), all four
     * corners (a cross), or square columns. FloorLayout.shapeRooms keeps every door and the middle open.
     */
    public final java.util.List<RectF> blocks = new java.util.ArrayList<>();

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
            case JUNCTION: return 0; // a corridor's turn, not a room to fight in
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

    /** True on the room's walkable floor: inside its rectangle and outside its blocks. */
    public boolean contains(float px, float py) {
        if (!inRect(px, py)) return false;
        for (int i = 0; i < blocks.size(); i++) if (blocks.get(i).contains(px, py)) return false;
        return true;
    }

    /** True anywhere in the rectangle the room was laid out in, blocks included. */
    public boolean inRect(float px, float py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    /** The block within pad of (px, py), or null. */
    public RectF blockNear(float px, float py, float pad) {
        for (RectF b : blocks)
            if (px > b.left - pad && px < b.right + pad && py > b.top - pad && py < b.bottom + pad) return b;
        return null;
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
