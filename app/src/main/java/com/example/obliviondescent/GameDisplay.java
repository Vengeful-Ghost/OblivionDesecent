package com.example.obliviondescent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.List;

/**
 * View that renders the game world: player, enemies, projectiles, coins, chests,
 * floor layout, portals, minimap and animations.
 * Redrawn by a dedicated thread via postInvalidate().
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
    private Paint wallEdgePaint;
    private Paint tilePaint;
    private Paint tileShadePaint;
    private Paint wallShadowPaint;
    private final Paint vignettePaint = new Paint();

    private GameEffects effects;

    // Camera-cutout insets, so the minimap stays visible
    private int safeTop = 0, safeRight = 0;

    // Co-op teammates (null in solo)
    private java.util.Collection<CoopSession.RemotePlayer> remotePlayers;
    private final Paint allyNamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint allyBarBgPaint = new Paint();
    private final Paint allyBarPaint = new Paint();
    private final Paint allyDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

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
        roomFloorPaint.setColor(Color.rgb(42, 36, 33));
        roomFloorPaint.setStyle(Paint.Style.FILL);

        entryFloorPaint = new Paint();
        entryFloorPaint.setColor(Color.rgb(32, 44, 34));
        entryFloorPaint.setStyle(Paint.Style.FILL);

        exitFloorPaint = new Paint();
        exitFloorPaint.setColor(Color.rgb(42, 32, 54));
        exitFloorPaint.setStyle(Paint.Style.FILL);

        bossFloorPaint = new Paint();
        bossFloorPaint.setColor(Color.rgb(56, 26, 24));
        bossFloorPaint.setStyle(Paint.Style.FILL);

        corridorFloorPaint = new Paint();
        corridorFloorPaint.setColor(Color.rgb(32, 28, 26));
        corridorFloorPaint.setStyle(Paint.Style.FILL);

        // Walls: a dark edge under a stone face
        wallEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wallEdgePaint.setColor(Color.rgb(16, 13, 12));
        wallEdgePaint.setStyle(Paint.Style.STROKE);
        wallEdgePaint.setStrokeWidth(20f);
        wallEdgePaint.setStrokeCap(Paint.Cap.SQUARE);

        wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wallPaint.setColor(Color.rgb(104, 92, 84));
        wallPaint.setStyle(Paint.Style.STROKE);
        wallPaint.setStrokeWidth(9f);
        wallPaint.setStrokeCap(Paint.Cap.SQUARE);

        tilePaint = new Paint();
        tilePaint.setColor(Color.argb(22, 0, 0, 0));
        tilePaint.setStrokeWidth(3f);

        tileShadePaint = new Paint();
        tileShadePaint.setColor(Color.argb(9, 255, 240, 220));

        wallShadowPaint = new Paint();
        wallShadowPaint.setColor(Color.argb(90, 0, 0, 0));
        wallShadowPaint.setStyle(Paint.Style.STROKE);
        wallShadowPaint.setStrokeWidth(24f);

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

        allyNamePaint.setColor(Color.WHITE);
        allyNamePaint.setTextSize(24f);
        allyNamePaint.setTextAlign(Paint.Align.CENTER);
        allyNamePaint.setFakeBoldText(true);
        allyNamePaint.setShadowLayer(3f, 0f, 1f, Color.BLACK);
        allyBarBgPaint.setColor(Color.argb(180, 0, 0, 0));
        allyBarPaint.setColor(Color.rgb(90, 220, 110));
    }

    /** Co-op: the other players to draw, live from the session. */
    public void setRemotePlayers(java.util.Collection<CoopSession.RemotePlayer> players) {
        this.remotePlayers = players;
    }

    /**
     * Connects logic objects to this view so they are rendered each draw frame.
     * @param player the player to draw
     * @param enemies list of enemies to draw
     * @param camera game camera
     * @param projectiles list of active projectiles
     * @param goldCoins list of gold coins
     * @param treasureChests list of treasure chests
     * @param shopRoom the shop room object
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

    public void setEffects(GameEffects effects) {
        this.effects = effects;
    }

    /** Top and right camera-cutout insets in pixels; the minimap sits inside them. */
    public void setSafeInsets(int top, int right) {
        this.safeTop = top;
        this.safeRight = right;
    }

    /** Torchlight: the edges of the screen fall off into darkness around the player. */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float radius = (float) Math.hypot(w, h) * 0.6f;
        vignettePaint.setShader(new android.graphics.RadialGradient(w / 2f, h / 2f, radius,
                new int[]{0x00000000, 0x00000000, 0xC0000000}, new float[]{0f, 0.5f, 1f},
                android.graphics.Shader.TileMode.CLAMP));
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
        drawRemotePlayers(canvas);
        player.draw(canvas, camera);

        if (effects != null) effects.draw(canvas, camera);

        if (vignettePaint.getShader() != null)
            canvas.drawRect(0, 0, getWidth(), getHeight(), vignettePaint);

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
     * Sets the floor layout to be rendered.
     * @param layout the current floor layout
     */
    public void setFloorLayout(FloorLayout layout) {
        this.floorLayout = layout;
    }

    private void drawFloorLayout(Canvas canvas) {
        float offX = camera.getOffsetX();
        float offY = camera.getOffsetY();

        // Corridors drawn first so room floors paint over any seam
        for (Corridor c : floorLayout.corridors) {
            float left = c.x - offX;
            float top  = c.y - offY;
            canvas.drawRect(left, top, left + c.width, top + c.height, corridorFloorPaint);
            drawTiles(canvas, left, top, left + c.width, top + c.height);
        }

        for (Room r : floorLayout.rooms) {
            float left   = r.x - offX;
            float top    = r.y - offY;
            float right  = left + r.width;
            float bottom = top + r.height;
            if (right < 0 || bottom < 0 || left > getWidth() || top > getHeight()) continue;
            canvas.drawRect(left, top, right, bottom, roomPaintFor(r));
            drawTiles(canvas, left, top, right, bottom);
            // Darker band along the walls gives the room some depth
            canvas.drawRect(left + 12f, top + 12f, right - 12f, bottom - 12f, wallShadowPaint);
            drawRoomWalls(canvas, r, offX, offY);
        }

        for (Corridor c : floorLayout.corridors) {
            float left   = c.x - offX;
            float top    = c.y - offY;
            float right  = c.x + c.width  - offX;
            float bottom = c.y + c.height - offY;
            if (c.width >= c.height) {
                // Horizontal corridor — top and bottom walls only
                wall(canvas, left, top,    right, top);
                wall(canvas, left, bottom, right, bottom);
            } else {
                // Vertical corridor — left and right walls only
                wall(canvas, left,  top, left,  bottom);
                wall(canvas, right, top, right, bottom);
            }
        }
    }

    /** One wall segment: dark edge, then the stone face on top. */
    private void wall(Canvas canvas, float x1, float y1, float x2, float y2) {
        canvas.drawLine(x1, y1, x2, y2, wallEdgePaint);
        canvas.drawLine(x1, y1, x2, y2, wallPaint);
    }

    /**
     * Stone tiles anchored to world space (so rooms and corridors line up): a faint checker
     * plus grout lines, drawn only over the visible part of the area.
     */
    private void drawTiles(Canvas canvas, float left, float top, float right, float bottom) {
        float visibleLeft   = Math.max(left, 0f);
        float visibleRight  = Math.min(right, getWidth());
        float visibleTop    = Math.max(top, 0f);
        float visibleBottom = Math.min(bottom, getHeight());
        if (visibleLeft >= visibleRight || visibleTop >= visibleBottom) return;

        float tile = Room.TILE / 2f;
        float offX = camera.getOffsetX();
        float offY = camera.getOffsetY();
        int firstCol = (int) Math.floor((visibleLeft + offX) / tile);
        int firstRow = (int) Math.floor((visibleTop + offY) / tile);
        for (int row = firstRow; row * tile - offY < visibleBottom; row++) {
            float ty = row * tile - offY;
            if (ty > visibleTop)
                canvas.drawLine(visibleLeft, ty, visibleRight, ty, tilePaint);
            for (int col = firstCol; col * tile - offX < visibleRight; col++) {
                float tx = col * tile - offX;
                if (row == firstRow && tx > visibleLeft)
                    canvas.drawLine(tx, visibleTop, tx, visibleBottom, tilePaint);
                if (((row + col) & 1) == 0) {
                    canvas.drawRect(Math.max(tx, visibleLeft), Math.max(ty, visibleTop),
                            Math.min(tx + tile, visibleRight), Math.min(ty + tile, visibleBottom),
                            tileShadePaint);
                }
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
            wall(canvas, left, top, door - hw, top);
            wall(canvas, door + hw, top, right, top);
        } else {
            wall(canvas, left, top, right, top);
        }

        // SOUTH wall (bottom edge)
        if (r.sideUsed[Room.SOUTH]) {
            float door = left + r.width * 0.5f;
            wall(canvas, left, bottom, door - hw, bottom);
            wall(canvas, door + hw, bottom, right, bottom);
        } else {
            wall(canvas, left, bottom, right, bottom);
        }

        // EAST wall (right edge)
        if (r.sideUsed[Room.EAST]) {
            float door = top + r.height * 0.5f;
            wall(canvas, right, top, right, door - hw);
            wall(canvas, right, door + hw, right, bottom);
        } else {
            wall(canvas, right, top, right, bottom);
        }

        // WEST wall (left edge)
        if (r.sideUsed[Room.WEST]) {
            float door = top + r.height * 0.5f;
            wall(canvas, left, top, left, door - hw);
            wall(canvas, left, door + hw, left, bottom);
        } else {
            wall(canvas, left, top, left, bottom);
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

        // Top-right corner placement, clear of the camera cutout
        float bgLeft   = getWidth()  - MINIMAP_SIZE - MINIMAP_PADDING - safeRight;
        float bgTop    = MINIMAP_PADDING + safeTop;
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

        // Teammates on this floor
        if (remotePlayers == null || floorManager == null) return;
        for (CoopSession.RemotePlayer r : remotePlayers) {
            if (r.dead || r.floor != floorManager.getCurrentFloor()) continue;
            float ax = ox + (r.body.x - minX) * scale;
            float ay = oy + (r.body.y - minY) * scale;
            if (ax < bgLeft || ax > bgRight || ay < bgTop || ay > bgBottom) continue; // e.g. in the shop
            allyDotPaint.setColor(r.color);
            canvas.drawCircle(ax, ay, 4f, allyDotPaint);
        }
    }

    /** Teammates on this floor, each with a name tag and a small health bar. */
    private void drawRemotePlayers(Canvas canvas) {
        if (remotePlayers == null || floorManager == null) return;
        for (CoopSession.RemotePlayer r : remotePlayers) {
            if (r.dead || r.floor != floorManager.getCurrentFloor()) continue;
            r.body.draw(canvas, camera);
            float sx = r.body.x - camera.getOffsetX();
            float top = r.body.y - camera.getOffsetY() - r.body.radius - 30f;
            canvas.drawText(r.name, sx, top - 12f, allyNamePaint);
            float ratio = r.maxHp > 0 ? Math.max(0f, Math.min(1f, (float) r.hp / r.maxHp)) : 0f;
            canvas.drawRect(sx - 40f, top - 4f, sx + 40f, top + 4f, allyBarBgPaint);
            canvas.drawRect(sx - 40f, top - 4f, sx - 40f + 80f * ratio, top + 4f, allyBarPaint);
        }
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
     * Sets the floor manager used to access portal data for rendering.
     * @param floorManager the current floor manager
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