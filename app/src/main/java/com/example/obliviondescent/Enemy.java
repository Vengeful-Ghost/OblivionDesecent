package com.example.obliviondescent;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single enemy in the game.
 * Contains an AI state machine, per-floor stat scaling,
 * support for Gemini AI templates and a death animation.
 * Updated every logic frame by GameActivity.
 */
public class Enemy {

    /* ============================================================
       ENEMY TYPES
       ============================================================ */
    public enum Type {
        GRUNT,    // Balanced — the basic enemy
        SPEEDER,  // Fast, fragile, low damage
        TANK,     // Slow, tanky, hits hard
        BRUTE,     // Medium speed, high HP, medium damage
        ARCHER,    // Keeps its distance and shoots
        BOSS
    }

    /** A projectile fired this tick; GameActivity turns it into a hostile Projectile. */
    public static class Shot {
        public final float dirX, dirY, speed, range, radius;
        public final int damage;

        Shot(float dirX, float dirY, float speed, float range, float radius, int damage) {
            this.dirX = dirX;
            this.dirY = dirY;
            this.speed = speed;
            this.range = range;
            this.radius = radius;
            this.damage = damage;
        }
    }

    /* ============================================================
       FIELDS
       ============================================================ */
    public float x, y;
    public float radius;
    public float speed;
    public boolean isDead = false;
    public Type type;

    // Co-op: matches a guest's mirror to the host's enemy
    private static int nextId = 1;
    public int id = nextId++;
    public int templateIndex = -1; // AI template in the batch pool, -1 = none
    private float remoteX, remoteY;
    private boolean remotePlaced = false;
    private long predictedUntil = 0;

    private int maxHealth;
    private int health;
    private Weapon weapon;

    private Paint bodyPaint;
    private Paint healthBarBgPaint;
    private Paint healthBarFgPaint;
    private Paint labelPaint;

    // Death animation
    public boolean readyToRemove = false;  // True only after fade animation completes
    private long deathStartTime = 0;
    private static final long DEATH_DURATION = 500; // ms

    public boolean counted = false; // True once this kill has been reported to FloorManager

    private int goldDrop; // How many coins this enemy drops on death
    public boolean noGoldDrop = false;

    // AI-generated identity
    public String displayName = null;  // null = use type name
    public int    displayColor = android.graphics.Color.WHITE;
    public float  hpMultiplier  = 1.0f;
    public float  dmgMultiplier = 1.0f;

    // Wake-up system — enemy sits idle until player enters trigger radius
    public boolean isAwake = false;
    public float   triggerRadius;

    // Knockback velocity — applied each frame and decayed so motion is smooth
    public float knockbackVX = 0f;
    public float knockbackVY = 0f;

    // Per-enemy hit cooldown — prevents the same enemy from being hit multiple times
    // per swing. GameActivity sets this to System.currentTimeMillis() on each hit.
    public long lastHitTime = 0;
    public long hitFlashUntil = 0;

    // Attacks only land when the wind-up ends, so the player can see them coming and dodge
    private long windupDuration;
    private long lastAttackTime = System.currentTimeMillis();
    private float aimX = 1f, aimY = 0f;   // locked direction for strikes, shots and boss charges
    private float faceX = 1f, faceY = 0f; // where the body points

    // Results GameActivity resolves (and resets) right after update()
    public int   strikeDamage = 0;
    public float strikeReach  = 0f;
    public float strikeDirX = 1f, strikeDirY = 0f; // centre of the strike cone
    public float strikeArc = 180f;                  // half-angle in degrees; 180 hits all around
    public final List<Shot> pendingShots = new ArrayList<>();
    public boolean justEnraged = false;
    private static final float STRIKE_ARC = 60f;  // melee cone half-angle

    // Boss move set
    private enum BossMove { SLAM, CHARGE, BURST }
    private static final float BOSS_SLAM_RADIUS = 260f;
    private BossMove bossMove = BossMove.SLAM;
    private boolean enraged = false;
    private boolean rushHit = false;

    private FloorLayout layout;
    private final Path shapePath = new Path();
    private final RectF arcRect = new RectF();

    private static final Paint SHADOW_PAINT   = solid(Color.argb(90, 0, 0, 0));
    private static final Paint FLASH_PAINT    = solid(Color.argb(190, 255, 255, 255));
    private static final Paint OUTLINE_PAINT  = stroke(Color.argb(140, 0, 0, 0), 3f);
    private static final Paint TELEGRAPH_FILL = solid(Color.argb(70, 255, 40, 40));
    private static final Paint TELEGRAPH_RING = stroke(Color.argb(170, 255, 60, 60), 4f);
    private static final Paint AIM_PAINT      = stroke(Color.argb(150, 255, 90, 60), 4f);
    private static final Paint BOW_PAINT      = stroke(Color.rgb(200, 240, 230), 4f);
    private static final Paint STUN_PAINT     = solid(Color.rgb(255, 220, 90));
    private static final Paint ALERT_PAINT    = solid(Color.rgb(255, 70, 50));
    static {
        ALERT_PAINT.setTextSize(48f);
        ALERT_PAINT.setTextAlign(Paint.Align.CENTER);
        ALERT_PAINT.setFakeBoldText(true);
        ALERT_PAINT.setShadowLayer(4f, 0f, 2f, Color.BLACK);
    }

