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
    private float shakeX = 0f, shakeY = 0f; // this tick's shake, shared by every draw call
    private boolean placed = false;

    // Fraction of the distance to the target covered each tick; lower = lazier camera
    private static final float FOLLOW = 0.2f;
    // Farther than this (a teleport) the camera jumps instead of gliding
    private static final float SNAP_DISTANCE = 600f;

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
     * Glides the camera toward centring the given point, and rolls this tick's shake.
     * Called once per logic tick.
     * @param targetX target X position (typically the player's position)
     * @param targetY target Y position
     */
    public void update(float targetX, float targetY) {
        float goalX = targetX - (screenWidth / 2f);
        float goalY = targetY - (screenHeight / 2f);
        if (!placed || Math.abs(goalX - offsetX) > SNAP_DISTANCE || Math.abs(goalY - offsetY) > SNAP_DISTANCE) {
            offsetX = goalX;
            offsetY = goalY;
            placed = true;
        } else {
            offsetX += (goalX - offsetX) * FOLLOW;
            offsetY += (goalY - offsetY) * FOLLOW;
        }
        shakeX = getShakeOffset();
        shakeY = getShakeOffset();
    }

    public float getOffsetX() {
        return offsetX + shakeX;
    }

    public float getOffsetY() {
        return offsetY + shakeY;
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
     * Rolled once per tick in update() so the whole scene shakes as one.
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