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
    private static final long PLAYER_DEATH_DURATION = 800; // ms
    private Paint deathPaint;

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
        deathPaint.setColor(Color.BLUE);
        deathPaint.setStyle(Paint.Style.FILL);
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
            long elapsed = System.currentTimeMillis() - deathStartTime;
            float progress = Math.min(1f, (float) elapsed / PLAYER_DEATH_DURATION);

            // Player expands outward and fades simultaneously
            float expandedRadius = radius * (1f + progress * 2.5f);
            int alpha = (int)(255 * (1f - progress));

            deathPaint.setAlpha(alpha);
            canvas.drawCircle(drawX, drawY, expandedRadius, deathPaint);
            return; // Skip normal draw
        }

        Weapon weapon = getWeapon();
        float ax = (float) Math.cos(Math.toRadians(aimAngle));
        float ay = (float) Math.sin(Math.toRadians(aimAngle));
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

    public void setInvulnerable(boolean invulnerable) { this.invulnerable = invulnerable; }
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