package com.example.obliviondescent;

import android.graphics.Color;

/**
 * Represents a single armour piece in the game world.
 * Uses a slot-based armour system (head, chest, legs, boots).
 * Fields map directly to the Firestore generated_armors schema.
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
     * Full constructor used when loading from Firestore or AI generation.
     * @param id Firestore document ID (null for default armour)
     * @param name armour piece name
     * @param defense flat damage reduction per hit
     * @param maxDurability maximum durability
     * @param rarity armour rarity tier
     * @param color display colour
     * @param slot armour slot (HEAD/CHEST/LEGS/BOOTS)
     * @param description AI flavour text (empty for default armour)
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
     * Convenience constructor for hardcoded or default armour without a Firestore ID.
     * @param name armour piece name
     * @param defense flat damage reduction per hit
     * @param maxDurability maximum durability
     * @param rarity armour rarity tier
     * @param color display colour
     * @param slot armour slot
     */
    // Convenience constructor for default/hardcoded armor
    public Armor(String name, int defense, float maxDurability,
                 Rarity rarity, int color, Slot slot) {
        this(null, name, defense, maxDurability, rarity, color, slot, "");
    }

    /**
     * Returns the display colour corresponding to an armour rarity tier.
     * @param rarity the rarity tier
     * @return colour as an ARGB integer
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