    // Behavior state machine
    private enum BehaviorState {
        APPROACH,   // Moving toward player
        WINDUP,     // Telegraphing an attack; it lands when the wind-up ends
        RETREAT,    // Post-attack pullback (melee only)
        BAIT,       // Deliberately backing off to bait a dash
        CHARGE,     // Full sprint ignoring normal logic
        STRAFE,     // Circling to cut off dodge direction
        RUSH,       // Boss charge attack
        STUNNED     // Wind-up interrupted by a heavy hit
    }

    private BehaviorState behaviorState = BehaviorState.APPROACH;
    private long stateStartTime = 0;
    private long stateDuration  = 0;

    // Retreat/charge positioning
    private float retreatTargetX = 0f;
    private float retreatTargetY = 0f;

    // Reference to tracker — set by GameActivity after spawn
    private PlayerBehaviorTracker behaviorTracker = null;

    public void setBehaviorTracker(PlayerBehaviorTracker tracker) {
        this.behaviorTracker = tracker;
    }

    /** Lets the enemy path through doors and corridors instead of walking into walls. */
    public void setLayout(FloorLayout layout) {
        this.layout = layout;
    }

    /* ============================================================
       CONSTRUCTOR
       ============================================================ */
    /**
     * Creates a new enemy at the given position with the given type.
     * Sets stats, weapon and wake radius according to the type.
     * @param startX initial X position in world space
     * @param startY initial Y position in world space
     * @param type enemy type (GRUNT, SPEEDER, TANK, BRUTE, ARCHER, BOSS)
     */
    public Enemy(float startX, float startY, Type type) {
        this.x = startX;
        this.y = startY;
        this.type = type;

        // --- Assign stats by type ---
        switch (type) {
            case GRUNT:
                maxHealth = 25;  speed = 4f;  radius = 40f;
                bodyPaint = makePaint(Color.rgb(200, 50, 50));
                weapon = new Weapon("Club", 5, 10f, 800, Weapon.Rarity.COMMON, Color.GRAY, 0.04f);
                goldDrop = 1 + (int)(Math.random() * 6);
                triggerRadius = 350f + (float)(Math.random() * 100f);
                break;
            case SPEEDER:
                maxHealth = 18;  speed = 9f;  radius = 28f;
                bodyPaint = makePaint(Color.rgb(255, 160, 0));
                weapon = new Weapon("Shiv", 3, 5f, 400, Weapon.Rarity.COMMON, Color.LTGRAY, 0.01f);
                goldDrop = 1 + (int)(Math.random() * 4);
                triggerRadius = 480f + (float)(Math.random() * 120f);
                break;
            case TANK:
                maxHealth = 120; speed = 2f;  radius = 65f;
                bodyPaint = makePaint(Color.rgb(120, 0, 180));
                weapon = new Weapon("Warhammer", 15, 20f, 1400, Weapon.Rarity.RARE, Color.rgb(0, 150, 255), 0.45f);
                goldDrop = 5 + (int)(Math.random() * 6);
                triggerRadius = 280f + (float)(Math.random() * 80f);
                break;
            case BRUTE:
                maxHealth = 65;  speed = 5f;  radius = 52f;
                bodyPaint = makePaint(Color.rgb(60, 160, 60));
                weapon = new Weapon("Axe", 9, 15f, 1000, Weapon.Rarity.RARE, Color.rgb(0, 150, 255), 0.2f);
                goldDrop = 3 + (int)(Math.random() * 6);
                triggerRadius = 400f + (float)(Math.random() * 100f);
                break;
            case ARCHER:
                maxHealth = 20;  speed = 3.5f; radius = 34f;
                bodyPaint = makePaint(Color.rgb(40, 170, 160));
                weapon = new Weapon("Bow", 6, 600f, 1800, Weapon.Rarity.COMMON,
                        Color.rgb(255, 110, 70), 0.05f, true);
                goldDrop = 2 + (int)(Math.random() * 4);
                triggerRadius = 550f + (float)(Math.random() * 100f);
                break;
            case BOSS:
                maxHealth = 180; speed = 3f; radius = 100f;
                bodyPaint = makePaint(Color.rgb(180, 0, 0));
                weapon = new Weapon("Void Cleave", 25, 5f, 1000,
                        Weapon.Rarity.LEGENDARY, Color.rgb(255, 215, 0), 0.6f);
                goldDrop = 25 + (int)(Math.random() * 16);
                triggerRadius = 0f; // Boss is always set awake=true on spawn
                break;
            default:
                maxHealth = 50;  speed = 4f;  radius = 40f;
                bodyPaint = makePaint(Color.RED);
                weapon = new Weapon("Fists", 3, 5f, 800, Weapon.Rarity.COMMON, Color.WHITE, 0.01f);
                goldDrop = 2 + (int)(Math.random() * 4);
                triggerRadius = 350f;
        }
        health = maxHealth;
        windupDuration = windupFor(type);

        // --- Health bar paints ---
        healthBarBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        healthBarBgPaint.setColor(Color.rgb(50, 50, 50));

        healthBarFgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        healthBarFgPaint.setColor(Color.GREEN);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(22f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
    }

    /**
     * Applies an AI-generated template to this enemy.
     * HP base = 10, DMG base = 5.
     */
    public void applyTemplate(EnemyTemplate template) {
        this.displayName  = template.name;
        this.displayColor = template.color;
        this.hpMultiplier  = template.hpMultiplier;
        this.dmgMultiplier = template.dmgMultiplier;

        bodyPaint.setColor(template.color);

        // Scale HP multiplicatively on top of the already floor-scaled base HP
        this.maxHealth = Math.max(1, (int)(this.maxHealth * hpMultiplier));
        this.health    = this.maxHealth;
        // getDamage() will multiply base by dmgMultiplier — see below

        // Projectile enemies are slower — player needs to be able to catch them
        if (weapon != null && weapon.isProjectile) {
            this.speed = Math.max(1.5f, this.speed * 0.65f);
        }
    }

    /* ============================================================
       UPDATE
       ============================================================ */
    /**
     * Checks whether the player has entered the enemy's wake radius and wakes it.
     * @param playerX player X position in world space
     * @param playerY player Y position in world space
     */
    public void tryWake(float playerX, float playerY) {
        if (isAwake || isDead) return;
        float dx = playerX - x;
        float dy = playerY - y;
        if (dx * dx + dy * dy <= triggerRadius * triggerRadius) {
            isAwake = true;
        }
    }

    /**
     * Updates the enemy AI every logic tick.
     * Runs a state machine with behaviours: approach, wind-up, retreat, bait, charge and strafe.
     * Bosses use their own move set (see updateBoss).
     * @param playerX player X position in world space
     * @param playerY player Y position in world space
     * @param playerRadius player body radius, used for reach checks
     */
    public void update(float playerX, float playerY, float playerRadius) {
        if (isDead || !isAwake) return;

        // Apply and decay knockback velocity each frame for smooth motion
        if (knockbackVX != 0f || knockbackVY != 0f) {
            x += knockbackVX;
            y += knockbackVY;
            knockbackVX *= 0.75f;
            knockbackVY *= 0.75f;
            if (Math.abs(knockbackVX) < 0.5f) knockbackVX = 0f;
            if (Math.abs(knockbackVY) < 0.5f) knockbackVY = 0f;
            if (Math.abs(knockbackVX) + Math.abs(knockbackVY) > 4f) return; // a hard shove interrupts walking
        }

        long now = System.currentTimeMillis();
        float dx = playerX - x;
        float dy = playerY - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > 0.01f) { faceX = dx / dist; faceY = dy / dist; }

        // Next door or corridor point on the way to the player; null when the path is straight
        float[] waypoint = layout != null ? layout.nextWaypoint(x, y, playerX, playerY, radius) : null;
        boolean direct = waypoint == null;

        if (type == Type.BOSS) {
            updateBoss(now, dx, dy, dist, playerRadius, waypoint);
            return;
        }

        boolean ranged = weapon.isProjectile;
        float reach = getStrikeReach();
        // Melee starts swinging slightly inside its reach so a target that stands still gets hit
        float attackRange = ranged ? playerRadius + weapon.range : playerRadius + reach - 10f;
        boolean canAttack = now - lastAttackTime >= weapon.cooldown && dist > 0.01f;

        // Determine if tracker has useful data
        float adaptStrength = (behaviorTracker != null && behaviorTracker.isReady())
                ? behaviorTracker.getAdaptationStrength() : 0f;

        // ============================================================
        // STATE MACHINE
        // ============================================================
        switch (behaviorState) {

            case APPROACH: {
                // Archers need a clear line (same room or corridor); melee only needs reach
                if (canAttack && dist <= attackRange && (direct || !ranged)) {
                    aimX = dx / dist;
                    aimY = dy / dist;
                    enterState(BehaviorState.WINDUP, windupDuration);
                    break;
                }
                if (!direct) {
                    moveToward(waypoint[0], waypoint[1], speed);
                    break;
                }
                if (ranged) {
                    keepDistance(dx, dy, dist);
                    break;
                }

                // Move toward player
                if (dist > 10f) {
                    float adaptedDX = dx / dist;
                    float adaptedDY = dy / dist;

                    // Adaptation: cut off dominant dodge direction
                    if (adaptStrength > 0 && behaviorTracker != null) {
                        float[] dodgeDir = behaviorTracker.getDominantDodgeDirection();
                        if (dodgeDir != null) {
                            // Steer slightly toward where the player will dodge
                            float cutX = dodgeDir[0] * adaptStrength * 0.4f;
                            float cutY = dodgeDir[1] * adaptStrength * 0.4f;
                            adaptedDX += cutX;
                            adaptedDY += cutY;
                            float len = (float) Math.sqrt(
                                    adaptedDX * adaptedDX + adaptedDY * adaptedDY);
                            if (len > 0) { adaptedDX /= len; adaptedDY /= len; }
                        }

                        // Kiter detected — charge at full sprint
                        float kiteRatio = behaviorTracker.getKiteRatio();
                        if (kiteRatio > 0.6f && adaptStrength > 0.3f) {
                            enterState(BehaviorState.CHARGE, 800);
                            break;
                        }

                        // Dash-heavy player — pause briefly to bait the dash
                        if (behaviorTracker.isDashHeavy() && adaptStrength > 0.4f
                                && dist < attackRange * 2.5f
                                && Math.random() < 0.003) {
                            enterState(BehaviorState.BAIT, 600 + (long)(Math.random() * 400));
                            break;
                        }

                        // Close fighter — back off to bait, then rush
                        float avgAttkDist = behaviorTracker.getAverageAttackDistance();
                        if (avgAttkDist < 100f && adaptStrength > 0.35f
                                && dist < attackRange * 1.5f
                                && Math.random() < 0.004) {
                            enterState(BehaviorState.BAIT, 500);
                            break;
                        }
                    }

                    x += adaptedDX * speed;
                    y += adaptedDY * speed;
                }
                break;
            }

            case WINDUP: {
                if (ranged) {
                    // Track the target until just before release, then the aim locks
                    if (now - stateStartTime < stateDuration - 150 && dist > 0.01f) {
                        aimX = dx / dist;
                        aimY = dy / dist;
                    }
                } else {
                    // Creep forward along the locked aim so standing still gets punished,
                    // while stepping out of the cone dodges the strike
                    x += aimX * speed * 0.25f;
                    y += aimY * speed * 0.25f;
                }

                if (now - stateStartTime >= stateDuration) {
                    lastAttackTime = now;
                    if (ranged) {
                        pendingShots.add(new Shot(aimX, aimY, 11f, weapon.range + 150f, 10f, getDamage()));
                        enterState(BehaviorState.APPROACH, 0);
                    } else {
                        strike(getDamage(), reach, STRIKE_ARC);
                        // Melee — retreat after attacking
                        float retreatDist = radius * 3f;
                        retreatTargetX = x - aimX * retreatDist;
                        retreatTargetY = y - aimY * retreatDist;
                        enterState(BehaviorState.RETREAT, 350);
                    }
                }
                break;
            }

            case RETREAT: {
                // Melee enemies back off after swinging — realistic hit-and-run
                float retDX = retreatTargetX - x;
                float retDY = retreatTargetY - y;
                float retDist = (float) Math.sqrt(retDX * retDX + retDY * retDY);

                if (retDist > 8f) {
                    x += (retDX / retDist) * speed * 1.1f;
                    y += (retDY / retDist) * speed * 1.1f;
                }

                if (now - stateStartTime >= stateDuration) {
                    enterState(BehaviorState.APPROACH, 0);
                }
                break;
            }

            case BAIT: {
                // Back away slightly to lure the player in or bait a dash
                if (dist > 10f) {
                    x -= (dx / dist) * speed * 0.4f;
                    y -= (dy / dist) * speed * 0.4f;
                }

                if (now - stateStartTime >= stateDuration) {
                    // After baiting — charge hard
                    enterState(BehaviorState.CHARGE,
                            400 + (long)(adaptStrength * 400));
                }
                break;
            }

            case CHARGE: {
                // Full sprint at the player — ignores normal caution, follows doors if needed
                float chargeSpeed = speed * (1.3f + adaptStrength * 0.5f);
                if (!direct) {
                    moveToward(waypoint[0], waypoint[1], chargeSpeed);
                } else if (dist > 10f) {
                    x += (dx / dist) * chargeSpeed;
                    y += (dy / dist) * chargeSpeed;
                }

                if (canAttack && dist <= attackRange) {
                    aimX = dx / dist;
                    aimY = dy / dist;
                    enterState(BehaviorState.WINDUP, windupDuration);
                } else if (now - stateStartTime >= stateDuration) {
                    enterState(BehaviorState.APPROACH, 0);
                }
                break;
            }

            case STUNNED: {
                if (now - stateStartTime >= stateDuration) enterState(BehaviorState.APPROACH, 0);
                break;
            }

            case STRAFE: {
                // Circle around the player to cut off a dodge direction
                float[] dodgeDir = (behaviorTracker != null)
                        ? behaviorTracker.getDominantDodgeDirection() : null;

                if (dodgeDir != null && dist > 10f) {
                    // Perpendicular to player direction, biased toward dodge cutoff
                    float perpX = -dy / dist;
                    float perpY =  dx / dist;

                    // Blend perpendicular movement with cutoff direction
                    float strafeX = perpX * 0.6f + dodgeDir[0] * 0.4f;
                    float strafeY = perpY * 0.6f + dodgeDir[1] * 0.4f;

                    // Also close the gap slowly
                    strafeX += (dx / dist) * 0.3f;
                    strafeY += (dy / dist) * 0.3f;

                    float len = (float) Math.sqrt(strafeX * strafeX + strafeY * strafeY);
                    if (len > 0) {
                        x += (strafeX / len) * speed * 0.9f;
                        y += (strafeY / len) * speed * 0.9f;
                    }
                } else if (dist > 10f) {
                    x += (dx / dist) * speed;
                    y += (dy / dist) * speed;
                }

                if (now - stateStartTime >= stateDuration) {
                    enterState(BehaviorState.APPROACH, 0);
                }
                break;
            }
        }

        // Periodically decide to strafe when adaptation is high enough
        if (adaptStrength > 0.5f
                && direct && !ranged
                && behaviorState == BehaviorState.APPROACH
                && dist < 400f
                && Math.random() < 0.002 * adaptStrength) {
            enterState(BehaviorState.STRAFE,
                    600 + (long)(adaptStrength * 600));
        }
    }

