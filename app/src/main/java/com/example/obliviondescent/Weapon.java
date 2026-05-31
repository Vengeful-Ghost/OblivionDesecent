package com.example.obliviondescent;

import android.graphics.Color;

/**
 * מייצגת נשק בעולם המשחק.
 * תומכת בנשק מלחמתי ובנשק קשתות (isProjectile).
 * שדות הנשק ממופים ישירות לסכמת Firestore generated_weapons.
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
     * יוצר נשק מלחמתי (isProjectile=false).
     * @param name שם הנשק
     * @param damage נזק בסיסי לפגיעה
     * @param range מרחק פגיעה ביחידות עולם
     * @param cooldown זמן צינון בין מתקפות במילישניות
     * @param rarity נדירות הנשק
     * @param color צבע הנשק בהצגה
     * @param knockback עוצמת דחיפת האויב לאחור
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
     * יוצר נשק עם שליטה מלאה בסוג התקיפה.
     * @param name שם הנשק
     * @param damage נזק בסיסי לפגיעה
     * @param range מרחק פגיעה ביחידות עולם
     * @param cooldown זמן צינון בין מתקפות במילישניות
     * @param rarity נדירות הנשק
     * @param color צבע הנשק בהצגה
     * @param knockback עוצמת דחיפת האויב לאחור
     * @param isProjectile האם הנשק יורה קליעים
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
     * מחזירה את הצבע המתאים לרמת נדירות נשק.
     * @param rarity רמת הנדירות
     * @return צבע כ-ARGB integer
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