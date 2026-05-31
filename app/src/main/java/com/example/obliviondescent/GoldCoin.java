package com.example.obliviondescent;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * מייצגת מטבע זהב שנאסף בעולם המשחק.
 * מושלך במיקום מות האויב עם פיזור אקראי קטן.
 * השחקן אוסף אותו על ידי הליכה מספיק קרוב.
 * כל מטבע שווה 1 — מושלכים מספר מטבעות לשווי גבוה יותר.
 */
public class GoldCoin {

    public float x, y;
    public boolean collected = false;

    // How close the player must be to collect (world units)
    public static final float COLLECT_RADIUS = 55f;

    private static final float COIN_RADIUS = 12f;

    private Paint coinPaint;
    private Paint shinePaint;

    /**
     * יוצר מטבע חדש עם פיזור אקראי סביב נקודת ההשלכה.
     * @param spawnX מיקום X של נקודת ההשלכה
     * @param spawnY מיקום Y של נקודת ההשלכה
     */
    public GoldCoin(float spawnX, float spawnY) {
        // Spread coins in a random radius around the drop point
        // so multiple coins don't stack on top of each other
        double angle = Math.random() * Math.PI * 2;
        float spread = 30f + (float)(Math.random() * 60f); // 30-90 units spread
        this.x = spawnX + (float)(Math.cos(angle) * spread);
        this.y = spawnY + (float)(Math.sin(angle) * spread);

        coinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        coinPaint.setColor(Color.rgb(255, 215, 0)); // Gold
        coinPaint.setStyle(Paint.Style.FILL);

        shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shinePaint.setColor(Color.rgb(255, 255, 180)); // Shine highlight
        shinePaint.setStyle(Paint.Style.FILL);
    }

    /**
     * מצייר את המטבע על הקנבס אם טרם נאסף.
     * @param canvas הקנבס לציור
     * @param camera מצלמת המשחק לחישוב מיקום מסך
     */
    public void draw(Canvas canvas, GameCamera camera) {
        if (collected) return;

        float drawX = x - camera.getOffsetX();
        float drawY = y - camera.getOffsetY();

        // Outer gold circle
        canvas.drawCircle(drawX, drawY, COIN_RADIUS, coinPaint);

        // Small shine dot in top-left of coin
        canvas.drawCircle(drawX - 3f, drawY - 3f, 3f, shinePaint);
    }
}