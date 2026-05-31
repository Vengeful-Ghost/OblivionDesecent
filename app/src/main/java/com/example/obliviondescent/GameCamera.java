package com.example.obliviondescent;

/**
 * Manages the game camera that follows the player.
 * Computes the offset for translating world positions to screen positions
 * and supports a screen-shake effect on player hits.
 */
public class GameCamera {

    /** Horizontal and vertical offset for translating world space to screen space. */
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
     * Creates a new camera for the given screen dimensions.
     * @param width screen width in pixels
     * @param height screen height in pixels
     */
    public GameCamera(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    /**
     * Updates the camera offset to centre the given point on screen.
     * @param targetX target X position (typically the player's position)
     * @param targetY target Y position
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