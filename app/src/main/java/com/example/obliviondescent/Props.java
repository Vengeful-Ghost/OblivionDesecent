package com.example.obliviondescent;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Props shared by the shop room and the dungeon floors, all drawn in world coordinates:
 * the rune portal, torches and their light, pillars, banners, crates, barrels and dungeon clutter.
 * Also places the decorations for every room of a floor.
 */
public class Props {

    // Palette (matches res/values/colors.xml)
    static final int GROUT = Color.rgb(14, 11, 9);
    static final int WALL_TOP = Color.rgb(54, 41, 31);
    static final int WALL_FACE_COLOR = Color.rgb(30, 24, 19);
    static final int MORTAR = Color.rgb(16, 12, 10);
    static final int WALL_EDGE = Color.rgb(92, 69, 23);
    static final int GOLD_DEEP = Color.rgb(122, 90, 31);
    static final int GOLD_BASE = Color.rgb(184, 145, 30);
    static final int GOLD_BRIGHT = Color.rgb(212, 175, 55);
    static final int GOLD_GLOW = Color.rgb(244, 215, 122);
    static final int CRIMSON_DEEP = Color.rgb(74, 10, 10);
    static final int CRIMSON_BASE = Color.rgb(139, 24, 24);
    static final int CRIMSON_GLOW = Color.rgb(224, 72, 72);
    static final int ARCANE_BRIGHT = Color.rgb(139, 95, 191);
    static final int ARCANE_GLOW = Color.rgb(181, 140, 224);
    static final int BONE = Color.rgb(200, 190, 165);
    static final int BONE_DIM = Color.rgb(184, 172, 145);
    static final int INK = Color.rgb(42, 33, 26);
    static final int PARCHMENT = Color.rgb(222, 205, 168);
    static final int WOOD_LIGHT = Color.rgb(120, 84, 48);
    static final int WOOD_MID = Color.rgb(92, 62, 34);
    static final int WOOD_DARK = Color.rgb(58, 38, 22);
    static final int WOOD_LINE = Color.rgb(38, 24, 14);
    static final int LEATHER = Color.rgb(70, 52, 30);
    static final int IRON = Color.rgb(60, 60, 66);
    static final int IRON_LIGHT = Color.rgb(120, 122, 130);
    static final int STEEL = Color.rgb(180, 190, 200);
    static final int STONE_DARK = Color.rgb(36, 30, 26);
    static final int STONE_MID = Color.rgb(70, 60, 52);
    static final int STONE_LIGHT = Color.rgb(104, 92, 80);
    static final int CLOAK_DARK = Color.rgb(28, 20, 34);
    static final int CLOAK_MID = Color.rgb(48, 34, 58);
    static final int FLAME = Color.rgb(230, 110, 30);
    static final int FLAME_CORE = Color.rgb(255, 214, 120);
    static final int BREW = Color.rgb(70, 130, 60);
    static final int BREW_LIGHT = Color.rgb(160, 230, 120);
    static final int SHADOW = Color.argb(90, 0, 0, 0);
    static final int[] POTION_COLORS = {Color.rgb(224, 72, 72), Color.rgb(181, 140, 224),
            Color.rgb(122, 184, 92), Color.rgb(86, 136, 199), Color.rgb(244, 215, 122)};
    // Biome hazards and event rooms
    static final int WATER_DEEP = Color.argb(190, 18, 36, 44);
    static final int WATER = Color.argb(170, 40, 78, 90);
    static final int WATER_GLOW = Color.rgb(70, 150, 170);
    static final int WATER_SHINE = Color.rgb(160, 214, 224);
    static final int LAVA = Color.rgb(255, 96, 24);
    static final int LAVA_CORE = Color.rgb(255, 210, 110);
    static final int EMBER = Color.rgb(150, 36, 10);
    static final int SCORCH = Color.argb(150, 10, 6, 4);
    static final int BLOOD_DARK = Color.rgb(66, 8, 10);
    static final int BLOOD_WET = Color.rgb(116, 14, 18);
    static final int CURSE = Color.rgb(176, 60, 220);
    static final int FELT = Color.rgb(36, 66, 44);
    static final int STRAW = Color.rgb(196, 168, 92);
    static final int STRAW_DARK = Color.argb(140, 92, 76, 34);
    static final int MOSS = Color.rgb(70, 104, 52);
    static final int MOSS_DARK = Color.argb(150, 32, 54, 28);
    static final int SKIN = Color.rgb(176, 146, 116);
    static final int TOME = Color.rgb(74, 30, 62);
    private static final int[] SPINES = {Color.rgb(110, 40, 34), Color.rgb(52, 72, 46), Color.rgb(46, 56, 92),
            Color.rgb(120, 92, 40), Color.rgb(78, 46, 86), Color.rgb(96, 84, 70)};

    // Decoration kinds
    static final int TORCH = 0, BARREL = 1, CRATE = 2, PILLAR = 3, BONES = 4, RUBBLE = 5,
            COBWEB = 6, CANDLES = 7, BANNER = 8, RUG = 9, SIGIL = 10,
            // Room-event centrepieces GameActivity answers when the player walks up (see answers())
            SHRINE = 11, GAMBLER = 12, GONG = 13, PRISONER = 14, LECTERN = 15, FOUNTAIN = 16,
            // Event-room dressing
            BLOOD_POOL = 17, CARD_TABLE = 18, COIN_PILE = 19, COFFIN = 20, RUNESTONE = 21, WEAPON_RACK = 22,
            BRAZIER = 23, HAY = 24, CHAINS = 25, BUCKET = 26, BOOKSHELF = 27, SCROLL = 28, MOSS_PATCH = 29,
            CURSE_SIGIL = 30,
            // Biome hazards: water slows, vents erupt, rifts only glow
            PUDDLE = 31, VENT = 32, RIFT = 33;

    /** One decoration in world space; a and b hold its size, angle or variant, depending on the kind. */
    static final class Decor {
        final int kind;
        final float x, y, a, b;
        volatile boolean broken; // a smashed crate or barrel
        volatile boolean used;   // a room event that has made its deal (shrine, gambler, gong, prisoner ...)
        long burnedCycle = -1;   // vents: the last eruption that burned (logic thread only)

