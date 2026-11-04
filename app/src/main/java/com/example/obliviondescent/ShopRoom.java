package com.example.obliviondescent;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Random;

/**
 * The shop room: a merchant's den in a fixed square of world space, far from the dungeon.
 * Draws the floor, walls, props and the shopkeeper, and keeps the player out of walls and solid props.
 *
 * Layout (top-down): counter and shopkeeper at the top centre, exit portal on the left,
 * weapon rack and barrels on the right, the player arrives at the bottom centre.
 */
public class ShopRoom {

    /* ============================================================
       ROOM DIMENSIONS — fixed world coordinates
       Far from the main game area (which starts at 1000,1000)
       ============================================================ */
    public static final float ROOM_LEFT   = 8000f;
    public static final float ROOM_TOP    = 8000f;
    public static final float ROOM_SIZE   = 1000f;
    public static final float ROOM_RIGHT  = ROOM_LEFT + ROOM_SIZE;
    public static final float ROOM_BOTTOM = ROOM_TOP  + ROOM_SIZE;

    private static final float WALL = 40f;       // side and bottom wall thickness
    private static final float WALL_FACE = 100f; // back wall including its visible front face

    /* ============================================================
       KEY POSITIONS
       ============================================================ */
    public static final float SPAWN_X = ROOM_LEFT + 500f;
    public static final float SPAWN_Y = ROOM_BOTTOM - 140f;

    // Front edge of the counter: walking up to it opens the shop
    public static final float SHOP_X = ROOM_LEFT + 500f;
    public static final float SHOP_Y = ROOM_TOP + 300f;

    public static final float EXIT_PORTAL_X = ROOM_LEFT + 220f;
    public static final float EXIT_PORTAL_Y = ROOM_TOP + 500f;

    // How close the player must be to the counter's front edge to trade
    public static final float SHOP_INTERACT_RADIUS = 100f;

    // Exit portal radius — same as main game portal
    public static final float EXIT_PORTAL_RADIUS = 60f;

    // Solid props: boxes {left, top, right, bottom} and circles {x, y, radius}
    private static final float[] COUNTER = {ROOM_LEFT + 350, ROOM_TOP + 230, ROOM_LEFT + 650, ROOM_TOP + 300};
    private static final float[] SHELF_L = {ROOM_LEFT + 190, ROOM_TOP + 100, ROOM_LEFT + 330, ROOM_TOP + 150};
    private static final float[] SHELF_R = {ROOM_LEFT + 670, ROOM_TOP + 100, ROOM_LEFT + 810, ROOM_TOP + 150};
    private static final float[] RACK    = {ROOM_RIGHT - 110, ROOM_TOP + 360, ROOM_RIGHT - WALL, ROOM_TOP + 540};
    private static final float[] CRATE_A = {ROOM_LEFT + 60, ROOM_BOTTOM - 170, ROOM_LEFT + 160, ROOM_BOTTOM - 70};
    private static final float[] CRATE_B = {ROOM_LEFT + 170, ROOM_BOTTOM - 130, ROOM_LEFT + 240, ROOM_BOTTOM - 60};
    private static final float[] CRATE_C = {ROOM_RIGHT - 160, ROOM_BOTTOM - 160, ROOM_RIGHT - 60, ROOM_BOTTOM - 70};
    // Invisible: keeps the player out from behind the counter
    private static final float[] KEEPER  = {ROOM_LEFT + 330, ROOM_TOP + WALL_FACE, ROOM_LEFT + 670, ROOM_TOP + 230};
    private static final float[][] SOLID_BOXES = {COUNTER, KEEPER, SHELF_L, SHELF_R, RACK, CRATE_A, CRATE_B, CRATE_C};

    private static final float[] PILLAR_L = {ROOM_LEFT + 75, ROOM_TOP + 135, 34};
    private static final float[] PILLAR_R = {ROOM_RIGHT - 75, ROOM_TOP + 135, 34};
    private static final float[] CAULDRON = {ROOM_LEFT + 285, ROOM_TOP + 290, 34};
    private static final float[] BARREL_A = {ROOM_RIGHT - 120, ROOM_BOTTOM - 300, 36};
    private static final float[] BARREL_B = {ROOM_RIGHT - 190, ROOM_BOTTOM - 230, 36};
    private static final float[] STONE_A  = {ROOM_LEFT + 85, EXIT_PORTAL_Y - 60, 22};
    private static final float[] STONE_B  = {ROOM_LEFT + 85, EXIT_PORTAL_Y + 60, 22};
    private static final float[][] SOLID_CIRCLES = {PILLAR_L, PILLAR_R, CAULDRON, BARREL_A, BARREL_B, STONE_A, STONE_B};

