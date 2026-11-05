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

    // Decoration kinds
    static final int TORCH = 0, BARREL = 1, CRATE = 2, PILLAR = 3, BONES = 4, RUBBLE = 5,
            COBWEB = 6, CANDLES = 7, BANNER = 8, RUG = 9, SIGIL = 10;

    /** One decoration in world space; a and b hold its size, angle or variant, depending on the kind. */
    static final class Decor {
        final int kind;
        final float x, y, a, b;
        volatile boolean broken; // a smashed crate or barrel

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
    private final Path bannerPath = new Path(), emblemPath = new Path();
    private final RectF rect = new RectF();
    private final float[] spot = new float[2];

    public Props() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        lightPaint.setShader(new RadialGradient(0, 0, 260,
                new int[]{0x50FF9A40, 0x20FF7A20, 0x00000000}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        haloPaint.setShader(new RadialGradient(0, 0, 40,
                new int[]{0x99FFC060, 0x00FF8020}, null, Shader.TileMode.CLAMP));
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
        fill.setColor(FLAME);
        canvas.drawCircle(0, 0, 9, fill);
        canvas.drawCircle(0, -8, 6, fill);
        fill.setColor(FLAME_CORE);
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
        fill.setColor(FLAME_CORE);
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
        int alpha = (int) (110 + 50 * Math.sin(now / 700.0));
        stroke.setColor(CRIMSON_BASE);
        stroke.setAlpha(alpha);
        stroke.setStrokeWidth(6f);
        canvas.drawCircle(x, y, r, stroke);
        stroke.setStrokeWidth(3f);
        canvas.drawCircle(x, y, r * 0.86f, stroke);
        canvas.save();
        canvas.rotate((now % 40000) / 40000f * 360f, x, y);
        stroke.setColor(CRIMSON_GLOW);
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
       COLLISION — barrels, crates and pillars are solid
       ============================================================ */

    private static boolean solid(Decor d) {
        return !d.broken && (d.kind == BARREL || d.kind == CRATE || d.kind == PILLAR);
    }

    /** Crates and barrels break when hit. */
    static boolean breakable(Decor d) {
        return !d.broken && (d.kind == BARREL || d.kind == CRATE);
    }

    /** Pushes a body of radius r out of every solid prop it overlaps; the result goes to out. */
    static void pushOut(List<Decor> decor, float x, float y, float r, float[] out) {
        for (Decor d : decor) {
            if (!solid(d)) continue;
            if (d.kind == CRATE) {
                float nx = Math.max(d.x - d.a, Math.min(d.x + d.a, x));
                float ny = Math.max(d.y - d.a, Math.min(d.y + d.a, y));
                float dx = x - nx, dy = y - ny, d2 = dx * dx + dy * dy;
                if (d2 >= r * r) continue;
                if (d2 > 0.0001f) {
                    float dist = (float) Math.sqrt(d2);
                    x = nx + dx / dist * r;
                    y = ny + dy / dist * r;
                } else {
                    // Centre inside the crate: leave through the nearest side
                    float left = x - (d.x - d.a), right = d.x + d.a - x;
                    float up = y - (d.y - d.a), down = d.y + d.a - y;
                    float min = Math.min(Math.min(left, right), Math.min(up, down));
                    if (min == left) x = d.x - d.a - r;
                    else if (min == right) x = d.x + d.a + r;
                    else if (min == up) y = d.y - d.a - r;
                    else y = d.y + d.a + r;
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
            boolean inside = d.kind == CRATE
                    ? Math.abs(x - d.x) < d.a && Math.abs(y - d.y) < d.a
                    : (x - d.x) * (x - d.x) + (y - d.y) * (y - d.y) < d.a * d.a;
            if (inside) return true;
        }
        return false;
    }

    void drawDecor(Canvas canvas, Decor d, long now) {
        if (d.broken) {
            drawSplinters(canvas, d.x, d.y, d.a);
            return;
        }
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
     * middle of each wall, so everything keeps to the corners and outer thirds. Same layout, same decor.
     */
    static List<Decor> decorate(FloorLayout layout) {
        List<Decor> list = new ArrayList<>();
        for (Room r : layout.rooms) {
            Random rng = new Random(r.id * 7919L + (long) (r.x * 31 + r.y));
            float l = r.x, t = r.y, rt = r.x + r.width, bt = r.y + r.height;
            boolean grand = r.type == Room.Type.LARGE || r.type == Room.Type.BOSS || r.type == Room.Type.EXIT;
            boolean roomy = r.width >= 800 && r.height >= 800; // hallways only get single props

            // Floor pieces first, so everything else sits on top
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
                if (grand) list.add(new Decor(PILLAR, cx + sx * 50, cy + sy * 50, 30, 0));
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
}
