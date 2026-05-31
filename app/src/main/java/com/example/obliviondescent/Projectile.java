package com.example.obliviondescent;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * מייצגת קליע נע שנורה מהשחקן לכיוון יעד.
 * נע בקו ישר במהירות קבועה, גורם נזק בעת מגע עם אויב,
 * ונעלם לאחר פגיעה או כאשר עבר את טווח הירי המקסימלי.
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
    /**
     * יוצר קליע חדש עם פרמטרים מלאים.
     * @param startX מיקום התחלתי X
     * @param startY מיקום התחלתי Y
     * @param targetDX כיוון אופקי (לא מנורמל)
     * @param targetDY כיוון אנכי (לא מנורמל)
     * @param speed מהירות הקליע ביחידות לפריים
     * @param maxRange מרחק מקסימלי עד להיעלמות
     * @param damage נזק לפגיעה
     * @param knockback עוצמת דחיפה לאחור
     * @param radius רדיוס הקליע לחישובי התנגשות
     * @param color צבע הקליע
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
     * מזיז את הקליע בכיוונו בכל פריים לוגי ומסמנת אותו כמת אם עבר הטווח.
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
     * מצייר את הקליע עם זוהר חיצוני וגרעין מוצק.
     * @param canvas הקנבס לציור
     * @param camera מצלמת המשחק לחישוב מיקום מסך
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