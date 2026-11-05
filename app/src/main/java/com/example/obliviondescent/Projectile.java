package com.example.obliviondescent;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Represents a moving projectile fired by the player toward a target.
 * Travels in a straight line at a fixed speed, deals damage on enemy contact,
 * and disappears after a hit or once it exceeds its maximum range.
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
    public boolean hostile = false; // fired by an enemy: hits the player instead of enemies
    public boolean heavy = false;   // a charged shot breaks an enemy's guard
    private float radius;
    private int color;

    /* ============================================================
       RENDERING
       ============================================================ */
    private Paint paint;

    /* ============================================================
       CONSTRUCTOR
       ============================================================ */
    /**
     * Creates a new projectile with all parameters.
     * @param startX initial X position
     * @param startY initial Y position
     * @param targetDX horizontal direction (un-normalised)
     * @param targetDY vertical direction (un-normalised)
     * @param speed projectile speed in world units per frame
     * @param maxRange maximum travel distance before disappearing
     * @param damage damage dealt on impact
     * @param knockback knockback force applied to the enemy
     * @param radius projectile radius used for collision detection
     * @param color projectile colour
     */
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
    /**
     * Moves the projectile along its direction every logic frame and marks it dead once max range is exceeded.
     */
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
    /**
     * Draws the projectile with an outer glow ring and a solid core.
     * @param canvas the canvas to draw on
     * @param camera game camera used to translate world position to screen position
     */
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