        Decor(int kind, float x, float y, float a, float b) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.a = a;
            this.b = b;
        }
    }

    // Rubble stones {dx, dy, radius}
    private static final float[] RUBBLE_STONES = {-14, 4, 9, 6, -6, 7, 16, 8, 5, -4, 12, 4, 2, -14, 5};
    // Cobweb strand directions (radians) inside one corner
    private static final float[] WEB_ANGLES = {0.09f, 0.52f, 0.96f, 1.48f};

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);   // warm pools on the floor
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);    // small glow round flames
    private final Paint arcaneGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint goldGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bannerPath = new Path(), emblemPath = new Path(), shapePath = new Path();
    private final RectF rect = new RectF();
    private final float[] spot = new float[2];
    // Torch and candle fire; the dungeon floors take their biome's (setBiome), the shop keeps the default
    private int flame = FLAME, flameCore = FLAME_CORE;

    public Props() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        setFire(0xFF9A40, 0xFFC060);
        arcaneGlowPaint.setShader(new RadialGradient(0, 0, 130,
                new int[]{0xAA8B5FBF, 0x442B1840, 0x00000000}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        goldGlowPaint.setShader(new RadialGradient(0, 0, 130,
                new int[]{0xAAD4AF37, 0x447A5A1F, 0x00000000}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(28f);
        labelPaint.setShadowLayer(6f, 0f, 0f, Color.BLACK);
        setFont(Typeface.SERIF);

        // Banner cloth hanging from (0, 0) and its gold emblem
        bannerPath.moveTo(-22, 0);
        bannerPath.lineTo(22, 0);
        bannerPath.lineTo(22, 84);
        bannerPath.lineTo(0, 68);
        bannerPath.lineTo(-22, 84);
        bannerPath.close();
        emblemPath.moveTo(0, 22);
        emblemPath.lineTo(10, 36);
        emblemPath.lineTo(0, 50);
        emblemPath.lineTo(-10, 36);
        emblemPath.close();
    }

    /** Uses the game's title font for portal labels. */
    public void setFont(Typeface font) {
        labelPaint.setTypeface(Typeface.create(font, Typeface.BOLD));
    }

    /** Lights the floor's torches and candles in its biome's fire: warm in the Crypt, witchfire in the Catacombs. */
    void setBiome(Biome biome) {
        flame = biome.flame;
        flameCore = biome.flameCore;
        setFire(biome.light, biome.flameCore & 0xFFFFFF);
        // Pixel fire in this biome's colours (art/sprites.mjs bakes a set per biome)
        String key = biome.name().toLowerCase(java.util.Locale.ROOT);
        torchNorth = "prop.torch.north." + key;
        torchSide = "prop.torch.side." + key;
        candle = "prop.candle." + key;
        brazier = "prop.brazier." + key;
    }

    /**
     * How far the brick face of a north wall reaches down into a room, seen from above: about the height of
     * a body, so walls stand taller than the people walking past them and torches hang on them, not above.
     */
    static final float WALL_FACE = 88f;
    // North torches are placed 34 below the wall's top; their plate hangs 30 lower, low on the face, so the
    // flame burns against the bricks and only its smoke climbs past the top of the wall
    private static final float TORCH_DROP = 30f;
    // Side torches are placed 24 in from their wall; the bar sits on the wall's inner edge, 8 in
    private static final float TORCH_INSET = 16f;
    private String torchNorth = "prop.torch.north.crypt", torchSide = "prop.torch.side.crypt";
    private String candle = "prop.candle.crypt", brazier = "prop.brazier.crypt";

    /** Light pools and flame halos for a fire whose light is the RGB colour light. */
    private void setFire(int light, int halo) {
        lightPaint.setShader(new RadialGradient(0, 0, 260,
                new int[]{0x50000000 | light, 0x20000000 | light, 0x00000000}, new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        haloPaint.setShader(new RadialGradient(0, 0, 40,
                new int[]{0x99000000 | halo, 0x00000000 | light}, null, Shader.TileMode.CLAMP));
    }

    /* ============================================================
       PROPS
       ============================================================ */

    /** Warm flickering pool of torchlight on the floor. */
    void drawLight(Canvas canvas, float x, float y, long now, int phase) {
        float flick = 0.92f + 0.08f * (float) (Math.sin(now / 90.0 + phase * 1.7) * Math.sin(now / 37.0 + phase));
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(flick, flick);
        canvas.drawCircle(0, 0, 260, lightPaint);
        canvas.restore();
    }

    /** Soft glow round a small flame. */
    void drawHalo(Canvas canvas, float x, float y, float scale) {
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        canvas.drawCircle(0, 0, 40, haloPaint);
        canvas.restore();
    }

    /** Rune circle with counter-turning arcs and a pulsing core; purple for the exit, gold for the shop. */
    void drawPortal(Canvas canvas, float x, float y, float r, long now, boolean gold, String label) {
        // Pixel art: pixelfx's standing portal, violet to the next floor, gold to the shop, its foot on the spot
        Sprites.Anim portal = Sprites.get(gold ? "fx.portal.shop" : "fx.portal.exit");
        if (portal != null) {
            canvas.save();
            canvas.translate(x, y);
            canvas.scale(0.9f + 0.1f * (float) Math.sin(now / 400.0), 0.9f + 0.1f * (float) Math.sin(now / 400.0));
            canvas.drawCircle(0, 0, 130, gold ? goldGlowPaint : arcaneGlowPaint);
            canvas.restore();
            Sprites.draw(canvas, portal, portal.at(now), x, y + r * 0.5f, false, 0, 255);
            if (label != null) {
                labelPaint.setColor(gold ? GOLD_GLOW : ARCANE_GLOW);
                canvas.drawText(label, x, y - r * 2f, labelPaint); // over the portal's top
            }
            return;
        }
        int rune = gold ? GOLD_GLOW : ARCANE_GLOW;
        int arc = gold ? GOLD_BRIGHT : ARCANE_BRIGHT;
        float pulse = 0.85f + 0.15f * (float) Math.sin(now / 400.0);
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(pulse, pulse);
        canvas.drawCircle(0, 0, 130, gold ? goldGlowPaint : arcaneGlowPaint);
        canvas.restore();

        stroke.setColor(STONE_MID);
        stroke.setStrokeWidth(12f);
        canvas.drawCircle(x, y, r + 18, stroke);
        stroke.setColor(STONE_DARK);
        stroke.setStrokeWidth(3f);
        canvas.drawCircle(x, y, r + 26, stroke);

        // Runes on the stone ring, turning slowly
        canvas.save();
        canvas.rotate((now % 12000) / 12000f * 360f, x, y);
        stroke.setColor(rune);
        for (int i = 0; i < 8; i++) {
            canvas.rotate(45f, x, y);
            canvas.drawLine(x, y - r - 23, x, y - r - 13, stroke);
        }
        canvas.restore();

        float a = (now % 3000) / 3000f * 360f;
        stroke.setColor(arc);
        stroke.setStrokeWidth(5f);
        rect.set(x - 52, y - 52, x + 52, y + 52);
        canvas.drawArc(rect, a, 100, false, stroke);
        canvas.drawArc(rect, a + 180, 100, false, stroke);
        stroke.setColor(rune);
        stroke.setStrokeWidth(3f);
        rect.set(x - 34, y - 34, x + 34, y + 34);
        canvas.drawArc(rect, -a * 1.5f, 120, false, stroke);
        canvas.drawArc(rect, -a * 1.5f + 180, 120, false, stroke);

        fill.setColor(rune);
        fill.setAlpha((int) (200 * pulse));
        canvas.drawCircle(x, y, 18 * pulse, fill);
        labelPaint.setColor(rune);
        canvas.drawText(label, x, y - r - 44, labelPaint);
    }

    /** Crimson banner hanging from (x, y), swaying a little. */
    void drawBanner(Canvas canvas, float x, float y, long now) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate((float) Math.sin(now / 900.0) * 2f);
        fill.setColor(CRIMSON_BASE);
        canvas.drawPath(bannerPath, fill);
        stroke.setColor(GOLD_DEEP);
        stroke.setStrokeWidth(3f);
        canvas.drawPath(bannerPath, stroke);
        fill.setColor(GOLD_BRIGHT);
        canvas.drawPath(emblemPath, fill);
        fill.setColor(IRON);
        canvas.drawRect(-26, -4, 26, 2, fill);
        canvas.restore();
    }

    /** Wall torch with a flickering flame. */
    void drawTorch(Canvas canvas, float x, float y, long now, int phase) {
        float flick = 0.85f + 0.15f * (float) Math.sin(now / 70.0 + phase * 2.1);
        fill.setColor(IRON);
        canvas.drawRect(x - 6, y - 2, x + 6, y + 14, fill);
        fill.setColor(WOOD_DARK);
        canvas.drawRect(x - 4, y - 16, x + 4, y + 2, fill);
        canvas.save();
        canvas.translate(x, y - 22);
        canvas.scale(flick, flick);
        canvas.drawCircle(0, 0, 40, haloPaint);
        fill.setColor(flame);
        canvas.drawCircle(0, 0, 9, fill);
        canvas.drawCircle(0, -8, 6, fill);
        fill.setColor(flameCore);
        canvas.drawCircle(0, 1, 5, fill);
        canvas.restore();
    }

    /** Soft drop shadow under a box, cast down-right. */
    void drawShadow(Canvas canvas, float l, float t, float r, float b) {
        fill.setColor(SHADOW);
        canvas.drawRect(l + 8, t + 10, r + 8, b + 10, fill);
    }

    /** Round stone pillar lit from the top-left. */
    void drawPillar(Canvas canvas, float x, float y, float r) {
        fill.setColor(SHADOW);
        canvas.drawCircle(x + 8, y + 10, r, fill);
        fill.setColor(STONE_DARK);
        canvas.drawCircle(x, y, r, fill);
        fill.setColor(STONE_MID);
        canvas.drawCircle(x - 3, y - 3, r - 6, fill);
        fill.setColor(STONE_LIGHT);
        canvas.drawCircle(x - r * 0.3f, y - r * 0.3f, r * 0.35f, fill);
        stroke.setColor(GOLD_DEEP);
        stroke.setStrokeWidth(3f);
        canvas.drawCircle(x, y, r - 2, stroke);
    }

    /** Small standing stone with a glowing rune. */
    void drawRuneStone(Canvas canvas, float x, float y, float r, long now) {
        drawPillar(canvas, x, y, r);
        fill.setColor(ARCANE_GLOW);
        fill.setAlpha((int) (140 + 100 * Math.sin(now / 500.0)));
        canvas.drawRect(x - 2, y - 9, x + 2, y + 9, fill);
        canvas.drawRect(x - 7, y - 2, x + 7, y + 2, fill);
    }

    void drawCandle(Canvas canvas, float x, float y, long now) {
        float flick = 0.8f + 0.2f * (float) Math.sin(now / 60.0);
        fill.setColor(PARCHMENT);
        canvas.drawRect(x - 4, y - 14, x + 4, y + 4, fill);
        drawHalo(canvas, x, y - 20, flick * 0.6f);
        fill.setColor(flameCore);
        canvas.drawCircle(x, y - 19, 3.5f * flick, fill);
    }

    /** Wooden crate: lit top with a brace, darker front face. */
    void drawCrate(Canvas canvas, float l, float t, float r, float b) {
        float face = b - 22;
        drawShadow(canvas, l, t, r, b);
        fill.setColor(WOOD_LIGHT);
        canvas.drawRect(l, t, r, face, fill);
        fill.setColor(WOOD_DARK);
        canvas.drawRect(l, face, r, b, fill);
        stroke.setColor(WOOD_LINE);
        stroke.setStrokeWidth(3f);
        canvas.drawRect(l + 4, t + 4, r - 4, face - 4, stroke);
        canvas.drawLine(l + 4, t + 4, r - 4, face - 4, stroke);
        canvas.drawLine(l, face + 11, r, face + 11, stroke);
    }

    /** Barrel seen from slightly above: lid, staves and iron hoops. */
    void drawBarrel(Canvas canvas, float x, float y, float r) {
        fill.setColor(SHADOW);
        canvas.drawCircle(x + 7, y + 10, r, fill);
        fill.setColor(WOOD_MID);
        rect.set(x - r, y - r * 0.6f, x + r, y + r);
        canvas.drawRoundRect(rect, r * 0.5f, r * 0.5f, fill);
        stroke.setColor(IRON);
        stroke.setStrokeWidth(5f);
        canvas.drawLine(x - r + 3, y + r * 0.05f, x + r - 3, y + r * 0.05f, stroke);
        canvas.drawLine(x - r + 4, y + r * 0.6f, x + r - 4, y + r * 0.6f, stroke);
        fill.setColor(WOOD_LIGHT);
        rect.set(x - r, y - r, x + r, y - r * 0.2f);
        canvas.drawOval(rect, fill);
        stroke.setStrokeWidth(4f);
        canvas.drawOval(rect, stroke);
        stroke.setColor(WOOD_LINE);
        stroke.setStrokeWidth(2f);
        canvas.drawLine(x - r * 0.5f, y - r * 0.6f, x + r * 0.5f, y - r * 0.6f, stroke);
    }

    /** Crimson rug with a gold border, a diamond medallion and fringed ends. */
    void drawRug(Canvas canvas, float l, float t, float r, float b) {
        fill.setColor(CRIMSON_DEEP);
        canvas.drawRect(l, t, r, b, fill);
        fill.setColor(CRIMSON_BASE);
        canvas.drawRect(l + 14, t + 14, r - 14, b - 14, fill);
        stroke.setColor(GOLD_DEEP);
        stroke.setStrokeWidth(4f);
        canvas.drawRect(l + 7, t + 7, r - 7, b - 7, stroke);
        stroke.setColor(GOLD_BASE);
        stroke.setStrokeWidth(2f);
        canvas.drawRect(l + 24, t + 24, r - 24, b - 24, stroke);
        float cx = (l + r) / 2f, cy = (t + b) / 2f;
        canvas.save();
        canvas.rotate(45f, cx, cy);
        fill.setColor(CRIMSON_DEEP);
        canvas.drawRect(cx - 38, cy - 38, cx + 38, cy + 38, fill);
        stroke.setColor(GOLD_BRIGHT);
        canvas.drawRect(cx - 38, cy - 38, cx + 38, cy + 38, stroke);
        canvas.drawRect(cx - 18, cy - 18, cx + 18, cy + 18, stroke);
        canvas.restore();
        stroke.setColor(GOLD_DEEP);
        for (float x = l + 6; x < r; x += 12) {
            canvas.drawLine(x, t, x, t - 10, stroke);
            canvas.drawLine(x, b, x, b + 10, stroke);
        }
    }

    /** Skull beside two crossed bones. */
    void drawBones(Canvas canvas, float x, float y, float angle) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(angle);
        stroke.setColor(BONE);
        stroke.setStrokeWidth(6f);
        canvas.drawLine(-22, -10, 22, 10, stroke);
        canvas.drawLine(-22, 10, 22, -10, stroke);
        fill.setColor(BONE);
        canvas.drawCircle(-24, -12, 5, fill);
        canvas.drawCircle(24, 12, 5, fill);
        canvas.drawCircle(-24, 12, 5, fill);
        canvas.drawCircle(24, -12, 5, fill);
        canvas.drawCircle(30, -20, 12, fill);
        canvas.drawRect(25, -12, 35, -5, fill);
        fill.setColor(STONE_DARK);
        canvas.drawCircle(26, -21, 3, fill);
        canvas.drawCircle(34, -21, 3, fill);
        canvas.restore();
    }

    /** A few loose stones. */
    void drawRubble(Canvas canvas, float x, float y, float angle) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(angle);
        for (int i = 0; i < RUBBLE_STONES.length; i += 3) {
            float sx = RUBBLE_STONES[i], sy = RUBBLE_STONES[i + 1], sr = RUBBLE_STONES[i + 2];
            fill.setColor(SHADOW);
            canvas.drawCircle(sx + 2, sy + 3, sr, fill);
            fill.setColor(STONE_MID);
            canvas.drawCircle(sx, sy, sr, fill);
            fill.setColor(STONE_LIGHT);
            canvas.drawCircle(sx - sr * 0.3f, sy - sr * 0.3f, sr * 0.4f, fill);
        }
        canvas.restore();
    }

    /** What is left of a smashed crate or barrel: planks scattered where it stood. */
    void drawSplinters(Canvas canvas, float x, float y, float r) {
        fill.setColor(WOOD_MID);
        for (int i = 0; i < 5; i++) {
            float a = i * 1.3f + x * 0.013f;
            float px = x + (float) Math.cos(a) * r * 0.7f, py = y + (float) Math.sin(a * 1.7f) * r * 0.6f;
            canvas.save();
            canvas.rotate((float) Math.toDegrees(a * 2.1f), px, py);
            canvas.drawRect(px - r * 0.35f, py - 3f, px + r * 0.35f, py + 3f, fill);
            canvas.restore();
        }
    }

    /** Cobweb in a room corner; (sx, sy) point into the room. */
    void drawCobweb(Canvas canvas, float x, float y, float sx, float sy) {
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(sx, sy);
        stroke.setColor(BONE);
        stroke.setAlpha(70);
        stroke.setStrokeWidth(1.5f);
        for (float a : WEB_ANGLES) canvas.drawLine(0, 0, (float) Math.cos(a) * 80, (float) Math.sin(a) * 80, stroke);
        for (float ring = 24; ring <= 72; ring += 24) {
            for (int i = 0; i < WEB_ANGLES.length - 1; i++) {
                canvas.drawLine((float) Math.cos(WEB_ANGLES[i]) * ring, (float) Math.sin(WEB_ANGLES[i]) * ring,
                        (float) Math.cos(WEB_ANGLES[i + 1]) * ring, (float) Math.sin(WEB_ANGLES[i + 1]) * ring, stroke);
            }
        }
        canvas.restore();
    }

    /** Crimson summoning circle with a slowly turning star, under the boss. */
    void drawSigil(Canvas canvas, float x, float y, float r, long now) {
        drawSigil(canvas, x, y, r, now, CRIMSON_BASE, CRIMSON_GLOW);
    }

    /** The summoning circle in any colours, e.g. purple under a cursed chest. */
    void drawSigil(Canvas canvas, float x, float y, float r, long now, int ring, int star) {
        int alpha = (int) (110 + 50 * Math.sin(now / 700.0));
        stroke.setColor(ring);
        stroke.setAlpha(alpha);
        stroke.setStrokeWidth(6f);
        canvas.drawCircle(x, y, r, stroke);
        stroke.setStrokeWidth(3f);
        canvas.drawCircle(x, y, r * 0.86f, stroke);
        canvas.save();
        canvas.rotate((now % 40000) / 40000f * 360f, x, y);
        stroke.setColor(star);
        stroke.setAlpha(alpha);
        for (int i = 0; i < 5; i++) {
            double a1 = -Math.PI / 2 + i * Math.PI * 2 / 5, a2 = a1 + Math.PI * 4 / 5;
            canvas.drawLine(x + (float) Math.cos(a1) * r * 0.84f, y + (float) Math.sin(a1) * r * 0.84f,
                    x + (float) Math.cos(a2) * r * 0.84f, y + (float) Math.sin(a2) * r * 0.84f, stroke);
        }
        canvas.restore();
    }

    /* ============================================================
       DUNGEON DECORATION
       ============================================================ */

    /** Torchlight first, so props and bodies sit inside it. */
    void drawDecorLight(Canvas canvas, Decor d, long now) {
        if (d.kind == BRAZIER) {
            drawLight(canvas, d.x, d.y, now, (int) d.x);
            return;
        }
        if (d.kind != TORCH) return;
        torchLight(d, spot);
        drawLight(canvas, spot[0], spot[1], now, (int) d.a);
    }

    /** Where a torch's light pool falls: a little into the room from its wall. */
    static void torchLight(Decor d, float[] out) {
        out[0] = d.b == Room.WEST ? d.x + 130 : d.b == Room.EAST ? d.x - 130 : d.x;
        out[1] = d.b == Room.NORTH ? d.y + 130 : d.y;
    }

    /* ============================================================
       COLLISION — barrels, crates, pillars and the heavier event furniture are solid
       ============================================================ */

    private static boolean solid(Decor d) {
        if (d.broken) return false;
        switch (d.kind) {
            case BARREL: case CRATE: case PILLAR: case RUNESTONE: case BRAZIER: case FOUNTAIN:
            case BOOKSHELF: case COFFIN: case CARD_TABLE:
                return true;
            default:
                return false;
        }
    }

    /** Box-shaped solids, a and b half their width and height (a crate is square); the rest are round, radius a. */
    private static boolean boxy(Decor d) {
        return d.kind == CRATE || d.kind == BOOKSHELF || d.kind == COFFIN || d.kind == CARD_TABLE;
    }

    private static float halfHeight(Decor d) {
        return d.kind == CRATE ? d.a : d.b;
    }

    /** Crates and barrels break when hit. */
    static boolean breakable(Decor d) {
        return !d.broken && (d.kind == BARREL || d.kind == CRATE);
    }

    /** Pushes a body of radius r out of every solid prop it overlaps; the result goes to out. */
    static void pushOut(List<Decor> decor, float x, float y, float r, float[] out) {
        for (Decor d : decor) {
            if (!solid(d)) continue;
            if (boxy(d)) {
                float hw = d.a, hh = halfHeight(d);
                float nx = Math.max(d.x - hw, Math.min(d.x + hw, x));
                float ny = Math.max(d.y - hh, Math.min(d.y + hh, y));
                float dx = x - nx, dy = y - ny, d2 = dx * dx + dy * dy;
                if (d2 >= r * r) continue;
                if (d2 > 0.0001f) {
                    float dist = (float) Math.sqrt(d2);
                    x = nx + dx / dist * r;
                    y = ny + dy / dist * r;
                } else {
                    // Centre inside the box: leave through the nearest side
                    float left = x - (d.x - hw), right = d.x + hw - x;
                    float up = y - (d.y - hh), down = d.y + hh - y;
                    float min = Math.min(Math.min(left, right), Math.min(up, down));
                    if (min == left) x = d.x - hw - r;
                    else if (min == right) x = d.x + hw + r;
                    else if (min == up) y = d.y - hh - r;
                    else y = d.y + hh + r;
                }
            } else {
                float dx = x - d.x, dy = y - d.y, min = r + d.a;
                float d2 = dx * dx + dy * dy;
                if (d2 >= min * min) continue;
                float dist = (float) Math.sqrt(d2);
                if (dist < 0.01f) {
                    x = d.x + min;
                    continue;
                }
                x = d.x + dx / dist * min;
                y = d.y + dy / dist * min;
            }
        }
        out[0] = x;
        out[1] = y;
    }

    /** True when a point is inside a solid prop, e.g. a shot hitting a barrel. */
    static boolean blocks(List<Decor> decor, float x, float y) {
        for (Decor d : decor) {
            if (!solid(d)) continue;
            boolean inside = boxy(d)
                    ? Math.abs(x - d.x) < d.a && Math.abs(y - d.y) < halfHeight(d)
                    : (x - d.x) * (x - d.x) + (y - d.y) * (y - d.y) < d.a * d.a;
            if (inside) return true;
        }
        return false;
    }

    /* ============================================================
       ROOM EVENTS AND BIOME HAZARDS — rules GameActivity applies
       ============================================================ */

    /** Room-event centrepieces: walking up to one pauses the game and asks (GameActivity.checkRoomEvents). */
    static boolean answers(Decor d) {
        switch (d.kind) {
            case SHRINE: case GAMBLER: case GONG: case PRISONER: case LECTERN: case FOUNTAIN:
                return true;
            default:
                return false;
        }
    }

    /** How close a player must come to be asked; the spring's basin is wide and solid. */
    static float reach(Decor d) {
        return d.kind == FOUNTAIN ? d.a + 70f : 90f;
    }

    /** True when a point is in a pool of water (Flooded Catacombs), which slows whoever wades in it. */
    static boolean inWater(List<Decor> decor, float x, float y) {
        for (Decor d : decor) {
            if (d.kind != PUDDLE) continue;
            float dx = (x - d.x) / d.a, dy = (y - d.y) / d.b;
            if (dx * dx + dy * dy <= 1f) return true;
        }
        return false;
    }

    // Lava vents (Infernal Forge) loop: smoulder, rumble bright from VENT_WARN, erupt from VENT_BURST.
    // a holds each vent's offset into the loop, so they don't all go off together; b is its radius.
    static final long VENT_CYCLE = 4000, VENT_WARN = 2600, VENT_BURST = 3400;

    static long ventTime(Decor d, long now) {
        return Math.floorMod(now + (long) d.a, VENT_CYCLE);
    }

    static boolean venting(Decor d, long now) {
        return ventTime(d, now) >= VENT_BURST;
    }

    /** Which eruption this is, so each one burns only once. */
    static long ventCycle(Decor d, long now) {
        return Math.floorDiv(now + (long) d.a, VENT_CYCLE);
    }

    /** Blood shrine: a standing stone with a crimson rune over a bowl; both go dark once it's used. */
    void drawShrine(Canvas canvas, float x, float y, long now, boolean used) {
        drawPillar(canvas, x, y, 34);
        fill.setColor(used ? STONE_DARK : CRIMSON_GLOW);
        if (!used) fill.setAlpha((int) (150 + 100 * Math.sin(now / 400.0)));
        canvas.drawRect(x - 3, y - 14, x + 3, y + 14, fill);
        canvas.drawRect(x - 10, y - 3, x + 10, y + 3, fill);
        fill.setColor(IRON);
        canvas.drawCircle(x, y + 50, 16, fill);
        fill.setColor(used ? STONE_MID : CRIMSON_BASE);
        canvas.drawCircle(x, y + 50, 11, fill);
    }

    /** The gambler: a hooded figure with glinting eyes behind a table of dice and gold. */
    void drawGambler(Canvas canvas, float x, float y, long now, boolean used) {
        fill.setColor(SHADOW);
        canvas.drawOval(x - 46, y + 22, x + 46, y + 46, fill);
        fill.setColor(CLOAK_DARK);
        canvas.drawCircle(x, y - 4, 30, fill);
        fill.setColor(CLOAK_MID);
        canvas.drawCircle(x, y - 14, 18, fill);
        fill.setColor(GOLD_GLOW);
        canvas.drawCircle(x - 6, y - 14, 2.5f, fill);
        canvas.drawCircle(x + 6, y - 14, 2.5f, fill);
        fill.setColor(WOOD_MID);
        canvas.drawRect(x - 42, y + 14, x + 42, y + 36, fill);
        fill.setColor(WOOD_LINE);
        canvas.drawRect(x - 42, y + 32, x + 42, y + 36, fill);
        if (!used) {
            float bob = (float) Math.sin(now / 300.0) * 2f;
            fill.setColor(GOLD_BRIGHT);
            canvas.drawCircle(x - 20, y + 23 + bob, 6, fill);
            canvas.drawCircle(x - 11, y + 21 + bob, 6, fill);
        }
        fill.setColor(BONE);
        canvas.drawRect(x + 8, y + 18, x + 18, y + 28, fill);
        canvas.drawRect(x + 21, y + 20, x + 30, y + 29, fill);
    }

    /* ============================================================
       EVENT ROOM PROPS
       ============================================================ */

    /** The Trial gong: a bronze disc hung in a wooden frame, a crimson shimmer until it is struck. */
    void drawGong(Canvas canvas, float x, float y, long now, boolean used) {
        fill.setColor(SHADOW);
        canvas.drawRect(x - 74, y + 34, x + 90, y + 50, fill);
        // Posts and the crossbar
        fill.setColor(WOOD_DARK);
        canvas.drawRect(x - 82, y - 62, x - 66, y + 44, fill);
        canvas.drawRect(x + 66, y - 62, x + 82, y + 44, fill);
        fill.setColor(WOOD_MID);
        canvas.drawRect(x - 88, y - 70, x + 88, y - 56, fill);
        stroke.setColor(WOOD_LINE);
        stroke.setStrokeWidth(2f);
        canvas.drawRect(x - 88, y - 70, x + 88, y - 56, stroke);
        // Ropes and the disc, swaying a little until it is struck
        float sway = used ? 0f : (float) Math.sin(now / 800.0) * 2f;
        stroke.setColor(LEATHER);
        stroke.setStrokeWidth(3f);
        canvas.drawLine(x - 22, y - 56, x - 16 + sway, y - 36, stroke);
        canvas.drawLine(x + 22, y - 56, x + 16 + sway, y - 36, stroke);
        float gx = x + sway;
        if (!used) {
            stroke.setColor(CRIMSON_GLOW);
            stroke.setAlpha((int) (90 + 70 * Math.sin(now / 350.0)));
            stroke.setStrokeWidth(4f);
            canvas.drawCircle(gx, y, 52, stroke);
        }
        fill.setColor(GOLD_DEEP);
        canvas.drawCircle(gx, y, 42, fill);
        fill.setColor(used ? WOOD_LIGHT : GOLD_BASE);
        canvas.drawCircle(gx - 2, y - 2, 36, fill);
        stroke.setColor(GOLD_DEEP);
        stroke.setStrokeWidth(2f);
        canvas.drawCircle(gx, y, 25, stroke);
        canvas.drawCircle(gx, y, 13, stroke);
        fill.setColor(used ? GOLD_DEEP : GOLD_BRIGHT);
        canvas.drawCircle(gx - 1, y - 1, 7, fill);
        // The mallet leans on the right post
        stroke.setColor(WOOD_MID);
        stroke.setStrokeWidth(5f);
        canvas.drawLine(x + 62, y + 40, x + 44, y + 6, stroke);
        fill.setColor(LEATHER);
        canvas.drawCircle(x + 42, y + 2, 9, fill);
    }

    /** A prisoner kneeling, chained to the wall behind them (side); once freed only broken shackles hang there. */
    void drawPrisoner(Canvas canvas, float x, float y, int side, long now, boolean used) {
        float ax = side == Room.WEST ? x - 150 : side == Room.EAST ? x + 150 : x;
        float ay = side == Room.NORTH ? y - 150 : side == Room.SOUTH ? y + 150 : y;
        boolean across = side == Room.NORTH || side == Room.SOUTH;
        float px = across ? 34 : 0, py = across ? 0 : 34; // the two chains sit either side of the body
        if (used) {
            for (int s = -1; s <= 1; s += 2) {
                float sx = ax + px * s, sy = ay + py * s;
                drawChainLinks(canvas, sx, sy, sx + (x - ax) * 0.3f, sy + (y - ay) * 0.3f);
            }
            return;
        }
        float bob = (float) Math.sin(now / 700.0) * 1.5f;
        for (int s = -1; s <= 1; s += 2)
            drawChainLinks(canvas, ax + px * s, ay + py * s, x + px * 0.6f * s, y + py * 0.6f * s + bob);
        fill.setColor(SHADOW);
        rect.set(x - 32, y + 10, x + 34, y + 36);
        canvas.drawOval(rect, fill);
        fill.setColor(LEATHER);
        canvas.drawCircle(x, y + 4 + bob, 26, fill);
        fill.setColor(WOOD_DARK);
        canvas.drawCircle(x + 6, y + 10 + bob, 14, fill); // rags in shadow
        fill.setColor(SKIN);
        canvas.drawCircle(x, y - 16 + bob, 13, fill);
        fill.setColor(INK);
        canvas.drawCircle(x - 4, y - 18 + bob, 2, fill);
        canvas.drawCircle(x + 4, y - 18 + bob, 2, fill);
        fill.setColor(IRON);
        canvas.drawCircle(x + px * 0.6f, y + py * 0.6f + bob, 6, fill);
        canvas.drawCircle(x - px * 0.6f, y - py * 0.6f + bob, 6, fill);
    }

    /** Iron chain links from one point to another. */
    private void drawChainLinks(Canvas canvas, float x1, float y1, float x2, float y2) {
        float len = (float) Math.hypot(x2 - x1, y2 - y1);
        int links = Math.max(2, (int) (len / 14f));
        stroke.setColor(IRON_LIGHT);
        stroke.setStrokeWidth(2.5f);
        for (int i = 0; i <= links; i++) {
            float t = i / (float) links;
            canvas.drawCircle(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, 4.5f, stroke);
        }
    }

    /** Empty shackles hanging off a wall point into the room (side = the wall they hang from). */
    /**
     * A pair of empty shackles fixed to a wall at (x, y), hanging straight down as chains do: down the brick
     * face of a north wall, or down the side of a west or east wall from a ring part way up it.
     */
    void drawChains(Canvas canvas, float x, float y, int side) {
        boolean north = side != Room.WEST && side != Room.EAST;
        float in = side == Room.WEST ? 10 : side == Room.EAST ? -10 : 0;
        for (int s = -1; s <= 1; s += 2) {
            float sx = north ? x + 14 * s : x + in, sy = north ? y + 6 : y + 16 * s - 56;
            float ey = sy + (north ? 54 : 44);
            drawChainLinks(canvas, sx, sy, sx, ey);
            stroke.setColor(IRON);
            stroke.setStrokeWidth(4f);
            canvas.drawCircle(sx, ey + 8, 9, stroke);
        }
    }

    /** A lectern bearing the forbidden tome: open and faintly glowing until read, then shut. */
    void drawLectern(Canvas canvas, float x, float y, long now, boolean used) {
        fill.setColor(SHADOW);
        canvas.drawRect(x - 36, y + 38, x + 44, y + 54, fill);
        fill.setColor(WOOD_MID);
        canvas.drawRect(x - 28, y + 36, x + 28, y + 48, fill);
        fill.setColor(WOOD_DARK);
        canvas.drawRect(x - 8, y, x + 8, y + 40, fill);
        fill.setColor(WOOD_MID);
        canvas.drawRect(x - 46, y - 32, x + 46, y + 8, fill);
        stroke.setColor(WOOD_LINE);
        stroke.setStrokeWidth(3f);
        canvas.drawRect(x - 46, y - 32, x + 46, y + 8, stroke);
        if (used) {
            fill.setColor(TOME);
            canvas.drawRect(x - 26, y - 26, x + 26, y + 2, fill);
            stroke.setColor(GOLD_DEEP);
            stroke.setStrokeWidth(2f);
            canvas.drawRect(x - 22, y - 22, x + 22, y - 2, stroke);
            return;
        }
        float pulse = 0.8f + 0.2f * (float) Math.sin(now / 500.0);
        canvas.save();
        canvas.translate(x, y - 12);
        canvas.scale(0.55f * pulse, 0.55f * pulse);
        canvas.drawCircle(0, 0, 130, arcaneGlowPaint);
        canvas.restore();
        fill.setColor(PARCHMENT);
        canvas.drawRect(x - 40, y - 28, x - 2, y + 2, fill);
        canvas.drawRect(x + 2, y - 28, x + 40, y + 2, fill);
        stroke.setColor(INK);
        stroke.setStrokeWidth(2f);
        canvas.drawLine(x, y - 28, x, y + 2, stroke);
        stroke.setAlpha(120);
        stroke.setStrokeWidth(1.5f);
        for (float ly = y - 22; ly < y; ly += 6) {
            canvas.drawLine(x - 35, ly, x - 7, ly, stroke);
            canvas.drawLine(x + 7, ly, x + 35, ly, stroke);
        }
        fill.setColor(ARCANE_GLOW);
        fill.setAlpha((int) (200 * pulse));
        canvas.drawCircle(x + 21, y - 13, 4, fill); // a sigil burns on the page
    }

    /** The healing spring: a stone basin of bright water round a spout; drunk dry, it lies dark and still. */
    void drawFountain(Canvas canvas, float x, float y, float r, long now, boolean used) {
        fill.setColor(SHADOW);
        canvas.drawCircle(x + 8, y + 10, r, fill);
        fill.setColor(STONE_DARK);
        canvas.drawCircle(x, y, r, fill);
        fill.setColor(STONE_MID);
        canvas.drawCircle(x - 2, y - 2, r - 8, fill);
        fill.setColor(used ? WATER_DEEP : WATER_GLOW);
        canvas.drawCircle(x, y, r - 18, fill);
        if (!used) {
            for (int i = 0; i < 2; i++) {
                float t = ((now + i * 1000L) % 2000) / 2000f;
                stroke.setColor(WATER_SHINE);
                stroke.setAlpha((int) (150 * (1f - t)));
                stroke.setStrokeWidth(2f);
                canvas.drawCircle(x, y, r * 0.24f + (r - 22) * 0.76f * t, stroke);
            }
        }
        stroke.setColor(STONE_LIGHT);
        stroke.setStrokeWidth(2f);
        canvas.drawCircle(x - 1, y - 1, r - 4, stroke);
        // Spout in the middle
        fill.setColor(STONE_LIGHT);
        canvas.drawCircle(x, y, r * 0.22f, fill);
        fill.setColor(STONE_MID);
        canvas.drawCircle(x, y, r * 0.15f, fill);
        if (used) return;
        float lift = (float) Math.sin(now / 200.0) * 2f;
        stroke.setColor(WATER_SHINE);
        stroke.setAlpha(190);
        stroke.setStrokeWidth(3f);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            canvas.drawLine(x + c * r * 0.18f, y + s * r * 0.18f - 4, x + c * r * 0.42f, y + s * r * 0.42f + lift, stroke);
        }
        fill.setColor(WATER_SHINE);
        fill.setAlpha((int) (180 + 70 * Math.sin(now / 160.0)));
        canvas.drawCircle(x, y - 4, 4, fill);
    }

    /** A pool of blood with a few drips round its edge and a wet sheen. */
    void drawBloodPool(Canvas canvas, float x, float y, float rx, float ry) {
        fill.setColor(BLOOD_DARK);
        rect.set(x - rx, y - ry, x + rx, y + ry);
        canvas.drawOval(rect, fill);
        float drip = Math.min(rx, ry) * 0.18f;
        for (int i = 0; i < 4; i++) {
            double a = i * 1.7 + x * 0.01;
            canvas.drawCircle(x + (float) Math.cos(a) * rx * 1.05f, y + (float) Math.sin(a) * ry * 1.05f, drip, fill);
        }
        fill.setColor(BLOOD_WET);
        rect.set(x - rx * 0.78f, y - ry * 0.74f, x + rx * 0.7f, y + ry * 0.66f);
        canvas.drawOval(rect, fill);
        fill.setColor(CRIMSON_GLOW);
        fill.setAlpha(60);
        rect.set(x - rx * 0.5f, y - ry * 0.5f, x - rx * 0.12f, y - ry * 0.3f);
        canvas.drawOval(rect, fill);
    }

    /** The gambler's card table: green felt, dealt cards, a few coins and a stub of candle. */
    void drawCardTable(Canvas canvas, float x, float y, float hw, float hh, long now) {
        drawShadow(canvas, x - hw, y - hh, x + hw, y + hh);
        fill.setColor(WOOD_MID);
        canvas.drawRect(x - hw, y - hh, x + hw, y + hh, fill);
        fill.setColor(FELT);
        canvas.drawRect(x - hw + 8, y - hh + 8, x + hw - 8, y + hh - 8, fill);
        stroke.setColor(WOOD_LINE);
        stroke.setStrokeWidth(3f);
        canvas.drawRect(x - hw, y - hh, x + hw, y + hh, stroke);
        for (int i = 0; i < 3; i++) {
            float cx = x - hw * 0.45f + i * 16, cy = y + (i - 1) * 3;
            canvas.save();
            canvas.rotate(-14 + i * 13, cx, cy);
            fill.setColor(PARCHMENT);
            canvas.drawRect(cx - 9, cy - 13, cx + 9, cy + 13, fill);
            fill.setColor(i == 1 ? INK : CRIMSON_BASE);
            canvas.drawCircle(cx, cy, 4, fill);
            canvas.restore();
        }
        fill.setColor(GOLD_BRIGHT);
        canvas.drawCircle(x + hw * 0.3f, y + 6, 6, fill);
        canvas.drawCircle(x + hw * 0.3f + 9, y + 2, 6, fill);
        drawCandle(canvas, x + hw - 18, y - hh + 26, now);
    }

    /** A heap of gold coins with a glint on top. */
    void drawCoinPile(Canvas canvas, float x, float y, float s) {
        fill.setColor(SHADOW);
        rect.set(x - s, y - s * 0.1f, x + s * 1.1f, y + s * 0.7f);
        canvas.drawOval(rect, fill);
        for (int i = 0; i < COIN_HEAP.length; i += 2) {
            float cx = x + COIN_HEAP[i] * s, cy = y + COIN_HEAP[i + 1] * s;
            fill.setColor(GOLD_DEEP);
            canvas.drawCircle(cx, cy + 2, s * 0.32f, fill);
            fill.setColor(GOLD_BRIGHT);
            canvas.drawCircle(cx, cy, s * 0.28f, fill);
        }
        fill.setColor(GOLD_GLOW);
        canvas.drawCircle(x + s * 0.05f - s * 0.08f, y - s * 0.3f, s * 0.09f, fill);
    }

    // Coins in a heap, {dx, dy} in units of the heap's size, back to front
    private static final float[] COIN_HEAP = {-0.5f, 0.15f, 0.3f, 0.25f, 0.6f, -0.05f, -0.15f, -0.1f, 0.15f, 0.05f,
            0.05f, -0.3f};

    /** A plain wooden coffin with a bone-white cross; lies along its longer side. */
    void drawCoffin(Canvas canvas, float x, float y, float hw, float hh) {
        drawShadow(canvas, x - hw, y - hh, x + hw, y + hh);
        canvas.save();
        canvas.translate(x, y);
        if (hw > hh) canvas.rotate(90f);
        float w = Math.min(hw, hh), h = Math.max(hw, hh);
        shapePath.reset();
        shapePath.moveTo(-w * 0.6f, -h);
        shapePath.lineTo(w * 0.6f, -h);
        shapePath.lineTo(w, -h * 0.45f);
        shapePath.lineTo(w * 0.7f, h);
        shapePath.lineTo(-w * 0.7f, h);
        shapePath.lineTo(-w, -h * 0.45f);
        shapePath.close();
        fill.setColor(WOOD_DARK);
        canvas.drawPath(shapePath, fill);
        stroke.setColor(WOOD_LINE);
        stroke.setStrokeWidth(3f);
        canvas.drawPath(shapePath, stroke);
        stroke.setColor(BONE_DIM);
        stroke.setStrokeWidth(4f);
        canvas.drawLine(0, -h * 0.55f, 0, h * 0.35f, stroke);
        canvas.drawLine(-w * 0.4f, -h * 0.25f, w * 0.4f, -h * 0.25f, stroke);
        canvas.restore();
    }

    /** A rack of spears against the wall: two rails and the shafts with their steel heads. */
    void drawWeaponRack(Canvas canvas, float x, float y, float hw) {
        fill.setColor(SHADOW);
        canvas.drawRect(x - hw + 6, y + 30, x + hw + 6, y + 40, fill);
        for (int i = 0; i < 4; i++) {
            float sx = x - hw * 0.75f + i * hw * 0.5f;
            stroke.setColor(WOOD_MID);
            stroke.setStrokeWidth(5f);
            canvas.drawLine(sx, y - 40, sx + 3, y + 32, stroke);
            stroke.setColor(STEEL);
            stroke.setStrokeWidth(6f);
            canvas.drawLine(sx, y - 42, sx, y - 58, stroke);
        }
        fill.setColor(WOOD_DARK);
        canvas.drawRect(x - hw, y - 8, x + hw, y + 4, fill);
        canvas.drawRect(x - hw, y + 20, x + hw, y + 30, fill);
        stroke.setColor(WOOD_LINE);
        stroke.setStrokeWidth(2f);
        canvas.drawRect(x - hw, y - 8, x + hw, y + 4, stroke);
    }

    /** An iron fire bowl, a big warm light for the arena. */
    void drawBrazier(Canvas canvas, float x, float y, float r, long now) {
        float flick = 0.85f + 0.15f * (float) Math.sin(now / 65.0 + x);
        fill.setColor(SHADOW);
        canvas.drawCircle(x + 7, y + 9, r, fill);
        fill.setColor(IRON);
        canvas.drawCircle(x, y, r, fill);
        stroke.setColor(IRON_LIGHT);
        stroke.setStrokeWidth(3f);
        canvas.drawCircle(x, y, r - 3, stroke);
        fill.setColor(WOOD_LINE);
        canvas.drawCircle(x, y, r * 0.72f, fill);
        drawHalo(canvas, x, y - 6, 1.7f * flick);
        fill.setColor(flame);
        canvas.drawCircle(x, y - 4, r * 0.55f * flick, fill);
        canvas.drawCircle(x - 4, y - 16, r * 0.35f * flick, fill);
        fill.setColor(flameCore);
        canvas.drawCircle(x, y - 3, r * 0.28f, fill);
    }

    /** A heap of old straw, the strands lying every which way. */
    void drawHay(Canvas canvas, float x, float y, float size, float angle) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(angle);
        fill.setColor(STRAW_DARK);
        rect.set(-size, -size * 0.6f, size, size * 0.6f);
        canvas.drawOval(rect, fill);
        stroke.setColor(STRAW);
        stroke.setAlpha(210);
        stroke.setStrokeWidth(2.5f);
        for (int i = 0; i < HAY_STRANDS.length; i += 3) {
            float sx = HAY_STRANDS[i] * size, sy = HAY_STRANDS[i + 1] * size * 0.5f;
            double a = Math.toRadians(HAY_STRANDS[i + 2]);
            float hx = (float) Math.cos(a) * size * 0.3f, hy = (float) Math.sin(a) * size * 0.3f;
            canvas.drawLine(sx - hx, sy - hy, sx + hx, sy + hy, stroke);
        }
        canvas.restore();
    }

    // Straw strands {dx, dy, angle in degrees}, dx and dy in units of the heap's size
    private static final float[] HAY_STRANDS = {-0.6f, 0.1f, 20, -0.3f, -0.5f, 80, 0f, 0.3f, -30, 0.4f, -0.2f, 50,
            0.6f, 0.4f, -70, -0.1f, -0.1f, 120, 0.2f, 0.6f, 10, -0.5f, 0.6f, 150, 0.7f, -0.4f, 100, 0.1f, -0.7f, -10};

    /** A wooden bucket of murky water. */
    void drawBucket(Canvas canvas, float x, float y) {
        fill.setColor(SHADOW);
        canvas.drawCircle(x + 5, y + 7, 18, fill);
        fill.setColor(WOOD_MID);
        canvas.drawCircle(x, y, 18, fill);
        fill.setColor(Color.rgb(60, 52, 30));
        canvas.drawCircle(x, y, 12, fill);
        stroke.setColor(IRON);
        stroke.setStrokeWidth(3f);
        canvas.drawCircle(x, y, 18, stroke);
        rect.set(x - 20, y - 26, x + 20, y + 8);
        canvas.drawArc(rect, 200, 140, false, stroke);
    }

    /**
     * A bookcase against a wall, the longer side along it: seen from the front on a north or south wall
     * (shelves of mismatched spines), from above on a side wall (the tops of the books).
     */
    void drawBookshelf(Canvas canvas, float x, float y, float hw, float hh) {
        drawShadow(canvas, x - hw, y - hh, x + hw, y + hh);
        fill.setColor(WOOD_DARK);
        canvas.drawRect(x - hw, y - hh, x + hw, y + hh, fill);
        boolean front = hw >= hh;
        float l = x - hw + 6, t = y - hh + 6, r = x + hw - 6, b = y + hh - 6;
        if (front) {
            float row = (b - t) / 2f;
            for (int k = 0; k < 2; k++) {
                float top = t + k * row, bottom = top + row - 3;
                int i = k * 3;
                for (float bx = l; bx < r; i++) {
                    float w = 8 + (i * 7 % 6);
                    fill.setColor(SPINES[i % SPINES.length]);
                    canvas.drawRect(bx, top + (i % 3 == 0 ? 4 : 1), Math.min(bx + w, r), bottom, fill);
                    bx += w + 1;
                }
            }
            stroke.setColor(WOOD_LINE);
            stroke.setStrokeWidth(3f);
            canvas.drawLine(x - hw, t + row - 1, x + hw, t + row - 1, stroke);
        } else {
            int i = 0;
            for (float by = t; by < b; i++) {
                float h = 8 + (i * 5 % 6);
                fill.setColor(SPINES[i % SPINES.length]);
                canvas.drawRect(l + (i % 2) * 3, by, r, Math.min(by + h, b), fill);
                by += h + 1;
            }
        }
        stroke.setColor(WOOD_LINE);
        stroke.setStrokeWidth(3f);
        canvas.drawRect(x - hw, y - hh, x + hw, y + hh, stroke);
    }

    /** A rolled scroll with its wooden ends. */
    void drawScroll(Canvas canvas, float x, float y, float angle) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(angle);
        fill.setColor(SHADOW);
        canvas.drawRect(-20, 2, 24, 12, fill);
        fill.setColor(PARCHMENT);
        canvas.drawRect(-20, -6, 20, 6, fill);
        fill.setColor(WOOD_MID);
        canvas.drawCircle(-22, 0, 7, fill);
        canvas.drawCircle(22, 0, 7, fill);
        stroke.setColor(CRIMSON_BASE);
        stroke.setStrokeWidth(3f);
        canvas.drawLine(-2, -6, -2, 6, stroke);
        canvas.restore();
    }

    /** A soft green patch of moss. */
    void drawMoss(Canvas canvas, float x, float y, float rx, float ry) {
        fill.setColor(MOSS_DARK);
        rect.set(x - rx, y - ry, x + rx, y + ry);
        canvas.drawOval(rect, fill);
        fill.setColor(MOSS);
        canvas.drawCircle(x - rx * 0.35f, y - ry * 0.1f, ry * 0.45f, fill);
        canvas.drawCircle(x + rx * 0.3f, y + ry * 0.15f, ry * 0.38f, fill);
        canvas.drawCircle(x + rx * 0.05f, y - ry * 0.35f, ry * 0.3f, fill);
    }

    /* ============================================================
       BIOME HAZARDS
       ============================================================ */

    /** A pool of black water with a slow ripple and a glint (Flooded Catacombs); wading through it slows. */
    void drawPuddle(Canvas canvas, float x, float y, float rx, float ry, long now) {
        fill.setColor(WATER_DEEP);
        rect.set(x - rx, y - ry, x + rx, y + ry);
        canvas.drawOval(rect, fill);
        fill.setColor(WATER);
        rect.set(x - rx * 0.82f, y - ry * 0.78f, x + rx * 0.76f, y + ry * 0.7f);
        canvas.drawOval(rect, fill);
        float t = Math.floorMod(now + (long) (x * 7 + y * 3), 2400L) / 2400f;
        stroke.setColor(WATER_SHINE);
        stroke.setAlpha((int) (110 * (1f - t)));
        stroke.setStrokeWidth(2f);
        rect.set(x - rx * 0.8f * t, y - ry * 0.8f * t, x + rx * 0.8f * t, y + ry * 0.8f * t);
        canvas.drawOval(rect, stroke);
        fill.setColor(WATER_SHINE);
        fill.setAlpha(80);
        rect.set(x - rx * 0.5f, y - ry * 0.5f, x - rx * 0.12f, y - ry * 0.3f);
        canvas.drawOval(rect, fill);
    }

    /** A lava vent (Infernal Forge): a grate over embers that rumbles bright, then spouts a column of fire. */
    void drawVent(Canvas canvas, Decor d, long now) {
        float x = d.x, y = d.y, r = d.b;
        long t = ventTime(d, now);
        float heat = t < VENT_WARN ? 0.25f
                : t < VENT_BURST ? 0.5f + 0.5f * (float) Math.abs(Math.sin(now / 70.0)) : 1f;
        fill.setColor(SCORCH);
        canvas.drawCircle(x, y, r, fill);
        fill.setColor(LAVA);
        fill.setAlpha((int) (180 * heat));
        canvas.drawCircle(x, y, r * 0.55f, fill);
        stroke.setColor(LAVA);
        stroke.setAlpha((int) (200 * heat));
        stroke.setStrokeWidth(3f);
        for (int i = 0; i < 5; i++) {
            double a = i * 1.26 + x * 0.003;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            canvas.drawLine(x + c * r * 0.6f, y + s * r * 0.6f, x + c * r * 0.97f, y + s * r * 0.9f, stroke);
        }
        stroke.setColor(IRON);
        stroke.setStrokeWidth(6f);
        for (int i = -1; i <= 1; i++)
            canvas.drawLine(x + i * r * 0.26f, y - r * 0.48f, x + i * r * 0.26f, y + r * 0.48f, stroke);
        canvas.drawCircle(x, y, r * 0.58f, stroke);
        if (t < VENT_WARN) return;
        if (t < VENT_BURST) {
            // The warning: a hot ring where the fire will reach
            stroke.setColor(LAVA_CORE);
            stroke.setAlpha((int) (170 * heat));
            stroke.setStrokeWidth(4f);
            canvas.drawCircle(x, y, r, stroke);
            return;
        }
        float burst = (t - VENT_BURST) / (float) (VENT_CYCLE - VENT_BURST);
        float flick = 0.85f + 0.15f * (float) Math.sin(now / 40.0);
        float fr = r * (1.1f - 0.4f * burst) * flick;
        fill.setColor(EMBER);
        fill.setAlpha((int) (200 * (1f - burst)));
        canvas.drawCircle(x, y, fr, fill);
        fill.setColor(LAVA);
        fill.setAlpha((int) (230 * (1f - burst * 0.5f)));
        canvas.drawCircle(x, y - fr * 0.25f, fr * 0.75f, fill);
        fill.setColor(LAVA_CORE);
        canvas.drawCircle(x, y - fr * 0.4f, fr * 0.45f, fill);
    }

    /** A rift in the floor (Hollow Void): a jagged crack breathing violet light. */
    void drawRift(Canvas canvas, float x, float y, float angle, float len, long now) {
        float pulse = 0.6f + 0.4f * (float) Math.sin(now / 600.0 + x * 0.01);
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(angle);
        shapePath.reset();
        for (int i = 0; i < RIFT_POINTS.length; i += 2) {
            float px = RIFT_POINTS[i] * len * 0.5f, py = RIFT_POINTS[i + 1] * len * 0.5f;
            if (i == 0) shapePath.moveTo(px, py);
            else shapePath.lineTo(px, py);
        }
        stroke.setColor(ARCANE_BRIGHT);
        stroke.setAlpha((int) (60 * pulse));
        stroke.setStrokeWidth(34f);
        canvas.drawPath(shapePath, stroke);
        stroke.setAlpha((int) (130 * pulse));
        stroke.setStrokeWidth(18f);
        canvas.drawPath(shapePath, stroke);
        stroke.setColor(CLOAK_DARK);
        stroke.setStrokeWidth(9f);
        canvas.drawPath(shapePath, stroke);
        stroke.setColor(ARCANE_GLOW);
        stroke.setAlpha((int) (230 * pulse));
        stroke.setStrokeWidth(3.5f);
        canvas.drawPath(shapePath, stroke);
        canvas.restore();
    }

    // Zigzag of a rift, {x, y} in units of half its length
    private static final float[] RIFT_POINTS = {-1f, 0f, -0.6f, -0.07f, -0.3f, 0.06f, 0f, -0.05f, 0.25f, 0.07f,
            0.6f, -0.04f, 1f, 0.02f};

    /**
     * Pixel art for the props that have it (art/sprites.mjs): pixelfx fire for torches, candles and braziers,
     * the game's own barrels, crates, pillars and bones. False leaves the prop to its shapes.
     */
    private boolean drawSprite(Canvas canvas, Decor d, long now) {
        Sprites.Anim a;
        switch (d.kind) {
            case TORCH: {
                // Mounted on the wall it was placed by: on the brick face of a north wall, or leaning out of a
                // side wall from a bar on the wall line (mirrored for east walls). The flame always rises.
                boolean side = d.b == Room.WEST || d.b == Room.EAST;
                if ((a = Sprites.get(side ? torchSide : torchNorth)) == null) return false;
                long at = now + (long) (d.a * 233);
                if (!side) Sprites.draw(canvas, a, a.at(at), d.x, d.y + TORCH_DROP, false, 0, 255);
                else if (d.b == Room.WEST) Sprites.draw(canvas, a, a.at(at), d.x - TORCH_INSET, d.y, false, 0, 255);
                else Sprites.draw(canvas, a, a.at(at), d.x + TORCH_INSET, d.y, true, 0, 255);
                return true;
            }
            case CANDLES:
                // One baked cluster of three, at a real candle's size; clusters flicker out of step
                if ((a = Sprites.get(candle)) == null) return false;
                Sprites.draw(canvas, a, a.at(now + (long) (d.x * 3 + d.y)), d.x, d.y, ((int) d.x & 64) != 0, 0, 255);
                return true;
            case BRAZIER:
                if ((a = Sprites.get(brazier)) == null) return false;
                Sprites.draw(canvas, a, a.at(now + (long) d.x), d.x, d.y + d.a * 0.6f, false, 0, 255);
                return true;
            case BARREL:
                return still(canvas, d, d.a >= 28 ? "prop.barrel.l" : "prop.barrel.s", false);
            case CRATE:
                return still(canvas, d, d.a >= 30 ? "prop.crate.l" : "prop.crate.s", false);
            case PILLAR:
                return still(canvas, d, d.a >= 34 ? "prop.pillar.l" : "prop.pillar.m", false);
            case BONES:
                return still(canvas, d, d.a < 180 ? "prop.bones.0" : "prop.bones.1", ((int) d.a & 1) == 1);
            case RUG: {
                // A woven rug baked at each size the rooms lay one (art/sprites.mjs prop.rug.*)
                if ((a = Sprites.get("prop.rug." + (int) d.a + "x" + (int) d.b)) == null) return false;
                Sprites.draw(canvas, a, 0, d.x, d.y, false, 0, 255);
                return true;
            }
            default:
                return false;
        }
    }

    /** A one-frame prop standing on a soft contact shadow. */
    private boolean still(Canvas canvas, Decor d, String name, boolean flip) {
        Sprites.Anim a = Sprites.get(name);
        if (a == null) return false;
        if (d.kind != BONES) {
            fill.setColor(SHADOW);
            canvas.drawOval(d.x - d.a, d.y + d.a * 0.2f, d.x + d.a * 1.1f, d.y + d.a * 0.75f, fill);
        }
        Sprites.draw(canvas, a, 0, d.x, d.y, flip, 0, 255);
        return true;
    }

    void drawDecor(Canvas canvas, Decor d, long now) {
        if (d.broken) {
            drawSplinters(canvas, d.x, d.y, d.a);
            return;
        }
        if (drawSprite(canvas, d, now)) return;
        switch (d.kind) {
            case TORCH:   drawTorch(canvas, d.x, d.y, now, (int) d.a); break;
            case BARREL:  drawBarrel(canvas, d.x, d.y, d.a); break;
            case CRATE:   drawCrate(canvas, d.x - d.a, d.y - d.a, d.x + d.a, d.y + d.a); break;
            case PILLAR:  drawPillar(canvas, d.x, d.y, d.a); break;
            case BONES:   drawBones(canvas, d.x, d.y, d.a); break;
            case RUBBLE:  drawRubble(canvas, d.x, d.y, d.a); break;
            case COBWEB:  drawCobweb(canvas, d.x, d.y, d.a, d.b); break;
            case BANNER:  drawBanner(canvas, d.x, d.y, now + (long) d.a); break;
            case RUG:     drawRug(canvas, d.x - d.a, d.y - d.b, d.x + d.a, d.y + d.b); break;
            case SIGIL:   drawSigil(canvas, d.x, d.y, d.a, now); break;
            case SHRINE:  drawShrine(canvas, d.x, d.y, now, d.used); break;
            case GAMBLER: drawGambler(canvas, d.x, d.y, now, d.used); break;
            case GONG:     drawGong(canvas, d.x, d.y, now, d.used); break;
            case PRISONER: drawPrisoner(canvas, d.x, d.y, (int) d.a, now, d.used); break;
            case LECTERN:  drawLectern(canvas, d.x, d.y, now, d.used); break;
            case FOUNTAIN: drawFountain(canvas, d.x, d.y, d.a, now, d.used); break;
            case BLOOD_POOL:  drawBloodPool(canvas, d.x, d.y, d.a, d.b); break;
            case CARD_TABLE:  drawCardTable(canvas, d.x, d.y, d.a, d.b, now); break;
            case COIN_PILE:   drawCoinPile(canvas, d.x, d.y, d.a); break;
            case COFFIN:      drawCoffin(canvas, d.x, d.y, d.a, d.b); break;
            case RUNESTONE:   drawRuneStone(canvas, d.x, d.y, d.a, now); break;
            case WEAPON_RACK: drawWeaponRack(canvas, d.x, d.y, d.a); break;
            case BRAZIER:     drawBrazier(canvas, d.x, d.y, d.a, now); break;
            case HAY:         drawHay(canvas, d.x, d.y, d.a, d.b); break;
            case CHAINS:      drawChains(canvas, d.x, d.y, (int) d.a); break;
            case BUCKET:      drawBucket(canvas, d.x, d.y); break;
            case BOOKSHELF:   drawBookshelf(canvas, d.x, d.y, d.a, d.b); break;
            case SCROLL:      drawScroll(canvas, d.x, d.y, d.a); break;
            case MOSS_PATCH:  drawMoss(canvas, d.x, d.y, d.a, d.b); break;
            case CURSE_SIGIL: drawSigil(canvas, d.x, d.y, d.a, now, ARCANE_BRIGHT, CURSE); break;
            case PUDDLE:      drawPuddle(canvas, d.x, d.y, d.a, d.b, now); break;
            case VENT:        drawVent(canvas, d, now); break;
            case RIFT:        drawRift(canvas, d.x, d.y, d.a, d.b, now); break;
            case CANDLES:
                drawCandle(canvas, d.x - 12, d.y, now);
                drawCandle(canvas, d.x + 9, d.y - 7, now + 200);
                drawCandle(canvas, d.x + 2, d.y + 12, now + 400);
                break;
        }
    }

    /**
     * Places decorations in every room: torches beside the north door, banners in the exit and boss rooms,
     * pillars in big rooms, clutter in the corners and a few bones along the walls. Doors sit in the
     * middle of each wall, so everything keeps to the corners and outer thirds. Ordinary rooms also get
     * their biome's hazards, and event rooms are dressed for their event instead. Same layout, same decor.
     */
    static List<Decor> decorate(FloorLayout layout) {
        List<Decor> list = new ArrayList<>();
        for (Room r : layout.rooms) {
            Random rng = new Random(r.id * 7919L + (long) (r.x * 31 + r.y));
            if (r.event != null) {
                decorateEvent(r, list, rng);
                continue;
            }
            if (r.type == Room.Type.JUNCTION) continue; // a corridor's bare corner: nothing to stand in the way
            float l = r.x, t = r.y, rt = r.x + r.width, bt = r.y + r.height;
            boolean grand = r.type == Room.Type.LARGE || r.type == Room.Type.BOSS || r.type == Room.Type.EXIT;
            boolean roomy = r.width >= 800 && r.height >= 800; // hallways only get single props
            boolean plain = r.type == Room.Type.SMALL || r.type == Room.Type.MEDIUM
                    || r.type == Room.Type.LARGE || r.type == Room.Type.HALLWAY;

            // Floor pieces first, so everything else sits on top
            if (plain && r != layout.treasureRoom && r != layout.vaultRoom) biomeHazards(layout.biome, r, list, rng);
            if (r.type == Room.Type.ENTRY) list.add(new Decor(RUG, r.centerX(), r.centerY(), 130, 95));
            if (r == layout.treasureRoom) list.add(new Decor(RUG, r.centerX(), r.centerY(), 110, 80));
            if (r.type == Room.Type.BOSS)
                list.add(new Decor(SIGIL, r.centerX(), r.centerY(), Math.min(r.width, r.height) * 0.28f, 0));

            list.add(new Decor(TORCH, l + r.width * 0.22f, t + 34, rng.nextInt(9), Room.NORTH));
            list.add(new Decor(TORCH, rt - r.width * 0.22f, t + 34, rng.nextInt(9), Room.NORTH));
            if (r.height >= 1200) {
                list.add(new Decor(TORCH, l + 24, t + r.height * 0.25f, rng.nextInt(9), Room.WEST));
                list.add(new Decor(TORCH, rt - 24, t + r.height * 0.25f, rng.nextInt(9), Room.EAST));
            }
            if (r.type == Room.Type.EXIT || r.type == Room.Type.BOSS) {
                list.add(new Decor(BANNER, l + r.width * 0.36f, t + 4, 0, 0));
                list.add(new Decor(BANNER, rt - r.width * 0.36f, t + 4, 700, 0));
            }

            for (int c = 0; c < 4; c++) {
                boolean west = c % 2 == 0, north = c < 2;
                float cx = west ? l : rt, cy = north ? t : bt;
                float sx = west ? 1 : -1, sy = north ? 1 : -1;
                if (north && rng.nextInt(3) > 0) list.add(new Decor(COBWEB, cx, cy, sx, sy));
                // Corner pillars stand on the floor in front of the north wall's face, not inside it
                if (grand) list.add(new Decor(PILLAR, cx + sx * 50, cy + sy * (north ? 110 : 50), 30, 0));
                float px = cx + sx * (grand ? 140 : 90), py = cy + sy * (grand ? 140 : 100);
                switch (rng.nextInt(6)) {
                    case 0:
                        list.add(new Decor(BARREL, px, py, 30, 0));
                        if (roomy && rng.nextBoolean()) list.add(new Decor(BARREL, px + sx * 62, py + sy * 18, 26, 0));
                        break;
                    case 1:
                        list.add(new Decor(CRATE, px, py, 34, 0));
                        if (roomy && rng.nextBoolean()) list.add(new Decor(CRATE, px + sx * 66, py - sy * 4, 24, 0));
                        break;
                    case 2:
                        list.add(new Decor(BONES, px, py, rng.nextFloat() * 360f, 0));
                        if (roomy) list.add(new Decor(RUBBLE, px + sx * 50, py + sy * 30, rng.nextFloat() * 360f, 0));
                        break;
                    case 3:
                        list.add(new Decor(CANDLES, px, py, 0, 0));
                        break;
                    case 4:
                        list.add(new Decor(RUBBLE, px, py, rng.nextFloat() * 360f, 0));
                        break;
                    default:
                        break;
                }
            }

            // A few bones and stones along the walls, clear of the door in the middle third
            int scatter = 1 + rng.nextInt(r.type == Room.Type.LARGE ? 3 : 2);
            for (int i = 0; i < scatter; i++) {
                int side = rng.nextInt(4);
                float u = rng.nextBoolean() ? 0.12f + rng.nextFloat() * 0.2f : 0.68f + rng.nextFloat() * 0.2f;
                float inset = 50 + rng.nextFloat() * 60;
                float px = side == Room.WEST ? l + inset : side == Room.EAST ? rt - inset : l + r.width * u;
                float py = side == Room.NORTH ? t + inset + 40 : side == Room.SOUTH ? bt - inset : t + r.height * u;
                list.add(new Decor(rng.nextInt(3) == 0 ? RUBBLE : BONES, px, py, rng.nextFloat() * 360f, 0));
            }
        }
        return list;
    }

    /** The biome's hazards in an ordinary room: pools of black water, lava vents or violet rifts. */
    private static void biomeHazards(Biome biome, Room r, List<Decor> list, Random rng) {
        boolean hallway = r.type == Room.Type.HALLWAY;
        switch (biome) {
            case CATACOMBS: {
                int pools = hallway ? 1 : 1 + rng.nextInt(r.type == Room.Type.LARGE ? 3 : 2);
                for (int i = 0; i < pools; i++) {
                    float rx = Math.min(80 + rng.nextFloat() * 90, r.width * 0.5f - 60);
                    float ry = Math.min(rx * (0.55f + rng.nextFloat() * 0.2f), r.height * 0.5f - 60);
                    float[] p = inside(r, rx + 40, ry + 40, rng);
                    list.add(new Decor(PUDDLE, p[0], p[1], rx, ry));
                }
                break;
            }
            case FORGE: {
                if (hallway || (r.type == Room.Type.SMALL && rng.nextBoolean())) break;
                int vents = r.type == Room.Type.LARGE ? 2 : 1;
                for (int i = 0; i < vents; i++) {
                    float[] p = inside(r, 220, 220, rng);
                    list.add(new Decor(VENT, p[0], p[1], rng.nextInt((int) VENT_CYCLE), 80));
                }
                break;
            }
            case VOID: {
                int rifts = 1 + rng.nextInt(2);
                for (int i = 0; i < rifts; i++) {
                    float[] p = inside(r, 140, 140, rng);
                    list.add(new Decor(RIFT, p[0], p[1], rng.nextFloat() * 360f, 120 + rng.nextFloat() * 100));
                }
                break;
            }
            default:
                break;
        }
    }

    /**
     * Dresses an event room around its centrepiece. Each is a dead end, so its one doorway is kept clear:
     * place() skips anything that would stand in front of it. Lighting comes from the props themselves.
     */
    private static void decorateEvent(Room r, List<Decor> list, Random rng) {
        float l = r.x, t = r.y, rt = r.x + r.width, bt = r.y + r.height;
        float cx = r.centerX(), cy = r.centerY(), w = r.width, h = r.height;
        int door = Room.SOUTH;
        for (int s = 0; s < 4; s++) if (r.sideUsed[s]) door = s;
        int back = (door + 2) % 4; // the wall across from the doorway

        switch (r.event) {
            case SHRINE: {
                // The floor runs with blood round the standing stone; candles burn in a ring
                list.add(new Decor(BLOOD_POOL, cx, cy + 20, 190, 150));
                for (int i = 0; i < 9; i++) {
                    float[] p = inside(r, 80, 80, rng);
                    list.add(new Decor(BLOOD_POOL, p[0], p[1], 30 + rng.nextFloat() * 45, 20 + rng.nextFloat() * 26));
                }
                list.add(new Decor(SHRINE, cx, cy - 30, 0, 0));
                for (int i = 0; i < 4; i++)
                    place(list, r, door, new Decor(CANDLES, cx + (i % 2 == 0 ? -125 : 125), cy + (i < 2 ? -140 : 135), 0, 0));
                place(list, r, door, new Decor(BANNER, l + w * 0.3f, t + 4, 0, 0));
                place(list, r, door, new Decor(BANNER, rt - w * 0.3f, t + 4, 700, 0));
                scatterBones(r, door, list, rng, 3);
                break;
            }
            case GAMBLER: {
                list.add(new Decor(RUG, cx, cy, 170, 115));
                list.add(new Decor(GAMBLER, cx, cy - 10, 0, 0));
                place(list, r, door, new Decor(CARD_TABLE, l + w * 0.22f, t + h * 0.25f, 62, 40));
                place(list, r, door, new Decor(CARD_TABLE, rt - w * 0.22f, bt - h * 0.25f, 62, 40));
                place(list, r, door, new Decor(COIN_PILE, cx + 70, cy + 70, 22, 0));
                place(list, r, door, new Decor(COIN_PILE, l + 110, t + 120, 26, 0));
                place(list, r, door, new Decor(COIN_PILE, rt - 130, bt - 230, 18, 0));
                place(list, r, door, new Decor(BARREL, l + 70, bt - 70, 30, 0));
                place(list, r, door, new Decor(BARREL, l + 132, bt - 58, 24, 0));
                place(list, r, door, new Decor(CRATE, rt - 80, t + 110, 34, 0));
                northTorches(r, door, list, rng);
                break;
            }
            case CURSED: {
                // Coffins line the walls round a purple circle; GameActivity sets the chest on it
                list.add(new Decor(CURSE_SIGIL, cx, cy, 190, 0));
                for (int i = 0; i < 4; i++) {
                    float x = i < 2 ? l + 64 : rt - 64, y = i % 2 == 0 ? t + h * 0.24f : bt - h * 0.24f;
                    place(list, r, door, new Decor(COFFIN, x, y, 38, 80));
                }
                for (int i = 0; i < 4; i++)
                    place(list, r, door, new Decor(RUNESTONE, i < 2 ? l + 60 : rt - 60, i % 2 == 0 ? t + 74 : bt - 64, 22, 0));
                place(list, r, door, new Decor(CANDLES, cx - 150, cy - 210, 0, 0));
                place(list, r, door, new Decor(CANDLES, cx + 150, cy - 210, 0, 0));
                corners(r, list, true);
                scatterBones(r, door, list, rng, 2);
                break;
            }
            case TRIAL: {
                // Old stains on the sand, a brazier in every corner, pillars for cover and the gong in the middle
                for (int i = 0; i < 5; i++) {
                    float[] p = inside(r, 200, 200, rng);
                    list.add(new Decor(BLOOD_POOL, p[0], p[1], 40 + rng.nextFloat() * 30, 26 + rng.nextFloat() * 18));
                }
                list.add(new Decor(GONG, cx, cy, 0, 0));
                for (int i = 0; i < 4; i++) {
                    float sx = i % 2 == 0 ? -1 : 1, sy = i < 2 ? -1 : 1;
                    place(list, r, door, new Decor(BRAZIER, cx + sx * (w * 0.5f - 200), cy + sy * (h * 0.5f - 200), 28, 0));
                    place(list, r, door, new Decor(PILLAR, cx + sx * 420, cy + sy * 380, 36, 0));
                }
                place(list, r, door, new Decor(WEAPON_RACK, l + w * 0.3f, t + 70, 80, 0));
                place(list, r, door, new Decor(WEAPON_RACK, rt - w * 0.3f, t + 70, 80, 0));
                place(list, r, door, new Decor(WEAPON_RACK, l + w * 0.3f, bt - 46, 80, 0));
                place(list, r, door, new Decor(WEAPON_RACK, rt - w * 0.3f, bt - 46, 80, 0));
                place(list, r, door, new Decor(BANNER, l + w * 0.42f, t + 4, 0, 0));
                place(list, r, door, new Decor(BANNER, rt - w * 0.42f, t + 4, 700, 0));
                corners(r, list, false);
                break;
            }
            case PRISONER: {
                // Chained to the wall across from the door, on a bed of old straw
                for (int i = 0; i < 3; i++) {
                    float[] p = inside(r, 110, 90, rng);
                    list.add(new Decor(HAY, p[0], p[1], 50 + rng.nextFloat() * 25, rng.nextFloat() * 360f));
                }
                float px = back == Room.WEST ? l + 150 : back == Room.EAST ? rt - 150 : cx;
                float py = back == Room.NORTH ? t + 150 : back == Room.SOUTH ? bt - 150 : cy;
                list.add(new Decor(PRISONER, px, py, back, 0));
                // Empty shackles on the side walls, toward the back of the cell
                float along = back == Room.NORTH || back == Room.WEST ? 0.3f : 0.7f;
                for (int side : new int[]{(back + 1) % 4, (back + 3) % 4}) {
                    // From above only a south wall's top shows, so its shackles hang on the north wall instead
                    int wall = side == Room.SOUTH ? Room.NORTH : side;
                    float at = side == Room.SOUTH ? (along < 0.5f ? along + 0.3f : along - 0.3f) : along;
                    float[] p = wallPoint(r, wall, at);
                    place(list, r, door, new Decor(CHAINS, p[0], p[1], wall, 0));
                }
                float bx = back == Room.EAST || back == Room.SOUTH ? rt - 70 : l + 70;
                float by = back == Room.NORTH || back == Room.EAST ? t + 80 : bt - 70;
                place(list, r, door, new Decor(BUCKET, bx, by, 0, 0));
                place(list, r, door, new Decor(TORCH, l + w * 0.22f, t + 34, rng.nextInt(9), Room.NORTH));
                corners(r, list, true);
                scatterBones(r, door, list, rng, 2);
                break;
            }
            case LIBRARY: {
                list.add(new Decor(RUG, cx, cy + 10, 150, 105));
                for (int i = 0; i < 4; i++) {
                    float[] p = inside(r, 170, 170, rng);
                    if (Math.abs(p[0] - cx) < 170 && Math.abs(p[1] - cy) < 130) continue; // off the rug
                    list.add(new Decor(SCROLL, p[0], p[1], rng.nextFloat() * 360f, 0));
                }
                list.add(new Decor(LECTERN, cx, cy, 0, 0));
                // Shelves along every wall, clear of the middle third where a door can be
                for (int i = 0; i < 2; i++) {
                    float sx = i == 0 ? l + w * 0.22f : rt - w * 0.22f;
                    float sy = i == 0 ? t + h * 0.27f : bt - h * 0.27f;
                    place(list, r, door, new Decor(BOOKSHELF, sx, t + 84, 150, 30)); // stands at the foot of the wall face
                    place(list, r, door, new Decor(BOOKSHELF, sx, bt - 36, 150, 30));
                    place(list, r, door, new Decor(BOOKSHELF, l + 34, sy, 30, 120));
                    place(list, r, door, new Decor(BOOKSHELF, rt - 34, sy, 30, 120));
                }
                place(list, r, door, new Decor(CANDLES, cx - 120, cy - 80, 0, 0));
                place(list, r, door, new Decor(CANDLES, cx + 120, cy - 80, 0, 0));
                corners(r, list, false);
                break;
            }
            case FOUNTAIN: {
                // A mossy grotto with the spring in the middle
                for (int i = 0; i < 6; i++) {
                    float[] p = inside(r, 120, 120, rng);
                    float rx = 50 + rng.nextFloat() * 40;
                    list.add(new Decor(MOSS_PATCH, p[0], p[1], rx, rx * 0.6f));
                }
                list.add(new Decor(FOUNTAIN, cx, cy, 82, 0));
                for (int i = 0; i < 4; i++)
                    place(list, r, door, new Decor(PILLAR, i % 2 == 0 ? l + 110 : rt - 110, i < 2 ? t + 110 : bt - 110, 30, 0));
                northTorches(r, door, list, rng);
                break;
            }
        }
    }

    /** Adds a prop unless it would stand in the room's doorway. */
    private static void place(List<Decor> list, Room r, int door, Decor d) {
        if (!inDoorway(r, door, d.x, d.y)) list.add(d);
    }

    /** True for a point in front of the door, where props would block the way in. */
    private static boolean inDoorway(Room r, int door, float x, float y) {
        float reach = 260, half = 190;
        switch (door) {
            case Room.NORTH: return y < r.y + reach && Math.abs(x - r.centerX()) < half;
            case Room.SOUTH: return y > r.y + r.height - reach && Math.abs(x - r.centerX()) < half;
            case Room.WEST:  return x < r.x + reach && Math.abs(y - r.centerY()) < half;
            default:         return x > r.x + r.width - reach && Math.abs(y - r.centerY()) < half;
        }
    }

    /** A point on one wall, along 0..1 from its left (north and south walls) or top (side walls). */
    private static float[] wallPoint(Room r, int side, float along) {
        switch (side) {
            case Room.NORTH: return new float[]{r.x + r.width * along, r.y};
            case Room.SOUTH: return new float[]{r.x + r.width * along, r.y + r.height};
            case Room.WEST:  return new float[]{r.x, r.y + r.height * along};
            default:         return new float[]{r.x + r.width, r.y + r.height * along};
        }
    }

    /**
     * A random point in the room keeping mx and my clear of the walls (the centre if it is too small), and
     * clear of the north wall's brick face too, so nothing lying on the floor is drawn up the wall.
     */
    private static float[] inside(Room r, float mx, float my, Random rng) {
        float top = my + WALL_FACE / 2f;
        float w = r.width - 2 * mx, h = r.height - my - top;
        return new float[]{w > 0 ? r.x + mx + rng.nextFloat() * w : r.centerX(),
                h > 0 ? r.y + top + rng.nextFloat() * h : r.centerY()};
    }

    /** The two torches beside the north wall's middle, as in every other room. */
    private static void northTorches(Room r, int door, List<Decor> list, Random rng) {
        place(list, r, door, new Decor(TORCH, r.x + r.width * 0.22f, r.y + 34, rng.nextInt(9), Room.NORTH));
        place(list, r, door, new Decor(TORCH, r.x + r.width * 0.78f, r.y + 34, rng.nextInt(9), Room.NORTH));
    }

    /** Cobwebs in the north corners, and the south ones too in a room long abandoned. */
    private static void corners(Room r, List<Decor> list, boolean all) {
        float l = r.x, t = r.y, rt = r.x + r.width, bt = r.y + r.height;
        list.add(new Decor(COBWEB, l, t, 1, 1));
        list.add(new Decor(COBWEB, rt, t, -1, 1));
        if (!all) return;
        list.add(new Decor(COBWEB, l, bt, 1, -1));
        list.add(new Decor(COBWEB, rt, bt, -1, -1));
    }

    /** A few bones along the walls, in their outer thirds and clear of the doorway. */
    private static void scatterBones(Room r, int door, List<Decor> list, Random rng, int count) {
        for (int i = 0; i < count; i++) {
            int side = rng.nextInt(4);
            float u = rng.nextBoolean() ? 0.12f + rng.nextFloat() * 0.2f : 0.68f + rng.nextFloat() * 0.2f;
            float inset = 50 + rng.nextFloat() * 50;
            float px = side == Room.WEST ? r.x + inset : side == Room.EAST ? r.x + r.width - inset : r.x + r.width * u;
            float py = side == Room.NORTH ? r.y + inset + 40 : side == Room.SOUTH ? r.y + r.height - inset
                    : r.y + r.height * u;
            place(list, r, door, new Decor(BONES, px, py, rng.nextFloat() * 360f, 0));
        }
    }
}
