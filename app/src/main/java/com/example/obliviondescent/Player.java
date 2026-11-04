package com.example.obliviondescent;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Represents the player character in the game.
 * Manages position, movement, weapon equipment and the death animation.
 * GameActivity updates the position every logic frame.
 */
public class Player {

    /** Player position in world space. */
    public float x = 1000f;
    public float y = 1000f;
    /** Player body radius used for collision detection. */
    public float radius = 50f;

    public float speed = 10f;
    private float facingAngle = 90f;
    private float aimAngle = 0f;
    private Weapon currentWeapon;

    private Paint paint;

    public long flashUntil = 0;           // red flash after taking a hit
    private boolean invulnerable = false; // drawn see-through while dashing or just hit
    private Paint outlinePaint, weaponPaint, orbPaint, flashPaint, shadowPaint, highlightPaint;

    // Death animation
    private boolean isDying = false;
    private long deathStartTime = 0;
    private static final long PLAYER_DEATH_DURATION = 1000; // ms
    private Paint deathPaint;

    // Spin special: the blade sweeps a full turn around the body
    private static final long SPIN_TIME = 380;
    private volatile long spinStart = 0;
    private float spinFrom, spinReach;
    private int spinColor;
    private final Paint spinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF spinRect = new android.graphics.RectF();