    // Torches {x, y, light pool x, light pool y}: two on the back wall, one on each side wall
    private static final float[][] TORCHES = {
            {ROOM_LEFT + 370, ROOM_TOP + 62, ROOM_LEFT + 370, ROOM_TOP + 190},
            {ROOM_LEFT + 630, ROOM_TOP + 62, ROOM_LEFT + 630, ROOM_TOP + 190},
            {ROOM_LEFT + WALL + 6, ROOM_TOP + 700, ROOM_LEFT + 150, ROOM_TOP + 700},
            {ROOM_RIGHT - WALL - 6, ROOM_TOP + 620, ROOM_RIGHT - 150, ROOM_TOP + 620},
    };

    // Coin heap beside the counter: offsets {x, y} from its centre
    private static final float GOLD_X = ROOM_LEFT + 705, GOLD_Y = ROOM_TOP + 300;
    private static final float[] COINS = {-24, -8, 12, -18, -6, -20, 2, -14, 26, -4, -13, -5, 14, -6,
            0, 0, -20, 7, 20, 8, 6, 10, -8, 11};

    private static final String GREETING = "Welcome, wanderer...";

    // Palette (matches res/values/colors.xml)
    private static final int GROUT = Color.rgb(14, 11, 9);
    private static final int WALL_TOP = Color.rgb(54, 41, 31);
    private static final int WALL_FACE_COLOR = Color.rgb(30, 24, 19);
    private static final int MORTAR = Color.rgb(16, 12, 10);
    private static final int WALL_EDGE = Color.rgb(92, 69, 23);
    private static final int GOLD_DEEP = Color.rgb(122, 90, 31);
    private static final int GOLD_BASE = Color.rgb(184, 145, 30);
    private static final int GOLD_BRIGHT = Color.rgb(212, 175, 55);
    private static final int CRIMSON_DEEP = Color.rgb(74, 10, 10);
    private static final int CRIMSON_BASE = Color.rgb(139, 24, 24);
    private static final int ARCANE_BRIGHT = Color.rgb(139, 95, 191);
    private static final int ARCANE_GLOW = Color.rgb(181, 140, 224);
    private static final int BONE_DIM = Color.rgb(184, 172, 145);
    private static final int INK = Color.rgb(42, 33, 26);
    private static final int PARCHMENT = Color.rgb(222, 205, 168);
    private static final int WOOD_LIGHT = Color.rgb(120, 84, 48);
    private static final int WOOD_MID = Color.rgb(92, 62, 34);
    private static final int WOOD_DARK = Color.rgb(58, 38, 22);
    private static final int WOOD_LINE = Color.rgb(38, 24, 14);
    private static final int LEATHER = Color.rgb(70, 52, 30);
    private static final int IRON = Color.rgb(60, 60, 66);
    private static final int IRON_LIGHT = Color.rgb(120, 122, 130);
    private static final int STEEL = Color.rgb(180, 190, 200);
    private static final int STONE_DARK = Color.rgb(36, 30, 26);
    private static final int STONE_MID = Color.rgb(70, 60, 52);
    private static final int STONE_LIGHT = Color.rgb(104, 92, 80);
    private static final int CLOAK_DARK = Color.rgb(28, 20, 34);
    private static final int CLOAK_MID = Color.rgb(48, 34, 58);
    private static final int FLAME = Color.rgb(230, 110, 30);
    private static final int FLAME_CORE = Color.rgb(255, 214, 120);
    private static final int BREW = Color.rgb(70, 130, 60);
    private static final int BREW_LIGHT = Color.rgb(160, 230, 120);
    private static final int SHADOW = Color.argb(90, 0, 0, 0);
    private static final int[] POTION_COLORS = {Color.rgb(224, 72, 72), Color.rgb(181, 140, 224),
            Color.rgb(122, 184, 92), Color.rgb(86, 136, 199), Color.rgb(244, 215, 122)};

    /* ============================================================
       STATE
       ============================================================ */
    public boolean isActive = false;
    private float bubbleAlpha = 0f;
    private float bubbleWidth;

