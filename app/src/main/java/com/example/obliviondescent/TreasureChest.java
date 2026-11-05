package com.example.obliviondescent;

import static com.example.obliviondescent.Props.*;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Represents a treasure chest that appears after killing a boss.
 * It is thrown out of the boss, tumbles through the air and lands with a bounce, then glows with a beam
 * in its reward's rarity colour until the player walks close enough to open it.
 * The reward item (set by GameActivity) is granted to the player on opening.
 */
public class TreasureChest {

    public float x, y;
    public boolean opened = false;
    public boolean landed = false; // set by GameActivity once the landing burst has played
    public boolean mimic = false;  // treasure room only: wakes up as an enemy instead of opening
    public boolean vault = false;  // vault chest: the rarest loot plus gold
    public boolean cursed = false; // room event: the rarest loot, but elites rise when it opens
    public boolean boss = false;   // thrown out by a slain boss

    public static final float OPEN_RADIUS = 80f;
    private static final int CURSE = Color.rgb(176, 60, 220); // a cursed chest glows purple
    private static final long DROP_TIME = 650;     // flight from the boss to the landing spot
    private static final float DROP_HEIGHT = 170f;
    private static final long BOUNCE_TIME = 300;
    private static final float BEAM_HEIGHT = 190f;

    // Reward item — set by GameActivity after construction
    public ShopItem rewardItem = null;
    public String   rewardItemName  = null;
    public int      rewardItemColor = Color.WHITE;

    private final float fromX, fromY;
    private final long launchAt;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /**
     * Creates a chest that flies from where the boss died to its landing spot.
     * @param spawnX landing X in world space
     * @param spawnY landing Y in world space
     * @param fromX X in world space the chest is thrown from
     * @param fromY Y in world space the chest is thrown from
     * @param launchAt time in milliseconds when it leaves the boss
     */
    public TreasureChest(float spawnX, float spawnY, float fromX, float fromY, long launchAt) {
        this.x = spawnX;
        this.y = spawnY;
        this.fromX = fromX;
        this.fromY = fromY;
        this.launchAt = launchAt;

        stroke.setStyle(Paint.Style.STROKE);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
        labelPaint.setShadowLayer(4f, 0f, 2f, Color.BLACK);
    }

    /** True once the chest has come down; it can't be opened mid-air. */
    public boolean hasLanded(long now) {
        return now >= launchAt + DROP_TIME;
    }

    /**
     * Draws the chest in flight, bouncing, glowing while closed, or open with the reward's name.
     * @param canvas the canvas to draw on
     * @param camera game camera used to translate world position to screen position
     */
    public void draw(Canvas canvas, GameCamera camera) {
        long now = System.currentTimeMillis();
        if (now < launchAt) return; // still waiting for its turn to fly out
        float t = Math.min(1f, (now - launchAt) / (float) DROP_TIME);
        float groundX = fromX + (x - fromX) * t - camera.getOffsetX();
        float groundY = fromY + (y - fromY) * t - camera.getOffsetY();
        float lift = (float) Math.sin(Math.PI * t) * DROP_HEIGHT;

        // The shadow stays on the ground and tightens as the chest comes down
        float s = 1f - 0.5f * lift / DROP_HEIGHT;
        fill.setColor(SHADOW);
        rect.set(groundX - 34 * s, groundY + 22 - 9 * s, groundX + 34 * s, groundY + 22 + 9 * s);
        canvas.drawOval(rect, fill);

        if (t >= 1f && !opened) drawLoot(canvas, groundX, groundY, now);

        canvas.save();
        canvas.translate(groundX, groundY - lift);
        if (t < 1f) {
            canvas.rotate((1f - t) * 360f); // one tumble on the way down
        } else {
            long since = now - launchAt - DROP_TIME;
            if (since < BOUNCE_TIME) {
                float k = since / (float) BOUNCE_TIME;
                float squash = (float) Math.sin(k * Math.PI) * (1f - k) * 0.35f;
                canvas.scale(1f + squash, 1f - squash, 0f, 25f);
            }
        }
        if (opened) drawOpen(canvas); else drawClosed(canvas);
        canvas.restore();

        if (t < 1f) return;
        if (opened) {
            labelPaint.setTextSize(18f);
            labelPaint.setColor(rewardItemName != null ? rewardItemColor : BONE_DIM);
            canvas.drawText(rewardItemName != null ? rewardItemName : "OPEN", groundX, groundY - 50f, labelPaint);
        } else {
            labelPaint.setTextSize(26f);
            labelPaint.setColor(cursed ? CURSE : GOLD_BRIGHT);
            canvas.drawText("!", groundX, groundY - 40f + (float) Math.sin(now / 200.0) * 5f, labelPaint);
        }
    }

