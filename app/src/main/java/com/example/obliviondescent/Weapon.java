package com.example.obliviondescent;

import android.graphics.Color;

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