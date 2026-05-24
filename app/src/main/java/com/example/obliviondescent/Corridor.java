package com.example.obliviondescent;

import android.graphics.RectF;

/**
 * An axis-aligned rectangular passage connecting two rooms.
 * Width is always CORRIDOR_WIDTH (150 px); length varies with room spacing.
 */
public class Corridor {

    public float x, y, width, height; // world-space rectangle (top-left origin)
    public final int roomIdA, roomIdB; // the two rooms this corridor joins

    public Corridor(float x, float y, float w, float h, int a, int b) {
        this.x = x;   this.y = y;
        this.width = w; this.height = h;
        this.roomIdA = a; this.roomIdB = b;
    }

    public boolean contains(float px, float py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    public RectF getBounds() { return new RectF(x, y, x + width, y + height); }
}
