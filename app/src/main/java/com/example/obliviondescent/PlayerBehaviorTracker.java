package com.example.obliviondescent;

/**
 * Tracks the player's behaviour patterns during a single run.
 * Enemies query it to adapt their movement accordingly.
 * Automatically reset on a new game because it is re-instantiated.
 */
public class PlayerBehaviorTracker {

    private static final int MIN_SAMPLES = 15;

    /* ============================================================
       DASH TRACKING
       ============================================================ */
    private int dashLeft  = 0;
    private int dashRight = 0;
    private int dashUp    = 0;
    private int dashDown  = 0;
    private int totalDashes = 0;

    /* ============================================================
       ATTACK DISTANCE TRACKING
       ============================================================ */
    private float totalAttackDistance = 0f;
    private int   attackSamples       = 0;

    /* ============================================================
       KITE TRACKING
       Whether player is moving AWAY from enemy while attacking
       ============================================================ */
    private int kiteSamples  = 0;
    private int kitePositive = 0; // times player was moving away while attacking

    /* ============================================================
       ADAPTATION STRENGTH
       Starts at 0, grows with samples, jumps on boss kills.
       Range: 0.0 (no adaptation) → 1.0 (full adaptation)
       ============================================================ */
    private float adaptationStrength = 0f;
    private static final float MAX_STRENGTH    = 1.0f;
    private static final float BOSS_JUMP       = 0.10f;
    private static final float SAMPLE_GROWTH   = 0.008f; // per sample after MIN_SAMPLES

    /* ============================================================
       RECORD METHODS — called by GameActivity
       ============================================================ */

    /**
     * Records a dash performed by the player and classifies it by dominant direction.
     * @param dx horizontal direction of the dash
     * @param dy vertical direction of the dash
     */
    public void recordDash(float dx, float dy) {
        totalDashes++;
        float absDX = Math.abs(dx);
        float absDY = Math.abs(dy);

        if (absDX > absDY) {
            if (dx > 0) dashRight++; else dashLeft++;
        } else {
            if (dy > 0) dashDown++; else dashUp++;
        }

        growStrength();
    }

    /**
     * Records an attack and the distance to the enemy, used to analyse combat style.
     * @param distanceToEnemy distance to the enemy at the time of the attack
     */
    public void recordAttack(float distanceToEnemy) {
        totalAttackDistance += distanceToEnemy;
        attackSamples++;
        growStrength();
    }

    /**
     * Records a kiting sample — whether the player was moving away from the enemy while attacking.
     * @param movingAway true if the player was moving away from the enemy
     */
    public void recordKiteSample(boolean movingAway) {
        kiteSamples++;
        if (movingAway) kitePositive++;
        growStrength();
    }

    /**
     * Boosts the adaptation strength when a boss is killed.
     */
    public void onBossKilled() {
        adaptationStrength = Math.min(MAX_STRENGTH,
                adaptationStrength + BOSS_JUMP);
    }

    private void growStrength() {
        int totalSamples = totalDashes + attackSamples + kiteSamples;
        if (totalSamples >= MIN_SAMPLES) {
            adaptationStrength = Math.min(MAX_STRENGTH,
                    adaptationStrength + SAMPLE_GROWTH);
        }
    }

    /* ============================================================
       QUERY METHODS — called by Enemy
       ============================================================ */

    /** 0.0 = no adaptation, 1.0 = full. */
    public float getAdaptationStrength() { return adaptationStrength; }

    /** True if enough data has been collected to start adapting. */
    public boolean isReady() {
        return (totalDashes + attackSamples + kiteSamples) >= MIN_SAMPLES;
    }

    /**
     * Returns the dominant dodge direction as a normalized vector.
     * Enemy will try to cut off this direction.
     * Returns null if not enough data.
     */
    public float[] getDominantDodgeDirection() {
        if (totalDashes < 5) return null;

        int max = Math.max(Math.max(dashLeft, dashRight),
                Math.max(dashUp, dashDown));
        if (max == dashLeft)  return new float[]{-1f,  0f};
        if (max == dashRight) return new float[]{ 1f,  0f};
        if (max == dashUp)    return new float[]{ 0f, -1f};
        return new float[]{ 0f,  1f};
    }

    /**
     * Average distance at which player attacks.
     * < 100 = close fighter, > 200 = ranged kiter.
     */
    public float getAverageAttackDistance() {
        if (attackSamples == 0) return 150f;
        return totalAttackDistance / attackSamples;
    }

    /**
     * 0.0 = never kites, 1.0 = always kiting.
     */
    public float getKiteRatio() {
        if (kiteSamples < 5) return 0f;
        return (float) kitePositive / kiteSamples;
    }

    public boolean isDashHeavy() { return totalDashes > 20; }
}