    private void enterState(BehaviorState newState, long duration) {
        behaviorState = newState;
        stateStartTime = System.currentTimeMillis();
        stateDuration  = duration;
    }

    /** Releases a strike along the locked aim; GameActivity checks whether the player is inside it. */
    private void strike(int damage, float reach, float arc) {
        strikeDamage = damage;
        strikeReach = reach;
        strikeArc = arc;
        strikeDirX = aimX;
        strikeDirY = aimY;
    }

    /**
     * Interrupts a wind-up in progress so the attack never lands. Bosses can't be staggered.
     * @return true if a wind-up was interrupted
     */
    public boolean stagger() {
        if (type == Type.BOSS || behaviorState != BehaviorState.WINDUP) return false;
        lastAttackTime = System.currentTimeMillis();
        enterState(BehaviorState.STUNNED, 700);
        return true;
    }

    private static long windupFor(Type type) {
        switch (type) {
            case SPEEDER: return 300;
            case BRUTE:   return 550;
            case TANK:    return 700;
            case ARCHER:  return 650;
            case BOSS:    return 900;
            default:      return 450;
        }
    }

    /** Distance from the enemy's centre that its melee strike reaches. */
    private float getStrikeReach() {
        return radius + weapon.range + 25f;
    }

    private void moveToward(float targetX, float targetY, float maxStep) {
        float mx = targetX - x;
        float my = targetY - y;
        float d = (float) Math.sqrt(mx * mx + my * my);
        if (d < 0.5f) return;
        // Never overshoot, so wide enemies can line up exactly with narrow doors
        float step = Math.min(maxStep, d);
        x += mx / d * step;
        y += my / d * step;
    }

