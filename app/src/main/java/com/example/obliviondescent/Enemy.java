package com.example.obliviondescent;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

public class Enemy {

    /* ============================================================
       ENEMY TYPES
       ============================================================ */
    public enum Type {
        GRUNT,    // Balanced — the basic enemy
        SPEEDER,  // Fast, fragile, low damage
        TANK,     // Slow, tanky, hits hard
        BRUTE,     // Medium speed, high HP, medium damage
        BOSS
    }

    /* ============================================================
       FIELDS
       ============================================================ */
    public float x, y;
    public float radius;
    public float speed;
    public boolean isDead = false;
    public Type type;

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

    // Behavior state machine
    private enum BehaviorState {
        APPROACH,   // Moving toward player
        ATTACK,     // In attack range, striking
        RETREAT,    // Post-attack pullback (melee only)
        BAIT,       // Deliberately backing off to bait a dash
        CHARGE,     // Full sprint ignoring normal logic
        STRAFE      // Circling to cut off dodge direction
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

    /* ============================================================
       CONSTRUCTOR
       ============================================================ */
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
    public void tryWake(float playerX, float playerY) {
        if (isAwake || isDead) return;
        float dx = playerX - x;
        float dy = playerY - y;
        if (dx * dx + dy * dy <= triggerRadius * triggerRadius) {
            isAwake = true;
        }
    }

    public void update(float playerX, float playerY) {
        if (isDead || !isAwake) return;

        // Apply and decay knockback velocity each frame for smooth motion
        if (knockbackVX != 0f || knockbackVY != 0f) {
            x += knockbackVX;
            y += knockbackVY;
            knockbackVX *= 0.75f;
            knockbackVY *= 0.75f;
            if (Math.abs(knockbackVX) < 0.5f) knockbackVX = 0f;
            if (Math.abs(knockbackVY) < 0.5f) knockbackVY = 0f;
        }

        long now = System.currentTimeMillis();
        float dx = playerX - x;
        float dy = playerY - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float attackRange = radius + weapon.range + 10f;

        // Determine if tracker has useful data
        float adaptStrength = (behaviorTracker != null && behaviorTracker.isReady())
                ? behaviorTracker.getAdaptationStrength() : 0f;

        // Boss adapts 3x faster
        if (type == Type.BOSS) adaptStrength = Math.min(1f, adaptStrength * 3f);

        // ============================================================
        // STATE MACHINE
        // ============================================================
        switch (behaviorState) {

            case APPROACH: {
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

                // Reached attack range
                if (dist <= attackRange) {
                    enterState(BehaviorState.ATTACK, getMeleeAttackDuration());
                }
                break;
            }

            case ATTACK: {
                // Melee: stay close and hit
                // Projectile: already handled by GameActivity — just hold position
                if (weapon.isProjectile) {
                    // Projectile enemies maintain preferred distance based on weapon range
                    float preferredDist = Math.max(100f, weapon.range * 0.8f);
                    if (dist < preferredDist - 30f) {
                        // Too close — back away slowly
                        if (dist > 10f) {
                            x -= (dx / dist) * speed * 0.5f;
                            y -= (dy / dist) * speed * 0.5f;
                        }
                    }
                }

                if (now - stateStartTime >= stateDuration) {
                    if (!weapon.isProjectile) {
                        // Melee — retreat after attacking
                        float retreatDist = radius * 3f;
                        retreatTargetX = x - (dx / dist) * retreatDist;
                        retreatTargetY = y - (dy / dist) * retreatDist;
                        enterState(BehaviorState.RETREAT, 350);
                    } else {
                        enterState(BehaviorState.APPROACH, 0);
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
                // Full sprint directly at player — ignores normal caution
                float chargeSpeed = speed * (1.3f + adaptStrength * 0.5f);
                if (dist > 10f) {
                    x += (dx / dist) * chargeSpeed;
                    y += (dy / dist) * chargeSpeed;
                }

                if (dist <= attackRange) {
                    enterState(BehaviorState.ATTACK, getMeleeAttackDuration());
                } else if (now - stateStartTime >= stateDuration) {
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

    private long getMeleeAttackDuration() {
        return weapon.cooldown + 100L;
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
                weapon.knockback
        );
    }

    /* ============================================================
       DRAW (with Health Bar + Label)
       ============================================================ */
    public void draw(Canvas canvas, GameCamera camera) {
        float drawX = x - camera.getOffsetX();
        float drawY = y - camera.getOffsetY();

        if (isDead) {
            // --- DEATH ANIMATION ---
            long elapsed = System.currentTimeMillis() - deathStartTime;

            if (elapsed >= DEATH_DURATION) {
                // Animation finished — flag for removal
                readyToRemove = true;
                return;
            }

            // Progress goes from 0.0 (just died) to 1.0 (fully faded)
            float progress = (float) elapsed / DEATH_DURATION;

            // Alpha fades from 255 → 0
            int alpha = (int)(255 * (1f - progress));

            // Enemy slowly shrinks as it fades
            float currentRadius = radius * (1f - progress * 0.5f);

            bodyPaint.setAlpha(alpha);
            canvas.drawCircle(drawX, drawY, currentRadius, bodyPaint);
            return; // Skip health bar during death
        }

        // --- NORMAL DRAW ---
        bodyPaint.setAlpha(255); // Reset alpha in case it was changed
        canvas.drawCircle(drawX, drawY, radius, bodyPaint);

        // Boss gets a pulsing outer ring
        if (type == Type.BOSS) {
            float pulse = (float)(Math.sin(System.currentTimeMillis() / 300.0) * 0.15 + 0.85);
            Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ringPaint.setColor(Color.rgb(255, 50, 50));
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(6f);
            ringPaint.setAlpha((int)(200 * pulse));
            canvas.drawCircle(drawX, drawY, radius * 1.3f * pulse, ringPaint);

            // "BOSS" label is larger and red
            labelPaint.setColor(Color.rgb(255, 80, 80));
            labelPaint.setTextSize(32f);
        }

        // Health bar background
        float barWidth = radius * 2.6f;
        float barHeight = 13f;
        float barLeft = drawX - barWidth / 2f;
        float barTop = drawY - radius - 30f;
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

    /* ============================================================
       COMBAT
       ============================================================ */
    public void takeDamage(int dmg) {
        health -= dmg;
        if (health <= 0) {
            health = 0;
            isDead = true;
            deathStartTime = System.currentTimeMillis();
        }
    }

    public int getDamage() { return Math.max(1, (int)(weapon.damage * dmgMultiplier * 0.65f));}
    public float getAttackRange() { return radius + weapon.range; }
    public long getAttackCooldown() { return weapon.cooldown; }
    public Weapon getWeapon()   { return weapon; }
    public int getGoldDrop() { return goldDrop; }

    /* ============================================================
       HELPER
       ============================================================ */
    private Paint makePaint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        return p;
    }
}