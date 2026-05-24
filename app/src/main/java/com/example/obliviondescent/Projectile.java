package com.example.obliviondescent;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Projectile
 *
 * A moving object fired from the player toward a target direction.
 * It travels in a straight line at a fixed speed, deals damage on
 * contact with an enemy, and disappears either on hit or when it
 * has traveled its maximum range.
 */
public class Projectile {

    /* ============================================================
       POSITION & MOVEMENT
       ============================================================ */
    public float x, y;
    private float dx, dy;        // Normalized direction vector
    private float speed;
    private float distanceTraveled = 0f;
    private float maxRange;      // Disappears after traveling this far

    /* ============================================================
       STATS
       ============================================================ */
    public int damage;
    public float knockback;
    public boolean isDead = false;
    private float radius;
    private int color;

    /* ============================================================
       RENDERING
       ============================================================ */
    private Paint paint;

    /* ============================================================
       CONSTRUCTOR
       ============================================================ */
    public Projectile(float startX, float startY, float targetDX, float targetDY,
                      float speed, float maxRange, int damage, float knockback,
                      float radius, int color) {
        this.x = startX;
        this.y = startY;
        this.speed = speed;
        this.maxRange = maxRange;
        this.damage = damage;
        this.knockback = knockback;
        this.radius = radius;
        this.color = color;

        // Normalize the direction so diagonal shots aren't faster
        float length = (float) Math.sqrt(targetDX * targetDX + targetDY * targetDY);
        if (length > 0) {
            this.dx = targetDX / length;
            this.dy = targetDY / length;
        }

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
    }

    /* ============================================================
       UPDATE — called every logic frame
       ============================================================ */
    public void update() {
        if (isDead) return;

        x += dx * speed;
        y += dy * speed;
        distanceTraveled += speed;

        // Disappear once max range is exceeded
        if (distanceTraveled >= maxRange) {
            isDead = true;
        }
    }

    /* ============================================================
       DRAW
       ============================================================ */
    public void draw(Canvas canvas, GameCamera camera) {
        if (isDead) return;

        float drawX = x - camera.getOffsetX();
        float drawY = y - camera.getOffsetY();

        // Draw a glowing outer ring then the solid core
        Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setColor(color);
        glowPaint.setAlpha(80);
        canvas.drawCircle(drawX, drawY, radius * 1.8f, glowPaint);
        canvas.drawCircle(drawX, drawY, radius, paint);
    }

    /* ============================================================
       COLLISION RADIUS
       ============================================================ */
    public float getRadius() { return radius; }
}