    /* ============================================================
       PAINTS AND SHAPES — built once, reused every frame
       ============================================================ */
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);      // warm pools on the floor
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);       // small glow round flames
    private final Paint portalGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wallShadowPaint = new Paint();
    private final Paint signPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cloakPath = new Path(), cloakFoldPath = new Path(), hoodPath = new Path();
    private final Path bannerPath = new Path(), emblemPath = new Path(), bubbleTail = new Path();
    private final RectF arcRect = new RectF();
    private final int[] tileColors = new int[100];

    public ShopRoom() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        lightPaint.setShader(new RadialGradient(0, 0, 260,
                new int[]{0x50FF9A40, 0x20FF7A20, 0x00000000}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        haloPaint.setShader(new RadialGradient(0, 0, 40,
                new int[]{0x99FFC060, 0x00FF8020}, null, Shader.TileMode.CLAMP));
        portalGlowPaint.setShader(new RadialGradient(0, 0, 130,
                new int[]{0xAA8B5FBF, 0x442B1840, 0x00000000}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        wallShadowPaint.setShader(new LinearGradient(0, ROOM_TOP + WALL_FACE, 0, ROOM_TOP + WALL_FACE + 50,
                0x99000000, 0x00000000, Shader.TileMode.CLAMP));

        signPaint.setTextAlign(Paint.Align.CENTER);
        signPaint.setColor(GOLD_BRIGHT);
        signPaint.setTextSize(28f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(ARCANE_GLOW);
        labelPaint.setTextSize(28f);
        labelPaint.setShadowLayer(6f, 0f, 0f, Color.BLACK);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setColor(BONE_DIM);
        hintPaint.setTextSize(24f);
        bubblePaint.setTextAlign(Paint.Align.CENTER);
        bubblePaint.setColor(INK);
        bubblePaint.setTextSize(24f);
        setFont(Typeface.SERIF);

        // Deterministic flagstone shades
        Random rnd = new Random(11);
        for (int i = 0; i < tileColors.length; i++) {
            int v = rnd.nextInt(13) - 6;
            tileColors[i] = Color.rgb(42 + v, 33 + v, 26 + v);
        }

        // Shopkeeper shapes around his chest at (0, 0)
        cloakPath.moveTo(-36, -12);
        cloakPath.quadTo(0, -24, 36, -12);
        cloakPath.lineTo(58, 58);
        cloakPath.quadTo(0, 66, -58, 58);
        cloakPath.close();
        cloakFoldPath.moveTo(-36, -12);
        cloakFoldPath.lineTo(-58, 58);
        cloakFoldPath.quadTo(-34, 62, -16, 61);
        cloakFoldPath.lineTo(-12, -15);
        cloakFoldPath.close();
        hoodPath.moveTo(-26, -8);
        hoodPath.cubicTo(-32, -48, -12, -70, 0, -74);
        hoodPath.cubicTo(12, -70, 32, -48, 26, -8);
        hoodPath.quadTo(0, 2, -26, -8);
        hoodPath.close();

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

        // Speech bubble tail, pointing left at the hood
        bubbleTail.moveTo(1, -8);
        bubbleTail.lineTo(-16, 2);
        bubbleTail.lineTo(1, 8);
        bubbleTail.close();
    }

    /** Uses the game's title font for the sign and labels. */
    public void setFont(Typeface font) {
        Typeface bold = Typeface.create(font, Typeface.BOLD);
        signPaint.setTypeface(bold);
        labelPaint.setTypeface(bold);
        hintPaint.setTypeface(font);
        bubblePaint.setTypeface(font);
        bubbleWidth = bubblePaint.measureText(GREETING) + 32f;
    }

    /* ============================================================
       DRAW — everything below works in world coordinates
       ============================================================ */
    /**
     * Draws the whole room, back to front.
     * @param canvas the canvas to draw on
     * @param camera game camera used to translate world position to screen position
     */
    public void draw(Canvas canvas, GameCamera camera) {
        if (!isActive) return;
        long now = System.currentTimeMillis();
        // The camera keeps the player near the middle of the screen
        float viewX = camera.getOffsetX() + canvas.getWidth() / 2f;
        float viewY = camera.getOffsetY() + canvas.getHeight() / 2f;

        canvas.save();
        canvas.translate(-camera.getOffsetX(), -camera.getOffsetY());
        drawFloor(canvas);
        drawLights(canvas, now);
        drawRug(canvas);
        drawPortal(canvas, now);
        drawWalls(canvas);
        drawBanner(canvas, ROOM_LEFT + 140, now);
        drawBanner(canvas, ROOM_RIGHT - 140, now + 700);
        drawTorches(canvas, now);
        drawSign(canvas, now);
        drawShelf(canvas, SHELF_L, 0);
        drawShelf(canvas, SHELF_R, 2);
        drawPillar(canvas, PILLAR_L);
        drawPillar(canvas, PILLAR_R);
        drawRuneStone(canvas, STONE_A, now);
        drawRuneStone(canvas, STONE_B, now + 500);
        drawShopkeeper(canvas, now);
        drawCounter(canvas, now);
        drawKeeperHands(canvas, now);
        drawCauldron(canvas, now);
        drawCoins(canvas, now);
        drawRack(canvas);
        drawCrate(canvas, CRATE_A);
        drawCrate(canvas, CRATE_B);
        drawCrate(canvas, CRATE_C);
        drawBarrel(canvas, BARREL_A);
        drawBarrel(canvas, BARREL_B);
        canvas.drawText("walk up to trade", SHOP_X, SHOP_Y + 34, hintPaint);
        drawGreeting(canvas, viewX, viewY);
        canvas.restore();
    }

    /** Flagstones with slightly varied shades over dark grout. */
    private void drawFloor(Canvas canvas) {
        fill.setColor(GROUT);
        canvas.drawRect(ROOM_LEFT, ROOM_TOP, ROOM_RIGHT, ROOM_BOTTOM, fill);
        stroke.setStrokeWidth(2f);
        stroke.setColor(STONE_DARK);
        for (int row = 0; row < 10; row++) {
            for (int col = 0; col < 10; col++) {
                float x = ROOM_LEFT + col * 100f, y = ROOM_TOP + row * 100f;
                fill.setColor(tileColors[row * 10 + col]);
                canvas.drawRect(x + 3, y + 3, x + 97, y + 97, fill);
                canvas.drawLine(x + 4, y + 5, x + 96, y + 5, stroke);
            }
        }
    }

    /** Warm flickering pools of torchlight on the floor. */
    private void drawLights(Canvas canvas, long now) {
        for (int i = 0; i < TORCHES.length; i++) {
            float flick = 0.92f + 0.08f * (float) (Math.sin(now / 90.0 + i * 1.7) * Math.sin(now / 37.0 + i));
            canvas.save();
            canvas.translate(TORCHES[i][2], TORCHES[i][3]);
            canvas.scale(flick, flick);
            canvas.drawCircle(0, 0, 260, lightPaint);
            canvas.restore();
        }
    }

    private void drawRug(Canvas canvas) {
        float l = ROOM_LEFT + 380, t = ROOM_TOP + 350, r = ROOM_LEFT + 620, b = ROOM_TOP + 570;
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
        // Diamond medallion
        float cx = (l + r) / 2f, cy = (t + b) / 2f;
        canvas.save();
        canvas.rotate(45f, cx, cy);
        fill.setColor(CRIMSON_DEEP);
        canvas.drawRect(cx - 38, cy - 38, cx + 38, cy + 38, fill);
        stroke.setColor(GOLD_BRIGHT);
        canvas.drawRect(cx - 38, cy - 38, cx + 38, cy + 38, stroke);
        canvas.drawRect(cx - 18, cy - 18, cx + 18, cy + 18, stroke);
        canvas.restore();
        // Fringe on the short ends
        stroke.setColor(GOLD_DEEP);
        for (float x = l + 6; x < r; x += 12) {
            canvas.drawLine(x, t, x, t - 10, stroke);
            canvas.drawLine(x, b, x, b + 10, stroke);
        }
    }

    /** Rune circle with counter-turning arcane arcs and a pulsing core. */
    private void drawPortal(Canvas canvas, long now) {
        float x = EXIT_PORTAL_X, y = EXIT_PORTAL_Y, r = EXIT_PORTAL_RADIUS;
        float pulse = 0.85f + 0.15f * (float) Math.sin(now / 400.0);
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(pulse, pulse);
        canvas.drawCircle(0, 0, 130, portalGlowPaint);
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
        stroke.setColor(ARCANE_GLOW);
        for (int i = 0; i < 8; i++) {
            canvas.rotate(45f, x, y);
            canvas.drawLine(x, y - r - 23, x, y - r - 13, stroke);
        }
        canvas.restore();

        float a = (now % 3000) / 3000f * 360f;
        stroke.setColor(ARCANE_BRIGHT);
        stroke.setStrokeWidth(5f);
        arcRect.set(x - 52, y - 52, x + 52, y + 52);
        canvas.drawArc(arcRect, a, 100, false, stroke);
        canvas.drawArc(arcRect, a + 180, 100, false, stroke);
        stroke.setColor(ARCANE_GLOW);
        stroke.setStrokeWidth(3f);
        arcRect.set(x - 34, y - 34, x + 34, y + 34);
        canvas.drawArc(arcRect, -a * 1.5f, 120, false, stroke);
        canvas.drawArc(arcRect, -a * 1.5f + 180, 120, false, stroke);

        fill.setColor(ARCANE_GLOW);
        fill.setAlpha((int) (200 * pulse));
        canvas.drawCircle(x, y, 18 * pulse, fill);
        canvas.drawText("NEXT FLOOR", x, y - r - 44, labelPaint);
    }

    /** Back wall with a lit top and a brick face; side and front walls seen from above. */
    private void drawWalls(Canvas canvas) {
        canvas.drawRect(ROOM_LEFT, ROOM_TOP + WALL_FACE, ROOM_RIGHT, ROOM_TOP + WALL_FACE + 50, wallShadowPaint);
        fill.setColor(WALL_TOP);
        canvas.drawRect(ROOM_LEFT, ROOM_TOP, ROOM_RIGHT, ROOM_TOP + WALL, fill);
        fill.setColor(WALL_FACE_COLOR);
        canvas.drawRect(ROOM_LEFT, ROOM_TOP + WALL, ROOM_RIGHT, ROOM_TOP + WALL_FACE, fill);
        stroke.setColor(MORTAR);
        stroke.setStrokeWidth(2f);
        for (int row = 0; row < 3; row++) {
            float y = ROOM_TOP + WALL + row * 20f;
            canvas.drawLine(ROOM_LEFT, y, ROOM_RIGHT, y, stroke);
            for (float x = ROOM_LEFT + (row % 2) * 30f; x < ROOM_RIGHT; x += 60f)
                canvas.drawLine(x, y, x, y + 20f, stroke);
        }

        fill.setColor(WALL_TOP);
        canvas.drawRect(ROOM_LEFT, ROOM_TOP, ROOM_LEFT + WALL, ROOM_BOTTOM, fill);
        canvas.drawRect(ROOM_RIGHT - WALL, ROOM_TOP, ROOM_RIGHT, ROOM_BOTTOM, fill);
        canvas.drawRect(ROOM_LEFT, ROOM_BOTTOM - WALL, ROOM_RIGHT, ROOM_BOTTOM, fill);
        stroke.setColor(WALL_EDGE);
        stroke.setStrokeWidth(3f);
        canvas.drawLine(ROOM_LEFT + WALL, ROOM_TOP + WALL, ROOM_RIGHT - WALL, ROOM_TOP + WALL, stroke);
        canvas.drawLine(ROOM_LEFT + WALL, ROOM_TOP + WALL, ROOM_LEFT + WALL, ROOM_BOTTOM - WALL, stroke);
        canvas.drawLine(ROOM_RIGHT - WALL, ROOM_TOP + WALL, ROOM_RIGHT - WALL, ROOM_BOTTOM - WALL, stroke);
        canvas.drawLine(ROOM_LEFT + WALL, ROOM_BOTTOM - WALL, ROOM_RIGHT - WALL, ROOM_BOTTOM - WALL, stroke);
    }

    private void drawBanner(Canvas canvas, float x, long now) {
        canvas.save();
        canvas.translate(x, ROOM_TOP + WALL + 2);
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

    private void drawTorches(Canvas canvas, long now) {
        for (int i = 0; i < TORCHES.length; i++) {
            float x = TORCHES[i][0], y = TORCHES[i][1];
            float flick = 0.85f + 0.15f * (float) Math.sin(now / 70.0 + i * 2.1);
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
    }

    /** Wooden "MERCHANT" sign hanging from the back wall, swaying a little. */
    private void drawSign(Canvas canvas, long now) {
        float cx = ROOM_LEFT + 500, top = ROOM_TOP + 50;
        canvas.save();
        canvas.rotate((float) Math.sin(now / 1100.0) * 1.2f, cx, ROOM_TOP + WALL);
        stroke.setColor(IRON);
        stroke.setStrokeWidth(2f);
        canvas.drawLine(cx - 70, ROOM_TOP + WALL, cx - 70, top, stroke);
        canvas.drawLine(cx + 70, ROOM_TOP + WALL, cx + 70, top, stroke);
        arcRect.set(cx - 95, top, cx + 95, top + 40);
        fill.setColor(WOOD_MID);
        canvas.drawRoundRect(arcRect, 6, 6, fill);
        stroke.setColor(GOLD_DEEP);
        stroke.setStrokeWidth(3f);
        canvas.drawRoundRect(arcRect, 6, 6, stroke);
        canvas.drawText("MERCHANT", cx, top + 30, signPaint);
        canvas.restore();
    }

    private void drawShadow(Canvas canvas, float[] b) {
        fill.setColor(SHADOW);
        canvas.drawRect(b[0] + 8, b[1] + 10, b[2] + 8, b[3] + 10, fill);
    }

    /** Shelf against the back wall with glowing potion bottles. */
    private void drawShelf(Canvas canvas, float[] b, int colorOffset) {
        drawShadow(canvas, b);
        fill.setColor(WOOD_MID);
        canvas.drawRect(b[0], b[1], b[2], b[3] - 18, fill);
        fill.setColor(WOOD_DARK);
        canvas.drawRect(b[0], b[3] - 18, b[2], b[3], fill);
        stroke.setColor(WOOD_LINE);
        stroke.setStrokeWidth(2f);
        canvas.drawLine(b[0], b[3] - 18, b[2], b[3] - 18, stroke);
        float step = (b[2] - b[0]) / 5f;
        for (int i = 0; i < 4; i++) {
            float x = b[0] + step * (i + 1), y = b[1] + 14;
            fill.setColor(POTION_COLORS[(i + colorOffset) % POTION_COLORS.length]);
            fill.setAlpha(60);
            canvas.drawCircle(x, y, 18, fill);
            fill.setAlpha(255);
            canvas.drawCircle(x, y, 10, fill);
            fill.setColor(STONE_LIGHT);
            canvas.drawRect(x - 3, y - 18, x + 3, y - 8, fill);
            fill.setColor(WOOD_LIGHT);
            canvas.drawRect(x - 4, y - 22, x + 4, y - 17, fill);
            fill.setColor(Color.argb(150, 255, 255, 255));
            canvas.drawCircle(x - 3, y - 3, 3, fill);
        }
    }

    /** Round stone pillar lit from the top-left. */
    private void drawPillar(Canvas canvas, float[] c) {
        float x = c[0], y = c[1], r = c[2];
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

    /** Small standing stone beside the portal with a glowing rune. */
    private void drawRuneStone(Canvas canvas, float[] c, long now) {
        drawPillar(canvas, c);
        fill.setColor(ARCANE_GLOW);
        fill.setAlpha((int) (140 + 100 * Math.sin(now / 500.0)));
        canvas.drawRect(c[0] - 2, c[1] - 9, c[0] + 2, c[1] + 9, fill);
        canvas.drawRect(c[0] - 7, c[1] - 2, c[0] + 7, c[1] + 2, fill);
    }

    /** Hooded merchant behind the counter; his face stays lost in the hood's shadow. */
    private void drawShopkeeper(Canvas canvas, long now) {
        float bob = (float) Math.sin(now / 650.0) * 2.5f;
        canvas.save();
        canvas.translate(ROOM_LEFT + 500, ROOM_TOP + 190 + bob);
        fill.setColor(CLOAK_DARK);
        canvas.drawPath(cloakPath, fill);
        fill.setColor(CLOAK_MID);
        canvas.drawPath(cloakFoldPath, fill);
        canvas.drawPath(hoodPath, fill);
        stroke.setColor(ARCANE_BRIGHT);
        stroke.setStrokeWidth(2f);
        stroke.setAlpha(110);
        canvas.drawPath(hoodPath, stroke);
        fill.setColor(Color.rgb(6, 4, 8));
        canvas.drawOval(-15, -52, 15, -14, fill);
        fill.setColor(GOLD_BASE);
        canvas.drawCircle(0, -6, 4, fill);
        canvas.restore();
    }

    /** One hand resting on the counter, the other holding a swinging lantern. */
    private void drawKeeperHands(Canvas canvas, long now) {
        float cx = ROOM_LEFT + 500, cy = ROOM_TOP + 190;
        fill.setColor(LEATHER);
        canvas.drawCircle(cx - 30, cy + 40, 7, fill);
        canvas.save();
        canvas.translate(cx + 38, cy + 34);
        canvas.rotate((float) Math.sin(now / 800.0) * 10f);
        canvas.drawCircle(0, 0, 7, fill);
        stroke.setColor(IRON);
        stroke.setStrokeWidth(2f);
        canvas.drawLine(0, 4, 0, 16, stroke);
        canvas.translate(0, 30);
        canvas.drawCircle(0, 0, 40, haloPaint);
        fill.setColor(IRON);
        canvas.drawRect(-9, -14, 9, 12, fill);
        fill.setColor(FLAME_CORE);
        canvas.drawRect(-5, -9, 5, 7, fill);
        canvas.restore();
    }

    /** Wooden counter with a lit top, a dark front face, a ledger, scales and candles. */
    private void drawCounter(Canvas canvas, long now) {
        float[] b = COUNTER;
        float face = b[3] - 28;
        drawShadow(canvas, b);
        fill.setColor(WOOD_LIGHT);
        canvas.drawRect(b[0], b[1], b[2], face, fill);
        stroke.setColor(WOOD_MID);
        stroke.setStrokeWidth(2f);
        for (float y = b[1] + 14; y < face; y += 14) canvas.drawLine(b[0], y, b[2], y, stroke);
        fill.setColor(WOOD_DARK);
        canvas.drawRect(b[0], face, b[2], b[3], fill);
        stroke.setColor(GOLD_BASE);
        stroke.setStrokeWidth(3f);
        canvas.drawLine(b[0], face, b[2], face, stroke);
        fill.setColor(GOLD_DEEP);
        for (float x = b[0] + 30; x < b[2]; x += 60) canvas.drawRect(x - 12, face + 6, x + 12, b[3] - 6, fill);

        // Open ledger
        fill.setColor(PARCHMENT);
        canvas.drawRect(b[0] + 50, b[1] + 8, b[0] + 81, b[1] + 34, fill);
        canvas.drawRect(b[0] + 85, b[1] + 8, b[0] + 116, b[1] + 34, fill);
        stroke.setColor(WOOD_LINE);
        canvas.drawLine(b[0] + 83, b[1] + 8, b[0] + 83, b[1] + 34, stroke);

        // Brass scales
        float sx = b[2] - 70, sy = b[1] + 10;
        stroke.setColor(GOLD_BASE);
        stroke.setStrokeWidth(3f);
        canvas.drawLine(sx, sy, sx, sy + 26, stroke);
        canvas.drawLine(sx - 22, sy + 4, sx + 22, sy + 4, stroke);
        fill.setColor(GOLD_BASE);
        canvas.drawOval(sx - 30, sy + 10, sx - 14, sy + 16, fill);
        canvas.drawOval(sx + 14, sy + 10, sx + 30, sy + 16, fill);

        drawCandle(canvas, b[0] + 20, b[1] + 24, now);
        drawCandle(canvas, b[2] - 20, b[1] + 24, now + 300);
    }

    private void drawCandle(Canvas canvas, float x, float y, long now) {
        float flick = 0.8f + 0.2f * (float) Math.sin(now / 60.0);
        fill.setColor(PARCHMENT);
        canvas.drawRect(x - 4, y - 14, x + 4, y + 4, fill);
        canvas.save();
        canvas.translate(x, y - 20);
        canvas.scale(flick * 0.6f, flick * 0.6f);
        canvas.drawCircle(0, 0, 40, haloPaint);
        canvas.restore();
        fill.setColor(FLAME_CORE);
        canvas.drawCircle(x, y - 19, 3.5f * flick, fill);
    }

    /** Iron cauldron with a bubbling green brew. */
    private void drawCauldron(Canvas canvas, long now) {
        float x = CAULDRON[0], y = CAULDRON[1], r = CAULDRON[2];
        fill.setColor(SHADOW);
        canvas.drawCircle(x + 6, y + 9, r, fill);
        fill.setColor(IRON);
        canvas.drawCircle(x, y, r, fill);
        fill.setColor(BREW);
        canvas.drawCircle(x, y, r - 9, fill);
        for (int i = 0; i < 3; i++) {
            float phase = ((now + i * 400) % 1200) / 1200f;
            fill.setColor(BREW_LIGHT);
            fill.setAlpha((int) (220 * (1f - phase)));
            canvas.drawCircle(x - 10 + i * 10, y + 6 - phase * 14, 3 + phase * 4, fill);
        }
        stroke.setColor(IRON_LIGHT);
        stroke.setStrokeWidth(4f);
        canvas.drawCircle(x, y, r - 5, stroke);
    }

    /** Heap of coins with an occasional glint. */
    private void drawCoins(Canvas canvas, long now) {
        for (int i = 0; i < COINS.length; i += 2) {
            float x = GOLD_X + COINS[i], y = GOLD_Y + COINS[i + 1];
            fill.setColor(GOLD_DEEP);
            canvas.drawOval(x - 9, y - 5, x + 9, y + 7, fill);
            fill.setColor(GOLD_BRIGHT);
            canvas.drawOval(x - 9, y - 7, x + 9, y + 5, fill);
        }
        long cycle = now % 1800;
        if (cycle < 300) {
            int k = (int) (now / 1800 % (COINS.length / 2)) * 2;
            float x = GOLD_X + COINS[k], y = GOLD_Y + COINS[k + 1] - 2;
            float s = 10f * (float) Math.sin(cycle / 300.0 * Math.PI);
            stroke.setColor(Color.WHITE);
            stroke.setStrokeWidth(2f);
            canvas.drawLine(x - s, y, x + s, y, stroke);
            canvas.drawLine(x, y - s, x, y + s, stroke);
        }
    }

    /** Weapon rack against the right wall: three swords and a round shield. */
    private void drawRack(Canvas canvas) {
        float[] b = RACK;
        drawShadow(canvas, b);
        fill.setColor(WOOD_DARK);
        canvas.drawRect(b[0], b[1], b[2], b[3], fill);
        fill.setColor(WOOD_MID);
        canvas.drawRect(b[0] + 6, b[1] + 6, b[2] - 6, b[3] - 6, fill);
        for (int i = 0; i < 3; i++) {
            float x = b[0] + 17 + i * 18;
            stroke.setColor(STEEL);
            stroke.setStrokeWidth(5f);
            canvas.drawLine(x, b[1] + 34, x, b[1] + 112, stroke);
            stroke.setColor(GOLD_BASE);
            stroke.setStrokeWidth(4f);
            canvas.drawLine(x - 7, b[1] + 32, x + 7, b[1] + 32, stroke);
            stroke.setColor(LEATHER);
            stroke.setStrokeWidth(5f);
            canvas.drawLine(x, b[1] + 14, x, b[1] + 30, stroke);
        }
        float sx = (b[0] + b[2]) / 2f, sy = b[3] - 30;
        fill.setColor(CRIMSON_BASE);
        canvas.drawCircle(sx, sy, 22, fill);
        stroke.setColor(GOLD_BASE);
        stroke.setStrokeWidth(4f);
        canvas.drawCircle(sx, sy, 22, stroke);
        fill.setColor(IRON_LIGHT);
        canvas.drawCircle(sx, sy, 6, fill);
    }

    /** Wooden crate: lit top with a brace, darker front face. */
    private void drawCrate(Canvas canvas, float[] b) {
        float face = b[3] - 22;
        drawShadow(canvas, b);
        fill.setColor(WOOD_LIGHT);
        canvas.drawRect(b[0], b[1], b[2], face, fill);
        fill.setColor(WOOD_DARK);
        canvas.drawRect(b[0], face, b[2], b[3], fill);
        stroke.setColor(WOOD_LINE);
        stroke.setStrokeWidth(3f);
        canvas.drawRect(b[0] + 4, b[1] + 4, b[2] - 4, face - 4, stroke);
        canvas.drawLine(b[0] + 4, b[1] + 4, b[2] - 4, face - 4, stroke);
        canvas.drawLine(b[0], face + 11, b[2], face + 11, stroke);
    }

    /** Barrel seen from slightly above: lid, staves and iron hoops. */
    private void drawBarrel(Canvas canvas, float[] c) {
        float x = c[0], y = c[1], r = c[2];
        fill.setColor(SHADOW);
        canvas.drawCircle(x + 7, y + 10, r, fill);
        fill.setColor(WOOD_MID);
        arcRect.set(x - r, y - r * 0.6f, x + r, y + r);
        canvas.drawRoundRect(arcRect, r * 0.5f, r * 0.5f, fill);
        stroke.setColor(IRON);
        stroke.setStrokeWidth(5f);
        canvas.drawLine(x - r + 3, y + r * 0.05f, x + r - 3, y + r * 0.05f, stroke);
        canvas.drawLine(x - r + 4, y + r * 0.6f, x + r - 4, y + r * 0.6f, stroke);
        fill.setColor(WOOD_LIGHT);
        arcRect.set(x - r, y - r, x + r, y - r * 0.2f);
        canvas.drawOval(arcRect, fill);
        stroke.setStrokeWidth(4f);
        canvas.drawOval(arcRect, stroke);
        stroke.setColor(WOOD_LINE);
        stroke.setStrokeWidth(2f);
        canvas.drawLine(x - r * 0.5f, y - r * 0.6f, x + r * 0.5f, y - r * 0.6f, stroke);
    }

    /** Parchment speech bubble that fades in while the player is near the counter. */
    private void drawGreeting(Canvas canvas, float viewX, float viewY) {
        float dx = viewX - SHOP_X, dy = viewY - SHOP_Y;
        float target = dx * dx + dy * dy < 380f * 380f ? 1f : 0f;
        bubbleAlpha += (target - bubbleAlpha) * 0.08f;
        if (bubbleAlpha < 0.02f) return;
        int alpha = (int) (bubbleAlpha * 255);
        float left = ROOM_LEFT + 548, cy = ROOM_TOP + 130;
        fill.setColor(PARCHMENT);
        fill.setAlpha(alpha);
        arcRect.set(left, cy - 20, left + bubbleWidth, cy + 20);
        canvas.drawRoundRect(arcRect, 12, 12, fill);
        canvas.save();
        canvas.translate(left, cy);
        canvas.drawPath(bubbleTail, fill);
        canvas.restore();
        bubblePaint.setAlpha(alpha);
        canvas.drawText(GREETING, left + bubbleWidth / 2f, cy + 8, bubblePaint);
    }

    /* ============================================================
       COLLISION — room walls, then solid props
       ============================================================ */
    /**
     * Keeps the player inside the room and out of solid props.
     * Called every logic frame while the player is inside the shop room.
     * @param player the player whose position is checked and corrected
     */
    public void enforceWallCollision(Player player) {
        float r = player.radius;
        player.x = Math.max(ROOM_LEFT + WALL + r, Math.min(ROOM_RIGHT - WALL - r, player.x));
        // The body may overlap the back wall's face a little, as if standing against it
        player.y = Math.max(ROOM_TOP + WALL_FACE + r * 0.5f, Math.min(ROOM_BOTTOM - WALL - r, player.y));

        for (float[] b : SOLID_BOXES) {
            float nx = Math.max(b[0], Math.min(b[2], player.x));
            float ny = Math.max(b[1], Math.min(b[3], player.y));
            float dx = player.x - nx, dy = player.y - ny;
            float d2 = dx * dx + dy * dy;
            if (d2 >= r * r) continue;
            if (d2 > 0.0001f) {
                float d = (float) Math.sqrt(d2);
                player.x = nx + dx / d * r;
                player.y = ny + dy / d * r;
            } else {
                // Centre inside the box: leave through the nearest side
                float left = player.x - b[0], right = b[2] - player.x;
                float up = player.y - b[1], down = b[3] - player.y;
                float min = Math.min(Math.min(left, right), Math.min(up, down));
                if (min == left) player.x = b[0] - r;
                else if (min == right) player.x = b[2] + r;
                else if (min == up) player.y = b[1] - r;
                else player.y = b[3] + r;
            }
        }

        for (float[] c : SOLID_CIRCLES) {
            float dx = player.x - c[0], dy = player.y - c[1];
            float min = r + c[2];
            float d2 = dx * dx + dy * dy;
            if (d2 >= min * min || d2 < 0.0001f) continue;
            float d = (float) Math.sqrt(d2);
            player.x = c[0] + dx / d * min;
            player.y = c[1] + dy / d * min;
        }
    }
}