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
        BOSS,
        BOMBER,      // Runs in and blows itself up
        BAT,         // Tiny and fast, comes in swarms
        CHARGER,     // Paws the ground, then rushes in a straight line
        NECROMANCER  // Keeps its distance, casts bolts and raises the dead
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
    // Special kinds, synced to guests: elites hit twice as hard, mimics and thralls come from GameActivity
    public boolean elite, mimic, summoned;
    public boolean shield; // shield-bearer: closes in behind a raised shield
    private CoopSession.Track remote; // guest: the host's recent positions
    private static final float[] REMOTE_POS = new float[2]; // logic thread only
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

    // Guard: while blocking, hits from within GUARD_ARC degrees of the guard do nothing (heavy hits break it).
    // The guard turns only GUARD_TURN degrees a tick, so a dash can get round it.
    private static final float GUARD_ARC = 75f, GUARD_TURN = 3f;
    private long nextGuardAt = 0;
    private long lastSwingAt = 0; // when the last strike swung, for the weapon animation

    // Boss move set (new moves go at the end: the order is synced to co-op guests)
    private enum BossMove { SLAM, CHARGE, BURST, SUMMON, FAN, BLINK, SPIRAL, WHIRL, LEAP, VOLLEY, QUAKE, STORM }
    /** Boss archetype, chosen by the AI for each range; each one moves, attacks and looks different. */
    public enum BossKind { BRUTE, SUMMONER, SORCERER, HUNTER }
    private BossKind bossKind = BossKind.BRUTE;
    public int pendingSummons = 0;     // summoner: thralls GameActivity raises, then resets
    public boolean justBlinked = false;
    private long lastCastBeat = 0;
    private float castAngle = 0f;
    private static final float BOSS_SLAM_RADIUS = 260f, LEAP_RADIUS = 210f;
    private BossMove bossMove = BossMove.SLAM;
    private boolean enraged = false;
    // Third phase below a quarter of health (not the summoner): faster, with a final move
    private boolean frenzied = false;
    public boolean justFrenzied = false;
    private boolean rushHit = false;
    // Hunter's leap: flies leapDist along the aim from where it jumped
    private float leapDist = 0f, leapFromX, leapFromY;
    private boolean leapChained = false;
    private long lastRaise = 0; // necromancer
    /** Boss only: it sleeps until a player walks into this room. */
    public Room arena;

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
    private static final Paint DETAIL_PAINT   = solid(Color.WHITE);
    private static final Paint GLOW_PAINT     = solid(Color.WHITE);
    private static final Paint RUNE_PAINT     = stroke(Color.WHITE, 5f);
    private static final Paint RAGE_PAINT     = stroke(Color.RED, 6f);
    private static final Paint ELITE_GLOW     = solid(Color.rgb(244, 215, 122));
    private static final Paint ELITE_RING     = stroke(Color.rgb(244, 215, 122), 4f);
    private static final Paint MIMIC_PAINT    = solid(Color.BLACK);
    private static final Paint GUARD_GLOW     = stroke(Color.argb(90, 170, 210, 255), 16f);
    private static final Paint GUARD_EDGE     = stroke(Color.rgb(225, 240, 255), 5f);
    private static final Paint SHIELD_FILL    = solid(Color.rgb(92, 70, 48));
    private static final Paint SHIELD_RIM     = stroke(Color.rgb(176, 150, 96), 4f);
    // Same round shading as the player, so bodies read as solid
    private static final Paint SHADE_PAINT    = solid(Color.WHITE);
    private static final android.graphics.Matrix SHADE_MATRIX = new android.graphics.Matrix();
    static {
        SHADE_PAINT.setShader(Player.SHADE);
        GUARD_GLOW.setStrokeCap(Paint.Cap.ROUND);
        GUARD_EDGE.setStrokeCap(Paint.Cap.ROUND);
    }
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
        STUNNED,    // Wind-up interrupted by a heavy hit
        CAST,       // Boss channelling a phase-two move (spiral, whirl)
        BLOCK,      // Guard raised toward the player: creeps forward and can't attack
        LEAP        // Hunter boss in the air, about to land
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
                goldDrop = 3 + (int)(Math.random() * 6);
                triggerRadius = 350f + (float)(Math.random() * 100f);
                break;
            case SPEEDER:
                maxHealth = 18;  speed = 9f;  radius = 28f;
                bodyPaint = makePaint(Color.rgb(255, 160, 0));
                weapon = new Weapon("Shiv", 3, 5f, 400, Weapon.Rarity.COMMON, Color.LTGRAY, 0.01f);
                goldDrop = 3 + (int)(Math.random() * 4);
                triggerRadius = 480f + (float)(Math.random() * 120f);
                break;
            case TANK:
                maxHealth = 120; speed = 2f;  radius = 65f;
                bodyPaint = makePaint(Color.rgb(120, 0, 180));
                weapon = new Weapon("Warhammer", 15, 20f, 1400, Weapon.Rarity.RARE, Color.rgb(0, 150, 255), 0.45f);
                goldDrop = 7 + (int)(Math.random() * 6);
                triggerRadius = 280f + (float)(Math.random() * 80f);
                break;
            case BRUTE:
                maxHealth = 65;  speed = 5f;  radius = 52f;
                bodyPaint = makePaint(Color.rgb(60, 160, 60));
                weapon = new Weapon("Axe", 9, 15f, 1000, Weapon.Rarity.RARE, Color.rgb(0, 150, 255), 0.2f);
                goldDrop = 5 + (int)(Math.random() * 6);
                triggerRadius = 400f + (float)(Math.random() * 100f);
                break;
            case ARCHER:
                maxHealth = 20;  speed = 3.5f; radius = 34f;
                bodyPaint = makePaint(Color.rgb(40, 170, 160));
                weapon = new Weapon("Bow", 6, 600f, 1800, Weapon.Rarity.COMMON,
                        Color.rgb(255, 110, 70), 0.05f, true);
                goldDrop = 4 + (int)(Math.random() * 4);
                triggerRadius = 550f + (float)(Math.random() * 100f);
                break;
            case BOSS:
                maxHealth = 180; speed = 3f; radius = 100f;
                bodyPaint = makePaint(Color.rgb(180, 0, 0));
                weapon = new Weapon("Void Cleave", 25, 5f, 1000,
                        Weapon.Rarity.LEGENDARY, Color.rgb(255, 215, 0), 0.6f);
                goldDrop = 27 + (int)(Math.random() * 16);
                triggerRadius = 0f; // Boss is always set awake=true on spawn
                break;
            case BOMBER:
                maxHealth = 16;  speed = 6.5f; radius = 32f;
                bodyPaint = makePaint(Color.rgb(196, 86, 30));
                weapon = new Weapon("Powder Keg", 8, 15f, 600, Weapon.Rarity.COMMON, Color.rgb(255, 140, 40), 0.3f);
                goldDrop = 2 + (int)(Math.random() * 3);
                triggerRadius = 500f + (float)(Math.random() * 100f);
                displayName = "Bomber";
                displayColor = Color.rgb(255, 160, 80);
                break;
            case BAT:
                maxHealth = 8;   speed = 10f; radius = 20f;
                bodyPaint = makePaint(Color.rgb(96, 72, 118));
                weapon = new Weapon("Fangs", 2, 5f, 700, Weapon.Rarity.COMMON, Color.WHITE, 0.01f);
                goldDrop = 1 + (int)(Math.random() * 2);
                triggerRadius = 520f + (float)(Math.random() * 100f);
                displayName = "Bat";
                displayColor = Color.rgb(190, 160, 220);
                break;
            case CHARGER:
                maxHealth = 70;  speed = 3.5f; radius = 50f;
                bodyPaint = makePaint(Color.rgb(132, 86, 58));
                weapon = new Weapon("Horns", 10, 10f, 2600, Weapon.Rarity.COMMON, Color.rgb(230, 220, 195), 0.5f);
                goldDrop = 6 + (int)(Math.random() * 5);
                triggerRadius = 450f + (float)(Math.random() * 100f);
                displayName = "Charger";
                displayColor = Color.rgb(230, 180, 140);
                break;
            case NECROMANCER:
                maxHealth = 30;  speed = 3f;  radius = 36f;
                bodyPaint = makePaint(Color.rgb(48, 92, 60));
                weapon = new Weapon("Bone Staff", 5, 520f, 2000, Weapon.Rarity.COMMON,
                        Color.rgb(140, 255, 170), 0.05f, true);
                goldDrop = 6 + (int)(Math.random() * 5);
                triggerRadius = 560f + (float)(Math.random() * 100f);
                displayName = "Necromancer";
                displayColor = Color.rgb(140, 255, 170);
                break;
            default:
                maxHealth = 50;  speed = 4f;  radius = 40f;
                bodyPaint = makePaint(Color.RED);
                weapon = new Weapon("Fists", 3, 5f, 800, Weapon.Rarity.COMMON, Color.WHITE, 0.01f);
                goldDrop = 4 + (int)(Math.random() * 4);
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
        // The newer types keep their own name and look; only the template's strength applies
        boolean ownLook = type == Type.BOMBER || type == Type.BAT || type == Type.CHARGER || type == Type.NECROMANCER;
        if (!ownLook) {
            this.displayName  = template.name;
            this.displayColor = template.color;
            bodyPaint.setColor(template.color);
        }
        this.hpMultiplier  = template.hpMultiplier;
        this.dmgMultiplier = template.dmgMultiplier;

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
        if (arena != null) {
            if (arena.contains(playerX, playerY)) isAwake = true;
            return;
        }
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
                // Charger: paws the ground from a distance, then rushes down a straight lane
                if (type == Type.CHARGER && direct && canAttack && dist < 650f) {
                    aimX = dx / dist;
                    aimY = dy / dist;
                    enterState(BehaviorState.WINDUP, windupDuration);
                    break;
                }
                // Necromancer: now and then stops to raise the fallen
                if (type == Type.NECROMANCER && now - lastRaise > 6000 && dist < 700f) {
                    lastRaise = now;
                    bossMove = BossMove.SUMMON;
                    enterState(BehaviorState.WINDUP, 1000);
                    break;
                }
                // Archers need a clear line (same room or corridor); melee only needs reach
                if (canAttack && dist <= attackRange && (direct || !ranged)) {
                    aimX = dx / dist;
                    aimY = dy / dist;
                    enterState(BehaviorState.WINDUP, windupDuration);
                    break;
                }
                // Shield-bearers close the last stretch behind their shield
                if (shield && dist < 420f && dist > 0.01f && now >= nextGuardAt) {
                    aimX = dx / dist;
                    aimY = dy / dist;
                    enterState(BehaviorState.BLOCK, 1300 + (long) (Math.random() * 900));
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

                    if (type == Type.BAT) {
                        // Bats flutter from side to side as they close in
                        float wobble = (float) Math.sin(now / 110.0 + id) * 0.9f;
                        adaptedDX -= dy / dist * wobble;
                        adaptedDY += dx / dist * wobble;
                        float len = (float) Math.sqrt(adaptedDX * adaptedDX + adaptedDY * adaptedDY);
                        adaptedDX /= len;
                        adaptedDY /= len;
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
                } else if (type == Type.CHARGER) {
                    // Pawing the ground: it keeps turning to follow until just before it bolts
                    if (now - stateStartTime < stateDuration - 300 && dist > 0.01f) {
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
                    if (type == Type.NECROMANCER && bossMove == BossMove.SUMMON) {
                        pendingSummons = 2;
                        bossMove = BossMove.SLAM;
                        enterState(BehaviorState.APPROACH, 0);
                    } else if (ranged) {
                        pendingShots.add(new Shot(aimX, aimY, 11f, weapon.range + 150f, 10f, getDamage()));
                        enterState(BehaviorState.APPROACH, 0);
                    } else if (type == Type.BOMBER) {
                        // The fuse runs out: it blows itself up, hurting everyone close by
                        strike(getDamage() * 2, 150f, 180f);
                        noGoldDrop = true;
                        takeDamage(health);
                    } else if (type == Type.CHARGER) {
                        rushHit = false;
                        enterState(BehaviorState.RUSH, 700);
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

            case RUSH: {
                // Charger: thunders down its lane and gores whatever it meets
                x += aimX * 16f;
                y += aimY * 16f;
                if (!rushHit && dist <= playerRadius + radius + 10f) {
                    rushHit = true;
                    strike(getDamage() * 3 / 2, radius + 10f, 180f);
                }
                if (now - stateStartTime >= stateDuration) enterState(BehaviorState.APPROACH, 0);
                break;
            }

            case BLOCK: {
                // Creeps in at a slow pace with the guard toward the target
                if (dist > 0.01f) turnGuard(dx / dist, dy / dist);
                float pace = speed * 0.4f;
                if (!direct) {
                    moveToward(waypoint[0], waypoint[1], pace);
                } else if (dist > radius + playerRadius + 10f) {
                    x += dx / dist * pace;
                    y += dy / dist * pace;
                }
                if (now - stateStartTime >= stateDuration) {
                    nextGuardAt = now + 1200;
                    enterState(BehaviorState.APPROACH, 0);
                }
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
        lastSwingAt = System.currentTimeMillis();
    }

    // Turns the guard toward a direction, at most GUARD_TURN degrees
    private void turnGuard(float tx, float ty) {
        double cur = Math.atan2(aimY, aimX);
        double diff = Math.IEEEremainder(Math.atan2(ty, tx) - cur, Math.PI * 2);
        double max = Math.toRadians(GUARD_TURN);
        cur += Math.max(-max, Math.min(max, diff));
        aimX = (float) Math.cos(cur);
        aimY = (float) Math.sin(cur);
    }

    /** True while its guard is raised. */
    public boolean isGuarding() {
        return !isDead && behaviorState == BehaviorState.BLOCK;
    }

    /** True when a hit coming from (fromX, fromY) lands on the raised guard. */
    public boolean blocks(float fromX, float fromY) {
        if (isDead || behaviorState != BehaviorState.BLOCK) return false;
        float dx = fromX - x, dy = fromY - y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 0.01f) return true;
        return (dx * aimX + dy * aimY) / d >= (float) Math.cos(Math.toRadians(GUARD_ARC));
    }

    /** A heavy blow smashes the guard aside and leaves the enemy reeling. */
    public void breakGuard() {
        nextGuardAt = System.currentTimeMillis() + 2500;
        stun(1000);
    }

    /** Dazes the enemy for a while; bosses shrug it off. */
    public void stun(long ms) {
        if (type == Type.BOSS) return;
        lastAttackTime = System.currentTimeMillis();
        enterState(BehaviorState.STUNNED, ms);
    }

    /** After taking a hit, fighters sometimes raise their guard, and shield-bearers usually do. */
    public void maybeGuard() {
        if (isDead || mimic || (type != Type.GRUNT && type != Type.BRUTE && type != Type.TANK)) return;
        if (behaviorState != BehaviorState.APPROACH && behaviorState != BehaviorState.RETREAT) return;
        long now = System.currentTimeMillis();
        if (now < nextGuardAt || Math.random() > (shield ? 0.6 : 0.2)) return;
        aimX = faceX;
        aimY = faceY;
        nextGuardAt = now + 3000;
        enterState(BehaviorState.BLOCK, shield ? 1400 : 900);
    }

    /** Shield-bearer: sturdier, and it closes in behind a raised shield. */
    public void makeShield() {
        shield = true;
        maxHealth = Math.round(maxHealth * 1.3f);
        health = maxHealth;
        goldDrop += 2;
    }

    /** A rushing charger ran into a wall: the rush ends and it reels. True if that happened. */
    public boolean crash() {
        if (type != Type.CHARGER || behaviorState != BehaviorState.RUSH) return false;
        stun(1200);
        return true;
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
            case BOMBER:  return 700;
            case BAT:     return 220;
            case CHARGER: return 850;
            case NECROMANCER: return 800;
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
     * Boss move set by archetype. Brute: slams and charges. Summoner: keeps its distance, raises thralls
     * and fires bursts. Sorcerer: blinks beside you and fires aimed fans. Below half health each one
     * gains a new move (whirl, volley, spiral) and its wind-ups get shorter.
     */
    private void updateBoss(long now, float dx, float dy, float dist, float playerRadius, float[] waypoint) {
        if (!enraged && health <= maxHealth / 2) {
            enraged = true;
            justEnraged = true;
        }
        if (!frenzied && bossKind != BossKind.SUMMONER && health <= maxHealth / 4) {
            frenzied = true;
            justFrenzied = true;
        }
        float pace = frenzied ? 0.7f : enraged ? 0.85f : 1f;

        switch (behaviorState) {
            case WINDUP: {
                // Everything but the slam keeps tracking until just before release
                if (bossMove != BossMove.SLAM && bossMove != BossMove.QUAKE
                        && now - stateStartTime < stateDuration - 200 && dist > 0.01f) {
                    aimX = dx / dist;
                    aimY = dy / dist;
                    if (bossMove == BossMove.LEAP) leapDist = Math.min(dist, 560f);
                }
                if (now - stateStartTime < stateDuration) break;
                lastAttackTime = now;
                releaseBossMove(dx, dy);
                break;
            }

            case LEAP: {
                // In the air from where it jumped to where it aimed, then it lands with a slam
                float t = Math.min(1f, (float) (now - stateStartTime) / stateDuration);
                x = leapFromX + aimX * leapDist * t;
                y = leapFromY + aimY * leapDist * t;
                if (t >= 1f) {
                    strike((int) (getDamage() * 1.2f), LEAP_RADIUS, 180f);
                    if (frenzied && !leapChained) {
                        // Frenzied, it pounces again at once
                        leapChained = true;
                        enterState(BehaviorState.WINDUP, 450);
                    } else {
                        leapChained = false;
                        lastAttackTime = now; // the pause before the next move starts after landing
                        enterState(BehaviorState.APPROACH, 0);
                    }
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
                if (now - stateStartTime >= stateDuration) {
                    lastAttackTime = now;
                    enterState(BehaviorState.APPROACH, 0);
                }
                break;
            }

            case CAST: {
                if (bossMove == BossMove.SPIRAL || bossMove == BossMove.STORM) {
                    // Arms of bolts sweeping round: two, or four in the storm
                    int arms = bossMove == BossMove.STORM ? 4 : 2;
                    if (now - lastCastBeat >= 110) {
                        lastCastBeat = now;
                        castAngle += 0.45f;
                        int damage = Math.max(1, (int) (getDamage() * 0.45f));
                        for (int arm = 0; arm < arms; arm++) {
                            double a = castAngle + arm * Math.PI * 2 / arms;
                            pendingShots.add(new Shot((float) Math.cos(a), (float) Math.sin(a), 7f, 1000f, 14f, damage));
                        }
                    }
                } else {
                    // Whirl: spins toward the target, hitting all around every beat
                    if (dist > playerRadius + radius && dist > 0.01f) {
                        x += dx / dist * speed * 1.6f;
                        y += dy / dist * speed * 1.6f;
                    }
                    if (now - lastCastBeat >= 350) {
                        lastCastBeat = now;
                        strike(Math.max(1, (int) (getDamage() * 0.6f)), radius + 60f, 180f);
                    }
                }
                if (now - stateStartTime >= stateDuration) {
                    lastAttackTime = now;
                    enterState(BehaviorState.APPROACH, 0);
                }
                break;
            }

            default: {
                if (waypoint != null) {
                    moveToward(waypoint[0], waypoint[1], speed);
                    break;
                }
                if (bossKind == BossKind.BRUTE) {
                    if (dist > playerRadius + radius) {
                        x += (dx / dist) * speed;
                        y += (dy / dist) * speed;
                    }
                } else if (dist > 460f) {
                    // Casters hold a middle distance
                    x += (dx / dist) * speed;
                    y += (dy / dist) * speed;
                } else if (dist < 300f && dist > 0.01f) {
                    x -= (dx / dist) * speed * 0.7f;
                    y -= (dy / dist) * speed * 0.7f;
                }
                if (now - lastAttackTime >= (long) (2400 * pace) && dist > 0.01f) {
                    bossMove = pickBossMove(dist);
                    aimX = dx / dist;
                    aimY = dy / dist;
                    leapDist = Math.min(dist, 560f);
                    long windup = bossMove == BossMove.SLAM ? windupDuration
                            : bossMove == BossMove.SUMMON || bossMove == BossMove.SPIRAL
                            || bossMove == BossMove.QUAKE || bossMove == BossMove.STORM ? 950
                            : bossMove == BossMove.VOLLEY ? 600 : 750;
                    enterState(BehaviorState.WINDUP, (long) (windup * pace));
                }
            }
        }
    }

    /** Next attack for this archetype; phase-two moves join below half health, finales below a quarter. */
    private BossMove pickBossMove(float dist) {
        boolean phaseTwo = enraged && Math.random() < 0.35;
        boolean phaseThree = frenzied && Math.random() < 0.4;
        double roll = Math.random();
        switch (bossKind) {
            case SUMMONER:
                if (phaseTwo) return BossMove.FAN;
                return roll < 0.5 ? BossMove.SUMMON : BossMove.BURST;
            case SORCERER:
                if (phaseThree) return BossMove.STORM;
                if (phaseTwo) return BossMove.SPIRAL;
                if (dist < 260f || roll < 0.25) return BossMove.BLINK;
                return roll < 0.7 ? BossMove.FAN : BossMove.BURST;
            case HUNTER:
                if (phaseThree) return BossMove.LEAP;
                if (phaseTwo) return BossMove.SUMMON; // calls its hounds
                return dist > 520f || roll < 0.45 ? BossMove.LEAP : BossMove.VOLLEY;
            default:
                if (phaseThree) return BossMove.QUAKE;
                if (phaseTwo) return BossMove.WHIRL;
                if (dist < BOSS_SLAM_RADIUS + 60f) return roll < 0.6 ? BossMove.SLAM : BossMove.BURST;
                return roll < 0.6 ? BossMove.CHARGE : BossMove.BURST;
        }
    }

    private void releaseBossMove(float dx, float dy) {
        int damage = getDamage();
        int shot = Math.max(1, (int) (damage * 0.6f));
        switch (bossMove) {
            case SLAM:
                strike((int) (damage * 1.3f), BOSS_SLAM_RADIUS, 180f);
                break;
            case BURST:
                ring(enraged ? 16 : 10, shot);
                break;
            case FAN:
                fan(enraged ? 7 : 5, shot);
                break;
            case SUMMON:
                pendingSummons = enraged ? 3 : 2;
                break;
            case BLINK:
                // Reappear at a distance, then wind up the fan from there, so its lanes show where it lands
                blink(x + dx, y + dy);
                bossMove = BossMove.FAN;
                enterState(BehaviorState.WINDUP, enraged ? 460 : 650);
                return;
            case CHARGE:
                rushHit = false;
                enterState(BehaviorState.RUSH, 900);
                return;
            case LEAP:
                leapFromX = x;
                leapFromY = y;
                enterState(BehaviorState.LEAP, 480);
                return;
            case VOLLEY:
                volley(enraged ? 5 : 3, shot);
                break;
            case QUAKE:
                strike((int) (damage * 1.4f), BOSS_SLAM_RADIUS * 1.2f, 180f);
                ring(14, shot);
                break;
            default: // SPIRAL, STORM and WHIRL channel for a while
                lastCastBeat = 0;
                enterState(BehaviorState.CAST, bossMove == BossMove.WHIRL ? 1500 : 1800);
                return;
        }
        enterState(BehaviorState.APPROACH, 0);
    }

    /** Bolts in every direction, starting from the aim. */
    private void ring(int count, int damage) {
        double start = Math.atan2(aimY, aimX);
        for (int i = 0; i < count; i++) {
            double angle = start + i * Math.PI * 2 / count;
            pendingShots.add(new Shot((float) Math.cos(angle), (float) Math.sin(angle), 8f, 1100f, 16f, damage));
        }
    }

    /** Aimed spread of bolts, 10 degrees apart. */
    private void fan(int count, int damage) {
        double step = Math.toRadians(10);
        double start = Math.atan2(aimY, aimX) - step * (count - 1) / 2.0;
        for (int i = 0; i < count; i++) {
            double angle = start + step * i;
            pendingShots.add(new Shot((float) Math.cos(angle), (float) Math.sin(angle), 10f, 1000f, 14f, damage));
        }
    }

    /** Hunter: a tight spread of fast spears, 7 degrees apart. */
    private void volley(int count, int damage) {
        double step = Math.toRadians(7);
        double start = Math.atan2(aimY, aimX) - step * (count - 1) / 2.0;
        for (int i = 0; i < count; i++) {
            double angle = start + step * i;
            pendingShots.add(new Shot((float) Math.cos(angle), (float) Math.sin(angle), 15f, 900f, 11f, damage));
        }
    }

    /** Sorcerer: vanishes and reappears a good distance from the target on open floor, then faces it. */
    private void blink(float targetX, float targetY) {
        for (int i = 0; i < 12; i++) {
            double a = Math.random() * Math.PI * 2;
            float nx = targetX + (float) Math.cos(a) * 460f;
            float ny = targetY + (float) Math.sin(a) * 460f;
            if (layout == null || layout.isOnFloor(nx, ny, radius)) {
                x = nx;
                y = ny;
                justBlinked = true;
                break;
            }
        }
        float ax = targetX - x, ay = targetY - y;
        float d = (float) Math.sqrt(ax * ax + ay * ay);
        if (d > 0.01f) {
            aimX = ax / d;
            aimY = ay / d;
        }
    }

    /** Sets the archetype and its body colour; the hunter fights with a spear. */
    public BossKind getBossKind() { return bossKind; }

    public void setBossKind(BossKind kind) {
        bossKind = kind;
        bodyPaint.setColor(kind == BossKind.SUMMONER ? Color.rgb(46, 26, 58)
                : kind == BossKind.SORCERER ? Color.rgb(64, 92, 190)
                : kind == BossKind.HUNTER ? Color.rgb(112, 82, 46) : Color.rgb(180, 0, 0));
        if (kind == BossKind.HUNTER)
            weapon = new Weapon("Hunter's Spear", weapon.damage, weapon.range, weapon.cooldown, weapon.rarity,
                    Color.rgb(226, 214, 190), weapon.knockback);
    }

    /** Elite: twice the health and damage, three times the gold, and a named trait. */
    public void makeElite() {
        elite = true;
        maxHealth *= 2;
        health = maxHealth;
        dmgMultiplier *= 2f;
        goldDrop *= 3;
        EliteTrait trait = eliteTrait();
        if (trait == EliteTrait.HASTED) speed *= 1.35f;
        else if (trait == EliteTrait.SHIELDED && !shield) makeShield();
    }

    /** What makes an elite stand out; its title leads the nameplate. */
    public enum EliteTrait {
        VAMPIRIC("Vampiric"),   // heals from the hits it lands
        EXPLOSIVE("Explosive"), // blows up a moment after it dies
        SHIELDED("Shielded"),   // fights behind a raised shield
        HASTED("Hasted");       // a third faster

        public final String title;

        EliteTrait(String title) {
            this.title = title;
        }
    }

    /** The elite's trait, or null for others; picked from the id, so a co-op guest's mirror agrees. */
    public EliteTrait eliteTrait() {
        if (!elite) return null;
        EliteTrait trait = EliteTrait.values()[Math.floorMod(id * 7 + 3, EliteTrait.values().length)];
        // Only fighters can raise a shield
        if (trait == EliteTrait.SHIELDED && type != Type.GRUNT && type != Type.BRUTE && type != Type.TANK)
            return EliteTrait.VAMPIRIC;
        return trait;
    }

    /** Vampiric elites mend from the hits they land. */
    public void heal(int amount) {
        if (!isDead) health = Math.min(maxHealth, health + amount);
    }

    /** A summoner's thrall: drops nothing and doesn't count toward the floor. */
    public void makeThrall() {
        summoned = true;
        noGoldDrop = true;
        displayName = "Thrall";
        displayColor = Color.rgb(181, 140, 224);
        bodyPaint.setColor(Color.rgb(90, 60, 120));
    }

    /** A treasure-room chest that bites; it doesn't count toward the floor. */
    public void makeMimic() {
        mimic = true;
        displayName = "Mimic";
        displayColor = Color.rgb(244, 215, 122);
        bodyPaint.setColor(Color.rgb(120, 84, 48));
    }

    /**
     * Called by GameActivity after construction.
     * Scales HP and damage based on current floor.
     * HP grows 30% per floor to keep pace with shop weapons (6 + 2.5/floor damage); damage grows 10%.
     * We don't scale speed — that's handled by type selection per floor instead.
     */
    public void applyFloorScaling(int floor) {
        float multiplier = 1f + (floor - 1) * 0.10f;
        maxHealth = (int)(maxHealth * (1f + (floor - 1) * 0.30f));
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

    /** Co-op: more players means more health to chew through. */
    public void scaleHealth(float factor) {
        maxHealth = Math.max(1, Math.round(maxHealth * factor));
        health = maxHealth;
    }

    /** Ascension: +50% health and +25% damage per level; call after every other scaling. */
    public void ascend(int level) {
        if (level <= 0) return;
        scaleHealth(1f + 0.5f * level);
        dmgMultiplier *= 1f + 0.25f * level;
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

        if (elite) {
            // A gold aura marks an elite
            float pulse = 0.85f + 0.15f * (float) Math.sin(now / 220.0);
            ELITE_GLOW.setAlpha((int) (70 * pulse));
            canvas.drawCircle(drawX, drawY, radius * 1.6f * pulse, ELITE_GLOW);
            ELITE_RING.setAlpha((int) (220 * pulse));
            canvas.drawCircle(drawX, drawY, radius * 1.3f, ELITE_RING);
        }

        // --- NORMAL DRAW ---
        canvas.drawOval(drawX - radius * 0.95f, drawY + radius * 0.45f, drawX + radius * 0.95f,
                drawY + radius * 1.1f, SHADOW_PAINT);
        if (behaviorState == BehaviorState.LEAP && stateDuration > 0) {
            // Airborne: the body rises above its shadow
            float t = Math.min(1f, (float) (now - stateStartTime) / stateDuration);
            drawY -= (float) Math.sin(Math.PI * t) * 90f;
        }
        if (frenzied) {
            // Final phase: a burning aura
            float pulse = 0.7f + 0.3f * (float) Math.sin(now / 90.0);
            GLOW_PAINT.setColor(Color.rgb(255, 70, 30));
            GLOW_PAINT.setAlpha((int) (80 * pulse));
            canvas.drawCircle(drawX, drawY, radius * 1.9f * pulse, GLOW_PAINT);
        }
        float weaponAngle = weaponAngle(now);
        boolean behind = Math.sin(Math.toRadians(weaponAngle)) < -0.3;
        if (behind) drawWeapon(canvas, drawX, drawY, weaponAngle);
        // A fresh hit squashes the body for a moment
        boolean squash = now < hitFlashUntil + 60;
        if (squash) {
            canvas.save();
            canvas.scale(1.14f, 0.86f, drawX, drawY + radius);
        }
        bodyPaint.setAlpha(255); // Reset alpha in case it was changed
        drawShape(canvas, drawX, drawY, radius, bodyPaint);
        SHADE_MATRIX.setScale(radius, radius);
        SHADE_MATRIX.postTranslate(drawX, drawY);
        Player.SHADE.setLocalMatrix(SHADE_MATRIX);
        drawShape(canvas, drawX, drawY, radius, SHADE_PAINT);
        drawShape(canvas, drawX, drawY, radius, OUTLINE_PAINT);
        if (now < hitFlashUntil) drawShape(canvas, drawX, drawY, radius, FLASH_PAINT);
        // A lit bomber blinks faster and faster
        if (type == Type.BOMBER && behaviorState == BehaviorState.WINDUP
                && (now - stateStartTime) / Math.max(40, 160 - (now - stateStartTime) / 6) % 2 == 0)
            drawShape(canvas, drawX, drawY, radius, FLASH_PAINT);
        if (squash) canvas.restore();
        if (mimic) drawMimicFace(canvas, drawX, drawY, now);
        if (type == Type.BOMBER) drawFuse(canvas, drawX, drawY, now);
        if (type == Type.NECROMANCER) {
            DETAIL_PAINT.setColor(Color.rgb(140, 255, 170));
            canvas.drawCircle(drawX - radius * 0.18f, drawY - radius * 0.7f, radius * 0.09f, DETAIL_PAINT);
            canvas.drawCircle(drawX + radius * 0.18f, drawY - radius * 0.7f, radius * 0.09f, DETAIL_PAINT);
        }
        if (shield) drawShield(canvas, drawX, drawY);
        if (!behind) drawWeapon(canvas, drawX, drawY, weaponAngle);
        if (behaviorState == BehaviorState.BLOCK) drawGuard(canvas, drawX, drawY, now);

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

        if (type == Type.BOSS) {
            drawBossDetails(canvas, drawX, drawY, now);

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
        if (elite) {
            label = eliteTrait().title + " " + label;
            labelPaint.setColor(Color.rgb(244, 215, 122));
        }
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
        if (behaviorState == BehaviorState.LEAP) {
            // Where the hunter will land
            float t = stateDuration > 0 ? Math.min(1f, (float) (now - stateStartTime) / stateDuration) : 1f;
            float left = leapDist * (1f - t);
            TELEGRAPH_RING.setAlpha(220);
            canvas.drawCircle(drawX + aimX * left, drawY + aimY * left, LEAP_RADIUS, TELEGRAPH_RING);
            TELEGRAPH_FILL.setAlpha(120);
            canvas.drawCircle(drawX + aimX * left, drawY + aimY * left, LEAP_RADIUS, TELEGRAPH_FILL);
            return;
        }
        if (behaviorState != BehaviorState.WINDUP) return;
        float progress = stateDuration > 0
                ? Math.min(1f, (float) (now - stateStartTime) / stateDuration) : 1f;

        // Alert marker; pulses faster as the strike gets closer
        float pulse = 1f + 0.15f * (float) Math.sin(now / (60.0 - 30.0 * progress));
        ALERT_PAINT.setTextSize(48f * pulse);
        canvas.drawText("!", drawX, drawY - radius * 1.3f - 62f, ALERT_PAINT);

        if ((type == Type.BOSS && bossMove == BossMove.CHARGE) || type == Type.CHARGER) {
            // The lane it is about to rush down (rush speed x ticks in the rush)
            float length = type == Type.CHARGER ? 16f * 42f : 17f * 54f;
            AIM_PAINT.setStrokeWidth(radius * 2f);
            AIM_PAINT.setAlpha((int) (40 + 60 * progress));
            canvas.drawLine(drawX, drawY, drawX + aimX * length, drawY + aimY * length, AIM_PAINT);
            AIM_PAINT.setStrokeWidth(4f);
            return;
        }
        if (type == Type.BOSS && bossMove == BossMove.LEAP) {
            // The landing spot fills up
            float tx = drawX + aimX * leapDist, ty = drawY + aimY * leapDist;
            TELEGRAPH_RING.setAlpha(200);
            canvas.drawCircle(tx, ty, LEAP_RADIUS, TELEGRAPH_RING);
            TELEGRAPH_FILL.setAlpha((int) (50 + 70 * progress));
            canvas.drawCircle(tx, ty, LEAP_RADIUS * progress, TELEGRAPH_FILL);
            return;
        }
        if (type == Type.BOMBER) {
            // The blast radius of the lit fuse
            TELEGRAPH_RING.setAlpha(200);
            canvas.drawCircle(drawX, drawY, 150f, TELEGRAPH_RING);
            TELEGRAPH_FILL.setAlpha((int) (40 + 90 * progress));
            canvas.drawCircle(drawX, drawY, 150f * progress, TELEGRAPH_FILL);
            return;
        }
        if ((type == Type.BOSS || type == Type.NECROMANCER) && (bossMove == BossMove.BURST
                || bossMove == BossMove.SUMMON || bossMove == BossMove.SPIRAL || bossMove == BossMove.WHIRL
                || bossMove == BossMove.STORM)) {
            TELEGRAPH_RING.setAlpha((int) (80 + 150 * progress));
            canvas.drawCircle(drawX, drawY, radius * (1.1f + 0.5f * progress), TELEGRAPH_RING);
            return;
        }
        if (type == Type.BOSS && bossMove == BossMove.BLINK) {
            // About to vanish: a ring closes in on it
            TELEGRAPH_RING.setAlpha(200);
            canvas.drawCircle(drawX, drawY, radius * (1.8f - 0.8f * progress), TELEGRAPH_RING);
            return;
        }
        if (type == Type.BOSS && (bossMove == BossMove.FAN || bossMove == BossMove.VOLLEY)) {
            // The lanes of the coming bolts or spears
            boolean volley = bossMove == BossMove.VOLLEY;
            int lines = volley ? (enraged ? 5 : 3) : enraged ? 7 : 5;
            double step = Math.toRadians(volley ? 7 : 10);
            double start = Math.atan2(aimY, aimX) - step * (lines - 1) / 2.0;
            AIM_PAINT.setAlpha((int) (60 + 160 * progress));
            for (int i = 0; i < lines; i++) {
                double a = start + step * i;
                canvas.drawLine(drawX, drawY, drawX + (float) Math.cos(a) * 500f,
                        drawY + (float) Math.sin(a) * 500f, AIM_PAINT);
            }
            return;
        }
        if (weapon.isProjectile) {
            AIM_PAINT.setAlpha((int) (60 + 160 * progress));
            canvas.drawLine(drawX, drawY, drawX + aimX * weapon.range, drawY + aimY * weapon.range, AIM_PAINT);
            return;
        }
        if (type == Type.BOSS) {
            // Slam hits all around; the quake reaches further
            float slam = bossMove == BossMove.QUAKE ? BOSS_SLAM_RADIUS * 1.2f : BOSS_SLAM_RADIUS;
            TELEGRAPH_RING.setAlpha(170);
            canvas.drawCircle(drawX, drawY, slam, TELEGRAPH_RING);
            TELEGRAPH_FILL.setAlpha((int) (50 + 70 * progress));
            canvas.drawCircle(drawX, drawY, radius + (slam - radius) * progress, TELEGRAPH_FILL);
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

    /**
     * Where the held weapon points: resting at the side, drawn back over the shoulder through a wind-up,
     * sweeping through a strike, or held across the body while guarding.
     */
    private float weaponAngle(long now) {
        float aim = (float) Math.toDegrees(Math.atan2(aimY, aimX));
        long since = now - lastSwingAt;
        if (since < 200) {
            float t = since / 200f;
            return aim - 120f + 180f * (1f - (1f - t) * (1f - t));
        }
        if (behaviorState == BehaviorState.WINDUP && stateDuration > 0) {
            float p = Math.min(1f, (float) (now - stateStartTime) / stateDuration);
            return aim + 40f - 160f * p;
        }
        if (behaviorState == BehaviorState.BLOCK) return aim + 90f;
        return (float) Math.toDegrees(Math.atan2(faceY, faceX)) + 40f;
    }

    /** The enemy's weapon in its hand; casters and archers have their own looks. */
    private void drawWeapon(Canvas canvas, float cx, float cy, float angle) {
        if (mimic || type == Type.ARCHER || type == Type.BOMBER || type == Type.BAT || type == Type.CHARGER
                || (type == Type.BOSS && bossKind != BossKind.BRUTE && bossKind != BossKind.HUNTER)) return;
        float length = Math.max(40f, radius * 0.8f + weapon.range + 12f);
        double rad = Math.toRadians(angle);
        float hand = radius * 0.75f;
        float hx = cx + (float) Math.cos(rad) * hand, hy = cy + (float) Math.sin(rad) * hand;
        if (behaviorState == BehaviorState.BLOCK && !shield) {
            // Held crosswise in front, as a parry
            hx = cx + aimX * radius * 0.95f - (float) Math.cos(rad) * length * 0.45f;
            hy = cy + aimY * radius * 0.95f - (float) Math.sin(rad) * length * 0.45f;
        }
        Weapon.draw(canvas, weapon.kind(), weapon.color, hx, hy, angle, length, 255);
    }

    /** Bomber: a short fuse on top with a sputtering spark. */
    private void drawFuse(Canvas canvas, float cx, float cy, long now) {
        RUNE_PAINT.setColor(Color.rgb(70, 50, 30));
        canvas.drawLine(cx, cy - radius * 0.9f, cx + radius * 0.35f, cy - radius * 1.35f, RUNE_PAINT);
        float flicker = 0.7f + 0.3f * (float) Math.sin(now / 35.0);
        GLOW_PAINT.setColor(Color.rgb(255, 170, 40));
        GLOW_PAINT.setAlpha(150);
        canvas.drawCircle(cx + radius * 0.35f, cy - radius * 1.35f, 11f * flicker, GLOW_PAINT);
        DETAIL_PAINT.setColor(Color.rgb(255, 240, 180));
        canvas.drawCircle(cx + radius * 0.35f, cy - radius * 1.35f, 4f * flicker, DETAIL_PAINT);
    }

    /** A wooden shield with an iron rim: carried at the side, raised in front while guarding. */
    private void drawShield(Canvas canvas, float cx, float cy) {
        boolean raised = behaviorState == BehaviorState.BLOCK;
        double dir = raised ? Math.atan2(aimY, aimX) : Math.atan2(faceY, faceX) - Math.toRadians(70);
        float dist = raised ? radius + 6f : radius * 0.85f;
        float half = radius * (raised ? 0.85f : 0.65f);
        canvas.save();
        canvas.translate(cx + (float) Math.cos(dir) * dist, cy + (float) Math.sin(dir) * dist);
        canvas.rotate((float) Math.toDegrees(dir));
        canvas.drawRoundRect(-9f, -half, 9f, half, 9f, 9f, SHIELD_FILL);
        canvas.drawRoundRect(-9f, -half, 9f, half, 9f, 9f, SHIELD_RIM);
        canvas.drawCircle(0f, 0f, 5f, SHIELD_RIM);
        canvas.restore();
    }

    /** The raised guard: a bright arc in front, showing which hits it will stop. */
    private void drawGuard(Canvas canvas, float cx, float cy, long now) {
        float r = radius + 26f;
        float start = (float) Math.toDegrees(Math.atan2(aimY, aimX)) - GUARD_ARC;
        float pulse = 0.8f + 0.2f * (float) Math.sin(now / 90.0);
        arcRect.set(cx - r, cy - r, cx + r, cy + r);
        GUARD_GLOW.setAlpha((int) (90 * pulse));
        canvas.drawArc(arcRect, start, GUARD_ARC * 2f, false, GUARD_GLOW);
        GUARD_EDGE.setAlpha((int) (235 * pulse));
        canvas.drawArc(arcRect, start, GUARD_ARC * 2f, false, GUARD_EDGE);
    }

    private void drawShape(Canvas canvas, float cx, float cy, float r, Paint paint) {
        float face = (float) Math.atan2(faceY, faceX);
        if (mimic) {
            drawPolygon(canvas, cx, cy, r * 1.25f, 4, (float) (Math.PI / 4), paint);
            return;
        }
        switch (type) {
            case SPEEDER: drawPolygon(canvas, cx, cy, r * 1.3f, 3, face, paint); break;
            case TANK:    drawPolygon(canvas, cx, cy, r * 1.25f, 4, (float) (Math.PI / 4), paint); break;
            case BRUTE:   drawPolygon(canvas, cx, cy, r * 1.12f, 6, 0f, paint); break;
            case ARCHER:  drawPolygon(canvas, cx, cy, r * 1.2f, 4, 0f, paint); break;
            case BAT:     drawBat(canvas, cx, cy, r, paint); break;
            case CHARGER: drawCharger(canvas, cx, cy, r, face, paint); break;
            case NECROMANCER: drawRobe(canvas, cx, cy, r, paint); break;
            case BOSS:
                if (bossKind == BossKind.SUMMONER) drawRobe(canvas, cx, cy, r, paint);
                else if (bossKind == BossKind.SORCERER) drawCrystal(canvas, cx, cy, r, paint);
                else if (bossKind == BossKind.HUNTER) drawHunter(canvas, cx, cy, r, face, paint);
                else drawStar(canvas, cx, cy, r * 1.25f, r, 10, System.currentTimeMillis() / 2000f, paint);
                break;
            default:      canvas.drawCircle(cx, cy, r, paint);
        }
    }

    /** Bat: a small body with two wings that flap. */
    private void drawBat(Canvas canvas, float cx, float cy, float r, Paint paint) {
        float flap = 0.45f + 0.55f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 70.0 + id));
        shapePath.reset();
        for (int side = -1; side <= 1; side += 2) {
            shapePath.moveTo(cx + side * r * 0.4f, cy - r * 0.2f);
            shapePath.lineTo(cx + side * r * 1.9f, cy - r * 1.1f * flap);
            shapePath.lineTo(cx + side * r * 1.55f, cy + r * 0.15f);
            shapePath.lineTo(cx + side * r * 1.05f, cy - r * 0.05f);
            shapePath.lineTo(cx + side * r * 0.4f, cy + r * 0.4f);
            shapePath.close();
        }
        canvas.drawPath(shapePath, paint);
        canvas.drawCircle(cx, cy, r * 0.75f, paint);
    }

    /** Charger: a heavy oval body with two horns pointing the way it faces. */
    private void drawCharger(Canvas canvas, float cx, float cy, float r, float face, Paint paint) {
        canvas.save();
        canvas.rotate((float) Math.toDegrees(face), cx, cy);
        canvas.drawOval(cx - r * 1.1f, cy - r * 0.85f, cx + r * 0.9f, cy + r * 0.85f, paint);
        shapePath.reset();
        for (int side = -1; side <= 1; side += 2) {
            shapePath.moveTo(cx + r * 0.5f, cy + side * r * 0.3f);
            shapePath.quadTo(cx + r * 1.2f, cy + side * r * 0.45f, cx + r * 1.4f, cy + side * r * 1.05f);
            shapePath.lineTo(cx + r * 0.9f, cy + side * r * 0.6f);
            shapePath.close();
        }
        canvas.drawPath(shapePath, paint);
        canvas.restore();
    }

    /** Hunter boss: a hunched, eared beast-hunter facing its prey. */
    private void drawHunter(Canvas canvas, float cx, float cy, float r, float face, Paint paint) {
        canvas.save();
        canvas.rotate((float) Math.toDegrees(face), cx, cy);
        shapePath.reset();
        shapePath.moveTo(cx + r * 1.15f, cy);
        shapePath.lineTo(cx + r * 0.45f, cy - r * 0.55f);
        shapePath.lineTo(cx + r * 0.15f, cy - r * 1.1f);
        shapePath.lineTo(cx - r * 0.15f, cy - r * 0.6f);
        shapePath.lineTo(cx - r * 0.85f, cy - r * 0.8f);
        shapePath.lineTo(cx - r, cy);
        shapePath.lineTo(cx - r * 0.85f, cy + r * 0.8f);
        shapePath.lineTo(cx - r * 0.15f, cy + r * 0.6f);
        shapePath.lineTo(cx + r * 0.15f, cy + r * 1.1f);
        shapePath.lineTo(cx + r * 0.45f, cy + r * 0.55f);
        shapePath.close();
        canvas.drawPath(shapePath, paint);
        canvas.restore();
    }

    /** Summoner: a hooded robe that flares into a ragged hem. */
    private void drawRobe(Canvas canvas, float cx, float cy, float r, Paint paint) {
        shapePath.reset();
        shapePath.moveTo(cx, cy - r * 1.25f);
        shapePath.quadTo(cx + r * 0.75f, cy - r * 1.1f, cx + r * 0.6f, cy - r * 0.3f);
        shapePath.lineTo(cx + r * 1.05f, cy + r);
        shapePath.lineTo(cx + r * 0.55f, cy + r * 0.8f);
        shapePath.lineTo(cx + r * 0.2f, cy + r * 1.05f);
        shapePath.lineTo(cx - r * 0.2f, cy + r * 0.8f);
        shapePath.lineTo(cx - r * 0.55f, cy + r * 1.05f);
        shapePath.lineTo(cx - r * 1.05f, cy + r);
        shapePath.lineTo(cx - r * 0.6f, cy - r * 0.3f);
        shapePath.quadTo(cx - r * 0.75f, cy - r * 1.1f, cx, cy - r * 1.25f);
        shapePath.close();
        canvas.drawPath(shapePath, paint);
    }

    /** Sorcerer: a tall crystal bobbing in the air. */
    private void drawCrystal(Canvas canvas, float cx, float cy, float r, Paint paint) {
        float bob = (float) Math.sin(System.currentTimeMillis() / 400.0) * 8f;
        shapePath.reset();
        shapePath.moveTo(cx, cy - r * 1.5f + bob);
        shapePath.lineTo(cx + r * 0.8f, cy - r * 0.2f + bob);
        shapePath.lineTo(cx, cy + r * 1.2f + bob);
        shapePath.lineTo(cx - r * 0.8f, cy - r * 0.2f + bob);
        shapePath.close();
        canvas.drawPath(shapePath, paint);
    }

    /** Archetype details on top of the body, so each boss reads at a glance. Phase two turns them red. */
    private void drawBossDetails(Canvas canvas, float cx, float cy, long now) {
        float r = radius;
        switch (bossKind) {
            case SUMMONER: {
                // Glowing eyes in the hood and three soul flames circling
                int soul = enraged ? Color.rgb(255, 110, 90) : Color.rgb(140, 255, 170);
                DETAIL_PAINT.setColor(soul);
                canvas.drawCircle(cx - r * 0.18f, cy - r * 0.7f, r * 0.08f, DETAIL_PAINT);
                canvas.drawCircle(cx + r * 0.18f, cy - r * 0.7f, r * 0.08f, DETAIL_PAINT);
                for (int i = 0; i < 3; i++) {
                    double a = now / 600.0 + i * Math.PI * 2 / 3;
                    float sx = cx + (float) Math.cos(a) * r * 1.6f;
                    float sy = cy + (float) Math.sin(a) * r * 1.6f;
                    GLOW_PAINT.setColor(soul);
                    GLOW_PAINT.setAlpha(90);
                    canvas.drawCircle(sx, sy, r * 0.28f, GLOW_PAINT);
                    canvas.drawCircle(sx, sy, r * 0.12f, DETAIL_PAINT);
                }
                break;
            }
            case HUNTER: {
                // Amber eyes on the snout, red once it is enraged, and a bone mask stripe
                float fx = faceX, fy = faceY;
                DETAIL_PAINT.setColor(Color.rgb(230, 220, 195));
                canvas.drawCircle(cx + fx * r * 0.35f, cy + fy * r * 0.35f, r * 0.2f, DETAIL_PAINT);
                DETAIL_PAINT.setColor(enraged ? Color.rgb(255, 80, 60) : Color.rgb(255, 190, 60));
                for (int side = -1; side <= 1; side += 2) {
                    canvas.drawCircle(cx + fx * r * 0.62f - fy * side * r * 0.2f,
                            cy + fy * r * 0.62f + fx * side * r * 0.2f, r * 0.08f, DETAIL_PAINT);
                }
                break;
            }
            case SORCERER: {
                // Pulsing core and two rune rings turning opposite ways
                float bob = (float) Math.sin(now / 400.0) * 8f;
                float pulse = 0.8f + 0.2f * (float) Math.sin(now / 180.0);
                int rune = enraged ? Color.rgb(255, 120, 120) : Color.rgb(140, 190, 255);
                GLOW_PAINT.setColor(rune);
                GLOW_PAINT.setAlpha(120);
                canvas.drawCircle(cx, cy - r * 0.2f + bob, r * 0.55f * pulse, GLOW_PAINT);
                DETAIL_PAINT.setColor(Color.rgb(235, 245, 255));
                canvas.drawCircle(cx, cy - r * 0.2f + bob, r * 0.22f * pulse, DETAIL_PAINT);
                RUNE_PAINT.setColor(rune);
                float spin = (now % 6000) / 6000f * 360f;
                arcRect.set(cx - r * 1.7f, cy - r * 1.7f, cx + r * 1.7f, cy + r * 1.7f);
                for (int i = 0; i < 6; i++) canvas.drawArc(arcRect, spin + i * 60f, 32f, false, RUNE_PAINT);
                arcRect.set(cx - r * 1.35f, cy - r * 1.35f, cx + r * 1.35f, cy + r * 1.35f);
                for (int i = 0; i < 4; i++) canvas.drawArc(arcRect, -spin * 1.5f + i * 90f, 50f, false, RUNE_PAINT);
                break;
            }
            default: {
                // Brute: bone horns and a pulsing rage ring, faster and orange once enraged
                float pulse = (float) (Math.sin(now / (enraged ? 120.0 : 300.0)) * 0.15 + 0.85);
                RAGE_PAINT.setColor(enraged ? Color.rgb(255, 140, 0) : Color.rgb(255, 50, 50));
                RAGE_PAINT.setAlpha((int) (200 * pulse));
                canvas.drawCircle(cx, cy, r * 1.4f * pulse, RAGE_PAINT);
                DETAIL_PAINT.setColor(Color.rgb(230, 220, 195));
                for (int side = -1; side <= 1; side += 2) {
                    shapePath.reset();
                    shapePath.moveTo(cx + side * r * 0.35f, cy - r * 0.75f);
                    shapePath.quadTo(cx + side * r * 1.1f, cy - r * 1.0f, cx + side * r * 0.85f, cy - r * 1.5f);
                    shapePath.quadTo(cx + side * r * 0.7f, cy - r * 1.0f, cx + side * r * 0.1f, cy - r * 0.95f);
                    shapePath.close();
                    canvas.drawPath(shapePath, DETAIL_PAINT);
                }
            }
        }
        if (behaviorState == BehaviorState.CAST && bossMove == BossMove.WHIRL) {
            // Blades sweeping round while it spins
            float spin = (now % 400) / 400f * 360f;
            arcRect.set(cx - r - 60f, cy - r - 60f, cx + r + 60f, cy + r + 60f);
            TELEGRAPH_RING.setAlpha(220);
            for (int i = 0; i < 3; i++) canvas.drawArc(arcRect, spin + i * 120f, 50f, false, TELEGRAPH_RING);
        }
    }

    /** Mimic: a chattering jaw full of teeth, red eyes on the lid and a gold lock. */
    private void drawMimicFace(Canvas canvas, float cx, float cy, long now) {
        float w = radius * 0.88f;
        float jaw = 5f + 7f * (float) Math.abs(Math.sin(now / 140.0));
        MIMIC_PAINT.setColor(Color.rgb(24, 8, 6));
        canvas.drawRect(cx - w, cy - jaw, cx + w, cy + jaw, MIMIC_PAINT);
        MIMIC_PAINT.setColor(Color.rgb(234, 224, 202));
        float tooth = w / 3f;
        shapePath.reset();
        for (float tx = cx - w; tx < cx + w - 1f; tx += tooth) {
            shapePath.moveTo(tx, cy - jaw);
            shapePath.lineTo(tx + tooth, cy - jaw);
            shapePath.lineTo(tx + tooth / 2f, cy - jaw + 10f);
            shapePath.close();
            shapePath.moveTo(tx, cy + jaw);
            shapePath.lineTo(tx + tooth, cy + jaw);
            shapePath.lineTo(tx + tooth / 2f, cy + jaw - 10f);
            shapePath.close();
        }
        canvas.drawPath(shapePath, MIMIC_PAINT);
        MIMIC_PAINT.setColor(Color.rgb(255, 60, 40));
        canvas.drawCircle(cx - w * 0.45f, cy - w * 0.55f, 6f, MIMIC_PAINT);
        canvas.drawCircle(cx + w * 0.45f, cy - w * 0.55f, 6f, MIMIC_PAINT);
        MIMIC_PAINT.setColor(Color.rgb(212, 175, 55));
        canvas.drawRect(cx - 7f, cy + w * 0.45f, cx + 7f, cy + w * 0.75f, MIMIC_PAINT);
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
            case CHARGER:
            case NECROMANCER: return 8;
            case BAT:    return 1;
            default:     return 3;
        }
    }

    /* ============================================================
       CO-OP SYNC
       ============================================================ */

    /**
     * Host: appends "id,type,template,x,y,hp,maxHp,state,elapsed,duration,aim,bossMove,rage,kinds,leap"
     * (rage: 1 enraged, 2 frenzied; kinds: 1 elite, 2 mimic, 4 thrall, 8 shield; leap: hunter leap length).
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
                .append(bossMove.ordinal()).append(',').append(enraged ? (frenzied ? 2 : 1) : 0).append(',')
                .append((elite ? 1 : 0) | (mimic ? 2 : 0) | (summoned ? 4 : 0) | (shield ? 8 : 0)).append(',')
                .append((int) leapDist);
    }

    /**
     * Guest: copies the host's state from appendState's fields; a reported death plays out locally.
     * @param sentAt host clock when the snapshot was taken
     * @param arrivedAt this phone's clock when it arrived
     */
    public void applyState(String[] f, long sentAt, long arrivedAt) {
        float hostX = Float.parseFloat(f[3]);
        float hostY = Float.parseFloat(f[4]);
        int kinds = Integer.parseInt(f[13]);
        if ((kinds & 1) != 0 && !elite) {
            elite = true;
            goldDrop *= 3;
        }
        if ((kinds & 2) != 0 && !mimic) makeMimic();
        if ((kinds & 4) != 0 && !summoned) makeThrall();
        if ((kinds & 8) != 0) shield = true;
        if (remote == null) {
            remote = new CoopSession.Track();
            x = hostX;
            y = hostY;
        }
        remote.add(sentAt, hostX, hostY);
        int hp = Integer.parseInt(f[5]);
        maxHealth = Integer.parseInt(f[6]);
        if (hp <= 0) {
            if (!isDead) {
                noGoldDrop = hp < 0;
                takeDamage(Integer.MAX_VALUE);
            }
            return;
        }
        health = arrivedAt < predictedUntil ? Math.min(health, hp) : hp;
        BehaviorState next = BehaviorState.values()[Integer.parseInt(f[7])];
        if (behaviorState == BehaviorState.WINDUP && next != BehaviorState.WINDUP) lastSwingAt = arrivedAt;
        behaviorState  = next;
        stateStartTime = arrivedAt - Long.parseLong(f[8]);
        stateDuration  = Long.parseLong(f[9]);
        double aim = Math.toRadians(Integer.parseInt(f[10]));
        aimX = (float) Math.cos(aim);
        aimY = (float) Math.sin(aim);
        bossMove = BossMove.values()[Integer.parseInt(f[11])];
        int rage = Integer.parseInt(f[12]);
        if (rage >= 1 && !enraged) justEnraged = true;
        if (rage >= 2 && !frenzied) justFrenzied = true;
        enraged = rage >= 1;
        frenzied = rage >= 2;
        if (f.length > 14) leapDist = Float.parseFloat(f[14]);
    }

    /**
     * Guest: shows a hit right away. A lethal one plays the death now, since the host's copy has at most
     * this much health and will die from the same hit a moment later.
     */
    public void predictHit(int damage) {
        long now = System.currentTimeMillis();
        predictedUntil = now + 1500; // covers the trip to the host and back
        if (damage >= health) {
            takeDamage(health);
            return;
        }
        health -= damage;
        hitFlashUntil = now + 90;
    }

    /** Guest: replays the host's movement a moment behind, instead of running the AI. */
    public void followRemote(long playbackTime) {
        if (isDead || remote == null || !remote.sample(playbackTime, REMOTE_POS)) return;
        float dx = REMOTE_POS[0] - x;
        float dy = REMOTE_POS[1] - y;
        x = REMOTE_POS[0];
        y = REMOTE_POS[1];
        if (behaviorState == BehaviorState.WINDUP) {
            faceX = aimX;
            faceY = aimY;
        } else if (dx * dx + dy * dy > 0.25f) {
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