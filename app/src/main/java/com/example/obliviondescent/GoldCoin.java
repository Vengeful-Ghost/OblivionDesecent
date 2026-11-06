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

    // The drop: from the body to its landing spot in FLIGHT ms, a high arc then two shrinking bounces
    static final long FLIGHT = 700;
    private final float fromX, fromY, hop;
    private final long born;
    private static final Paint SHADOW = new Paint(Paint.ANTI_ALIAS_FLAG);
    static { SHADOW.setColor(Color.BLACK); }

    /** Height of a bounce at u (0..1 through it) peaking at peak. */
    private static float arc(float u, float peak) {
        return peak * 4f * u * (1f - u);
    }

    /** True once the coin has landed and stopped bouncing; it can't be picked up in the air. */
    public boolean landed() {
        return System.currentTimeMillis() - born >= FLIGHT;
    }

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
        // The coin flies out from the body to where it lands (render only: x, y is where it ends up)
        fromX = spawnX;
        fromY = spawnY;
        born = System.currentTimeMillis();
        hop = 1f + (float) Math.random() * 0.5f;

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

        // Pixel art: a small spinning coin that flies off the body, bounces twice and settles, glinting
        Sprites.Anim spin = Sprites.get("item.coin");
        if (spin != null) {
            long now = System.currentTimeMillis(), age = now - born;
            float lift = 0f;
            if (age < FLIGHT) {
                float t = (float) age / FLIGHT, reach = Math.min(1f, t / 0.55f);
                reach = 1f - (1f - reach) * (1f - reach);
                drawX = fromX + (x - fromX) * reach - camera.getOffsetX();
                drawY = fromY + (y - fromY) * reach - camera.getOffsetY();
                lift = hop * (t < 0.55f ? arc(t / 0.55f, 64f) : t < 0.85f ? arc((t - 0.55f) / 0.3f, 18f)
                        : arc((t - 0.85f) / 0.15f, 5f));
            }
            // its shadow stays on the floor and shrinks while the coin is up in the air
            float s = 1f - Math.min(0.6f, lift / 110f);
            SHADOW.setAlpha((int) (90 * s));
            canvas.drawOval(drawX - 11f * s, drawY + 6f - 4f * s, drawX + 11f * s, drawY + 6f + 4f * s, SHADOW);
            // it spins twice as fast in flight, then each coin turns at its own pace
            long phase = (age < FLIGHT ? now * 2 : now) + (long) (x * 7 + y * 3);
            Sprites.draw(canvas, spin, spin.at(phase), drawX, drawY - 8f - lift, false, 0, 255);
            return;
        }

        // Outer gold circle
        canvas.drawCircle(drawX, drawY, COIN_RADIUS, coinPaint);

        // Small shine dot in top-left of coin
        canvas.drawCircle(drawX - 3f, drawY - 3f, 3f, shinePaint);
    }
}