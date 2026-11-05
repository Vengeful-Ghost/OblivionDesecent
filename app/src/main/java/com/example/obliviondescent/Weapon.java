package com.example.obliviondescent;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

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
     * How a weapon is swung, read from its name. Each kind has its own swing arc (half-angle, degrees),
     * finisher arc, combo length, reach factor, finisher damage and finisher knockback.
     */
    public enum Kind {
        SWORD(55f, 80f, 3, 1f, 1.5f, 2f),
        DAGGER(40f, 50f, 4, 0.85f, 1.6f, 1f),
        AXE(72f, 100f, 3, 1f, 1.6f, 2.5f),
        SPEAR(22f, 30f, 3, 1.35f, 1.5f, 1.5f),
        HAMMER(50f, 180f, 2, 1f, 1.8f, 3f),   // the finisher slams the ground all around
        FIST(45f, 60f, 3, 1f, 1.5f, 2f),
        STAFF(0f, 0f, 1, 1f, 1f, 1f),
        BOW(0f, 0f, 1, 1f, 1f, 1f);

        public final float arc, finisherArc, reach, finisherDamage, finisherPush;
        public final int combo;

        Kind(float arc, float finisherArc, int combo, float reach, float finisherDamage, float finisherPush) {
            this.arc = arc;
            this.finisherArc = finisherArc;
            this.combo = combo;
            this.reach = reach;
            this.finisherDamage = finisherDamage;
            this.finisherPush = finisherPush;
        }

        /** Display name, e.g. "Dagger". */
        public String title() {
            return name().charAt(0) + name().substring(1).toLowerCase(java.util.Locale.ROOT);
        }
    }

    private Kind kind;

    public Kind kind() {
        if (kind == null) kind = kindOf(name, isProjectile);
        return kind;
    }

    static Kind kindOf(String name, boolean projectile) {
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (projectile) return has(n, "bow", "sling") ? Kind.BOW : Kind.STAFF;
        if (has(n, "fist", "gauntlet", "knuckle")) return Kind.FIST;
        if (has(n, "dagger", "knife", "shiv", "dirk", "stiletto", "fang", "kris", "claw")) return Kind.DAGGER;
        if (has(n, "spear", "glaive", "lance", "pike", "halberd", "trident", "partisan", "javelin", "scythe"))
            return Kind.SPEAR;
        if (has(n, "hammer", "mace", "maul", "club", "flail", "morningstar", "cudgel")) return Kind.HAMMER;
        if (has(n, "axe", "cleave", "hatchet", "reaver", "tomahawk")) return Kind.AXE;
        return Kind.SWORD;
    }

    private static boolean has(String name, String... words) {
        for (String w : words) if (name.contains(w)) return true;
        return false;
    }

    // Drawing runs on the UI thread only, so the paints are shared
    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint LINE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path SHAPE = new Path();
    private static final int WOOD = Color.rgb(92, 62, 34), LEATHER = Color.rgb(58, 40, 26),
            GOLD = Color.rgb(212, 175, 55), INK = Color.argb(210, 18, 12, 8);
    static {
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setStrokeCap(Paint.Cap.ROUND);
        LINE.setStrokeJoin(Paint.Join.ROUND);
    }

    /**
     * Draws a weapon held in a hand at (x, y), pointing along angle (degrees) and length units long.
     * Blades are steel tinted with the weapon's colour.
     */
    public static void draw(Canvas canvas, Kind kind, int color, float x, float y, float angle, float length, int alpha) {
        int metal = mix(Color.rgb(196, 202, 212), color, 0.4f);
        float l = length;
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(angle);
        LINE.setColor(INK);
        LINE.setAlpha(alpha);
        LINE.setStrokeWidth(2.5f);
        switch (kind) {
            case DAGGER:
            case SWORD: {
                boolean dagger = kind == Kind.DAGGER;
                float grip = l * (dagger ? 0.28f : 0.2f), w = dagger ? 5f : 7f, guard = dagger ? 10f : 15f;
                rod(canvas, 0, grip, 6f, LEATHER, alpha);
                SHAPE.reset();
                SHAPE.moveTo(grip, -w);
                SHAPE.lineTo(l * 0.88f, -w * 0.7f);
                SHAPE.lineTo(l, 0);
                SHAPE.lineTo(l * 0.88f, w * 0.7f);
                SHAPE.lineTo(grip, w);
                SHAPE.close();
                fill(canvas, SHAPE, metal, alpha);
                LINE.setColor(mix(metal, Color.BLACK, 0.35f));
                LINE.setAlpha(alpha);
                LINE.setStrokeWidth(2f);
                canvas.drawLine(grip + 4f, 0, l * 0.85f, 0, LINE);
                FILL.setColor(GOLD);
                FILL.setAlpha(alpha);
                canvas.drawRoundRect(grip - 3f, -guard, grip + 3f, guard, 3f, 3f, FILL);
                canvas.drawCircle(0, 0, 5f, FILL);
                break;
            }
            case AXE: {
                rod(canvas, 0, l, 7f, WOOD, alpha);
                SHAPE.reset();
                SHAPE.moveTo(l - 30f, -4f);
                SHAPE.lineTo(l - 38f, -26f);
                SHAPE.quadTo(l - 16f, -38f, l + 4f, -24f);
                SHAPE.lineTo(l - 8f, -4f);
                SHAPE.close();
                SHAPE.moveTo(l - 24f, 4f);
                SHAPE.lineTo(l - 19f, 14f);
                SHAPE.lineTo(l - 13f, 4f);
                SHAPE.close();
                fill(canvas, SHAPE, metal, alpha);
                break;
            }
            case SPEAR: {
                rod(canvas, 0, l - 24f, 5f, WOOD, alpha);
                FILL.setColor(GOLD);
                FILL.setAlpha(alpha);
                canvas.drawRect(l - 30f, -5f, l - 24f, 5f, FILL);
                SHAPE.reset();
                SHAPE.moveTo(l - 26f, 0);
                SHAPE.lineTo(l - 13f, -9f);
                SHAPE.lineTo(l + 6f, 0);
                SHAPE.lineTo(l - 13f, 9f);
                SHAPE.close();
                fill(canvas, SHAPE, metal, alpha);
                break;
            }
            case HAMMER: {
                rod(canvas, 0, l - 8f, 7f, WOOD, alpha);
                SHAPE.reset();
                SHAPE.addRoundRect(l - 24f, -19f, l + 4f, 19f, 5f, 5f, Path.Direction.CW);
                fill(canvas, SHAPE, mix(metal, Color.BLACK, 0.2f), alpha);
                LINE.setColor(mix(metal, Color.WHITE, 0.3f));
                LINE.setAlpha(alpha);
                LINE.setStrokeWidth(3f);
                canvas.drawLine(l - 4f, -15f, l - 4f, 15f, LINE);
                break;
            }
            case STAFF: {
                rod(canvas, 0, l, 6f, WOOD, alpha);
                FILL.setColor(color);
                FILL.setAlpha(alpha / 3);
                canvas.drawCircle(l + 6f, 0, 19f, FILL);
                FILL.setAlpha(alpha);
                canvas.drawCircle(l + 6f, 0, 10f, FILL);
                FILL.setColor(Color.WHITE);
                FILL.setAlpha(alpha * 2 / 3);
                canvas.drawCircle(l + 3f, -3f, 3.5f, FILL);
                break;
            }
            case BOW: {
                LINE.setColor(WOOD);
                LINE.setAlpha(alpha);
                LINE.setStrokeWidth(6f);
                SHAPE.reset();
                SHAPE.moveTo(-4f, -l * 0.5f);
                SHAPE.quadTo(l * 0.45f, 0, -4f, l * 0.5f);
                canvas.drawPath(SHAPE, LINE);
                LINE.setColor(Color.argb(200, 235, 225, 200));
                LINE.setAlpha(alpha);
                LINE.setStrokeWidth(1.5f);
                canvas.drawLine(-4f, -l * 0.5f, -4f, l * 0.5f, LINE);
                FILL.setColor(color);
                FILL.setAlpha(alpha);
                canvas.drawCircle(l * 0.2f, 0, 5f, FILL);
                break;
            }
            default: { // FIST: a wrapped, glowing knuckle
                FILL.setColor(color);
                FILL.setAlpha(alpha / 3);
                canvas.drawCircle(l, 0, 17f, FILL);
                FILL.setColor(LEATHER);
                FILL.setAlpha(alpha);
                canvas.drawCircle(l, 0, 11f, FILL);
                canvas.drawCircle(l, 0, 11f, LINE);
            }
        }
        canvas.restore();
    }

    // A handle or shaft along the x axis
    private static void rod(Canvas canvas, float from, float to, float width, int color, int alpha) {
        FILL.setColor(color);
        FILL.setAlpha(alpha);
        canvas.drawRoundRect(from, -width / 2f, to, width / 2f, width / 2f, width / 2f, FILL);
    }

    private static void fill(Canvas canvas, Path path, int color, int alpha) {
        FILL.setColor(color);
        FILL.setAlpha(alpha);
        canvas.drawPath(path, FILL);
        LINE.setColor(INK);
        LINE.setAlpha(alpha);
        LINE.setStrokeWidth(2f);
        canvas.drawPath(path, LINE);
    }

    static int mix(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

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