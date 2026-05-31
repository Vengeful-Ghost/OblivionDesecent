package com.example.obliviondescent;

import android.graphics.RectF;
import java.util.Random;

/**
 * מייצגת חדר יחיד בפריסה הפרוצדורלית של הקומה.
 * מכיל גיאומטריה, כושר קיבול אויבים, מצב חקירה למיניאמפ,
 * ומצב חיבורי מסדרונות לכל ארבעת הכיוונים.
 */
public class Room {

    public enum Type { ENTRY, EXIT, SMALL, MEDIUM, LARGE, HALLWAY, BOSS }

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

    // ── Connection state — one corridor per cardinal side ─────────────────────
    public final boolean[] sideUsed = new boolean[4]; // [N, E, S, W]

    // ─────────────────────────────────────────────────────────────────────────
    /**
     * יוצר חדר חדש עם גיאומטריה וסוג נתונים.
     * @param id מזהה ייחודי של החדר בפריסה
     * @param x מיקום X פינה שמאל-עליונה
     * @param y מיקום Y פינה שמאל-עליונה
     * @param width רוחב החדר ביחידות עולם
     * @param height גובה החדר ביחידות עולם
     * @param type סוג החדר
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
            case HALLWAY: return 2;
            case SMALL:   return 3;
            case MEDIUM:  return 5;
            case LARGE:   return 8;
            default:      return 3;
        }
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    /** @return מרכז X של החדר */
    public float centerX() { return x + width  * 0.5f; }
    /** @return מרכז Y של החדר */
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