    /** Archers hover at a comfortable distance instead of walking into melee. */
    private void keepDistance(float dx, float dy, float dist) {
        if (dist < 0.01f) return;
        float preferred = Math.max(220f, weapon.range * 0.6f);
        if (dist > preferred + 40f) {
            x += (dx / dist) * speed;
            y += (dy / dist) * speed;
        } else if (dist < preferred - 40f) {
            x -= (dx / dist) * speed * 0.7f;
            y -= (dy / dist) * speed * 0.7f;
        }
    }

    /**
     * Boss move set: walk toward the player, then pick a telegraphed move.
     * Close: SLAM (area hit) or BURST (ring of projectiles). Far: CHARGE (rush along a line) or BURST.
     * Below half health it becomes enraged: shorter wind-ups and denser bursts.
     */
    private void updateBoss(long now, float dx, float dy, float dist, float playerRadius, float[] waypoint) {
        if (!enraged && health <= maxHealth / 2) {
            enraged = true;
            justEnraged = true;
        }
        float pace = enraged ? 0.7f : 1f;

        switch (behaviorState) {
            case WINDUP: {
                // Charges and bursts keep tracking until just before release
                if (bossMove != BossMove.SLAM && now - stateStartTime < stateDuration - 200 && dist > 0.01f) {
                    aimX = dx / dist;
                    aimY = dy / dist;
                }
                if (now - stateStartTime < stateDuration) break;

                lastAttackTime = now;
                if (bossMove == BossMove.SLAM) {
                    strike((int) (getDamage() * 1.3f), BOSS_SLAM_RADIUS, 180f);
                    enterState(BehaviorState.APPROACH, 0);
                } else if (bossMove == BossMove.BURST) {
                    int count = enraged ? 16 : 10;
                    int damage = Math.max(1, (int) (getDamage() * 0.6f));
                    double start = Math.atan2(aimY, aimX);
                    for (int i = 0; i < count; i++) {
                        double angle = start + i * Math.PI * 2 / count;
                        pendingShots.add(new Shot((float) Math.cos(angle), (float) Math.sin(angle),
                                8f, 1100f, 16f, damage));
                    }
                    enterState(BehaviorState.APPROACH, 0);
                } else {
                    rushHit = false;
                    enterState(BehaviorState.RUSH, 900);
                }
                break;
            }

            case RUSH: {
                x += aimX * 17f;
                y += aimY * 17f;
                if (!rushHit && dist <= playerRadius + radius + 10f) {
                    rushHit = true;
                    strike(getDamage(), radius + 10f, 180f);
                }
                if (now - stateStartTime >= stateDuration) enterState(BehaviorState.APPROACH, 0);
                break;
            }

            default: {
                if (waypoint != null) {
                    moveToward(waypoint[0], waypoint[1], speed);
                    break;
                }
                if (dist > playerRadius + radius) {
                    x += (dx / dist) * speed;
                    y += (dy / dist) * speed;
                }
                if (now - lastAttackTime >= (long) (1800 * pace) && dist > 0.01f) {
                    boolean close = dist < BOSS_SLAM_RADIUS + 60f;
                    if (close) bossMove = Math.random() < 0.6 ? BossMove.SLAM : BossMove.BURST;
                    else       bossMove = Math.random() < 0.6 ? BossMove.CHARGE : BossMove.BURST;
                    aimX = dx / dist;
                    aimY = dy / dist;
                    long windup = bossMove == BossMove.SLAM ? windupDuration : 700;
                    enterState(BehaviorState.WINDUP, (long) (windup * pace));
                }
            }
        }
    }

