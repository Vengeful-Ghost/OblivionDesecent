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
    private Paint attackDebugPaint;
    private boolean debugAttack = false;

    // Death animation
    private boolean isDying = false;
    private long deathStartTime = 0;
    private static final long PLAYER_DEATH_DURATION = 800; // ms
    private Paint deathPaint;

    public Player() {
        paint = new Paint();
        paint.setColor(Color.BLUE);
        paint.setStyle(Paint.Style.FILL);

        attackDebugPaint = new Paint();
        attackDebugPaint.setColor(Color.argb(100, 255, 0, 0));

        deathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        deathPaint.setColor(Color.BLUE);
        deathPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * Equips a new weapon and updates the attack debug paint colour.
     * @param weapon the weapon to equip
     */
    public void equipWeapon(Weapon weapon) {
        this.currentWeapon = weapon;
        attackDebugPaint.setColor(Weapon.getRarityColor(weapon.rarity));
        attackDebugPaint.setAlpha(100);
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

        canvas.drawCircle(drawX, drawY, radius, paint);

        if (debugAttack) {
            float range = radius + currentWeapon.range;
            canvas.drawArc(
                    drawX - range, drawY - range,
                    drawX + range, drawY + range,
                    aimAngle - 45, 90, true, attackDebugPaint
            );
        }
    }

    public void setDebugAttack(boolean isAttacking) { this.debugAttack = isAttacking; }
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