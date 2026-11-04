package com.example.obliviondescent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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

    private List<Projectile> projectiles;

    private Paint arrowPaint;
    private Paint arrowGlowPaint;

    private List<GoldCoin> goldCoins;

    private List<TreasureChest> treasureChests;

    private Paint shopArrowPaint;
    private Paint shopArrowGlowPaint;

    private ShopRoom shopRoom;
    private boolean inShopRoom = false;

    // Floor layout rendering
    private FloorLayout floorLayout;
    private final Props props = new Props(); // portals and room decorations, shared with the shop
    private volatile List<Props.Decor> decor;
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

    // Co-op session (null in solo): teammates and connection status
    private CoopSession coop;
    private final Paint coopStatusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint allyNamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint allyBarBgPaint = new Paint();
    private final Paint allyBarPaint = new Paint();
    private final Paint allyDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Minimap: player-centred circle in the top-right corner (the HUD layout reserves this area)
    private static final float MINIMAP_DIAMETER_DP  = 112f;
    private static final float MINIMAP_MARGIN_DP    = 12f;
    private static final float MINIMAP_WORLD_RADIUS = 1600f; // world units from centre to frame
    private final float density;
    private final Path minimapClip  = new Path();
    private final Path minimapArrow = new Path();
    private float mapCx, mapCy, mapRadius;
    private Paint minimapBgPaint;
    private Paint minimapRimPaint;
    private Paint minimapBorderPaint;
    private Paint minimapHighlightPaint;
    private Paint minimapRoomPaint;
    private Paint minimapEntryPaint;
    private Paint minimapExitPaint;
    private Paint minimapBossRoomPaint;
    private Paint minimapCorridorPaint;
    private Paint minimapPlayerPaint;
    private Paint minimapPortalPaint;
    private Paint minimapShopPaint;
    private Paint minimapNorthPaint;

    public GameDisplay(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        floorPaint = new Paint();
        floorPaint.setColor(Color.DKGRAY);
        floorPaint.setStrokeWidth(2f);
        floorPaint.setAlpha(50); // Faint grid lines
        props.setFont(getResources().getFont(R.font.cinzel));

        arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setColor(Color.rgb(160, 100, 255));
        arrowPaint.setStyle(Paint.Style.FILL);

        arrowGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowGlowPaint.setColor(Color.rgb(160, 100, 255));
        arrowGlowPaint.setStyle(Paint.Style.FILL);
        arrowGlowPaint.setAlpha(60);

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

        minimapBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minimapBgPaint.setColor(Color.argb(200, 10, 8, 7));
        minimapBgPaint.setStyle(Paint.Style.FILL);

        // Frame: dark bronze band, gold line inside it, faint gold highlight
        minimapRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minimapRimPaint.setColor(Color.rgb(60, 45, 26));
        minimapRimPaint.setStyle(Paint.Style.STROKE);
        minimapRimPaint.setStrokeWidth(4f * density);

        minimapBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minimapBorderPaint.setColor(Color.rgb(139, 111, 44));
        minimapBorderPaint.setStyle(Paint.Style.STROKE);
        minimapBorderPaint.setStrokeWidth(2f * density);

        minimapHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minimapHighlightPaint.setColor(Color.argb(128, 212, 175, 55));
        minimapHighlightPaint.setStyle(Paint.Style.STROKE);
        minimapHighlightPaint.setStrokeWidth(density);

        minimapRoomPaint = new Paint();
        minimapRoomPaint.setColor(Color.rgb(58, 49, 40));
        minimapRoomPaint.setStyle(Paint.Style.FILL);

        minimapEntryPaint = new Paint();
        minimapEntryPaint.setColor(Color.argb(179, 122, 139, 61));
        minimapEntryPaint.setStyle(Paint.Style.FILL);

        minimapExitPaint = new Paint();
        minimapExitPaint.setColor(Color.rgb(90, 58, 138));
        minimapExitPaint.setStyle(Paint.Style.FILL);

        minimapBossRoomPaint = new Paint();
        minimapBossRoomPaint.setColor(Color.rgb(139, 24, 24));
        minimapBossRoomPaint.setStyle(Paint.Style.FILL);

        minimapCorridorPaint = new Paint();
        minimapCorridorPaint.setColor(Color.rgb(42, 33, 26));
        minimapCorridorPaint.setStyle(Paint.Style.FILL);

        minimapPlayerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minimapPlayerPaint.setColor(Color.rgb(234, 224, 202));
        minimapPlayerPaint.setStyle(Paint.Style.FILL);

        minimapPortalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minimapPortalPaint.setColor(Color.rgb(181, 140, 224));

        minimapShopPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minimapShopPaint.setColor(Color.rgb(212, 175, 55));

        minimapNorthPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minimapNorthPaint.setColor(Color.rgb(212, 175, 55));
        minimapNorthPaint.setTextSize(9f * density);
        minimapNorthPaint.setTextAlign(Paint.Align.CENTER);
        minimapNorthPaint.setTypeface(getResources().getFont(R.font.cinzel_bold));
        minimapNorthPaint.setShadowLayer(2f * density, 0f, 0f, Color.BLACK);

        // Player arrow pointing along +x; rotated to the facing angle when drawn
        minimapArrow.moveTo(6f * density, 0f);
        minimapArrow.lineTo(-4f * density, -4.5f * density);
        minimapArrow.lineTo(-2f * density, 0f);
        minimapArrow.lineTo(-4f * density, 4.5f * density);
        minimapArrow.close();

        allyNamePaint.setColor(Color.WHITE);
        allyNamePaint.setTextSize(24f);
        allyNamePaint.setTextAlign(Paint.Align.CENTER);
        allyNamePaint.setFakeBoldText(true);
        allyNamePaint.setShadowLayer(3f, 0f, 1f, Color.BLACK);
        allyBarBgPaint.setColor(Color.argb(180, 0, 0, 0));
        allyBarPaint.setColor(Color.rgb(90, 220, 110));
        coopStatusPaint.setTextSize(22f);
        coopStatusPaint.setTextAlign(Paint.Align.RIGHT);
        coopStatusPaint.setShadowLayer(3f, 0f, 1f, Color.BLACK);
    }

    /** Co-op: teammates to draw and connection status, live from the session. */
    public void setCoop(CoopSession coop) {
        this.coop = coop;
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
        layoutMinimap(getWidth());
    }

    /** Places the minimap circle in the top-right corner, inside the safe insets. */
    private void layoutMinimap(int width) {
        float margin = MINIMAP_MARGIN_DP * density;
        mapRadius = MINIMAP_DIAMETER_DP * 0.5f * density;
        mapCx = width - safeRight - margin - mapRadius;
        mapCy = safeTop + margin + mapRadius;
        // Clip edge sits under the bronze band, hiding any aliasing
        minimapClip.reset();
        minimapClip.addCircle(mapCx, mapCy, mapRadius - 2f * density, Path.Direction.CW);
    }

    /** Torchlight: the edges of the screen fall off into darkness around the player. */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float radius = (float) Math.hypot(w, h) * 0.6f;
        vignettePaint.setShader(new android.graphics.RadialGradient(w / 2f, h / 2f, radius,
                new int[]{0x00000000, 0x00000000, 0xC0000000}, new float[]{0f, 0.5f, 1f},
                android.graphics.Shader.TileMode.CLAMP));
        layoutMinimap(w);
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

        // Portals lie on the floor, under coins, enemies and players
        if (!inShopRoom) {
            drawPortal(canvas);
            drawShopPortal(canvas);
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
            drawPortalArrow(canvas);
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
        drawCoopStatus(canvas);
    }

    /** One line under the minimap: party size, or what is wrong with the connection. */
    private void drawCoopStatus(Canvas canvas) {
        if (coop == null) return;
        long now = System.currentTimeMillis();
        coopStatusPaint.setColor(coop.isSyncHealthy(now) ? Color.rgb(170, 220, 170) : Color.rgb(255, 120, 90));
        canvas.drawText(coop.statusLine(now), mapCx + mapRadius, mapCy + mapRadius + 18f * density,
                coopStatusPaint);
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
        decor = layout != null ? Props.decorate(layout) : null;
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
        drawDecor(canvas);
    }

    /** Room decorations near the screen, in world space: torchlight first, then the props. */
    private void drawDecor(Canvas canvas) {
        List<Props.Decor> list = decor;
        if (list == null) return;
        long now = System.currentTimeMillis();
        float offX = camera.getOffsetX(), offY = camera.getOffsetY();
        float left = offX - 400, top = offY - 400;
        float right = offX + getWidth() + 400, bottom = offY + getHeight() + 400;
        canvas.save();
        canvas.translate(-offX, -offY);
        for (Props.Decor d : list)
            if (d.x > left && d.x < right && d.y > top && d.y < bottom) props.drawDecorLight(canvas, d, now);
        for (Props.Decor d : list)
            if (d.x > left && d.x < right && d.y > top && d.y < bottom) props.drawDecor(canvas, d, now);
        canvas.restore();
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

    /** Circular, player-centred, north-up minimap inside a gold frame. */
    private void drawMinimap(Canvas canvas) {
        if (floorLayout == null || player == null) return;

        // World -> minimap: the player at the centre, MINIMAP_WORLD_RADIUS reaches the frame
        float scale = (mapRadius - 4f * density) / MINIMAP_WORLD_RADIUS;
        float ox = mapCx - player.x * scale;
        float oy = mapCy - player.y * scale;

        canvas.save();
        canvas.clipPath(minimapClip);
        canvas.drawCircle(mapCx, mapCy, mapRadius, minimapBgPaint);

        // Corridors — visible when at least one endpoint room is explored
        for (Corridor c : floorLayout.corridors) {
            Room rA = minimapRoom(c.roomIdA);
            Room rB = minimapRoom(c.roomIdB);
            if ((rA != null && rA.explored) || (rB != null && rB.explored)) {
                canvas.drawRect(ox + c.x * scale, oy + c.y * scale,
                        ox + (c.x + c.width) * scale, oy + (c.y + c.height) * scale, minimapCorridorPaint);
            }
        }

        // Rooms — only explored ones
        for (Room r : floorLayout.rooms) {
            if (!r.explored) continue;
            canvas.drawRect(ox + r.x * scale, oy + r.y * scale,
                    ox + (r.x + r.width) * scale, oy + (r.y + r.height) * scale, minimapPaintFor(r));
        }

        // Open portals, same positions as drawPortal() / drawShopPortal()
        if (floorManager != null) {
            if (floorManager.isPortalOpen()) {
                float pulse = (float) (Math.sin(System.currentTimeMillis() / 400.0) * 0.125 + 0.875);
                drawMinimapMarker(canvas, floorManager.getPortalX(), floorManager.getPortalY(),
                        scale, pulse, minimapPortalPaint);
            }
            if (floorManager.isShopPortalOpen()) {
                drawMinimapMarker(canvas, floorManager.getShopPortalX(), floorManager.getShopPortalY(),
                        scale, 1f, minimapShopPaint);
            }
        }

        // Teammates on this floor (ones in the shop fall outside the clip)
        if (coop != null && floorManager != null) {
            for (CoopSession.RemotePlayer r : coop.others.values()) {
                if (r.dead || r.floor != floorManager.getCurrentFloor()) continue;
                allyDotPaint.setColor(r.color);
                canvas.drawCircle(ox + r.body.x * scale, oy + r.body.y * scale, 3f * density, allyDotPaint);
            }
        }

        // Player arrow along the facing angle; restore() drops the rotation and the clip
        canvas.translate(mapCx, mapCy);
        canvas.rotate(player.getFacingAngle());
        canvas.drawPath(minimapArrow, minimapPlayerPaint);
        canvas.restore();

        // Frame on top: bronze band with a gold line, inner highlight, "N" at the top
        float band = mapRadius - 2f * density;
        canvas.drawCircle(mapCx, mapCy, band, minimapRimPaint);
        canvas.drawCircle(mapCx, mapCy, band, minimapBorderPaint);
        canvas.drawCircle(mapCx, mapCy, mapRadius - 4.5f * density, minimapHighlightPaint);
        canvas.drawText("N", mapCx, mapCy - mapRadius + 14f * density, minimapNorthPaint);
    }

    /** Portal marker with a soft glow; pinned just inside the frame when out of range. */
    private void drawMinimapMarker(Canvas canvas, float worldX, float worldY, float scale,
                                   float pulse, Paint paint) {
        float edge = mapRadius - 9f * density;
        float dx = (worldX - player.x) * scale;
        float dy = (worldY - player.y) * scale;
        float dist = (float) Math.hypot(dx, dy);
        if (dist > edge) {
            dx *= edge / dist;
            dy *= edge / dist;
        }
        paint.setAlpha((int) (90 * pulse));
        canvas.drawCircle(mapCx + dx, mapCy + dy, 6f * density * pulse, paint);
        paint.setAlpha(255);
        canvas.drawCircle(mapCx + dx, mapCy + dy, 3f * density, paint);
    }

    /** Teammates on this floor, each with a name tag and a small health bar. */
    private void drawRemotePlayers(Canvas canvas) {
        if (coop == null || floorManager == null) return;
        for (CoopSession.RemotePlayer r : coop.others.values()) {
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

    /** Exit portal: the same arcane rune circle as in the shop room. */
    private void drawPortal(Canvas canvas) {
        if (floorManager == null || !floorManager.isPortalOpen()) return;
        float drawX = floorManager.getPortalX() - camera.getOffsetX();
        float drawY = floorManager.getPortalY() - camera.getOffsetY();
        props.drawPortal(canvas, drawX, drawY, FloorManager.PORTAL_RADIUS, System.currentTimeMillis(), false, "NEXT FLOOR");
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

    /** Shop portal: the rune circle in gold. */
    private void drawShopPortal(Canvas canvas) {
        if (floorManager == null || !floorManager.isShopPortalOpen()) return;
        float drawX = floorManager.getShopPortalX() - camera.getOffsetX();
        float drawY = floorManager.getShopPortalY() - camera.getOffsetY();
        props.drawPortal(canvas, drawX, drawY, FloorManager.SHOP_PORTAL_RADIUS, System.currentTimeMillis(), true, "SHOP");
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