package com.example.obliviondescent;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * ShopRoom
 *
 * A fixed square room in world space, far from the main game area.
 * Manages wall boundaries, shop counter position, and exit portal.
 *
 * Layout (top-down):
 *   TL = Exit portal (purple)
 *   TR = Shop counter
 *   BL = Player spawn
 *   BR = empty
 *
 * Wall collision is handled by GameActivity clamping the player
 * position to the inner bounds of the room every frame.
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

    // Wall thickness (visual only — collision uses the bounds above)
    private static final float WALL_THICKNESS = 30f;

    // Corner margin — how far from the corner each object sits
    private static final float CORNER_MARGIN = 120f;

    /* ============================================================
       KEY POSITIONS
       ============================================================ */
    // Player spawns bottom-left
    public static final float SPAWN_X = ROOM_LEFT + CORNER_MARGIN;
    public static final float SPAWN_Y = ROOM_BOTTOM - CORNER_MARGIN;

    // Shop counter top-right
    public static final float SHOP_X  = ROOM_RIGHT - CORNER_MARGIN;
    public static final float SHOP_Y  = ROOM_TOP   + CORNER_MARGIN;

    // Exit portal top-left
    public static final float EXIT_PORTAL_X = ROOM_LEFT  + CORNER_MARGIN;
    public static final float EXIT_PORTAL_Y = ROOM_TOP   + CORNER_MARGIN;

    // How close the player must be to interact with the shop counter
    public static final float SHOP_INTERACT_RADIUS = 100f;

    // Exit portal radius — same as main game portal
    public static final float EXIT_PORTAL_RADIUS = 60f;

    /* ============================================================
       STATE
       ============================================================ */
    public boolean isActive = false;

    /* ============================================================
       PAINTS
       ============================================================ */
    private Paint floorPaint;
    private Paint wallPaint;
    private Paint counterBodyPaint;
    private Paint counterTopPaint;
    private Paint counterLabelPaint;
    private Paint interactLabelPaint;
    private Paint exitPortalGlowPaint;
    private Paint exitPortalCorePaint;
    private Paint exitPortalLabelPaint;
    private Paint cornerAccentPaint;

    public ShopRoom() {
        // Floor — dark stone feel
        floorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        floorPaint.setColor(Color.rgb(30, 25, 35));
        floorPaint.setStyle(Paint.Style.FILL);

        // Walls — slightly lighter than floor
        wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wallPaint.setColor(Color.rgb(55, 45, 65));
        wallPaint.setStyle(Paint.Style.FILL);

        // Shop counter base
        counterBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        counterBodyPaint.setColor(Color.rgb(80, 50, 20));
        counterBodyPaint.setStyle(Paint.Style.FILL);

        // Shop counter top surface
        counterTopPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        counterTopPaint.setColor(Color.rgb(120, 80, 30));
        counterTopPaint.setStyle(Paint.Style.FILL);

        // "THE SHOP" label on counter
        counterLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        counterLabelPaint.setColor(Color.rgb(255, 215, 0));
        counterLabelPaint.setTextSize(32f);
        counterLabelPaint.setTextAlign(Paint.Align.CENTER);
        counterLabelPaint.setFakeBoldText(true);

        // "Press to interact" hint
        interactLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        interactLabelPaint.setColor(Color.rgb(200, 200, 200));
        interactLabelPaint.setTextSize(22f);
        interactLabelPaint.setTextAlign(Paint.Align.CENTER);

        // Exit portal — reuses purple style
        exitPortalGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        exitPortalGlowPaint.setColor(Color.rgb(100, 60, 255));
        exitPortalGlowPaint.setStyle(Paint.Style.FILL);

        exitPortalCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        exitPortalCorePaint.setColor(Color.rgb(160, 100, 255));
        exitPortalCorePaint.setStyle(Paint.Style.FILL);

        exitPortalLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        exitPortalLabelPaint.setColor(Color.WHITE);
        exitPortalLabelPaint.setTextSize(24f);
        exitPortalLabelPaint.setTextAlign(Paint.Align.CENTER);
        exitPortalLabelPaint.setFakeBoldText(true);

        // Corner accent — golden trim in each corner
        cornerAccentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cornerAccentPaint.setColor(Color.rgb(255, 215, 0));
        cornerAccentPaint.setStyle(Paint.Style.STROKE);
        cornerAccentPaint.setStrokeWidth(3f);
    }

    /* ============================================================
       DRAW
       ============================================================ */
    public void draw(Canvas canvas, GameCamera camera) {
        if (!isActive) return;

        float offX = camera.getOffsetX();
        float offY = camera.getOffsetY();

        // Convert room bounds to screen coords
        float sLeft   = ROOM_LEFT   - offX;
        float sTop    = ROOM_TOP    - offY;
        float sRight  = ROOM_RIGHT  - offX;
        float sBottom = ROOM_BOTTOM - offY;

        // --- Floor ---
        canvas.drawRect(sLeft, sTop, sRight, sBottom, floorPaint);

        // --- Walls ---
        // Top wall
        canvas.drawRect(sLeft, sTop,
                sRight, sTop + WALL_THICKNESS, wallPaint);
        // Bottom wall
        canvas.drawRect(sLeft, sBottom - WALL_THICKNESS,
                sRight, sBottom, wallPaint);
        // Left wall
        canvas.drawRect(sLeft, sTop,
                sLeft + WALL_THICKNESS, sBottom, wallPaint);
        // Right wall
        canvas.drawRect(sRight - WALL_THICKNESS, sTop,
                sRight, sBottom, wallPaint);

        // --- Corner accents ---
        float accentSize = 40f;
        // Top-left
        canvas.drawRect(sLeft, sTop,
                sLeft + accentSize, sTop + WALL_THICKNESS, cornerAccentPaint);
        canvas.drawRect(sLeft, sTop,
                sLeft + WALL_THICKNESS, sTop + accentSize, cornerAccentPaint);
        // Top-right
        canvas.drawRect(sRight - accentSize, sTop,
                sRight, sTop + WALL_THICKNESS, cornerAccentPaint);
        canvas.drawRect(sRight - WALL_THICKNESS, sTop,
                sRight, sTop + accentSize, cornerAccentPaint);
        // Bottom-left
        canvas.drawRect(sLeft, sBottom - WALL_THICKNESS,
                sLeft + accentSize, sBottom, cornerAccentPaint);
        canvas.drawRect(sLeft, sBottom - accentSize,
                sLeft + WALL_THICKNESS, sBottom, cornerAccentPaint);
        // Bottom-right
        canvas.drawRect(sRight - accentSize, sBottom - WALL_THICKNESS,
                sRight, sBottom, cornerAccentPaint);
        canvas.drawRect(sRight - WALL_THICKNESS, sBottom - accentSize,
                sRight, sBottom, cornerAccentPaint);

        // --- Shop counter (top-right corner) ---
        drawShopCounter(canvas, offX, offY);

        // --- Exit portal (top-left corner) ---
        drawExitPortal(canvas, offX, offY);
    }

    private void drawShopCounter(Canvas canvas, float offX, float offY) {
        float cx = SHOP_X - offX;
        float cy = SHOP_Y - offY;
        float w = 110f, h = 80f;

        // Counter body
        canvas.drawRoundRect(
                new RectF(cx - w/2f, cy - h/2f, cx + w/2f, cy + h/2f),
                12f, 12f, counterBodyPaint);

        // Counter top surface (slightly inset)
        canvas.drawRoundRect(
                new RectF(cx - w/2f + 6f, cy - h/2f, cx + w/2f - 6f, cy),
                8f, 8f, counterTopPaint);

        // "THE SHOP" label above counter
        canvas.drawText("THE SHOP", cx, cy - h/2f - 16f, counterLabelPaint);

        // "Walk close to browse" hint below
        canvas.drawText("[ walk close ]", cx, cy + h/2f + 30f, interactLabelPaint);
    }

    private void drawExitPortal(Canvas canvas, float offX, float offY) {
        float px = EXIT_PORTAL_X - offX;
        float py = EXIT_PORTAL_Y - offY;

        float pulse = (float)(Math.sin(System.currentTimeMillis() / 400.0) * 0.125 + 0.875);
        float coreRadius = EXIT_PORTAL_RADIUS * pulse;
        float glowRadius = EXIT_PORTAL_RADIUS * 1.8f * pulse;

        exitPortalGlowPaint.setAlpha((int)(90 * pulse));
        canvas.drawCircle(px, py, glowRadius, exitPortalGlowPaint);

        exitPortalCorePaint.setAlpha((int)(210 * pulse));
        canvas.drawCircle(px, py, coreRadius, exitPortalCorePaint);

        canvas.drawText("NEXT FLOOR", px, py - EXIT_PORTAL_RADIUS - 16f,
                exitPortalLabelPaint);
    }

    /* ============================================================
       WALL COLLISION
       Clamps player position to the inner bounds of the room.
       Called by GameActivity every logic frame when in shop room.
       ============================================================ */
    public void enforceWallCollision(Player player) {
        float inner = WALL_THICKNESS + player.radius;

        if (player.x < ROOM_LEFT  + inner) player.x = ROOM_LEFT  + inner;
        if (player.x > ROOM_RIGHT - inner) player.x = ROOM_RIGHT - inner;
        if (player.y < ROOM_TOP   + inner) player.y = ROOM_TOP   + inner;
        if (player.y > ROOM_BOTTOM- inner) player.y = ROOM_BOTTOM- inner;
    }
}