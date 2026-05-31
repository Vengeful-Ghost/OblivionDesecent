package com.example.obliviondescent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.List;

/**
 * View המציגה את עולם המשחק — השחקן, אויבים, קליעים, מטבעות, ארגזים,
 * פריסת הקומה, פורטלים, מיניאמפ ואנימציות.
 * מצוירת על ידי thread ייעודי דרך postInvalidate().
 */
public class GameDisplay extends View {

    private Player player;
    private List<Enemy> enemies;
    private GameCamera camera;

    private Paint floorPaint;

    private FloorManager floorManager; // Reference passed in from GameActivity

    // Portal drawing paints
    private Paint portalGlowPaint;  // Outer soft glow ring
    private Paint portalCorePaint;  // Solid inner circle
    private Paint portalLabelPaint; // "EXIT" text above it

    private List<Projectile> projectiles;

    private Paint arrowPaint;
    private Paint arrowGlowPaint;

    private List<GoldCoin> goldCoins;

    private List<TreasureChest> treasureChests;

    private Paint shopPortalGlowPaint;
    private Paint shopPortalCorePaint;
    private Paint shopPortalLabelPaint;
    private Paint shopArrowPaint;
    private Paint shopArrowGlowPaint;

    private ShopRoom shopRoom;
    private boolean inShopRoom = false;

    // Floor layout rendering
    private FloorLayout floorLayout;
    private Paint roomFloorPaint;
    private Paint entryFloorPaint;
    private Paint exitFloorPaint;
    private Paint bossFloorPaint;
    private Paint corridorFloorPaint;
    private Paint wallPaint;

    // Minimap
    private Paint minimapBgPaint;
    private Paint minimapBorderPaint;
    private Paint minimapRoomPaint;
    private Paint minimapEntryPaint;
    private Paint minimapExitPaint;
    private Paint minimapBossRoomPaint;
    private Paint minimapCorridorPaint;
    private Paint minimapPlayerPaint;
    private static final float MINIMAP_SIZE    = 160f;
    private static final float MINIMAP_PADDING = 20f;

