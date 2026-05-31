package com.example.obliviondescent;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Represents a gold coin collectible in the game world.
 * Dropped at the enemy's death position with a small random spread.
 * Collected by the player by walking close enough.
 * Each coin is worth 1 — multiple coins are dropped for higher values.
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
     * Creates a new coin with a random offset around the drop point.
     * @param spawnX X position of the drop point
     * @param spawnY Y position of the drop point
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
     * Draws the coin on the canvas if it has not yet been collected.
     * @param canvas the canvas to draw on
     * @param camera game camera used to translate world position to screen position
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