    /**
     * Called by GameActivity after construction.
     * Scales HP and damage based on current floor.
     * Stats grow 8% per floor, so floor 5 enemies are ~40% stronger than floor 1.
     * We don't scale speed — that's handled by type selection per floor instead.
     */
    public void applyFloorScaling(int floor) {
        float multiplier = 1f + (floor - 1) * 0.08f;
        maxHealth = (int)(maxHealth * multiplier);
        health = maxHealth;
        // Scale weapon damage through a direct field write
        weapon = new Weapon(
                weapon.name,
                (int)(weapon.damage * multiplier),
                weapon.range,
                weapon.cooldown,
                weapon.rarity,
                weapon.color,
                weapon.knockback,
                weapon.isProjectile
        );
    }

    /** Boss strength from the AI batch, on top of floor scaling. */
    public void applyBossScaling(float hp, float dmg) {
        maxHealth = Math.max(1, (int) (maxHealth * hp));
        health = maxHealth;
        dmgMultiplier = dmg;
    }

    /* ============================================================
       DRAW (with Health Bar + Label)
       ============================================================ */
    /**
     * Draws the enemy, its health bar and its name label on the canvas.
     * @param canvas the canvas to draw on
     * @param camera game camera used to translate world position to screen position
     */
    public void draw(Canvas canvas, GameCamera camera) {
        float drawX = x - camera.getOffsetX();
        float drawY = y - camera.getOffsetY();
        long now = System.currentTimeMillis();

        if (isDead) {
            // --- DEATH ANIMATION ---
            long elapsed = now - deathStartTime;

            if (elapsed >= DEATH_DURATION) {
                // Animation finished — flag for removal
                readyToRemove = true;
                return;
            }

            // Progress goes from 0.0 (just died) to 1.0 (fully faded)
            float progress = (float) elapsed / DEATH_DURATION;

            // Shrink and fade
            bodyPaint.setAlpha((int)(255 * (1f - progress)));
            drawShape(canvas, drawX, drawY, radius * (1f - progress * 0.5f), bodyPaint);
            return; // Skip health bar during death
        }

        drawTelegraph(canvas, drawX, drawY, now);

        // --- NORMAL DRAW ---
        drawShape(canvas, drawX + 6f, drawY + 10f, radius, SHADOW_PAINT);
        bodyPaint.setAlpha(255); // Reset alpha in case it was changed
        drawShape(canvas, drawX, drawY, radius, bodyPaint);
        drawShape(canvas, drawX, drawY, radius, OUTLINE_PAINT);
        if (now < hitFlashUntil) drawShape(canvas, drawX, drawY, radius, FLASH_PAINT);

        // Stars circling the head while stunned
        if (behaviorState == BehaviorState.STUNNED) {
            for (int i = 0; i < 3; i++) {
                double a = now / 150.0 + i * Math.PI * 2 / 3;
                canvas.drawCircle(drawX + (float) Math.cos(a) * radius * 0.6f,
                        drawY - radius * 1.1f + (float) Math.sin(a) * 8f, 6f, STUN_PAINT);
            }
        }

        if (type == Type.ARCHER) {
            float r = radius * 1.15f;
            float face = (float) Math.toDegrees(Math.atan2(faceY, faceX));
            canvas.drawArc(drawX - r, drawY - r, drawX + r, drawY + r, face - 50f, 100f, false, BOW_PAINT);
        }

        // Boss gets a pulsing outer ring; faster and orange once enraged
        if (type == Type.BOSS) {
            float pulse = (float)(Math.sin(now / (enraged ? 120.0 : 300.0)) * 0.15 + 0.85);
            Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ringPaint.setColor(enraged ? Color.rgb(255, 140, 0) : Color.rgb(255, 50, 50));
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(6f);
            ringPaint.setAlpha((int)(200 * pulse));
            canvas.drawCircle(drawX, drawY, radius * 1.4f * pulse, ringPaint);

            // "BOSS" label is larger and red
            labelPaint.setColor(Color.rgb(255, 80, 80));
            labelPaint.setTextSize(32f);
        }

        // Health bar background (above the tallest shape)
        float barWidth = radius * 2.6f;
        float barHeight = 13f;
        float barLeft = drawX - barWidth / 2f;
        float barTop = drawY - radius * 1.3f - 24f;
        float barBottom = barTop + barHeight;

        canvas.drawRoundRect(
                new RectF(barLeft, barTop, barLeft + barWidth, barBottom),
                6f, 6f, healthBarBgPaint);

        float ratio = (float) health / maxHealth;
        if (ratio > 0.5f)       healthBarFgPaint.setColor(Color.GREEN);
        else if (ratio > 0.25f) healthBarFgPaint.setColor(Color.YELLOW);
        else                    healthBarFgPaint.setColor(Color.RED);

        if (ratio > 0) {
            canvas.drawRoundRect(
                    new RectF(barLeft, barTop, barLeft + barWidth * ratio, barBottom),
                    6f, 6f, healthBarFgPaint);
        }

        String label = (displayName != null) ? displayName : type.name();
        if (displayName != null) labelPaint.setColor(displayColor);
        canvas.drawText(label, drawX, barTop - 6f, labelPaint);

        // Reset label paint to defaults for non-boss enemies
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(22f);
    }

