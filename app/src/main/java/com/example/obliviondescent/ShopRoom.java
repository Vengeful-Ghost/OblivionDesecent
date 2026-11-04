package com.example.obliviondescent;

import static com.example.obliviondescent.Props.*;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
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
    private final Paint wallShadowPaint = new Paint();
    private final Paint signPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cloakPath = new Path(), cloakFoldPath = new Path(), hoodPath = new Path();
    private final Path bubbleTail = new Path();
    private final RectF arcRect = new RectF();
    private final int[] tileColors = new int[100];
    private final Props props = new Props(); // portal, torches, pillars, crates and other shared props

    public ShopRoom() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        wallShadowPaint.setShader(new LinearGradient(0, ROOM_TOP + WALL_FACE, 0, ROOM_TOP + WALL_FACE + 50,
                0x99000000, 0x00000000, Shader.TileMode.CLAMP));

        signPaint.setTextAlign(Paint.Align.CENTER);
        signPaint.setColor(GOLD_BRIGHT);
        signPaint.setTextSize(28f);
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
        props.setFont(font);
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
        for (int i = 0; i < TORCHES.length; i++) props.drawLight(canvas, TORCHES[i][2], TORCHES[i][3], now, i);
        props.drawRug(canvas, ROOM_LEFT + 380, ROOM_TOP + 350, ROOM_LEFT + 620, ROOM_TOP + 570);
        props.drawPortal(canvas, EXIT_PORTAL_X, EXIT_PORTAL_Y, EXIT_PORTAL_RADIUS, now, false, "NEXT FLOOR");
        drawWalls(canvas);
        props.drawBanner(canvas, ROOM_LEFT + 140, ROOM_TOP + WALL + 2, now);
        props.drawBanner(canvas, ROOM_RIGHT - 140, ROOM_TOP + WALL + 2, now + 700);
        for (int i = 0; i < TORCHES.length; i++) props.drawTorch(canvas, TORCHES[i][0], TORCHES[i][1], now, i);
        drawSign(canvas, now);
        drawShelf(canvas, SHELF_L, 0);
        drawShelf(canvas, SHELF_R, 2);
        props.drawPillar(canvas, PILLAR_L[0], PILLAR_L[1], PILLAR_L[2]);
        props.drawPillar(canvas, PILLAR_R[0], PILLAR_R[1], PILLAR_R[2]);
        props.drawRuneStone(canvas, STONE_A[0], STONE_A[1], STONE_A[2], now);
        props.drawRuneStone(canvas, STONE_B[0], STONE_B[1], STONE_B[2], now + 500);
        drawShopkeeper(canvas, now);
        drawCounter(canvas, now);
        drawKeeperHands(canvas, now);
        drawCauldron(canvas, now);
        drawCoins(canvas, now);
        drawRack(canvas);
        props.drawCrate(canvas, CRATE_A[0], CRATE_A[1], CRATE_A[2], CRATE_A[3]);
        props.drawCrate(canvas, CRATE_B[0], CRATE_B[1], CRATE_B[2], CRATE_B[3]);
        props.drawCrate(canvas, CRATE_C[0], CRATE_C[1], CRATE_C[2], CRATE_C[3]);
        props.drawBarrel(canvas, BARREL_A[0], BARREL_A[1], BARREL_A[2]);
        props.drawBarrel(canvas, BARREL_B[0], BARREL_B[1], BARREL_B[2]);
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

    /** Shelf against the back wall with glowing potion bottles. */
    private void drawShelf(Canvas canvas, float[] b, int colorOffset) {
        props.drawShadow(canvas, b[0], b[1], b[2], b[3]);
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
        props.drawHalo(canvas, 0, 0, 1f);
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
        props.drawShadow(canvas, b[0], b[1], b[2], b[3]);
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

        props.drawCandle(canvas, b[0] + 20, b[1] + 24, now);
        props.drawCandle(canvas, b[2] - 20, b[1] + 24, now + 300);
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
        props.drawShadow(canvas, b[0], b[1], b[2], b[3]);
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