package com.example.obliviondescent;

/**
 * מייצגת פריט בודד שנוצר על ידי Gemini AI.
 * הסוג יכול להיות WEAPON, ARMOR או CONSUMABLE.
 * הסטטיסטיקות ממופות ישירות לבנאי הרלוונטי של כל סוג.
 */
public class ShopItem {

    public enum ItemType { WEAPON, ARMOR, CONSUMABLE }

    // Common fields
    public String   name;
    public int      price;
    public String   colorHex;   // e.g. "#FF4400"
    public int      color;      // parsed from colorHex
    public String   description;
    public ItemType type;

    // Weapon fields (type == WEAPON)
    public int     damage;
    public float   range;
    public long    cooldown;
    public float   knockback;
    public boolean isProjectile;
    public String  rarity;      // "COMMON" / "RARE" / "EPIC" / "LEGENDARY"

    // Armor fields (type == ARMOR)
    public int    defense;
    public float  maxDurability;
    public String armorSlot;    // "HEAD" / "CHEST" / "LEGS" / "BOOTS"

    // Consumable fields (type == CONSUMABLE)
    public String effect;       // "heal" / "armor_restore"
    public int    effectValue;  // amount to heal/restore

    /**
     * ממיר את ShopItem לאובייקט Weapon.
     * תקף רק כאשר type == WEAPON.
     * @return אובייקט Weapon המקביל
     */
    public Weapon toWeapon() {
        Weapon.Rarity r;
        try {
            r = Weapon.Rarity.valueOf(rarity.toUpperCase());
        } catch (Exception e) {
            r = Weapon.Rarity.COMMON;
        }
        return new Weapon(name, damage, range, cooldown, r, color, knockback, isProjectile);
    }

    /**
     * ממיר את ShopItem לאובייקט Armor.
     * תקף רק כאשר type == ARMOR.
     * @return אובייקט Armor המקביל
     */
    public Armor toArmor() {
        Armor.Rarity r;
        try {
            r = Armor.Rarity.valueOf(rarity.toUpperCase());
        } catch (Exception e) {
            r = Armor.Rarity.COMMON;
        }
        Armor.Slot slot;
        try {
            slot = Armor.Slot.valueOf(armorSlot.toUpperCase());
        } catch (Exception e) {
            slot = Armor.Slot.CHEST;
        }
        return new Armor(name, defense, maxDurability, r, color, slot);
    }
}