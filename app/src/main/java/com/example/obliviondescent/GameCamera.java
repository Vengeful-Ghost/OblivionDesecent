package com.example.obliviondescent;

/**
 * מנהלת את מצלמת המשחק שעוקבת אחרי השחקן.
 * מחשבת offset לתרגום מיקומי עולם למיקומי מסך,
 * ותומכת באפקט רעידת מצלמה בעת פגיעה.
 */
public class GameCamera {

    /** היסט אופקי ואנכי לתרגום עולם → מסך. */
    private float offsetX = 0;
    private float offsetY = 0;

    private int screenWidth;
    private int screenHeight;

    // Screen shake
    private boolean isShaking = false;
    private long shakeStartTime = 0;
    private long shakeDuration = 0;
    private float shakeMagnitude = 0f;

    /**
     * יוצר מצלמה חדשה לגודל מסך הנתון.
     * @param width רוחב המסך בפיקסלים
     * @param height גובה המסך בפיקסלים
     */
    public GameCamera(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    /**
     * מעדכנת את offset המצלמה כדי למרכז את הנקודה הנתונה במסך.
     * @param targetX מיקום X היעד (בדרך כלל מיקום השחקן)
     * @param targetY מיקום Y היעד
     */
    public void update(float targetX, float targetY) {
        this.offsetX = targetX - (screenWidth / 2f);
        this.offsetY = targetY - (screenHeight / 2f);
    }

    public float getOffsetX() {
        return offsetX + getShakeOffset();
    }

    public float getOffsetY() {
        return offsetY + getShakeOffset();
    }

    /**
     * Called by GameActivity when the player takes a hit.
     * Stores the shake parameters — the actual offset is applied in update().
     */
    public void startShake(long duration, float magnitude) {
        isShaking = true;
        shakeStartTime = System.currentTimeMillis();
        shakeDuration = duration;
        shakeMagnitude = magnitude;
    }

    /**
     * Returns a random offset within [-magnitude, magnitude].
     * Called inside getOffsetX() and getOffsetY() so every draw
     * call automatically includes the shake without changing anything else.
     *
     * The shake fades out over its duration — magnitude shrinks linearly
     * from full to zero so it doesn't cut off abruptly.
     */
    private float getShakeOffset() {
        if (!isShaking) return 0f;

        long elapsed = System.currentTimeMillis() - shakeStartTime;
        if (elapsed > shakeDuration) {
            isShaking = false;
            return 0f;
        }

        // Linear fade: full magnitude at start, zero at end
        float progress = 1f - ((float) elapsed / shakeDuration);
        float currentMagnitude = shakeMagnitude * progress;

        // Random offset within current magnitude
        return (float)(Math.random() * currentMagnitude * 2 - currentMagnitude);
    }
}