    /**
     * Shows where an attack in wind-up will land, filling up as the strike gets closer,
     * with a "!" over the attacker. Melee strikes are a cone, so stepping aside dodges them.
     */
    private void drawTelegraph(Canvas canvas, float drawX, float drawY, long now) {
        if (behaviorState != BehaviorState.WINDUP) return;
        float progress = stateDuration > 0
                ? Math.min(1f, (float) (now - stateStartTime) / stateDuration) : 1f;

        // Alert marker; pulses faster as the strike gets closer
        float pulse = 1f + 0.15f * (float) Math.sin(now / (60.0 - 30.0 * progress));
        ALERT_PAINT.setTextSize(48f * pulse);
        canvas.drawText("!", drawX, drawY - radius * 1.3f - 62f, ALERT_PAINT);

        if (type == Type.BOSS && bossMove == BossMove.CHARGE) {
            // The lane the boss is about to rush down (rush speed × ticks in 900 ms)
            float length = 17f * 54f;
            AIM_PAINT.setStrokeWidth(radius * 2f);
            AIM_PAINT.setAlpha((int) (40 + 60 * progress));
            canvas.drawLine(drawX, drawY, drawX + aimX * length, drawY + aimY * length, AIM_PAINT);
            AIM_PAINT.setStrokeWidth(4f);
            return;
        }
        if (type == Type.BOSS && bossMove == BossMove.BURST) {
            TELEGRAPH_RING.setAlpha((int) (80 + 150 * progress));
            canvas.drawCircle(drawX, drawY, radius * (1.1f + 0.5f * progress), TELEGRAPH_RING);
            return;
        }
        if (weapon.isProjectile) {
            AIM_PAINT.setAlpha((int) (60 + 160 * progress));
            canvas.drawLine(drawX, drawY, drawX + aimX * weapon.range, drawY + aimY * weapon.range, AIM_PAINT);
            return;
        }
        if (type == Type.BOSS) {
            // Slam hits all around
            TELEGRAPH_RING.setAlpha(170);
            canvas.drawCircle(drawX, drawY, BOSS_SLAM_RADIUS, TELEGRAPH_RING);
            TELEGRAPH_FILL.setAlpha((int) (50 + 70 * progress));
            canvas.drawCircle(drawX, drawY, radius + (BOSS_SLAM_RADIUS - radius) * progress, TELEGRAPH_FILL);
            return;
        }
        float reach = getStrikeReach();
        float start = (float) Math.toDegrees(Math.atan2(aimY, aimX)) - STRIKE_ARC;
        arcRect.set(drawX - reach, drawY - reach, drawX + reach, drawY + reach);
        TELEGRAPH_RING.setAlpha(170);
        canvas.drawArc(arcRect, start, STRIKE_ARC * 2f, true, TELEGRAPH_RING);
        float fill = radius + (reach - radius) * progress;
        arcRect.set(drawX - fill, drawY - fill, drawX + fill, drawY + fill);
        TELEGRAPH_FILL.setAlpha((int) (50 + 70 * progress));
        canvas.drawArc(arcRect, start, STRIKE_ARC * 2f, true, TELEGRAPH_FILL);
    }

