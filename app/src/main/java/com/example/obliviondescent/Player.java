package com.example.obliviondescent;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * מייצגת את דמות השחקן במשחק.
 * מנהלת מיקום, תנועה, ציוד נשק, ואנימציית מוות.
 * GameActivity מעדכנת את המיקום בכל פריים לוגי.
 */
public class Player {

    /** מיקום השחקן בעולם המשחק. */
    public float x = 1000f;
    public float y = 1000f;
    /** רדיוס גוף השחקן לחישובי התנגשות. */
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
     * מצייד את השחקן בנשק חדש ומעדכן צבע דיבאג ההתקפה.
     * @param weapon הנשק לציוד
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
     * מעדכנת את מיקום השחקן לפי קלט הג'ויסטיק.
     * @param joyDX כיוון אופקי מנורמל (-1 עד 1)
     * @param joyDY כיוון אנכי מנורמל (-1 עד 1)
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
     * מצייר את השחקן על הקנבס, כולל אנימציית מוות אם פעילה.
     * @param canvas הקנבס לציור
     * @param camera מצלמת המשחק לחישוב מיקום מסך
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
     * מאפסת את מצב אנימציית המוות כדי לאפשר הפעלה מחדש.
     */
    public void resetDeathAnimation() {
        isDying = false;
        deathStartTime = 0;
    }
}