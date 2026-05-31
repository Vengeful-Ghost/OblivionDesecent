package com.example.obliviondescent;

import android.graphics.Color;

/**
 * Represents a weapon in the game world.
 * Supports both melee and projectile weapons (isProjectile).
 * Fields map directly to the Firestore generated_weapons schema.
 */
public class Weapon {

    // ─── ENUM ───────────────────────────────────────────────────────────────────
    public enum Rarity {
        COMMON, RARE, EPIC, LEGENDARY
    }

    // ─── FIELDS ─────────────────────────────────────────────────────────────────
    public String  name;
    public int     damage;
    public float   range;     // world-unit attack distance
    public long    cooldown;  // milliseconds between attacks
    public Rarity  rarity;
    public int     color;
    public float   knockback;
    public boolean isProjectile;

    // ─── CONSTRUCTORS ────────────────────────────────────────────────────────────
    /**
     * Creates a melee weapon (isProjectile=false).
     * @param name weapon name
     * @param damage base damage per hit
     * @param range attack distance in world units
     * @param cooldown milliseconds between attacks
     * @param rarity weapon rarity tier
     * @param color display colour
     * @param knockback knockback force applied to the enemy
     */
    public Weapon(String name, int damage, float range, long cooldown, Rarity rarity, int color, float knockback) {
        this.name        = name;
        this.damage      = damage;
        this.range       = range;
        this.cooldown    = cooldown;
        this.rarity      = rarity;
        this.color       = color;
        this.knockback   = knockback;
        this.isProjectile = false;
    }

    /**
     * Creates a weapon with full control over the attack type.
     * @param name weapon name
     * @param damage base damage per hit
     * @param range attack distance in world units
     * @param cooldown milliseconds between attacks
     * @param rarity weapon rarity tier
     * @param color display colour
     * @param knockback knockback force applied to the enemy
     * @param isProjectile true if the weapon fires projectiles
     */
    public Weapon(String name, int damage, float range, long cooldown,
                  Rarity rarity, int color, float knockback, boolean isProjectile) {
        this.name        = name;
        this.damage      = damage;
        this.range       = range;
        this.cooldown    = cooldown;
        this.rarity      = rarity;
        this.color       = color;
        this.knockback   = knockback;
        this.isProjectile = isProjectile;
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────────
    /**
     * Returns the display colour corresponding to a weapon rarity tier.
     * @param rarity the rarity tier
     * @return colour as an ARGB integer
     */
    public static int getRarityColor(Rarity rarity) {
        switch (rarity) {
            case COMMON: return Color.WHITE;
            case RARE: return Color.rgb(0, 150, 255); // Blue
            case EPIC: return Color.rgb(200, 0, 255); // Purple
            case LEGENDARY: return Color.rgb(255, 215, 0); // Gold
            default: return Color.WHITE;
        }
    }
}