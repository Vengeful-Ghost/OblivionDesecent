package com.example.obliviondescent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
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
    private Paint roomFloorPaint;
    private final Paint treasureFloorPaint = new Paint();
    private final Paint minimapTreasurePaint = new Paint();
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
    // Brick front faces under north walls, so the walls read as standing up
    private static final float WALL_FACE = Props.WALL_FACE;
    private final Paint wallFacePaint = new Paint(), mortarPaint = new Paint(), wallFootPaint = new Paint();
    // Low health in a fight: red edges that throb with each heartbeat (0 = off)
    private final Paint dangerPaint = new Paint();
    private volatile long heartbeatAt = 0;

    // Dungeon darkness: everything dims, then lights cut soft holes back out of it; the Void is darker
    private volatile int ambientDark = Color.argb(150, 6, 5, 12);
    private volatile float playerLight = 430f;
    private final Paint lightHolePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] lightSpot = new float[2];

    // Event rooms: their own floor and map colours (by RoomEvent ordinal), and the Trial arena's gate
    private final Paint[] eventFloorPaints = new Paint[RoomEvent.values().length];
    private final Paint[] eventMapPaints = new Paint[RoomEvent.values().length];

    // Red edge arrows toward the floor's last enemies
    private final Paint enemyArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint enemyArrowGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path edgeArrowPath = new Path();

    private GameEffects effects;

    // Camera-cutout insets, so the minimap stays visible
    private int safeTop = 0, safeRight = 0;

    // Co-op session (null in solo): teammates and connection status
    private CoopSession coop;
    private volatile String spectating;
    private final Paint coopStatusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spectatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    private Wear.Stone minimapWear; // cracks and a chip in the frame, like the rest of the UI
    private Paint minimapRoomPaint;
    private Paint minimapEntryPaint;
    private Paint minimapExitPaint;
    private Paint minimapBossRoomPaint;
    private Paint minimapCorridorPaint;
    private Paint minimapPlayerPaint;
    private Paint minimapPortalPaint;
    private Paint minimapShopPaint;
    private Paint minimapNorthPaint;

    // Map ink: room outlines, rooms only seen through a door, icons, the full-floor view
    private final Paint mapStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mapHerePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mapUnknownPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mapIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mapGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mapEnemyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mapTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mapLetterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mapTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mapHintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mapRect = new RectF(), iconRect = new RectF();
    private final Path iconPath = new Path();
    private volatile boolean mapExpanded = false; // tap the minimap for the whole floor

    // Locked vault gate
    private final Paint gatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Camera punch-in, e.g. on a boss kill; render-only, so it is safe in co-op
    private volatile long zoomStart = 0, zoomUntil = 0;
    private volatile float zoomAmount = 0f;

    // The world is drawn small, one pixel per Sprites.PX units, and scaled up without smoothing: pixel art
    private PixelCanvas pixels;
    private final Paint blitPaint = Sprites.crisp();
    // Flagstones and bricks repeated across the world, tinted to each room's and biome's colours
    private final Paint floorTexPaint = Sprites.crisp(), brickTexPaint = Sprites.crisp();
    private final android.graphics.Matrix texMatrix = new android.graphics.Matrix();
    // A shaped room's walled-off blocks: the same dark as the rock past every wall, on the map too
    private final Paint rockPaint = new Paint(), mapRockPaint = new Paint();
    {
        rockPaint.setColor(Color.BLACK);
        mapRockPaint.setColor(Color.argb(235, 10, 8, 7));
    }
    private final java.util.Map<Integer, android.graphics.ColorFilter> tints = new java.util.HashMap<>();

    public GameDisplay(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        Sprites.load(context);
        android.graphics.Bitmap floorTex = Sprites.texture("tile.floor"), brickTex = Sprites.texture("tile.bricks");
        if (floorTex != null)
            floorTexPaint.setShader(new android.graphics.BitmapShader(floorTex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        if (brickTex != null)
            brickTexPaint.setShader(new android.graphics.BitmapShader(brickTex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        floorPaint = new Paint();
        floorPaint.setColor(Color.DKGRAY);
        floorPaint.setStrokeWidth(2f);
        floorPaint.setAlpha(50); // Faint grid lines
        props.setFont(getResources().getFont(R.font.pixel));

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

        wallFacePaint.setColor(Color.rgb(62, 53, 46));
        mortarPaint.setColor(Color.rgb(26, 21, 17));
        mortarPaint.setStrokeWidth(2f);
        wallFootPaint.setColor(Color.argb(130, 0, 0, 0));

        minimapBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minimapBgPaint.setColor(Color.argb(200, 10, 8, 7));
        minimapBgPaint.setStyle(Paint.Style.FILL);

        // Frame: dark iron band, worn iron line inside it, faint bone highlight
        minimapRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minimapRimPaint.setColor(Color.rgb(34, 31, 27));
        minimapRimPaint.setStyle(Paint.Style.STROKE);
        minimapRimPaint.setStrokeWidth(4f * density);

        minimapBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minimapBorderPaint.setColor(Color.rgb(90, 82, 68));
        minimapBorderPaint.setStyle(Paint.Style.STROKE);
        minimapBorderPaint.setStrokeWidth(2f * density);

        minimapHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minimapHighlightPaint.setColor(Color.argb(90, 216, 200, 160));
        minimapHighlightPaint.setStyle(Paint.Style.STROKE);
        minimapHighlightPaint.setStrokeWidth(density);

        minimapRoomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        minimapRoomPaint.setColor(Color.rgb(74, 62, 48));
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
        minimapCorridorPaint.setColor(Color.rgb(62, 52, 40));
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
        minimapNorthPaint.setTypeface(getResources().getFont(R.font.pixel_bold));
        minimapNorthPaint.setShadowLayer(2f * density, 0f, 0f, Color.BLACK);

        mapStrokePaint.setStyle(Paint.Style.STROKE);
        mapStrokePaint.setStrokeWidth(density);
        mapStrokePaint.setColor(Color.argb(200, 139, 111, 44));
        mapHerePaint.setStyle(Paint.Style.STROKE);
        mapHerePaint.setStrokeWidth(1.6f * density);
        mapHerePaint.setColor(Color.rgb(244, 215, 122));
        mapHerePaint.setShadowLayer(3f * density, 0f, 0f, Color.rgb(212, 175, 55));
        mapUnknownPaint.setStyle(Paint.Style.STROKE);
        mapUnknownPaint.setStrokeWidth(density);
        mapUnknownPaint.setColor(Color.rgb(122, 112, 96));
        mapUnknownPaint.setPathEffect(new DashPathEffect(new float[]{2f * density, 2f * density}, 0f));
        mapGlowPaint.setColor(Color.argb(70, 244, 215, 122));
        mapEnemyPaint.setColor(Color.rgb(224, 72, 72));
        mapTickPaint.setStyle(Paint.Style.STROKE);
        mapTickPaint.setStrokeCap(Paint.Cap.ROUND);
        mapTickPaint.setColor(Color.rgb(212, 175, 55));
        mapLetterPaint.setColor(Color.rgb(184, 172, 145));
        mapLetterPaint.setTextSize(7f * density);
        mapLetterPaint.setTextAlign(Paint.Align.CENTER);
        mapLetterPaint.setTypeface(getResources().getFont(R.font.pixel_bold));
        mapLetterPaint.setShadowLayer(2f * density, 0f, 0f, Color.BLACK);
        mapTitlePaint.setColor(Color.rgb(212, 175, 55));
        mapTitlePaint.setTextSize(20f * density);
        mapTitlePaint.setTextAlign(Paint.Align.CENTER);
        mapTitlePaint.setLetterSpacing(0.12f);
        mapTitlePaint.setTypeface(getResources().getFont(R.font.pixel_bold));
        mapTitlePaint.setShadowLayer(8f * density, 0f, 0f, Color.rgb(92, 69, 23));
        mapHintPaint.setColor(Color.rgb(122, 112, 96));
        mapHintPaint.setTextSize(11f * density);
        mapHintPaint.setTextAlign(Paint.Align.CENTER);
        mapHintPaint.setTypeface(getResources().getFont(R.font.pixel));

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
        // Teammates' names and the link status in the pixel font too
        allyNamePaint.setTypeface(getResources().getFont(R.font.pixel));
        coopStatusPaint.setTypeface(getResources().getFont(R.font.pixel));
        spectatePaint.setColor(Color.rgb(212, 175, 55));
        spectatePaint.setTextSize(16f * density);
        spectatePaint.setTextAlign(Paint.Align.CENTER);
        spectatePaint.setTypeface(getResources().getFont(R.font.pixel));
        spectatePaint.setShadowLayer(4f, 0f, 1f, Color.BLACK);

        treasureFloorPaint.setColor(Color.rgb(48, 40, 26));
        minimapTreasurePaint.setColor(Color.argb(200, 212, 175, 55));
        for (RoomEvent e : RoomEvent.values()) {
            Paint floor = new Paint();
            floor.setColor(e.floorColor);
            eventFloorPaints[e.ordinal()] = floor;
            Paint map = new Paint();
            map.setColor(e.mapColor);
            map.setAlpha(150);
            eventMapPaints[e.ordinal()] = map;
        }

        lightHolePaint.setShader(new RadialGradient(0, 0, 1f,
                new int[]{0xFF000000, 0xC0000000, 0x00000000}, new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
        lightHolePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        enemyArrowPaint.setColor(Color.rgb(224, 72, 72));
        enemyArrowGlowPaint.setColor(Color.rgb(224, 72, 72));
        edgeArrowPath.moveTo(28f, 0f);
        edgeArrowPath.lineTo(-18f, -18f);
        edgeArrowPath.lineTo(-18f, 18f);
        edgeArrowPath.close();
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
        if (inShopRoom) mapExpanded = false;
    }

    /** Briefly zooms the view in and back out; amount 0.2 peaks at 120%. */
    public void punchZoom(long ms, float amount) {
        zoomAmount = amount;
        zoomStart = System.currentTimeMillis();
        zoomUntil = zoomStart + ms;
    }

    /** Time of the latest heartbeat while low on health in a fight, or 0 to clear the red edges. */
    public void setHeartbeat(long at) {
        heartbeatAt = at;
    }

    /** Tapping the minimap opens the whole-floor map; any tap closes it. */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return mapExpanded;
        if (mapExpanded) {
            mapExpanded = false;
        } else {
            float dx = event.getX() - mapCx, dy = event.getY() - mapCy;
            if (inShopRoom || floorLayout == null || dx * dx + dy * dy > mapRadius * mapRadius) return false;
            mapExpanded = true;
        }
        performClick();
        invalidate();
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
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
        minimapBgPaint.setShader(new RadialGradient(mapCx, mapCy, mapRadius,
                Color.argb(220, 36, 30, 23), Color.argb(235, 10, 8, 7), Shader.TileMode.CLAMP));
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
        dangerPaint.setShader(new RadialGradient(w / 2f, h / 2f, radius,
                new int[]{0x00000000, 0x00000000, 0xD0B00010}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        layoutMinimap(w);
        if (w > 0 && h > 0) pixels = new PixelCanvas(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (player == null || camera == null || pixels == null) return;

        // The world goes through the pixel buffer; the vignette and HUD layers are drawn at full resolution
        long nowMs = System.currentTimeMillis();
        PixelCanvas world = pixels;
        int frame = world.begin();
        drawWorld(world, nowMs);
        world.restoreToCount(frame);
        world.present(canvas, blitPaint);

        if (vignettePaint.getShader() != null)
            canvas.drawRect(0, 0, getWidth(), getHeight(), vignettePaint);
        long beat = heartbeatAt;
        if (beat > 0 && dangerPaint.getShader() != null) {
            float throb = (float) Math.exp(-(nowMs - beat) / 220.0);
            dangerPaint.setAlpha((int) (255 * (0.45f + 0.55f * throb)));
            canvas.drawRect(0, 0, getWidth(), getHeight(), dangerPaint);
        }

        // 4. Minimap HUD overlay (always on top, hidden in shop), or the whole floor when tapped
        if (!inShopRoom) {
            if (mapExpanded && floorLayout != null) drawFullMap(canvas);
            else drawMinimap(canvas);
        }
        drawCoopStatus(canvas);
    }

    /** Every world layer, in world (screen) coordinates, inside the punch zoom. */
    private void drawWorld(Canvas canvas, long nowMs) {
        int world = canvas.save();
        if (nowMs < zoomUntil) {
            float t = (float) (nowMs - zoomStart) / Math.max(1L, zoomUntil - zoomStart);
            float z = 1f + zoomAmount * (float) Math.sin(Math.PI * t);
            canvas.scale(z, z, getWidth() / 2f, getHeight() / 2f);
        }
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
        if (effects != null && !inShopRoom) effects.drawFloor(canvas, camera);

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

        drawDarkness(canvas);
        if (effects != null) effects.draw(canvas, camera);
        if (!inShopRoom) drawEnemyArrows(canvas);
        canvas.restoreToCount(world);
    }

    /** Co-op: the teammate a fallen player is watching, or null. */
    public void setSpectating(String name) {
        this.spectating = name;
    }

    /**
     * Darkens the dungeon, then lights it back up around the player, teammates, torches, candles,
     * open portals and closed chests. The shop room keeps its own lighting.
     */
    private void drawDarkness(Canvas canvas) {
        if (floorLayout == null || inShopRoom || (shopRoom != null && shopRoom.isActive)) return;
        long now = System.currentTimeMillis();
        float offX = camera.getOffsetX(), offY = camera.getOffsetY();
        int layer = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawColor(ambientDark);

        lightHole(canvas, player.x - offX, player.y - offY, playerLight, 1f);
        if (coop != null && floorManager != null) {
            for (CoopSession.RemotePlayer r : coop.others.values()) {
                if (r.dead || r.floor != floorManager.getCurrentFloor()) continue;
                lightHole(canvas, r.body.x - offX, r.body.y - offY, 340f, 0.9f);
            }
        }
        for (Props.Decor d : floorLayout.decor) {
            if (d.kind == Props.TORCH) {
                Props.torchLight(d, lightSpot);
                float flick = 0.92f + 0.08f * (float) Math.sin(now / 90.0 + d.a * 1.7);
                lightHole(canvas, lightSpot[0] - offX, lightSpot[1] - offY, 360f * flick, 0.95f);
            } else if (d.kind == Props.CANDLES) {
                lightHole(canvas, d.x - offX, d.y - offY, 160f, 0.7f);
            } else if (d.kind == Props.BRAZIER) {
                lightHole(canvas, d.x - offX, d.y - offY, 380f, 0.95f);
            } else if (d.kind == Props.FOUNTAIN) {
                lightHole(canvas, d.x - offX, d.y - offY, d.used ? 180f : 300f, 0.8f);
            } else if ((d.kind == Props.SHRINE || d.kind == Props.LECTERN) && !d.used) {
                lightHole(canvas, d.x - offX, d.y - offY, 220f, 0.75f);
            } else if (d.kind == Props.RUNESTONE || d.kind == Props.RIFT) {
                lightHole(canvas, d.x - offX, d.y - offY, 150f, 0.6f);
            } else if (d.kind == Props.VENT) {
                long t = Props.ventTime(d, now);
                if (t >= Props.VENT_WARN) lightHole(canvas, d.x - offX, d.y - offY, t >= Props.VENT_BURST ? 300f : 200f, 0.85f);
            }
        }
        if (floorManager != null) {
            if (floorManager.isPortalOpen())
                lightHole(canvas, floorManager.getPortalX() - offX, floorManager.getPortalY() - offY, 300f, 0.9f);
            if (floorManager.isShopPortalOpen())
                lightHole(canvas, floorManager.getShopPortalX() - offX, floorManager.getShopPortalY() - offY, 300f, 0.9f);
        }
        if (treasureChests != null) {
            synchronized (treasureChests) {
                for (TreasureChest chest : treasureChests) {
                    if (!chest.opened) lightHole(canvas, chest.x - offX, chest.y - offY, 200f, 0.7f);
                }
            }
        }
        float[] gate = floorLayout.vaultGate;
        if (gate != null && floorLayout.vaultLocked)
            lightHole(canvas, (gate[0] + gate[2]) / 2f - offX, (gate[1] + gate[3]) / 2f - offY, 220f, 0.8f);
        gate = floorLayout.trialGate;
        if (gate != null && floorLayout.trialSealed)
            lightHole(canvas, (gate[0] + gate[2]) / 2f - offX, (gate[1] + gate[3]) / 2f - offY, 200f, 0.8f);
        canvas.restoreToCount(layer);
    }

    /** Erases a soft circle of darkness; strength 1 lights its centre fully. */
    private void lightHole(Canvas canvas, float sx, float sy, float radius, float strength) {
        if (sx < -radius || sy < -radius || sx > getWidth() + radius || sy > getHeight() + radius) return;
        canvas.save();
        canvas.translate(sx, sy);
        canvas.scale(radius, radius);
        lightHolePaint.setAlpha((int) (255 * strength));
        canvas.drawCircle(0, 0, 1f, lightHolePaint);
        canvas.restore();
    }

    /** With two or fewer enemies left, edge arrows point at the ones off screen. */
    private void drawEnemyArrows(Canvas canvas) {
        if (enemies == null || floorManager == null || floorManager.isPortalOpen()) return;
        synchronized (enemies) {
            int left = 0;
            for (Enemy e : enemies) if (!e.isDead && !e.summoned && !e.mimic) left++;
            if (left == 0 || left > 2) return;
            for (Enemy e : enemies) {
                if (!e.isDead && !e.summoned && !e.mimic) drawEdgeArrow(canvas, e.x, e.y);
            }
        }
    }

    /** Pulsing red triangle on the screen edge, pointing at an off-screen world point. */
    private void drawEdgeArrow(Canvas canvas, float worldX, float worldY) {
        float sx = worldX - camera.getOffsetX(), sy = worldY - camera.getOffsetY();
        int w = getWidth(), h = getHeight();
        float margin = 80f;
        if (sx > margin && sx < w - margin && sy > margin && sy < h - margin) return;
        float cx = w / 2f, cy = h / 2f;
        float angle = (float) Math.atan2(sy - cy, sx - cx);
        float reach = Math.min(cx, cy) - margin;
        float pulse = (float) (Math.sin(System.currentTimeMillis() / 300.0) * 0.25 + 0.75);
        canvas.save();
        canvas.translate(cx + (float) Math.cos(angle) * reach, cy + (float) Math.sin(angle) * reach);
        canvas.rotate((float) Math.toDegrees(angle));
        enemyArrowGlowPaint.setAlpha((int) (80 * pulse));
        canvas.drawCircle(0, 0, 36f, enemyArrowGlowPaint);
        enemyArrowPaint.setAlpha((int) (255 * pulse));
        canvas.drawPath(edgeArrowPath, enemyArrowPaint);
        canvas.restore();
    }

    /** One line under the minimap: party size, or what is wrong with the connection. */
    private void drawCoopStatus(Canvas canvas) {
        if (coop == null) return;
        long now = System.currentTimeMillis();
        coopStatusPaint.setColor(coop.isSyncHealthy(now) ? Color.rgb(170, 220, 170) : Color.rgb(255, 120, 90));
        canvas.drawText(coop.statusLine(now), mapCx + mapRadius, mapCy + mapRadius + 18f * density,
                coopStatusPaint);
        String watched = spectating;
        if (watched != null)
            canvas.drawText("Spectating " + watched + "  -  you return next floor",
                    getWidth() / 2f, getHeight() - 24f * density, spectatePaint);
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
        applyBiome(layout.biome);
        this.floorLayout = layout;
    }

    /** Paints the floors, walls, fire and darkness in the floor's biome. */
    private void applyBiome(Biome biome) {
        roomFloorPaint.setColor(biome.floor);
        corridorFloorPaint.setColor(biome.corridor);
        wallPaint.setColor(biome.wall);
        wallFacePaint.setColor(biome.wallFace);
        mortarPaint.setColor(biome.mortar);
        minimapRoomPaint.setColor(biome.mapRoom);
        minimapCorridorPaint.setColor(biome.mapCorridor);
        ambientDark = Color.argb(biome.darkness, 6, 5, 12);
        playerLight = biome == Biome.VOID ? 350f : 430f;
        props.setBiome(biome);
    }

    private void drawFloorLayout(Canvas canvas) {
        float offX = camera.getOffsetX();
        float offY = camera.getOffsetY();
        Shader stones = floorTexPaint.getShader();
        if (stones != null) {
            // Flagstones anchored to the world, one texture pixel per art pixel
            texMatrix.setScale(Sprites.PX, Sprites.PX);
            texMatrix.postTranslate(-offX, -offY);
            stones.setLocalMatrix(texMatrix);
        }

        // Corridors drawn first so room floors paint over any seam
        for (Corridor c : floorLayout.corridors) {
            float left = c.x - offX;
            float top  = c.y - offY;
            floor(canvas, left, top, left + c.width, top + c.height, corridorFloorPaint);
        }

        for (Room r : floorLayout.rooms) {
            float left   = r.x - offX;
            float top    = r.y - offY;
            float right  = left + r.width;
            float bottom = top + r.height;
            if (right < 0 || bottom < 0 || left > getWidth() || top > getHeight()) continue;
            floor(canvas, left, top, right, bottom, roomPaintFor(r));
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
                wallFace(canvas, left, right, top);
                wall(canvas, left, top,    right, top);
                wall(canvas, left, bottom, right, bottom);
            } else {
                // Vertical corridor — left and right walls only
                wall(canvas, left,  top, left,  bottom);
                wall(canvas, right, top, right, bottom);
            }
        }
        drawRoomBlocks(canvas, offX, offY);
        drawVaultGate(canvas, offX, offY);
        drawTrialGate(canvas, offX, offY);
        drawDecor(canvas);
    }

    /**
     * Shaped rooms' walled-off blocks (cut corners, a missing wing, columns): solid rock like the dark past the
     * walls, walled where they face the room, with a brick face on any side that faces south into it, as the
     * room's own north wall has.
     */
    private void drawRoomBlocks(Canvas canvas, float offX, float offY) {
        for (Room r : floorLayout.rooms) {
            for (int i = 0; i < r.blocks.size(); i++) {
                RectF b = r.blocks.get(i);
                float l = b.left - offX, t = b.top - offY, rt = b.right - offX, bt = b.bottom - offY;
                if (rt < -100 || bt < -WALL_FACE - 100 || l > getWidth() + 100 || t > getHeight() + 100) continue;
                // where the block meets the room's outer wall, reach over it so no stray wall shows in the rock
                boolean west = b.left <= r.x + 1, east = b.right >= r.x + r.width - 1;
                boolean north = b.top <= r.y + 1, south = b.bottom >= r.y + r.height - 1;
                canvas.drawRect(l - (west ? 16 : 0), t - (north ? 16 : 0), rt + (east ? 16 : 0), bt + (south ? 16 : 0), rockPaint);
                if (!south) {
                    wallFace(canvas, l, rt, bt);
                    wall(canvas, l, bt, rt, bt);
                }
                if (!north) wall(canvas, l, t, rt, t);
                if (!west) wall(canvas, l, t, l, bt);
                if (!east) wall(canvas, rt, t, rt, bt);
            }
        }
    }

    /** The Trial arena while its waves fight: an iron portcullis across the door, glowing crimson. */
    private void drawTrialGate(Canvas canvas, float offX, float offY) {
        float[] g = floorLayout.trialGate;
        if (g == null || !floorLayout.trialSealed) return;
        float x1 = g[0] - offX, y1 = g[1] - offY, x2 = g[2] - offX, y2 = g[3] - offY;
        float cx = (x1 + x2) / 2f, cy = (y1 + y2) / 2f;
        float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 250.0);
        gatePaint.setStyle(Paint.Style.FILL);
        gatePaint.setColor(Color.argb((int) (70 * pulse), 224, 72, 72));
        canvas.drawCircle(cx, cy, 80f, gatePaint);
        gatePaint.setStyle(Paint.Style.STROKE);
        gatePaint.setStrokeCap(Paint.Cap.ROUND);
        gatePaint.setStrokeWidth(22f);
        gatePaint.setColor(Color.rgb(28, 24, 22));
        canvas.drawLine(x1, y1, x2, y2, gatePaint);
        // Bars across the beam, each tipped with a spike
        boolean vertical = Math.abs(x2 - x1) < Math.abs(y2 - y1);
        gatePaint.setStrokeWidth(6f);
        for (int i = 1; i < 6; i++) {
            float t = i / 6f, bx = x1 + (x2 - x1) * t, by = y1 + (y2 - y1) * t;
            gatePaint.setColor(Color.rgb(60, 60, 66));
            if (vertical) canvas.drawLine(bx - 16f, by, bx + 16f, by, gatePaint);
            else canvas.drawLine(bx, by - 16f, bx, by + 16f, gatePaint);
            gatePaint.setColor(Color.rgb(224, 72, 72));
            canvas.drawPoint(bx, by, gatePaint);
        }
        gatePaint.setStrokeWidth(3f);
        gatePaint.setColor(Color.rgb(139, 24, 24));
        canvas.drawLine(x1, y1, x2, y2, gatePaint);
    }

    /** Locked vault: an iron beam with gold bar tops and a lock plate across the corridor mouth. */
    private void drawVaultGate(Canvas canvas, float offX, float offY) {
        float[] g = floorLayout.vaultGate;
        if (g == null || !floorLayout.vaultLocked) return;
        float x1 = g[0] - offX, y1 = g[1] - offY, x2 = g[2] - offX, y2 = g[3] - offY;
        float cx = (x1 + x2) / 2f, cy = (y1 + y2) / 2f;
        float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        gatePaint.setStyle(Paint.Style.FILL);
        gatePaint.setColor(Color.argb((int) (60 * pulse), 244, 215, 122));
        canvas.drawCircle(cx, cy, 70f, gatePaint);
        gatePaint.setStyle(Paint.Style.STROKE);
        gatePaint.setStrokeCap(Paint.Cap.ROUND);
        gatePaint.setStrokeWidth(26f);
        gatePaint.setColor(Color.rgb(28, 24, 22));
        canvas.drawLine(x1, y1, x2, y2, gatePaint);
        gatePaint.setStrokeWidth(4f);
        gatePaint.setColor(Color.rgb(139, 111, 44));
        canvas.drawLine(x1, y1, x2, y2, gatePaint);
        gatePaint.setStyle(Paint.Style.FILL);
        gatePaint.setColor(Color.rgb(212, 175, 55));
        for (int i = 1; i < 8; i++) {
            float t = i / 8f;
            canvas.drawCircle(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, 7f, gatePaint);
        }
        canvas.drawRoundRect(cx - 22f, cy - 26f, cx + 22f, cy + 26f, 7f, 7f, gatePaint);
        gatePaint.setColor(Color.rgb(28, 24, 22));
        canvas.drawCircle(cx, cy - 6f, 7f, gatePaint);
        canvas.drawRect(cx - 3f, cy - 4f, cx + 3f, cy + 12f, gatePaint);
    }

    /** Room decorations near the screen, in world space: torchlight first, then the props. */
    private void drawDecor(Canvas canvas) {
        List<Props.Decor> list = floorLayout.decor;
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

    /** A floor area in its colour: pixel flagstones tinted to it, or the old flat fill and tile grid. */
    private void floor(Canvas canvas, float left, float top, float right, float bottom, Paint colour) {
        if (floorTexPaint.getShader() == null) {
            canvas.drawRect(left, top, right, bottom, colour);
            drawTiles(canvas, left, top, right, bottom);
            return;
        }
        floorTexPaint.setColorFilter(tintFor(colour.getColor()));
        canvas.drawRect(left, top, right, bottom, floorTexPaint);
    }

    private android.graphics.ColorFilter tintFor(int color) {
        android.graphics.ColorFilter f = tints.get(color);
        if (f == null) {
            if (tints.size() > 64) tints.clear();
            tints.put(color, f = Sprites.tint(color));
        }
        return f;
    }

    /** The brick front of a wall along y, seen from slightly above; bricks are anchored to the world. */
    private void wallFace(Canvas canvas, float x1, float x2, float y) {
        if (x2 <= x1 || y > getHeight() || y + WALL_FACE < 0) return;
        Shader bricks = brickTexPaint.getShader();
        if (bricks != null) {
            // Pixel bricks, their courses starting at the top of this wall
            texMatrix.setScale(Sprites.PX, Sprites.PX);
            texMatrix.postTranslate(-camera.getOffsetX(), y);
            bricks.setLocalMatrix(texMatrix);
            brickTexPaint.setColorFilter(tintFor(wallFacePaint.getColor()));
            canvas.drawRect(x1, y, x2, y + WALL_FACE, brickTexPaint);
            canvas.drawRect(x1, y + WALL_FACE - 6f, x2, y + WALL_FACE, wallFootPaint);
            return;
        }
        float row = WALL_FACE / 2f, brick = 48f, offX = camera.getOffsetX();
        canvas.drawRect(x1, y, x2, y + WALL_FACE, wallFacePaint);
        canvas.drawLine(x1, y + row, x2, y + row, mortarPaint);
        for (int r = 0; r < 2; r++) {
            float shift = r * brick / 2f;
            float bx = (float) Math.floor((x1 + offX - shift) / brick) * brick - offX + shift;
            for (; bx < x2; bx += brick) {
                if (bx > x1) canvas.drawLine(bx, y + r * row, bx, y + (r + 1) * row, mortarPaint);
            }
        }
        canvas.drawRect(x1, y + WALL_FACE - 6f, x2, y + WALL_FACE, wallFootPaint);
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
        if (r.event != null) return eventFloorPaints[r.event.ordinal()];
        if (r == floorLayout.treasureRoom || r == floorLayout.vaultRoom) return treasureFloorPaint;
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
            wallFace(canvas, left, door - hw, top);
            wallFace(canvas, door + hw, right, top);
            wall(canvas, left, top, door - hw, top);
            wall(canvas, door + hw, top, right, top);
        } else {
            wallFace(canvas, left, right, top);
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

    /** Circular, player-centred, north-up minimap in a compass frame; tap it for the whole floor. */
    private void drawMinimap(Canvas canvas) {
        if (floorLayout == null || player == null) return;

        // World -> minimap: the player at the centre, MINIMAP_WORLD_RADIUS reaches the frame
        float scale = (mapRadius - 4f * density) / MINIMAP_WORLD_RADIUS;
        float ox = mapCx - player.x * scale;
        float oy = mapCy - player.y * scale;

        canvas.save();
        canvas.clipPath(minimapClip);
        canvas.drawCircle(mapCx, mapCy, mapRadius, minimapBgPaint);
        drawMapRooms(canvas, ox, oy, scale, 4f * density);

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

        // Teammates and the last enemies (ones in the shop fall outside the clip)
        drawMapUnits(canvas, ox, oy, scale, 3f * density);
        drawMapPlayer(canvas, mapCx, mapCy, 1f);
        canvas.restore();

        // Compass frame: bronze band with a gold line, ticks every 30 degrees, N E S W
        float band = mapRadius - 2f * density;
        canvas.drawCircle(mapCx, mapCy, band, minimapRimPaint);
        canvas.drawCircle(mapCx, mapCy, band, minimapBorderPaint);
        canvas.drawCircle(mapCx, mapCy, mapRadius - 4.5f * density, minimapHighlightPaint);
        if (minimapWear == null) minimapWear = new Wear.Stone(R.id.game_display, density, false, true);
        minimapWear.setBounds((int) (mapCx - mapRadius), (int) (mapCy - mapRadius),
                (int) (mapCx + mapRadius), (int) (mapCy + mapRadius));
        minimapWear.draw(canvas);
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            float inner = mapRadius - (i % 3 == 0 ? 9f : 7f) * density;
            mapTickPaint.setStrokeWidth((i % 3 == 0 ? 1.6f : 1f) * density);
            canvas.drawLine(mapCx + cos * inner, mapCy + sin * inner,
                    mapCx + cos * (mapRadius - 5f * density), mapCy + sin * (mapRadius - 5f * density), mapTickPaint);
        }
        float letter = mapRadius - 15f * density, lift = 2.5f * density;
        canvas.drawText("N", mapCx, mapCy - letter + 3f * density, minimapNorthPaint);
        canvas.drawText("S", mapCx, mapCy + letter + lift, mapLetterPaint);
        canvas.drawText("E", mapCx + letter, mapCy + lift, mapLetterPaint);
        canvas.drawText("W", mapCx - letter, mapCy + lift, mapLetterPaint);
    }

    /** Whole floor on one screen: every room seen so far, fitted and centred. Any tap closes it. */
    private void drawFullMap(Canvas canvas) {
        canvas.drawColor(Color.argb(225, 8, 6, 5));
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Room r : floorLayout.rooms) {
            if (!r.explored && !nextToExplored(r)) continue;
            minX = Math.min(minX, r.x);
            minY = Math.min(minY, r.y);
            maxX = Math.max(maxX, r.x + r.width);
            maxY = Math.max(maxY, r.y + r.height);
        }
        float w = getWidth(), h = getHeight();
        float top = 64f * density, pad = 24f * density;
        if (floorManager != null)
            canvas.drawText("FLOOR " + floorManager.getCurrentFloor() + "  ·  " + floorLayout.biome.title.toUpperCase(),
                    w / 2f, 30f * density, mapTitlePaint);
        canvas.drawText("Tap anywhere to close", w / 2f, 48f * density, mapHintPaint);
        if (minX > maxX) return;

        // Fit the seen rooms, but don't blow a small map up past a readable size
        float scale = Math.min(0.1f * density, Math.min((w - 2f * pad) / (maxX - minX), (h - top - pad) / (maxY - minY)));
        float ox = w / 2f - (minX + maxX) / 2f * scale;
        float oy = top + (h - top - pad) / 2f - (minY + maxY) / 2f * scale;
        drawMapRooms(canvas, ox, oy, scale, 8f * density);
        if (floorManager != null) {
            float pulse = (float) (Math.sin(System.currentTimeMillis() / 400.0) * 0.125 + 0.875);
            if (floorManager.isPortalOpen())
                drawMapDot(canvas, ox + floorManager.getPortalX() * scale, oy + floorManager.getPortalY() * scale,
                        pulse, minimapPortalPaint);
            if (floorManager.isShopPortalOpen())
                drawMapDot(canvas, ox + floorManager.getShopPortalX() * scale, oy + floorManager.getShopPortalY() * scale,
                        1f, minimapShopPaint);
        }
        drawMapUnits(canvas, ox, oy, scale, 4.5f * density);
        drawMapPlayer(canvas, ox + player.x * scale, oy + player.y * scale, 1.6f);
    }

    /** Map ink for both maps: seen corridors, explored rooms filled, door-seen rooms dashed, icons. */
    private void drawMapRooms(Canvas canvas, float ox, float oy, float scale, float icon) {
        Room here = floorLayout.getRoomAt(player.x, player.y);
        long now = System.currentTimeMillis();
        for (Corridor c : floorLayout.corridors) {
            Room a = minimapRoom(c.roomIdA), b = minimapRoom(c.roomIdB);
            if ((a == null || !a.explored) && (b == null || !b.explored)) continue;
            canvas.drawRect(ox + c.x * scale, oy + c.y * scale,
                    ox + (c.x + c.width) * scale, oy + (c.y + c.height) * scale, minimapCorridorPaint);
        }
        float corner = 2f * density;
        for (Room r : floorLayout.rooms) {
            if (!r.explored && !nextToExplored(r)) continue;
            mapRect.set(ox + r.x * scale, oy + r.y * scale, ox + (r.x + r.width) * scale, oy + (r.y + r.height) * scale);
            if (r.explored) {
                canvas.drawRoundRect(mapRect, corner, corner, minimapPaintFor(r));
                canvas.drawRoundRect(mapRect, corner, corner, r == here ? mapHerePaint : mapStrokePaint);
            } else {
                canvas.drawRoundRect(mapRect, corner, corner, mapUnknownPaint);
            }
            // a shaped room shows its shape: its walled-off blocks are cut back out of it
            for (RectF b : r.blocks)
                canvas.drawRect(ox + b.left * scale, oy + b.top * scale, ox + b.right * scale, oy + b.bottom * scale, mapRockPaint);
            drawRoomIcon(canvas, r, mapRect.centerX(), mapRect.centerY(), icon, now);
        }
    }

    /** True when a corridor joins this room to an explored one, so its door has been seen. */
    private boolean nextToExplored(Room r) {
        for (Corridor c : floorLayout.corridors) {
            int other = c.roomIdA == r.id ? c.roomIdB : c.roomIdB == r.id ? c.roomIdA : -1;
            Room o = other >= 0 ? minimapRoom(other) : null;
            if (o != null && o.explored) return true;
        }
        return false;
    }

    /** Glyphs for special rooms: lock (vault), chest (treasure), skull (boss), portal ring (exit). */
    private void drawRoomIcon(Canvas canvas, Room r, float cx, float cy, float s, long now) {
        int ink = Color.rgb(20, 16, 12);
        mapIconPaint.setStyle(Paint.Style.FILL);
        if (r.event != null) {
            drawEventIcon(canvas, r, cx, cy, s, ink);
        } else if (r == floorLayout.vaultRoom) {
            float lift = floorLayout.vaultLocked ? 0f : s * 0.4f; // an open lock's shackle pops up
            mapIconPaint.setColor(Color.rgb(244, 215, 122));
            mapIconPaint.setStyle(Paint.Style.STROKE);
            mapIconPaint.setStrokeWidth(s * 0.28f);
            iconRect.set(cx - s * 0.45f, cy - s * 1.05f - lift, cx + s * 0.45f, cy - lift);
            canvas.drawArc(iconRect, 180f, 180f, false, mapIconPaint);
            mapIconPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(cx - s * 0.75f, cy - s * 0.5f, cx + s * 0.75f, cy + s * 0.75f, mapIconPaint);
            mapIconPaint.setColor(ink);
            canvas.drawCircle(cx, cy + s * 0.1f, s * 0.2f, mapIconPaint);
        } else if (r == floorLayout.treasureRoom) {
            mapIconPaint.setColor(Color.rgb(184, 145, 30));
            canvas.drawRect(cx - s, cy - s * 0.35f, cx + s, cy + s * 0.7f, mapIconPaint);
            mapIconPaint.setColor(Color.rgb(244, 215, 122));
            canvas.drawRect(cx - s, cy - s * 0.8f, cx + s, cy - s * 0.25f, mapIconPaint);
            mapIconPaint.setColor(ink);
            canvas.drawRect(cx - s * 0.18f, cy - s * 0.4f, cx + s * 0.18f, cy + s * 0.1f, mapIconPaint);
        } else if (r.type == Room.Type.BOSS) {
            mapIconPaint.setColor(Color.rgb(224, 72, 72));
            canvas.drawCircle(cx, cy - s * 0.2f, s * 0.85f, mapIconPaint);
            canvas.drawRect(cx - s * 0.5f, cy + s * 0.3f, cx + s * 0.5f, cy + s * 0.85f, mapIconPaint);
            mapIconPaint.setColor(ink);
            canvas.drawCircle(cx - s * 0.33f, cy - s * 0.2f, s * 0.24f, mapIconPaint);
            canvas.drawCircle(cx + s * 0.33f, cy - s * 0.2f, s * 0.24f, mapIconPaint);
        } else if (r.type == Room.Type.EXIT) {
            float pulse = 0.9f + 0.1f * (float) Math.sin(now / 300.0);
            mapIconPaint.setColor(Color.rgb(181, 140, 224));
            mapIconPaint.setStyle(Paint.Style.STROKE);
            mapIconPaint.setStrokeWidth(s * 0.25f);
            canvas.drawCircle(cx, cy, s * 0.8f * pulse, mapIconPaint);
            mapIconPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, s * 0.25f, mapIconPaint);
        }
    }

    /**
     * Event-room glyphs in the event's colour, dimmed once it's over: a blood drop (shrine), a coin (gambler),
     * a chest (cursed crypt), crossed blades (trial), a shackle (prison), a book (library), a basin (spring).
     */
    private void drawEventIcon(Canvas canvas, Room r, float cx, float cy, float s, int ink) {
        mapIconPaint.setColor(r.event.mapColor);
        mapIconPaint.setAlpha(r.eventDone ? 110 : 255);
        switch (r.event) {
            case SHRINE:
                canvas.drawCircle(cx, cy + s * 0.3f, s * 0.55f, mapIconPaint);
                iconPath.reset();
                iconPath.moveTo(cx, cy - s);
                iconPath.lineTo(cx + s * 0.5f, cy + s * 0.15f);
                iconPath.lineTo(cx - s * 0.5f, cy + s * 0.15f);
                iconPath.close();
                canvas.drawPath(iconPath, mapIconPaint);
                break;
            case GAMBLER:
                canvas.drawCircle(cx, cy, s * 0.8f, mapIconPaint);
                mapIconPaint.setColor(ink);
                canvas.drawRect(cx - s * 0.12f, cy - s * 0.45f, cx + s * 0.12f, cy + s * 0.45f, mapIconPaint);
                break;
            case CURSED:
                canvas.drawRect(cx - s, cy - s * 0.35f, cx + s, cy + s * 0.7f, mapIconPaint);
                canvas.drawRect(cx - s, cy - s * 0.8f, cx + s, cy - s * 0.25f, mapIconPaint);
                mapIconPaint.setColor(ink);
                canvas.drawRect(cx - s * 0.18f, cy - s * 0.4f, cx + s * 0.18f, cy + s * 0.1f, mapIconPaint);
                break;
            case TRIAL:
                mapIconPaint.setStyle(Paint.Style.STROKE);
                mapIconPaint.setStrokeWidth(s * 0.28f);
                canvas.drawLine(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.8f, mapIconPaint);
                canvas.drawLine(cx + s * 0.8f, cy - s * 0.8f, cx - s * 0.8f, cy + s * 0.8f, mapIconPaint);
                mapIconPaint.setStyle(Paint.Style.FILL);
                break;
            case PRISONER:
                mapIconPaint.setStyle(Paint.Style.STROKE);
                mapIconPaint.setStrokeWidth(s * 0.25f);
                canvas.drawCircle(cx, cy - s * 0.15f, s * 0.55f, mapIconPaint);
                mapIconPaint.setStyle(Paint.Style.FILL);
                canvas.drawRect(cx - s * 0.2f, cy + s * 0.35f, cx + s * 0.2f, cy + s * 0.9f, mapIconPaint);
                break;
            case LIBRARY:
                canvas.drawRect(cx - s * 0.9f, cy - s * 0.6f, cx - s * 0.06f, cy + s * 0.6f, mapIconPaint);
                canvas.drawRect(cx + s * 0.06f, cy - s * 0.6f, cx + s * 0.9f, cy + s * 0.6f, mapIconPaint);
                break;
            case FOUNTAIN:
                mapIconPaint.setStyle(Paint.Style.STROKE);
                mapIconPaint.setStrokeWidth(s * 0.25f);
                canvas.drawCircle(cx, cy, s * 0.8f, mapIconPaint);
                mapIconPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, s * 0.3f, mapIconPaint);
                break;
        }
    }

    /** Teammates as coloured dots; with two or fewer enemies left, those as pulsing red dots. */
    private void drawMapUnits(Canvas canvas, float ox, float oy, float scale, float dot) {
        if (floorManager == null) return;
        if (coop != null) {
            for (CoopSession.RemotePlayer r : coop.others.values()) {
                if (r.dead || r.floor != floorManager.getCurrentFloor()) continue;
                allyDotPaint.setColor(r.color);
                canvas.drawCircle(ox + r.body.x * scale, oy + r.body.y * scale, dot, allyDotPaint);
            }
        }
        if (enemies == null || floorManager.isPortalOpen()) return;
        synchronized (enemies) {
            int left = 0;
            for (Enemy e : enemies) if (!e.isDead && !e.summoned && !e.mimic) left++;
            if (left == 0 || left > 2) return;
            float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 250.0);
            mapEnemyPaint.setAlpha((int) (255 * pulse));
            for (Enemy e : enemies) {
                if (!e.isDead && !e.summoned && !e.mimic)
                    canvas.drawCircle(ox + e.x * scale, oy + e.y * scale, dot * pulse, mapEnemyPaint);
            }
        }
    }

    /** The player's arrow along the facing angle, in a soft gold halo. */
    private void drawMapPlayer(Canvas canvas, float x, float y, float size) {
        canvas.save();
        canvas.translate(x, y);
        canvas.drawCircle(0, 0, 8f * density * size, mapGlowPaint);
        canvas.rotate(player.getFacingAngle());
        canvas.scale(size, size);
        canvas.drawPath(minimapArrow, minimapPlayerPaint);
        canvas.restore();
    }

    /** A glowing dot, e.g. an open portal on the full map. */
    private void drawMapDot(Canvas canvas, float x, float y, float pulse, Paint paint) {
        paint.setAlpha((int) (90 * pulse));
        canvas.drawCircle(x, y, 7f * density * pulse, paint);
        paint.setAlpha(255);
        canvas.drawCircle(x, y, 3.5f * density, paint);
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
        if (r.event != null) return eventMapPaints[r.event.ordinal()];
        if (r == floorLayout.treasureRoom || r == floorLayout.vaultRoom) return minimapTreasurePaint;
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