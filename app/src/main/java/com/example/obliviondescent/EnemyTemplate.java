package com.example.obliviondescent;

/**
 * תבנית אויב שנוצרה על ידי Gemini AI.
 * מוחלת על אויב קיים דרך Enemy.applyTemplate() כדי לשנות
 * שם, צבע ומכפילי HP/DMG מבלי לשנות את הלוגיקה הבסיסית.
 */
public class EnemyTemplate {
    /** שם הצגה מותאם אישית לאויב. */
    public String name;
    /** תיאור AI של האויב (לשימוש עתידי). */
    public String description;
    /** צבע גוף האויב. */
    public int color;
    /** מכפיל HP על גבי ה-HP הבסיסי המסוקל בקומה. */
    public float hpMultiplier;
    /** מכפיל נזק על גבי הנזק הבסיסי המסוקל בקומה. */
    public float dmgMultiplier;

    /**
     * יוצר תבנית אויב חדשה שנוצרה על ידי AI.
     * @param name שם הצגה מותאם
     * @param description תיאור AI
     * @param color צבע גוף האויב
     * @param hpMultiplier מכפיל HP
     * @param dmgMultiplier מכפיל נזק
     */
    public EnemyTemplate(String name, String description,
                         int color, float hpMultiplier, float dmgMultiplier) {
        this.name        = name;
        this.description = description;
        this.color       = color;
        this.hpMultiplier = hpMultiplier;
        this.dmgMultiplier = dmgMultiplier;
    }
}