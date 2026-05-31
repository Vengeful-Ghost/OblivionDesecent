package com.example.obliviondescent;

import android.graphics.RectF;

/**
 * מייצגת מסדרון מלבני המחבר בין שני חדרים.
 * הרוחב תמיד CORRIDOR_WIDTH (150px); האורך משתנה לפי מרווח החדרים.
 */
public class Corridor {

    public float x, y, width, height; // world-space rectangle (top-left origin)
    public final int roomIdA, roomIdB; // the two rooms this corridor joins

    /**
     * יוצר מסדרון חדש המחבר בין שני חדרים.
     * @param x מיקום X פינה שמאל-עליונה
     * @param y מיקום Y פינה שמאל-עליונה
     * @param w רוחב המסדרון
     * @param h גובה המסדרון
     * @param a מזהה חדר A
     * @param b מזהה חדר B
     */
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