    /** Pulsing glow, a beam of light and rising sparks in the reward's colour. */
    private void drawLoot(Canvas canvas, float cx, float cy, long now) {
        if (beamPaint.getShader() == null) {
            int rgb = (cursed ? CURSE : rewardItemColor) & 0x00FFFFFF;
            beamPaint.setShader(new LinearGradient(0, 0, 0, -BEAM_HEIGHT,
                    0x90000000 | rgb, rgb, Shader.TileMode.CLAMP));
            glowPaint.setShader(new RadialGradient(0, 0, 70,
                    0x80000000 | rgb, rgb, Shader.TileMode.CLAMP));
        }
        float pulse = 0.75f + 0.25f * (float) Math.sin(now / 250.0);
        canvas.save();
        canvas.translate(cx, cy);
        glowPaint.setAlpha((int) (255 * pulse));
        canvas.drawCircle(0, 0, 70, glowPaint);
        beamPaint.setAlpha((int) (255 * pulse));
        canvas.drawRect(-16, -BEAM_HEIGHT, 16, 0, beamPaint);
        fill.setColor(rewardItemColor);
        for (int i = 0; i < 4; i++) {
            float p = ((now + i * 350L) % 1400) / 1400f;
            fill.setAlpha((int) (230 * (1f - p)));
            canvas.drawCircle((float) Math.sin(now / 300.0 + i * 1.9) * 18f, -10f - p * 120f, 3.5f, fill);
        }
        canvas.restore();
    }

    /** Iron-banded wooden chest with a gold lock, centred on (0, 0). */
    private void drawClosed(Canvas canvas) {
        fill.setColor(WOOD_DARK);
        canvas.drawRect(-30, -6, 30, 25, fill);
        fill.setColor(WOOD_LIGHT);
        rect.set(-30, -25, 30, -2);
        canvas.drawRoundRect(rect, 10, 10, fill);
        fill.setColor(IRON);
        canvas.drawRect(-22, -24, -16, 25, fill);
        canvas.drawRect(16, -24, 22, 25, fill);
        stroke.setColor(GOLD_DEEP);
        stroke.setStrokeWidth(3f);
        canvas.drawLine(-30, -4, 30, -4, stroke);
        fill.setColor(GOLD_BRIGHT);
        canvas.drawRect(-7, -10, 7, 6, fill);
        fill.setColor(INK);
        canvas.drawCircle(0, -3, 2.5f, fill);
    }

    /** Open chest: the lid tipped back and an empty, dark inside. */
    private void drawOpen(Canvas canvas) {
        fill.setColor(WOOD_DARK);
        rect.set(-30, -40, 30, -18);
        canvas.drawRoundRect(rect, 10, 10, fill);
        fill.setColor(IRON);
        canvas.drawRect(-22, -40, -16, -18, fill);
        canvas.drawRect(16, -40, 22, -18, fill);
        fill.setColor(WOOD_DARK);
        canvas.drawRect(-30, -6, 30, 25, fill);
        fill.setColor(GROUT);
        canvas.drawRect(-26, -16, 26, -4, fill);
        fill.setColor(WOOD_LIGHT);
        canvas.drawRect(-30, -20, 30, -16, fill);
        canvas.drawRect(-30, -6, 30, -2, fill);
        fill.setColor(IRON);
        canvas.drawRect(-22, -6, -16, 25, fill);
        canvas.drawRect(16, -6, 22, 25, fill);
    }
}