    public Player() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(70, 120, 235));
        paint.setStyle(Paint.Style.FILL);

        outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outlinePaint.setColor(Color.argb(160, 10, 20, 60));
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(4f);

        weaponPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        weaponPaint.setStyle(Paint.Style.STROKE);
        weaponPaint.setStrokeWidth(7f);
        weaponPaint.setStrokeCap(Paint.Cap.ROUND);

        orbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        orbPaint.setStyle(Paint.Style.FILL);

        flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        flashPaint.setColor(Color.argb(170, 255, 40, 40));

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.argb(90, 0, 0, 0));

        highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setColor(Color.argb(45, 255, 255, 255));

        deathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        deathPaint.setColor(Color.WHITE);
        deathPaint.setStyle(Paint.Style.FILL);

        spinPaint.setStyle(Paint.Style.STROKE);
        spinPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * Starts the spin animation: the weapon whips a full turn around the body.
     * @param reach distance the spin hits, from the player's centre
     * @param color colour of the blade trail
     */
    public void startSpin(float reach, int color) {
        spinFrom = aimAngle;
        spinReach = reach;
        spinColor = color;
        spinStart = System.currentTimeMillis();
    }

    /**
     * Equips a new weapon.
     * @param weapon the weapon to equip
     */
    public void equipWeapon(Weapon weapon) {
        this.currentWeapon = weapon;
    }

    /** Returns current weapon; defaults to Fists if none equipped. */
    public Weapon getWeapon() {
        if (currentWeapon == null)
            currentWeapon = new Weapon("Fists", 5, 20f, 250, Weapon.Rarity.COMMON, Color.WHITE, 0.02f);
        return currentWeapon;
    }

    /**
     * Updates the player's position based on joystick input.
     * @param joyDX normalised horizontal direction (-1 to 1)
     * @param joyDY normalised vertical direction (-1 to 1)
     */
    public void update(float joyDX, float joyDY) {
        if (joyDX != 0 || joyDY != 0) {
            x += joyDX * speed;
            y += joyDY * speed;
            facingAngle = (float) Math.toDegrees(Math.atan2(joyDY, joyDX));
        }
    }

    /**
     * Called by GameActivity when health hits zero.
     * GameActivity waits PLAYER_DEATH_DURATION ms before
     * showing the game over dialog so the animation plays first.
     */
    public void startDeathAnimation() {
        isDying = true;
        deathStartTime = System.currentTimeMillis();
    }

    public boolean isDying() { return isDying; }
    public long getDeathDuration() { return PLAYER_DEATH_DURATION; }

    /**
     * Draws the player on the canvas, including the death animation when active.
     * @param canvas the canvas to draw on
     * @param camera game camera used to translate world position to screen position
     */
    public void draw(Canvas canvas, GameCamera camera) {
        float drawX = x - camera.getOffsetX();
        float drawY = y - camera.getOffsetY();

        if (isDying) {
            // The body flashes white and collapses while GameEffects.poof() scatters it
            float progress = Math.min(1f, (System.currentTimeMillis() - deathStartTime) / 220f);
            if (progress < 1f) {
                deathPaint.setAlpha((int) (255 * (1f - progress)));
                canvas.drawCircle(drawX, drawY, radius * (1f - progress * 0.7f), deathPaint);
            }
            return; // Skip normal draw
        }

        Weapon weapon = getWeapon();
        float angle = aimAngle;
        long sinceSpin = System.currentTimeMillis() - spinStart;
        if (sinceSpin < SPIN_TIME) {
            float t = (float) sinceSpin / SPIN_TIME;
            float sweep = (1f - (1f - t) * (1f - t)) * 400f; // eases out, a little over one turn
            angle = spinFrom + sweep;
            drawSpin(canvas, drawX, drawY, angle, sweep, t);
        }
        float ax = (float) Math.cos(Math.toRadians(angle));
        float ay = (float) Math.sin(Math.toRadians(angle));
        int alpha = invulnerable ? 130 : 255;

        canvas.drawCircle(drawX + 6f, drawY + 10f, radius, shadowPaint);

        // Weapon held toward the facing direction
        weaponPaint.setColor(weapon.color);
        weaponPaint.setAlpha(alpha);
        orbPaint.setColor(weapon.color);
        orbPaint.setAlpha(alpha);
        float handX = drawX + ax * radius * 0.8f;
        float handY = drawY + ay * radius * 0.8f;
        if (weapon.isProjectile) {
            float tipX = drawX + ax * (radius + 34f);
            float tipY = drawY + ay * (radius + 34f);
            canvas.drawLine(handX, handY, tipX, tipY, weaponPaint);
            canvas.drawCircle(tipX, tipY, 9f, orbPaint);
        } else if (weapon.range < 40f) {
            canvas.drawCircle(drawX + ax * (radius + 6f), drawY + ay * (radius + 6f), 12f, orbPaint);
        } else {
            float length = Math.min(weapon.range, 90f);
            canvas.drawLine(handX, handY, handX + ax * length, handY + ay * length, weaponPaint);
        }

        paint.setAlpha(alpha);
        canvas.drawCircle(drawX, drawY, radius, paint);
        canvas.drawCircle(drawX - radius * 0.25f, drawY - radius * 0.25f, radius * 0.55f, highlightPaint);
        canvas.drawCircle(drawX, drawY, radius, outlinePaint);
        if (System.currentTimeMillis() < flashUntil) canvas.drawCircle(drawX, drawY, radius, flashPaint);
    }

    /** Glowing blade out to the spin's reach, with a fading trail behind it. */
    private void drawSpin(Canvas canvas, float cx, float cy, float angle, float sweep, float t) {
        float fade = 1f - t;
        spinRect.set(cx - spinReach, cy - spinReach, cx + spinReach, cy + spinReach);
        float trail = Math.min(sweep, 170f) / 5f;
        spinPaint.setColor(spinColor);
        for (int i = 0; i < 5; i++) {
            spinPaint.setAlpha((int) (fade * (210 - i * 40)));
            spinPaint.setStrokeWidth(30f - i * 5f);
            canvas.drawArc(spinRect, angle - trail * (i + 1), trail, false, spinPaint);
        }
        double rad = Math.toRadians(angle);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        spinPaint.setAlpha((int) (fade * 255));
        spinPaint.setStrokeWidth(10f);
        canvas.drawLine(cx + cos * radius, cy + sin * radius,
                cx + cos * spinReach, cy + sin * spinReach, spinPaint);
    }

    public void setInvulnerable(boolean invulnerable) { this.invulnerable = invulnerable; }
    public void setColor(int color) { paint.setColor(color); }
    public int getColor() { return paint.getColor(); }
    public float getX() { return x; }
    public float getY() { return y; }

    public float getFacingAngle() { return facingAngle; }

    public void setAimAngle(float angle) { this.aimAngle = angle; }

    /**
     * Resets the death animation state so it can be triggered again.
     */
    public void resetDeathAnimation() {
        isDying = false;
        deathStartTime = 0;
    }
}