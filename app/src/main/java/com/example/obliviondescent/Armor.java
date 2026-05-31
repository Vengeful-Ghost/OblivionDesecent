package com.example.obliviondescent;

import android.graphics.Color;

/**
 * מייצגת פריט שריון אחד בעולם המשחק.
 * מערכת שריון מבוססת חריצים (ראש, חזה, רגלים, נעליים).
 * שדות הכיתה ממופים ישירות לסכמת Firestore generated_armors.
 */
public class Armor {

    public enum Slot {
        HEAD, CHEST, LEGS, BOOTS
    }

    public enum Rarity {
        COMMON, RARE, EPIC, LEGENDARY
    }

    // Core fields — mirror the Firestore generated_armors document exactly
    public String id;           // Firestore document ID (null for default armor)
    public String name;
    public int defense;         // Flat damage reduction per hit
    public float durability;    // Current durability
    public float maxDurability;
    public Rarity rarity;
    public int color;
    public Slot slot;
    public String description;  // AI flavor text (empty for default)

    /**
     * בנאי מלא לטעינה מ-Firestore או יצירת AI.
     * @param id מזהה מסמך ב-Firestore (null לשריון ברירת מחדל)
     * @param name שם פריט השריון
     * @param defense הפחתת נזק שטוח לכל פגיעה
     * @param maxDurability עמידות מקסימלית
     * @param rarity נדירות השריון
     * @param color צבע הצגה
     * @param slot חריץ השריון (HEAD/CHEST/LEGS/BOOTS)
     * @param description טקסט AI (ריק לשריון ברירת מחדל)
     */
    // Full constructor — used when loading from Firestore or AI generation
    public Armor(String id, String name, int defense, float maxDurability,
                 Rarity rarity, int color, Slot slot, String description) {
        this.id           = id;
        this.name         = name;
        this.defense      = defense;
        this.maxDurability = maxDurability;
        this.durability   = maxDurability;
        this.rarity       = rarity;
        this.color        = color;
        this.slot         = slot;
        this.description  = description;
    }

    /**
     * בנאי נוחות לשריון קשיח/ברירת מחדל ללא מזהה Firestore.
     * @param name שם פריט השריון
     * @param defense הפחתת נזק שטוח לכל פגיעה
     * @param maxDurability עמידות מקסימלית
     * @param rarity נדירות השריון
     * @param color צבע הצגה
     * @param slot חריץ השריון
     */
    // Convenience constructor for default/hardcoded armor
    public Armor(String name, int defense, float maxDurability,
                 Rarity rarity, int color, Slot slot) {
        this(null, name, defense, maxDurability, rarity, color, slot, "");
    }

    /**
     * מחזירה את הצבע המתאים לרמת נדירות שריון.
     * @param rarity רמת הנדירות
     * @return צבע כ-ARGB integer
     */
    public static int getRarityColor(Rarity rarity) {
        switch (rarity) {
            case COMMON:    return Color.rgb(150, 150, 150);
            case RARE:      return Color.rgb(0, 150, 255);
            case EPIC:      return Color.rgb(200, 0, 255);
            case LEGENDARY: return Color.rgb(255, 215, 0);
            default:        return Color.rgb(150, 150, 150);
        }
    }
}