    public GameDisplay(Context context, AttributeSet attrs) {
        super(context, attrs);
        floorPaint = new Paint();
        floorPaint.setColor(Color.DKGRAY);
        floorPaint.setStrokeWidth(2f);
        floorPaint.setAlpha(50); // Faint grid lines

        portalGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        portalGlowPaint.setColor(Color.rgb(100, 60, 255)); // Purple glow
        portalGlowPaint.setStyle(Paint.Style.FILL);

        portalCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        portalCorePaint.setColor(Color.rgb(160, 100, 255));
        portalCorePaint.setStyle(Paint.Style.FILL);

        portalLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        portalLabelPaint.setColor(Color.WHITE);
        portalLabelPaint.setTextSize(28f);
        portalLabelPaint.setTextAlign(Paint.Align.CENTER);
        portalLabelPaint.setFakeBoldText(true);

        arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setColor(Color.rgb(160, 100, 255));
        arrowPaint.setStyle(Paint.Style.FILL);

        arrowGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowGlowPaint.setColor(Color.rgb(160, 100, 255));
        arrowGlowPaint.setStyle(Paint.Style.FILL);
        arrowGlowPaint.setAlpha(60);

        shopPortalGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shopPortalGlowPaint.setColor(Color.rgb(255, 200, 0));
        shopPortalGlowPaint.setStyle(Paint.Style.FILL);

        shopPortalCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shopPortalCorePaint.setColor(Color.rgb(255, 230, 80));
        shopPortalCorePaint.setStyle(Paint.Style.FILL);

        shopPortalLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shopPortalLabelPaint.setColor(Color.WHITE);
        shopPortalLabelPaint.setTextSize(28f);
        shopPortalLabelPaint.setTextAlign(Paint.Align.CENTER);
        shopPortalLabelPaint.setFakeBoldText(true);

        shopArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shopArrowPaint.setColor(Color.rgb(255, 215, 0));
        shopArrowPaint.setStyle(Paint.Style.FILL);

        shopArrowGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shopArrowGlowPaint.setColor(Color.rgb(255, 215, 0));
        shopArrowGlowPaint.setStyle(Paint.Style.FILL);
        shopArrowGlowPaint.setAlpha(60);

        roomFloorPaint = new Paint();
        roomFloorPaint.setColor(Color.rgb(38, 38, 42));
        roomFloorPaint.setStyle(Paint.Style.FILL);

        entryFloorPaint = new Paint();
        entryFloorPaint.setColor(Color.rgb(30, 44, 30));
        entryFloorPaint.setStyle(Paint.Style.FILL);

        exitFloorPaint = new Paint();
        exitFloorPaint.setColor(Color.rgb(38, 28, 50));
        exitFloorPaint.setStyle(Paint.Style.FILL);

        bossFloorPaint = new Paint();
        bossFloorPaint.setColor(Color.rgb(50, 22, 22));
        bossFloorPaint.setStyle(Paint.Style.FILL);

        corridorFloorPaint = new Paint();
        corridorFloorPaint.setColor(Color.rgb(28, 28, 32));
        corridorFloorPaint.setStyle(Paint.Style.FILL);

        wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wallPaint.setColor(Color.rgb(100, 100, 110));
        wallPaint.setStyle(Paint.Style.STROKE);
        wallPaint.setStrokeWidth(10f);
        wallPaint.setStrokeCap(Paint.Cap.SQUARE);

        minimapBgPaint = new Paint();
        minimapBgPaint.setColor(Color.argb(170, 0, 0, 0));
        minimapBgPaint.setStyle(Paint.Style.FILL);

        minimapBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minimapBorderPaint.setColor(Color.rgb(110, 110, 125));
        minimapBorderPaint.setStyle(Paint.Style.STROKE);
        minimapBorderPaint.setStrokeWidth(2f);

        minimapRoomPaint = new Paint();
        minimapRoomPaint.setColor(Color.rgb(80, 80, 92));
        minimapRoomPaint.setStyle(Paint.Style.FILL);

        minimapEntryPaint = new Paint();
        minimapEntryPaint.setColor(Color.rgb(55, 110, 55));
        minimapEntryPaint.setStyle(Paint.Style.FILL);

        minimapExitPaint = new Paint();
        minimapExitPaint.setColor(Color.rgb(90, 55, 130));
        minimapExitPaint.setStyle(Paint.Style.FILL);

        minimapBossRoomPaint = new Paint();
        minimapBossRoomPaint.setColor(Color.rgb(130, 40, 40));
        minimapBossRoomPaint.setStyle(Paint.Style.FILL);

        minimapCorridorPaint = new Paint();
        minimapCorridorPaint.setColor(Color.rgb(58, 58, 65));
        minimapCorridorPaint.setStyle(Paint.Style.FILL);

        minimapPlayerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minimapPlayerPaint.setColor(Color.WHITE);
        minimapPlayerPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * מחברת אובייקטי לוגיקה לתצוגה להצגה בכל פריים ציור.
     * @param player השחקן לציור
     * @param enemies רשימת האויבים לציור
     * @param camera מצלמת המשחק
     * @param projectiles רשימת קליעים פעילים
     * @param goldCoins רשימת מטבעות זהב
     * @param treasureChests רשימת ארגזי אוצר
     * @param shopRoom אובייקט חדר החנות
     */
    // Connect logic objects to this view
    public void setGameData(Player player, List<Enemy> enemies, GameCamera camera,
                            List<Projectile> projectiles, List<GoldCoin> goldCoins,
                            List<TreasureChest> treasureChests, ShopRoom shopRoom) {
        this.player         = player;
        this.enemies        = enemies;
        this.camera         = camera;
        this.projectiles    = projectiles;
        this.goldCoins      = goldCoins;
        this.treasureChests = treasureChests;
        this.shopRoom       = shopRoom;
    }

    public void setInShopRoom(boolean inShopRoom) {
        this.inShopRoom = inShopRoom;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (player == null || camera == null) return;

        if (shopRoom != null && shopRoom.isActive) {
            shopRoom.draw(canvas, camera);
            if (inShopRoom) {
                Paint blackout = new Paint();
                blackout.setColor(Color.BLACK);
                blackout.setStyle(Paint.Style.FILL);

                float offX = camera.getOffsetX();
                float offY = camera.getOffsetY();

                float sLeft   = ShopRoom.ROOM_LEFT   - offX;
                float sTop    = ShopRoom.ROOM_TOP    - offY;
                float sRight  = ShopRoom.ROOM_RIGHT  - offX;
                float sBottom = ShopRoom.ROOM_BOTTOM - offY;

                int w = getWidth();
                int h = getHeight();

                canvas.drawRect(0, 0, w, sTop, blackout);
                canvas.drawRect(0, sBottom, w, h, blackout);
                canvas.drawRect(0, sTop, sLeft, sBottom, blackout);
                canvas.drawRect(sRight, sTop, w, sBottom, blackout);
            }
        } else if (floorLayout != null) {
            drawFloorLayout(canvas);
        } else {
            drawGrid(canvas);
        }

        // Draw gold coins
        if (goldCoins != null) {
            synchronized (goldCoins) {
                for (GoldCoin coin : goldCoins) {
                    coin.draw(canvas, camera);
                }
            }
        }

        if (treasureChests != null) {
            synchronized (treasureChests) {
                for (TreasureChest chest : treasureChests) {
                    chest.draw(canvas, camera);
                }
            }
        }

        // 2. Draw Enemies
        if (enemies != null) {
            synchronized (enemies) {
                for (Enemy e : enemies) {
                    e.draw(canvas, camera);
                }
            }
        }

        // Draw projectiles
        if (projectiles != null) {
            synchronized (projectiles) {
                for (Projectile p : projectiles) {
                    p.draw(canvas, camera);
                }
            }
        }

        if (!inShopRoom) {
            drawPortal(canvas);
            drawPortalArrow(canvas);
            drawShopPortal(canvas);
            drawShopPortalArrow(canvas);
        }
        // 3. Draw Player
        player.draw(canvas, camera);

        // 4. Minimap HUD overlay (always on top, hidden in shop)
        if (!inShopRoom) {
            drawMinimap(canvas);
        }
    }

    // Helper to visualize the "Infinite" world
    private void drawGrid(Canvas canvas) {
        // Calculate where the grid lines should be based on camera
        // This makes the floor look like it's moving
        float gridSize = 200f;
        float offsetX = camera.getOffsetX() % gridSize;
        float offsetY = camera.getOffsetY() % gridSize;

        for (int i = 0; i < getWidth() / gridSize + 2; i++) {
            float lineX = (i * gridSize) - offsetX;
            canvas.drawLine(lineX, 0, lineX, getHeight(), floorPaint);
        }
        for (int i = 0; i < getHeight() / gridSize + 2; i++) {
            float lineY = (i * gridSize) - offsetY;
            canvas.drawLine(0, lineY, getWidth(), lineY, floorPaint);
        }
    }

    /**
     * מגדירה את פריסת הקומה לציור.
     * @param layout פריסת הקומה הנוכחית
     */
    public void setFloorLayout(FloorLayout layout) {
        this.floorLayout = layout;
    }

    private void drawFloorLayout(Canvas canvas) {
        float offX = camera.getOffsetX();
        float offY = camera.getOffsetY();

        // Corridors drawn first so room floors paint over any seam
        for (Corridor c : floorLayout.corridors) {
            canvas.drawRect(c.x - offX, c.y - offY,
                    c.x + c.width - offX, c.y + c.height - offY,
                    corridorFloorPaint);
        }

        for (Room r : floorLayout.rooms) {
            canvas.drawRect(r.x - offX, r.y - offY,
                    r.x + r.width - offX, r.y + r.height - offY,
                    roomPaintFor(r));
            drawRoomWalls(canvas, r, offX, offY);
        }

        for (Corridor c : floorLayout.corridors) {
            float left   = c.x - offX;
            float top    = c.y - offY;
            float right  = c.x + c.width  - offX;
            float bottom = c.y + c.height - offY;
            if (c.width >= c.height) {
                // Horizontal corridor — top and bottom walls only
                canvas.drawLine(left, top,    right, top,    wallPaint);
                canvas.drawLine(left, bottom, right, bottom, wallPaint);
            } else {
                // Vertical corridor — left and right walls only
                canvas.drawLine(left,  top, left,  bottom, wallPaint);
                canvas.drawLine(right, top, right, bottom, wallPaint);
            }
        }
    }

    private Paint roomPaintFor(Room r) {
        switch (r.type) {
            case ENTRY:  return entryFloorPaint;
            case EXIT:   return exitFloorPaint;
            case BOSS:   return bossFloorPaint;
            default:     return roomFloorPaint;
        }
    }

    private void drawRoomWalls(Canvas canvas, Room r, float offX, float offY) {
        float left   = r.x - offX;
        float top    = r.y - offY;
        float right  = r.x + r.width  - offX;
        float bottom = r.y + r.height - offY;
        float hw     = FloorLayout.CORRIDOR_WIDTH * 0.5f; // 75 px — half door width

        // NORTH wall (top edge)
        if (r.sideUsed[Room.NORTH]) {
            float door = left + r.width * 0.5f;
            canvas.drawLine(left, top, door - hw, top, wallPaint);
            canvas.drawLine(door + hw, top, right, top, wallPaint);
        } else {
            canvas.drawLine(left, top, right, top, wallPaint);
        }

        // SOUTH wall (bottom edge)
        if (r.sideUsed[Room.SOUTH]) {
            float door = left + r.width * 0.5f;
            canvas.drawLine(left, bottom, door - hw, bottom, wallPaint);
            canvas.drawLine(door + hw, bottom, right, bottom, wallPaint);
        } else {
            canvas.drawLine(left, bottom, right, bottom, wallPaint);
        }

        // EAST wall (right edge)
        if (r.sideUsed[Room.EAST]) {
            float door = top + r.height * 0.5f;
            canvas.drawLine(right, top, right, door - hw, wallPaint);
            canvas.drawLine(right, door + hw, right, bottom, wallPaint);
        } else {
            canvas.drawLine(right, top, right, bottom, wallPaint);
        }

        // WEST wall (left edge)
        if (r.sideUsed[Room.WEST]) {
            float door = top + r.height * 0.5f;
            canvas.drawLine(left, top, left, door - hw, wallPaint);
            canvas.drawLine(left, door + hw, left, bottom, wallPaint);
        } else {
            canvas.drawLine(left, top, left, bottom, wallPaint);
        }
    }

    private void drawMinimap(Canvas canvas) {
        if (floorLayout == null || player == null) return;

        // Compute world bounding box across all rooms
        float minX = Float.MAX_VALUE,  minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Room r : floorLayout.rooms) {
            if (r.x           < minX) minX = r.x;
            if (r.y           < minY) minY = r.y;
            if (r.x + r.width  > maxX) maxX = r.x + r.width;
            if (r.y + r.height > maxY) maxY = r.y + r.height;
        }
        float layoutW = maxX - minX;
        float layoutH = maxY - minY;
        if (layoutW <= 0 || layoutH <= 0) return;

        // Scale so the whole layout fits inside MINIMAP_SIZE, keeping aspect ratio
        float scale   = Math.min(MINIMAP_SIZE / layoutW, MINIMAP_SIZE / layoutH);

        // Top-right corner placement
        float bgLeft   = getWidth()  - MINIMAP_SIZE - MINIMAP_PADDING;
        float bgTop    = MINIMAP_PADDING;
        float bgRight  = bgLeft + MINIMAP_SIZE;
        float bgBottom = bgTop  + MINIMAP_SIZE;

        // Origin for world→minimap translation (centres the layout inside the box)
        float ox = bgLeft + (MINIMAP_SIZE - layoutW * scale) * 0.5f;
        float oy = bgTop  + (MINIMAP_SIZE - layoutH * scale) * 0.5f;

        // Background
        canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, minimapBgPaint);
        canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, minimapBorderPaint);

        // Corridors — visible when at least one endpoint room is explored
        for (Corridor c : floorLayout.corridors) {
            Room rA = minimapRoom(c.roomIdA);
            Room rB = minimapRoom(c.roomIdB);
            if ((rA != null && rA.explored) || (rB != null && rB.explored)) {
                float cx = ox + (c.x - minX) * scale;
                float cy = oy + (c.y - minY) * scale;
                float cw = Math.max(1f, c.width  * scale);
                float ch = Math.max(1f, c.height * scale);
                canvas.drawRect(cx, cy, cx + cw, cy + ch, minimapCorridorPaint);
            }
        }

        // Rooms — only explored ones
        for (Room r : floorLayout.rooms) {
            if (!r.explored) continue;
            float rx = ox + (r.x - minX) * scale;
            float ry = oy + (r.y - minY) * scale;
            float rw = Math.max(2f, r.width  * scale);
            float rh = Math.max(2f, r.height * scale);
            canvas.drawRect(rx, ry, rx + rw, ry + rh, minimapPaintFor(r));
        }

        // Player dot
        float px = ox + (player.x - minX) * scale;
        float py = oy + (player.y - minY) * scale;
        canvas.drawCircle(px, py, 4f, minimapPlayerPaint);
    }

    private Paint minimapPaintFor(Room r) {
        switch (r.type) {
            case ENTRY: return minimapEntryPaint;
            case EXIT:  return minimapExitPaint;
            case BOSS:  return minimapBossRoomPaint;
            default:    return minimapRoomPaint;
        }
    }

    private Room minimapRoom(int id) {
        if (floorLayout == null) return null;
        for (Room r : floorLayout.rooms) if (r.id == id) return r;
        return null;
    }

    /**
     * מגדירה את מנהל הקומות לגישה לנתוני פורטל.
     * @param floorManager מנהל הקומות הנוכחי
     */
    public void setFloorManager(FloorManager floorManager) {
        this.floorManager = floorManager;
    }

    /**
     * Draws the exit portal with a pulsing animation.
     *
     * The pulse works by feeding System.currentTimeMillis() into Math.sin().
     * sin() returns a value between -1 and 1 smoothly over time.
     * We scale it to 0.75–1.0 so the portal breathes slightly rather than
     * fully disappearing.
     *
     * The outer glow is drawn first (larger radius, semi-transparent),
     * then the solid core on top, then the "EXIT" label.
     */
    private void drawPortal(Canvas canvas) {
        if (floorManager == null || !floorManager.isPortalOpen()) return;

        // Convert world position to screen position
        float drawX = floorManager.getPortalX() - camera.getOffsetX();
        float drawY = floorManager.getPortalY() - camera.getOffsetY();

        // Pulse value smoothly oscillates between 0.75 and 1.0
        // Dividing millis by 400 controls the speed of the pulse
        float pulse = (float)(Math.sin(System.currentTimeMillis() / 400.0) * 0.125 + 0.875);

        float coreRadius   = FloorManager.PORTAL_RADIUS * pulse;
        float glowRadius   = FloorManager.PORTAL_RADIUS * 1.8f * pulse;

        // Outer glow — semi-transparent, larger than the core
        portalGlowPaint.setAlpha((int)(90 * pulse));
        canvas.drawCircle(drawX, drawY, glowRadius, portalGlowPaint);

        // Inner core — more opaque
        portalCorePaint.setAlpha((int)(210 * pulse));
        canvas.drawCircle(drawX, drawY, coreRadius, portalCorePaint);

        // "EXIT" label floats above the portal
        canvas.drawText("EXIT", drawX, drawY - FloorManager.PORTAL_RADIUS - 16f, portalLabelPaint);
    }

    /**
     * Draws a directional arrow on the edge of the screen pointing
     * toward the portal when it's off screen.
     *
     * How it works:
     * 1. Convert the portal's world position to screen position
     * 2. If that screen position is within screen bounds, the portal
     *    is visible — don't draw the arrow
     * 3. If off screen, calculate the angle from screen center to the
     *    portal's screen position, then clamp the arrow to the screen edge
     * 4. Draw a triangle at that edge position rotated toward the portal
     *
     * The arrow pulses using the same sine wave trick as the portal itself.
     */
    private void drawPortalArrow(Canvas canvas) {
        if (floorManager == null || !floorManager.isPortalOpen()) return;

        // Portal position in screen space
        float portalScreenX = floorManager.getPortalX() - camera.getOffsetX();
        float portalScreenY = floorManager.getPortalY() - camera.getOffsetY();

        int w = getWidth();
        int h = getHeight();

        // Margin from screen edge where the arrow sits
        float margin = 80f;

        // If portal is already visible on screen, don't draw the arrow
        if (portalScreenX > margin && portalScreenX < w - margin &&
                portalScreenY > margin && portalScreenY < h - margin) return;

        // Angle from screen center toward the portal
        float centerX = w / 2f;
        float centerY = h / 2f;
        float angle = (float) Math.atan2(portalScreenY - centerY, portalScreenX - centerX);

        // Clamp the arrow position to the screen edge
        float arrowX = centerX + (float) Math.cos(angle) * (Math.min(centerX, centerY) - margin);
        float arrowY = centerY + (float) Math.sin(angle) * (Math.min(centerX, centerY) - margin);

        // Pulse the arrow alpha so it draws attention
        float pulse = (float)(Math.sin(System.currentTimeMillis() / 300.0) * 0.25 + 0.75);
        int alpha = (int)(255 * pulse);

        // Save canvas state, rotate around the arrow position, draw triangle
        canvas.save();
        canvas.translate(arrowX, arrowY);
        canvas.rotate((float) Math.toDegrees(angle));

        // Outer glow
        arrowGlowPaint.setAlpha((int)(80 * pulse));
        canvas.drawCircle(0, 0, 36f, arrowGlowPaint);

        // Triangle arrow shape — three points: tip (right), top-left, bottom-left
        arrowPaint.setAlpha(alpha);
        android.graphics.Path arrowPath = new android.graphics.Path();
        arrowPath.moveTo(28f, 0f);    // Tip — points toward portal
        arrowPath.lineTo(-18f, -18f); // Top left corner
        arrowPath.lineTo(-18f,  18f); // Bottom left corner
        arrowPath.close();
        canvas.drawPath(arrowPath, arrowPaint);

        canvas.restore();
    }

    private void drawShopPortal(Canvas canvas) {
        if (floorManager == null || !floorManager.isShopPortalOpen()) return;

        float drawX = floorManager.getShopPortalX() - camera.getOffsetX();
        float drawY = floorManager.getShopPortalY() - camera.getOffsetY();

        float pulse = (float)(Math.sin(System.currentTimeMillis() / 400.0) * 0.125 + 0.875);

        float coreRadius = FloorManager.SHOP_PORTAL_RADIUS * pulse;
        float glowRadius = FloorManager.SHOP_PORTAL_RADIUS * 1.8f * pulse;

        shopPortalGlowPaint.setAlpha((int)(90 * pulse));
        canvas.drawCircle(drawX, drawY, glowRadius, shopPortalGlowPaint);

        shopPortalCorePaint.setAlpha((int)(210 * pulse));
        canvas.drawCircle(drawX, drawY, coreRadius, shopPortalCorePaint);

        canvas.drawText("SHOP", drawX, drawY - FloorManager.SHOP_PORTAL_RADIUS - 16f,
                shopPortalLabelPaint);
    }

    private void drawShopPortalArrow(Canvas canvas) {
        if (floorManager == null || !floorManager.isShopPortalOpen()) return;

        float portalScreenX = floorManager.getShopPortalX() - camera.getOffsetX();
        float portalScreenY = floorManager.getShopPortalY() - camera.getOffsetY();

        int w = getWidth();
        int h = getHeight();
        float margin = 80f;

        if (portalScreenX > margin && portalScreenX < w - margin &&
                portalScreenY > margin && portalScreenY < h - margin) return;

        float centerX = w / 2f;
        float centerY = h / 2f;
        float angle = (float) Math.atan2(portalScreenY - centerY, portalScreenX - centerX);

        float arrowX = centerX + (float) Math.cos(angle) * (Math.min(centerX, centerY) - margin);
        float arrowY = centerY + (float) Math.sin(angle) * (Math.min(centerX, centerY) - margin);

        float pulse = (float)(Math.sin(System.currentTimeMillis() / 300.0) * 0.25 + 0.75);
        int alpha = (int)(255 * pulse);

        canvas.save();
        canvas.translate(arrowX, arrowY);
        canvas.rotate((float) Math.toDegrees(angle));

        shopArrowGlowPaint.setAlpha((int)(80 * pulse));
        canvas.drawCircle(0, 0, 36f, shopArrowGlowPaint);

        shopArrowPaint.setAlpha(alpha);
        android.graphics.Path arrowPath = new android.graphics.Path();
        arrowPath.moveTo(28f, 0f);
        arrowPath.lineTo(-18f, -18f);
        arrowPath.lineTo(-18f,  18f);
        arrowPath.close();
        canvas.drawPath(arrowPath, shopArrowPaint);

        canvas.restore();
    }
}