    private void drawShape(Canvas canvas, float cx, float cy, float r, Paint paint) {
        float face = (float) Math.atan2(faceY, faceX);
        switch (type) {
            case SPEEDER: drawPolygon(canvas, cx, cy, r * 1.3f, 3, face, paint); break;
            case TANK:    drawPolygon(canvas, cx, cy, r * 1.25f, 4, (float) (Math.PI / 4), paint); break;
            case BRUTE:   drawPolygon(canvas, cx, cy, r * 1.12f, 6, 0f, paint); break;
            case ARCHER:  drawPolygon(canvas, cx, cy, r * 1.2f, 4, 0f, paint); break;
            case BOSS:    drawStar(canvas, cx, cy, r * 1.25f, r, 10, System.currentTimeMillis() / 2000f, paint); break;
            default:      canvas.drawCircle(cx, cy, r, paint);
        }
    }

    private void drawPolygon(Canvas canvas, float cx, float cy, float r, int sides, float rotation, Paint paint) {
        shapePath.reset();
        for (int i = 0; i < sides; i++) {
            double angle = rotation + i * Math.PI * 2 / sides;
            float px = cx + (float) Math.cos(angle) * r;
            float py = cy + (float) Math.sin(angle) * r;
            if (i == 0) shapePath.moveTo(px, py);
            else        shapePath.lineTo(px, py);
        }
        shapePath.close();
        canvas.drawPath(shapePath, paint);
    }

    /** Spiked outline alternating between two radii, used for the boss. */
    private void drawStar(Canvas canvas, float cx, float cy, float outer, float inner,
                          int points, float rotation, Paint paint) {
        shapePath.reset();
        for (int i = 0; i < points * 2; i++) {
            double angle = rotation + i * Math.PI / points;
            float r = (i % 2 == 0) ? outer : inner;
            float px = cx + (float) Math.cos(angle) * r;
            float py = cy + (float) Math.sin(angle) * r;
            if (i == 0) shapePath.moveTo(px, py);
            else        shapePath.lineTo(px, py);
        }
        shapePath.close();
        canvas.drawPath(shapePath, paint);
    }

    /* ============================================================
       COMBAT
       ============================================================ */
    /**
     * Deducts damage from health and triggers the death animation when health reaches zero.
     * @param dmg amount of damage to apply
     */
    public void takeDamage(int dmg) {
        health -= dmg;
        hitFlashUntil = System.currentTimeMillis() + 90;
        if (health <= 0) {
            health = 0;
            isDead = true;
            deathStartTime = System.currentTimeMillis();
        }
    }

    // Normal enemies hit hard; the boss keeps its own scale since its base damage is already high
    public int getDamage() { return Math.max(1, (int)(weapon.damage * dmgMultiplier * (type == Type.BOSS ? 0.65f : 1.6f)));}
    public Weapon getWeapon()   { return weapon; }
    public int getGoldDrop() { return goldDrop; }

    /** Body colour at full opacity, used for death particles. */
    public int getColor() { return bodyPaint.getColor() | 0xFF000000; }

    /** XP the player earns for this kill. */
    public int getXpReward() {
        switch (type) {
            case TANK:   return 10;
            case BRUTE:  return 6;
            case ARCHER: return 4;
            case BOSS:   return 40;
            default:     return 3;
        }
    }

    /* ============================================================
       CO-OP SYNC
       ============================================================ */

    /**
     * Host: appends "id,type,template,x,y,hp,maxHp,state,elapsed,duration,aim,bossMove,enraged".
     * hp 0 means it died, -1 that it died without dropping loot.
     */
    public void appendState(StringBuilder sb, long now) {
        sb.append(id).append(',').append(type.ordinal()).append(',').append(templateIndex).append(',')
                .append((int) x).append(',').append((int) y).append(',')
                .append(isDead ? (noGoldDrop ? -1 : 0) : health).append(',').append(maxHealth).append(',')
                .append(behaviorState.ordinal()).append(',')
                // Only a wind-up needs its timing; zero elsewhere keeps idle snapshots identical
                .append(behaviorState == BehaviorState.WINDUP ? now - stateStartTime : 0).append(',')
                .append(stateDuration).append(',')
                .append((int) Math.toDegrees(Math.atan2(aimY, aimX))).append(',')
                .append(bossMove.ordinal()).append(',').append(enraged ? 1 : 0);
    }

    /** Guest: copies the host's state from appendState's fields; a reported death plays out locally. */
    public void applyState(String[] f, long now) {
        remoteX = Float.parseFloat(f[3]);
        remoteY = Float.parseFloat(f[4]);
        if (!remotePlaced) {
            x = remoteX;
            y = remoteY;
            remotePlaced = true;
        }
        int hp = Integer.parseInt(f[5]);
        maxHealth = Integer.parseInt(f[6]);
        if (hp <= 0) {
            if (!isDead) {
                noGoldDrop = hp < 0;
                takeDamage(Integer.MAX_VALUE);
            }
            return;
        }
        health = now < predictedUntil ? Math.min(health, hp) : hp;
        behaviorState  = BehaviorState.values()[Integer.parseInt(f[7])];
        stateStartTime = now - Long.parseLong(f[8]);
        stateDuration  = Long.parseLong(f[9]);
        double aim = Math.toRadians(Integer.parseInt(f[10]));
        aimX = (float) Math.cos(aim);
        aimY = (float) Math.sin(aim);
        bossMove = BossMove.values()[Integer.parseInt(f[11])];
        boolean nowEnraged = "1".equals(f[12]);
        if (nowEnraged && !enraged) justEnraged = true;
        enraged = nowEnraged;
    }

    /** Guest: shows a hit right away (never lethal); the host's snapshots catch up within a moment. */
    public void predictHit(int damage) {
        health = Math.max(1, health - damage);
        hitFlashUntil = System.currentTimeMillis() + 90;
        predictedUntil = System.currentTimeMillis() + 500;
    }

    /** Guest: glides toward the host's latest position instead of running the AI. */
    public void followRemote() {
        if (isDead) return;
        float dx = remoteX - x;
        float dy = remoteY - y;
        x += dx * 0.3f;
        y += dy * 0.3f;
        if (behaviorState == BehaviorState.WINDUP) {
            faceX = aimX;
            faceY = aimY;
        } else if (dx * dx + dy * dy > 1f) {
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            faceX = dx / d;
            faceY = dy / d;
        }
    }

    /* ============================================================
       HELPER
       ============================================================ */
    private Paint makePaint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    private static Paint solid(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }

    private static Paint stroke(int color, float width) {
        Paint p = solid(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        return p;
    }
}