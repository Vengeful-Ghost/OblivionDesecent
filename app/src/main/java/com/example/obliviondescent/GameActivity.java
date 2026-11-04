package com.example.obliviondescent;

import java.util.Map;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Main game activity — the core engine of Oblivion Descent.
 * Manages two threads: a logic thread (~60 fps) and a render thread (~60 fps).
 * Responsible for:
 * - Game loop (player movement, enemy AI, collisions, combat)
 * - Floor transitions, the shop system, treasure chests
 * - Save/load via Firestore
 * - Batch content generation via Gemini AI
 * - Sound, vibration and HUD management
 */
public class GameActivity extends AppCompatActivity implements CoopSession.Listener {

    /* ============================================================
       UI ELEMENTS
       ============================================================ */
    private ProgressBar playerHealthBar;
    private ProgressBar playerArmorBar;
    private TextView txtHealthValue;
    private TextView txtArmorValue;
    private GameDisplay gameDisplay;
    private JoystickView joystick;

    private MaterialButton btnAttack;
    private MaterialButton btnInventory;
    private MaterialButton menuBtn;
    private MaterialButton btnDash;
    private AimJoystickView aimJoystick;
    private MaterialButton btnPotion;
    private TextView txtPotionCount;
    private TextView txtLevel;

    /* ============================================================
       GAME ENGINE OBJECTS
       ============================================================ */
    private GameCamera camera;
    private Player player;
    private List<Enemy> enemies = Collections.synchronizedList(new ArrayList<>());

    private List<Weapon> weapons = new ArrayList<>();
    private List<Projectile> projectiles = Collections.synchronizedList(new ArrayList<>());
    private final GameEffects effects = new GameEffects();
    private static final int HOSTILE_SHOT_COLOR = Color.rgb(255, 110, 70);

    /* ============================================================
       GAME STATE
       ============================================================ */
    private boolean layoutReady = false;
    private int screenWidth = 0;
    private int screenHeight = 0;
    private volatile boolean isGamePaused = false;
    private volatile boolean threadsRunning = true;

    private boolean showMenuOnResume = false; // re-open menu after returning from Settings

    // Player Stats
    private int playerHealth = 100;
    private int maxHealth = 100;
    private int playerLevel = 1;
    private int playerXp = 0;
    private int playerArmor = 100;
    private int oldHealth = 0;
    private int oldArmor = 0;

    // Combat
    private boolean isAttacking = false;
    private long attackStartTime = 0;
    private long lastAttackTime = 0;
    private final long ATTACK_DURATION = 150;
    private final long ATTACK_COOLDOWN = 400;
    // No damage is taken until this time: set by dashing and briefly after every hit
    private long invulnerableUntil = 0;
    private static final long HIT_INVULNERABILITY = 300;
    // Melee combo: quick swings chain 1-2-3; the third is a wide, heavy finisher
    private int comboStep = 0;
    private float swingArc = 55f;
    private static final long COMBO_WINDOW = 700;
    private static final float CRIT_CHANCE = 0.12f;
    // Spin: special attack around the player, queued from the UI and resolved on the logic thread
    private static final long SPIN_COOLDOWN = 6000;
    private static final long AIM_GRACE = 90; // ms a fresh touch waits for a drag before auto-aiming
    private long lastSpinTime = 0;
    private volatile boolean spinQueued = false;

    private int   checkpointFloor  = 1;
    private int   checkpointHealth = 100;
    private int   checkpointArmor  = 0;
    private int   checkpointGold   = 0;

    private float aimDX = 1f;
    private float aimDY = 0f;

    // Floor system
    private FloorManager floorManager;
    private FloorLayout  currentLayout;
    private boolean isTransitioning = false;

    // UI references for floor HUD
    private TextView txtFloorNumber;
    private TextView txtKillProgress;
    private FrameLayout floorTransitionOverlay;
    private TextView txtFloorTitle;

    // Boss intro card
    private View bossIntro, bossBand, bossLineTop, bossLineBottom;
    private TextView bossLabel, bossName, bossTitle;

    private boolean enemiesKilledOnPortalOpen = false;

    // Dash
    private final long DASH_DURATION = 120;
    private final long DASH_COOLDOWN = 1500;
    private final float DASH_POWER = 28f;
    private boolean isDashing = false;
    private long dashStartTime = 0;
    private long lastDashTime = 0;
    private float dashDX = 0f;
    private float dashDY = 0f;

    // Stats
    private StatsManager statsManager;
    private DBManager dbManager;
    private String userId;
    private String gameId; // loaded from SharedPreferences or created fresh

    private long gameStartTime = System.currentTimeMillis();

    // Gold
    private int playerGold = 0;
    private List<GoldCoin> goldCoins = Collections.synchronizedList(new ArrayList<>());

    private TextView txtGold;

    private Enemy activeBoss = null;
    private boolean bossSpawnHandled = false;

    private List<TreasureChest> treasureChests = Collections.synchronizedList(new ArrayList<>());

    private boolean enteringShop = false;

    private ShopRoom shopRoom = new ShopRoom();
    private boolean inShopRoom = false;

    private boolean shopDialogOpen = false;

    // Tracks Firestore document IDs for purchased items
    private List<String> inventoryWeaponIds = new ArrayList<>();
    private List<String> inventoryArmorIds  = new ArrayList<>();

    private long lastShopCloseTime = 0;
    private static final long SHOP_REOPEN_COOLDOWN = 2000; // 2 seconds

    private java.util.EnumMap<Armor.Slot, Armor> equippedArmor = new java.util.EnumMap<>(Armor.Slot.class);
    private java.util.Map<Armor, String> armorFirestoreIds = new java.util.HashMap<>();
    private java.util.Map<Weapon, String> weaponFirestoreIds = new java.util.HashMap<>();
    private int maxArmor = 0;
    private List<ConsumableStack> consumableInventory = new ArrayList<>();
    private List<Armor> inventoryArmors = new ArrayList<>();
    private String currentInventoryFilter = "ALL";
    private String inventorySort = "Type";
    private int inventoryPage = 0;
    private static final String[] SORT_KEYS = {"Type", "Rarity", "Damage", "Defense", "Durability", "Healing", "Name"};
    private static final float[] SELL_RARITY = {1f, 1.6f, 2.4f, 4f}; // by rarity tier

    private BatchGenerator batchGenerator = new BatchGenerator();

    // Cache: key = rangeStart (1, 6, 11 ...), value = result. Written from network threads.
    private final java.util.Map<Integer, BatchResult> batchCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    // Ranges currently being fetched, and failed attempts per range
    private final Set<Integer> batchesInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<Integer, Integer> batchFailures =
            new java.util.concurrent.ConcurrentHashMap<>();

    // Firestore reads still in flight while restoring a save; saving before they finish would drop gear
    private int pendingLoads = 0;

    // Current boss identity
    private String currentBossName  = null;
    private String currentBossTitle = null;
    private int    currentBossColor = android.graphics.Color.RED;

    private PlayerBehaviorTracker behaviorTracker = new PlayerBehaviorTracker();

    private SoundManager soundManager;

    private Set<Integer> shopBoughtIndices = new HashSet<>();

    // Tracks which enemies were already hit during the current melee swing so that
    // each enemy can only be damaged once per swing regardless of frame rate.
    private final Set<Enemy> hitThisSwing = new HashSet<>();
    private long lastKnownSwingTime = -1;

    private DashCooldownView dashCooldownView;
    private DashCooldownView spinCooldownView;

    // Co-op (null in solo): the host simulates enemies and floors, guests mirror them
    private CoopSession coop;
    private String username = "";
    private int coopTicks = 0;
    private int swingCount = 0;          // other phones draw a swing whenever this changes
    private float lookAngle = 0f;
    private boolean portalRequested = false;
    private boolean coopEnded = false;
    private String appliedSnapshot = null;
    private final Set<Integer> deadEnemyIds = new HashSet<>(); // guest: deaths already shown this floor
    private final float[] target = new float[2];
    private Dialog shopDialog;

    // Last HUD values sent to the UI thread; posting every tick floods it and delays Firebase callbacks
    private int dashStepShown = -1, spinStepShown = -1;
    private volatile String floorHudShown = "";

    /**
     * Initialises the game activity: UI, camera, player, floor manager, AI batch,
     * loads a saved game if one exists, and starts the logic and render threads.
     * @param savedInstanceState previously saved instance state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        hideSystemUI();

        // Keep the HUD clear of the camera cutout; the world itself still fills the screen
        View hudLayer = findViewById(R.id.hud_layer);
        ViewCompat.setOnApplyWindowInsetsListener(hudLayer, (v, insets) -> {
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            v.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom);
            gameDisplay.setSafeInsets(cutout.top, cutout.right);
            return insets;
        });

        soundManager = new SoundManager(this);
        // Restore saved volume
        SharedPreferences soundPrefs = getSharedPreferences("gameSettings", MODE_PRIVATE);
        float savedVol = soundPrefs.getFloat("sfx_volume", 1.0f);
        soundManager.setVolume(savedVol);

        weapons.add(new Weapon("Fists", 7, 30f, 250,
                Weapon.Rarity.COMMON, Color.WHITE, 0.03f));

        playerHealthBar = findViewById(R.id.healthBar);
        playerArmorBar  = findViewById(R.id.armorBar);
        txtHealthValue  = findViewById(R.id.txt_health_value);
        txtArmorValue   = findViewById(R.id.txt_armor_value);
        gameDisplay = findViewById(R.id.game_display);
        joystick = findViewById(R.id.joystick);
        btnAttack = findViewById(R.id.btn_attack);
        btnInventory = findViewById(R.id.btn_inventory);
        menuBtn = findViewById(R.id.btn_menu);
        btnDash = findViewById(R.id.btn_dash);
        txtGold = findViewById(R.id.txt_gold);
        dashCooldownView = findViewById(R.id.dash_cooldown_view);
        spinCooldownView = findViewById(R.id.spin_cooldown_view);
        btnPotion = findViewById(R.id.btn_potion);
        txtPotionCount = findViewById(R.id.txt_potion_count);
        txtLevel = findViewById(R.id.txt_level);
        gameDisplay.setEffects(effects);
        effects.setFont(getResources().getFont(R.font.cinzel));
        shopRoom.setFont(getResources().getFont(R.font.cinzel));

        player = new Player();
        player.equipWeapon(weapons.get(0));

        String coopCode = getIntent().getStringExtra(CoopSession.EXTRA_CODE);
        if (coopCode != null) {
            coop = new CoopSession(coopCode,
                    getSharedPreferences("loginPrefs", MODE_PRIVATE).getString("user_id", ""),
                    getIntent().getBooleanExtra(CoopSession.EXTRA_HOST, false),
                    getIntent().getLongExtra(CoopSession.EXTRA_SEED, 0L),
                    getIntent().getIntExtra(CoopSession.EXTRA_SLOT, 0));
            player.setColor(CoopSession.SLOT_COLORS[coop.slot & 3]);
        }

        floorManager = new FloorManager();

        // Fire batch for floors 1-5 (floor 1 now included so it has shop items + named enemies)
        generateBatch(1, 5);

        // Start floor 1 immediately with generic placeholder enemies while the batch loads.
        // When the batch arrives, applyBatchToCurrentFloorIfReady() will re-spawn with
        // AI templates and the shop will auto-populate via pollForBatchThenPopulate().

        currentLayout = generateLayout();
        gameDisplay.setFloorLayout(currentLayout);
        player.x = currentLayout.entryRoom.centerX();
        player.y = currentLayout.entryRoom.centerY();
        currentLayout.entryRoom.explored = true;
        if (currentLayout.exitRoom != null) {
            floorManager.setExitRoomCenter(
                    currentLayout.exitRoom.centerX(),
                    currentLayout.exitRoom.centerY());
        }
        spawnEnemiesForLayout(currentLayout);
        Log.d("FloorLayout", "Floor 1 layout ready: "
                + currentLayout.rooms.size() + " rooms");

        dbManager = new DBManager();
        SharedPreferences loginPrefs = getSharedPreferences("loginPrefs", MODE_PRIVATE);
        userId = loginPrefs.getString("user_id", null);
        username = loginPrefs.getString("username", "Player");
        gameId = getIntent().getStringExtra("game_id");
        int slotIndex = getIntent().getIntExtra("slot_index", 1);

        statsManager = new StatsManager(dbManager, userId, gameId);

        if (gameId == null && userId != null && coop == null) {
            // New game — create Firestore save, get back the ID
            dbManager.createGameSave(userId, slotIndex, newId -> {
                gameId = newId;
            });
        }
        // If gameId != null, loadGameFromFirestore() is called after layout is ready (see below)

        txtFloorNumber        = findViewById(R.id.txt_floor_number);
        txtKillProgress       = findViewById(R.id.txt_kill_progress);
        floorTransitionOverlay = findViewById(R.id.floor_transition_overlay);
        txtFloorTitle         = findViewById(R.id.txt_floor_title);

        bossIntro      = findViewById(R.id.boss_intro);
        bossBand       = findViewById(R.id.boss_band);
        bossLineTop    = findViewById(R.id.boss_line_top);
        bossLineBottom = findViewById(R.id.boss_line_bottom);
        bossLabel      = findViewById(R.id.boss_label);
        bossName       = findViewById(R.id.boss_name);
        bossTitle      = findViewById(R.id.boss_title);
        // Dark band that fades out at its top and bottom edges
        bossBand.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xE6000000, 0xE6000000, 0x00000000}));

        gameDisplay.setFloorManager(floorManager);

        updateFloorHUD();

        gameDisplay.post(() -> {
            screenWidth = gameDisplay.getWidth();
            screenHeight = gameDisplay.getHeight();

            camera = new GameCamera(screenWidth, screenHeight);

            gameDisplay.setGameData(player, enemies, camera, projectiles,
                    goldCoins, treasureChests, shopRoom);
            layoutReady = true;
            if (gameId != null && getIntent().hasExtra("game_id")) {
                loadGameFromFirestore();
            }
        });

        btnAttack.setOnClickListener(v -> attack());
        btnDash.setOnClickListener(v -> dash());
        findViewById(R.id.btn_spin).setOnClickListener(v -> {
            if (System.currentTimeMillis() - lastSpinTime >= SPIN_COOLDOWN) spinQueued = true;
        });
        btnPotion.setOnClickListener(v -> quickHeal());
        menuBtn.setOnClickListener(v -> showMenuDialog());
        btnInventory.setOnClickListener(v -> showInventoryDialog());

        // Held down = keep attacking; polled by the logic thread in tick()
        aimJoystick = findViewById(R.id.aim_joystick);

        playerArmor = 0;
        maxArmor = 0;
        playerArmorBar.setMax(1);
        playerArmorBar.setProgress(0);

        refreshAttackUI();
        updateLevelHUD();
        updatePotionButton();

        if (coop != null) {
            gameDisplay.setCoop(coop);
            coop.start(this);
        }

        startLogicThread();
        startRenderThread();
    }

    private boolean isGuest() {
        return coop != null && !coop.isHost();
    }

    /** Layout for the current floor; co-op seeds it so every phone builds the same dungeon. */
    private FloorLayout generateLayout() {
        long seed = coop != null ? coop.seed * 31 + floorManager.getCurrentFloor() : System.nanoTime();
        return FloorLayout.generate(floorManager.getCurrentFloor(), floorManager.isBossFloor(),
                floorManager.getKillsRequired(), seed);
    }

    /* ============================================================
       CO-OP — the host runs the world, guests mirror it (see CoopSession)
       ============================================================ */

    /** One tick of co-op: exchange state, mirror or publish enemies, follow the host's floor. */
    private void coopTick(long now) {
        int floor = floorManager.getCurrentFloor();
        for (CoopSession.RemotePlayer r : coop.others.values()) {
            r.follow();
            if (r.swing != r.shownSwing) {
                r.shownSwing = r.swing;
                if (r.floor == floor && !r.dead) showRemoteSwing(r);
            }
        }

        if (coop.isHost()) {
            applyCoopHits();
            if (!isTransitioning && floorManager.isPortalOpen() && coop.anyPortalRequest(floor))
                startFloorTransition();
            if (playerHealth <= 0 && !coopEnded && coop.allOthersDown(floor)) {
                coopEnded = true;
                coop.publishState("over");
            }
        } else {
            applyEnemySnapshot(now);
            applyCoopEvents(now);
            if (coop.hostFloor > floor && !isTransitioning) startFloorTransition();
        }

        coopTicks++;
        if (coopTicks % 5 == 0) publishSelf(floor);
        if (coop.isHost() && coopTicks % 6 == 0) publishEnemies(floor, now);
    }

    private void publishSelf(int floor) {
        Weapon w = player.getWeapon();
        int flags = (playerHealth <= 0 ? CoopSession.FLAG_DEAD : 0)
                | (portalRequested ? CoopSession.FLAG_PORTAL : 0)
                | (w.isProjectile ? CoopSession.FLAG_RANGED : w.range >= 40f ? CoopSession.FLAG_BLADE : 0);
        coop.publishSelf(player.x, player.y, lookAngle, playerHealth, maxHealth, flags,
                swingCount, floor, w.color, username);
    }

    /** Host: "floor,kills" followed by one entry per enemy (see Enemy.appendState). */
    private void publishEnemies(int floor, long now) {
        StringBuilder sb = new StringBuilder().append(floor).append(',').append(floorManager.getKillsThisFloor());
        synchronized (enemies) {
            for (Enemy e : enemies) {
                sb.append(';');
                e.appendState(sb, now);
            }
        }
        coop.publishEnemies(sb.toString());
    }

    /** Guest: copies the host's latest enemy snapshot onto local stand-ins. */
    private void applyEnemySnapshot(long now) {
        String snap = coop.enemySnapshot;
        if (snap == null || snap.equals(appliedSnapshot)) return;
        appliedSnapshot = snap;
        try {
            String[] parts = snap.split(";");
            String[] head = parts[0].split(",");
            if (Integer.parseInt(head[0]) != floorManager.getCurrentFloor()) return;
            floorManager.syncKills(Integer.parseInt(head[1]), player.x, player.y);

            Set<Integer> seen = new HashSet<>();
            synchronized (enemies) {
                for (int i = 1; i < parts.length; i++) {
                    String[] f = parts[i].split(",");
                    int id = Integer.parseInt(f[0]);
                    seen.add(id);
                    if (deadEnemyIds.contains(id)) continue;
                    Enemy e = findEnemy(id);
                    if (e == null) {
                        if (Integer.parseInt(f[5]) <= 0) continue; // died before this phone saw it
                        e = createMirror(id, Enemy.Type.values()[Integer.parseInt(f[1])], Integer.parseInt(f[2]));
                        enemies.add(e);
                    }
                    e.applyState(f, now);
                    if (e.isDead) deadEnemyIds.add(id);
                }
                // Removed by the host without dying, e.g. re-spawned once the AI templates arrived
                enemies.removeIf(e -> !e.isDead && !seen.contains(e.id));
            }
        } catch (RuntimeException ex) {
            Log.e("Coop", "Bad enemy snapshot", ex);
        }
    }

    /** Guest: a local stand-in for one of the host's enemies; it never runs AI. */
    private Enemy createMirror(int id, Enemy.Type type, int template) {
        Enemy e = new Enemy(0f, 0f, type);
        e.id = id;
        e.isAwake = true;
        List<EnemyTemplate> pool = getEnemyPoolForFloor(floorManager.getCurrentFloor());
        if (template >= 0 && template < pool.size()) e.applyTemplate(pool.get(template));
        if (type == Enemy.Type.BOSS) {
            loadBossIdentity();
            e.displayName  = currentBossName;
            e.displayColor = currentBossColor;
            activeBoss = e;
        }
        return e;
    }

    /** Must be called while holding the enemies lock. */
    private Enemy findEnemy(int id) {
        for (Enemy e : enemies) if (e.id == id) return e;
        return null;
    }

    /** Guest: replays the host's enemy strikes and shots against this phone's player. */
    private void applyCoopEvents(long now) {
        CoopSession.Received event;
        while ((event = coop.events.poll()) != null) {
            if (now - event.at > 1000) continue; // arrived during a floor change
            try {
                String[] f = event.data.split(",");
                if (Integer.parseInt(f[1]) != floorManager.getCurrentFloor()) continue;
                float x = Float.parseFloat(f[2]);
                float y = Float.parseFloat(f[3]);
                float dirX = Float.parseFloat(f[4]);
                float dirY = Float.parseFloat(f[5]);
                if ("p".equals(f[0])) {
                    spawnHostileShot(x, y, dirX, dirY, Float.parseFloat(f[6]), Float.parseFloat(f[7]),
                            Integer.parseInt(f[8]), Float.parseFloat(f[9]));
                } else {
                    resolveStrike(x, y, dirX, dirY, Float.parseFloat(f[6]), Float.parseFloat(f[7]),
                            Integer.parseInt(f[8]), Float.parseFloat(f[9]), Float.parseFloat(f[10]),
                            "1".equals(f[11]), now);
                }
            } catch (RuntimeException ex) {
                Log.e("Coop", "Bad event: " + event.data);
            }
        }
    }

    /** Host: applies the damage guests dealt ("uid,enemyId,damage,pushX,pushY,stagger"). */
    private void applyCoopHits() {
        String hit;
        while ((hit = coop.hits.poll()) != null) {
            try {
                String[] f = hit.split(",");
                synchronized (enemies) {
                    Enemy e = findEnemy(Integer.parseInt(f[1]));
                    if (e == null || e.isDead) continue;
                    int damage = Math.max(0, Integer.parseInt(f[2]));
                    e.takeDamage(damage);
                    e.knockbackVX = Float.parseFloat(f[3]);
                    e.knockbackVY = Float.parseFloat(f[4]);
                    showEnemyHit(e, damage, false);
                    if ("1".equals(f[5])) staggerEnemy(e);
                }
            } catch (RuntimeException ex) {
                Log.e("Coop", "Bad hit: " + hit);
            }
        }
    }

    /** A teammate attacked: draw their swing, or a flash at the staff tip for shots. */
    private void showRemoteSwing(CoopSession.RemotePlayer r) {
        if (r.ranged) {
            double a = Math.toRadians(r.angle);
            float reach = r.body.radius + 34f;
            effects.burst(r.body.x + (float) Math.cos(a) * reach, r.body.y + (float) Math.sin(a) * reach,
                    r.weaponColor, 6, 5f);
        } else {
            effects.slash(r.body.x, r.body.y, r.angle, r.body.radius + 60f, 55f, Color.WHITE);
        }
    }

    /** Puts the closest living player on this floor into target; false when nobody is left standing. */
    private boolean nearestTarget(Enemy e) {
        float best = Float.MAX_VALUE;
        if (playerHealth > 0) {
            best = (player.x - e.x) * (player.x - e.x) + (player.y - e.y) * (player.y - e.y);
            target[0] = player.x;
            target[1] = player.y;
        }
        if (coop == null) return best < Float.MAX_VALUE;
        int floor = floorManager.getCurrentFloor();
        for (CoopSession.RemotePlayer r : coop.others.values()) {
            if (r.dead || r.floor != floor) continue;
            float dx = r.body.x - e.x;
            float dy = r.body.y - e.y;
            if (dx * dx + dy * dy < best) {
                best = dx * dx + dy * dy;
                target[0] = r.body.x;
                target[1] = r.body.y;
            }
        }
        return best < Float.MAX_VALUE;
    }

    /** Guest: the host's AI content for a floor range ("fallback" = the shared offline set). */
    @Override
    public void onBatch(int rangeStart, String json) {
        BatchResult result;
        try {
            result = "fallback".equals(json)
                    ? BatchGenerator.fallbackBatch(rangeStart, rangeStart + 4, fallbackSeed(rangeStart))
                    : batchGenerator.parseBatch(json, rangeStart, rangeStart + 4);
        } catch (Exception e) {
            Log.e("Coop", "Bad batch from host", e);
            result = BatchGenerator.fallbackBatch(rangeStart, rangeStart + 4, fallbackSeed(rangeStart));
        }
        batchCache.put(rangeStart, result);
    }

    /** The host left or the whole party fell: end the run on this phone. */
    @Override
    public void onRunEnded(String reason) {
        if (isFinishing()) return;
        coopEnded = true;
        isGamePaused = true;
        new android.app.AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Run over")
                .setMessage(reason)
                .setCancelable(false)
                .setPositiveButton("Main Menu", (dialog, which) -> {
                    leaveCoop();
                    SoundManager.stopGameMusic();
                    startActivity(new Intent(this, MenuActivity.class));
                    finish();
                })
                .show();
    }

    /** Leaves the run once; the host also deletes the lobby it started from. */
    private void leaveCoop() {
        if (coop != null && coop.leave() && coop.isHost()) dbManager.closeLobby(coop.code);
    }

    /* ============================================================
       GAME LOOP (LOGIC)
       ============================================================ */
    /**
     * Starts the logic thread. It runs a fixed 60 ticks per second on every phone: when a frame
     * runs late it catches up with extra ticks instead of slowing the whole game down.
     */
    private void startLogicThread() {
        new Thread(() -> {
            final long tickNanos = 16_666_667L;
            long previous = System.nanoTime();
            long backlog = 0;
            while (threadsRunning) {
                long current = System.nanoTime();
                backlog += current - previous;
                previous = current;

                // Boss death detection runs even while paused to avoid missing kills
                if (layoutReady) checkBossDeathDetection();

                if (isGamePaused || !layoutReady) backlog = 0;
                for (int i = 0; i < 5 && backlog >= tickNanos && !isGamePaused; i++) {
                    tick(System.currentTimeMillis());
                    backlog -= tickNanos;
                }
                // After a long stall (e.g. a dialog or the app in the background) skip ahead instead of fast-forwarding
                if (backlog >= tickNanos) backlog = 0;

                long sleepMs = Math.max(1L, (tickNanos - backlog) / 1_000_000L);
                try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    /**
     * One logic step: updates the player, enemies, projectiles, hits, coins, portals and camera.
     * @param now current time in milliseconds
     */
    private void tick(long now) {
        // --- 1. Update Player ---
        float prevPlayerX = player.x, prevPlayerY = player.y;
        if (isDashing) {
            player.update(dashDX * (DASH_POWER / player.speed), dashDY * (DASH_POWER / player.speed));
            effects.trail(player.x, player.y, Color.argb(160, 90, 140, 255));
            if (now - dashStartTime > DASH_DURATION) {
                isDashing = false;
            }
        } else if (playerHealth > 0) {
            player.update(joystick.getDX(), joystick.getDY());
        }
        if (currentLayout != null && !inShopRoom) {
            float[] resolved = currentLayout.resolveWallCollision(
                    prevPlayerX, prevPlayerY, player.x, player.y, player.radius);
            player.x = resolved[0];
            player.y = resolved[1];
        }
        player.setInvulnerable(now < invulnerableUntil);

        // Update dash and spin cooldown arcs, only when they visibly change
        float dashProgress = Math.min(1f, (float) (now - lastDashTime) / DASH_COOLDOWN);
        float spinProgress = Math.min(1f, (float) (now - lastSpinTime) / SPIN_COOLDOWN);
        int dashStep = (int) (dashProgress * 50);
        int spinStep = (int) (spinProgress * 50);
        if (dashStep != dashStepShown || spinStep != spinStepShown) {
            dashStepShown = dashStep;
            spinStepShown = spinStep;
            runOnUiThread(() -> {
                if (dashCooldownView != null)
                    dashCooldownView.setProgress(dashProgress);
                if (spinCooldownView != null)
                    spinCooldownView.setProgress(spinProgress);
            });
        }

        // Record kite behavior — is player moving away from nearest enemy?
        if (joystick.getDX() != 0 || joystick.getDY() != 0) {
            Enemy nearest = null;
            float nearestDist = Float.MAX_VALUE;
            synchronized (enemies) {
                for (Enemy e : enemies) {
                    if (e.isDead) continue;
                    float d = getDistance(player.x, player.y, e.x, e.y);
                    if (d < nearestDist) { nearestDist = d; nearest = e; }
                }
            }
            if (nearest != null && nearestDist < 500f) {
                float toDX = nearest.x - player.x;
                float toDY = nearest.y - player.y;
                float joyDot = joystick.getDX() * toDX + joystick.getDY() * toDY;
                behaviorTracker.recordKiteSample(joyDot < 0); // negative = moving away
            }
        }

        // Attack: hold the attack stick to keep swinging or shooting (a fallen co-op player can't).
        // The first swing waits a moment so a drag can set the aim; a quick tap still swings.
        boolean alive = playerHealth > 0;
        if (aimJoystick.isHeld()) {
            if (aimJoystick.isAiming() || aimJoystick.heldMs() >= AIM_GRACE) {
                aimJoystick.takeTap();
                if (alive) tryAttack(now);
            }
        } else if (aimJoystick.takeTap() && alive) {
            tryAttack(now);
        }
        if (spinQueued) {
            spinQueued = false;
            if (alive) spinAttack(now);
        }
        // Face the aimed direction while aiming, the last attack for a moment, otherwise the walk
        if (alive && aimJoystick.isAiming())
            lookAngle = (float) Math.toDegrees(Math.atan2(aimJoystick.getAimY(), aimJoystick.getAimX()));
        else if (now - lastAttackTime < 400)
            lookAngle = (float) Math.toDegrees(Math.atan2(aimDY, aimDX));
        else
            lookAngle = player.getFacingAngle();
        player.setAimAngle(lookAngle);

        // Mark current room as explored for minimap
        if (currentLayout != null && !inShopRoom) {
            Room currentRoom = currentLayout.getRoomAt(player.x, player.y);
            if (currentRoom != null) currentRoom.explored = true;
        }

        // --- 2. Update Camera ---
        camera.update(player.getX(), player.getY());

        if (coop != null) coopTick(now);

        // --- 3. Update Enemies (co-op guests only mirror the host's) ---
        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (isGuest()) {
                    e.followRemote();
                    continue;
                }
                if (!nearestTarget(e)) continue; // every player is down
                if (!e.isAwake) {
                    e.tryWake(target[0], target[1]);
                    continue;
                }
                float prevEX = e.x, prevEY = e.y;
                e.update(target[0], target[1], player.radius);
                if (currentLayout != null && !inShopRoom) {
                    float[] resolved = currentLayout.resolveWallCollision(
                            prevEX, prevEY, e.x, e.y, e.radius);
                    e.x = resolved[0];
                    e.y = resolved[1];
                }
            }
        }

        resolveCollisions();

        // --- 4. Enemy strikes and shots that finished their wind-up ---
        resolveEnemyAttacks(now);

        // --- 5. Collision: Attack ---
        if (isAttacking) {
            checkAttackCollisions();
            if (now - attackStartTime > ATTACK_DURATION) isAttacking = false;
        }

        updateProjectiles();
        checkProjectileCollisions();

        checkGoldCollection();
        checkChestInteraction();

        cleanupDeadEnemies();
        checkPortalCollision();
        checkShopPortalCollision();
        checkShopRoomInteractions();
        checkBossStatus();

        effects.update();
    }

    /* ============================================================
       RENDER LOOP (DRAWING)
       ============================================================ */
    /**
     * Starts the render thread that refreshes GameDisplay at ~60 fps.
     */
    private void startRenderThread() {
        new Thread(() -> {
            while (threadsRunning) {
                if (!isGamePaused && layoutReady) {
                    gameDisplay.postInvalidate();
                }
                try { Thread.sleep(16); } catch (Exception ignored) {}
            }
        }).start();
    }

    /* ============================================================
       GAMEPLAY LOGIC
       ============================================================ */

    /**
     * Spawns enemies into the floor layout according to each room's assigned count.
     * Applies AI templates if available and scales stats by floor number.
     * @param layout the floor layout to populate with enemies
     */
    private void spawnEnemiesForLayout(FloorLayout layout) {
        if (isGuest()) return; // guests mirror the host's enemies
        int floor = floorManager.getCurrentFloor();
        List<EnemyTemplate> pool = getEnemyPoolForFloor(floor);
        float margin = 80f;

        synchronized (enemies) {
            for (Room r : layout.rooms) {
                if (r.assignedEnemyCount <= 0) continue;
                for (int i = 0; i < r.assignedEnemyCount; i++) {
                    float spawnX = r.x + margin + (float)(Math.random() * Math.max(1f, r.width  - 2f * margin));
                    float spawnY = r.y + margin + (float)(Math.random() * Math.max(1f, r.height - 2f * margin));

                    Enemy.Type type = pickEnemyType(floor);
                    Enemy enemy = new Enemy(spawnX, spawnY, type);
                    enemy.applyFloorScaling(floor);

                    if (!pool.isEmpty()) {
                        enemy.templateIndex = (int) (Math.random() * pool.size());
                        enemy.applyTemplate(pool.get(enemy.templateIndex));
                    }
                    enemy.setBehaviorTracker(behaviorTracker);
                    enemy.setLayout(layout);
                    // isAwake = false — enemy waits for player to enter trigger radius
                    enemies.add(enemy);
                }
            }
        }
        Log.d("FloorLayout", "Pre-placed " + enemies.size() + " enemies for floor " + floor);
    }

    /**
     * Picks a random enemy type appropriate for the current floor.
     * Early floors only introduce GRUNT; archers join from floor 3, later floors add TANK and BRUTE.
     * @param floor the current floor number
     * @return the selected enemy type
     */
    private Enemy.Type pickEnemyType(int floor) {
        double roll = Math.random();
        if (floor <= 2) return Enemy.Type.GRUNT;
        if (Math.random() < 0.15) return Enemy.Type.ARCHER;
        if (floor <= 5) return (roll < 0.65) ? Enemy.Type.GRUNT : Enemy.Type.SPEEDER;
        if (floor <= 9) {
            if (roll < 0.40) return Enemy.Type.GRUNT;
            if (roll < 0.75) return Enemy.Type.SPEEDER;
            return Enemy.Type.BRUTE;
        }
        float tankChance    = Math.min(0.20f, 0.05f + (floor - 10) * 0.015f);
        float bruteChance   = 0.30f;
        float speederChance = 0.25f;
        if (roll < tankChance)                               return Enemy.Type.TANK;
        if (roll < tankChance + bruteChance)                 return Enemy.Type.BRUTE;
        if (roll < tankChance + bruteChance + speederChance) return Enemy.Type.SPEEDER;
        return Enemy.Type.GRUNT;
    }

    /**
     * Checks melee attack collisions against all enemies in range.
     * Each enemy can only be hit once per swing.
     * Applies knockback and updates statistics.
     */
    private void checkAttackCollisions() {
        Weapon w = player.getWeapon();
        long now = System.currentTimeMillis();

        // Clear hit-set at the start of each new swing so each enemy is hit at most once
        if (attackStartTime != lastKnownSwingTime) {
            hitThisSwing.clear();
            lastKnownSwingTime = attackStartTime;
        }

        float attackRange = player.radius + w.range;
        float aimAngle = (float) Math.toDegrees(Math.atan2(aimDY, aimDX));

        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (e.isDead) continue;

                // Each enemy can only be hit once per swing AND must respect weapon cooldown
                if (hitThisSwing.contains(e)) continue;
                if (now - e.lastHitTime < w.cooldown) continue;

                float dist = getDistance(player.x, player.y, e.x, e.y);
                if (dist > attackRange + e.radius) continue;

                float dx = e.x - player.x;
                float dy = e.y - player.y;
                float angleToEnemy = (float) Math.toDegrees(Math.atan2(dy, dx));
                float angleDiff = Math.abs((angleToEnemy - aimAngle + 540) % 360 - 180);
                // Big bodies are easier to clip with the edge of the swing
                float allowance = (float) Math.toDegrees(Math.atan2(e.radius, Math.max(1f, dist)));

                if (angleDiff > swingArc + allowance) continue;
                behaviorTracker.recordAttack(dist);

                boolean finisher = comboStep == 3;
                boolean crit = Math.random() < CRIT_CHANCE;
                int damage = Math.round(w.damage * damageMultiplier()
                        * (finisher ? 1.5f : 1f) * (crit ? 2f : 1f));
                e.lastHitTime = now;
                hitThisSwing.add(e);
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float push = d > 0 ? w.knockback * 25f * (finisher ? 2f : 1f) / d : 0f;
                damageEnemy(e, damage, crit, dx * push, dy * push, finisher || crit);
            }
        }
    }

    /**
     * Resolves overlaps between the player and enemies, and between enemies.
     * Uses a half-half push for each body pair.
     */
    private void resolveCollisions() {
        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (e.isDead) continue;

                float dx = player.x - e.x;
                float dy = player.y - e.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float minDist = player.radius + e.radius;

                if (dist < minDist && dist > 0) {
                    float overlap = minDist - dist;
                    float pushX = (dx / dist) * (overlap / 2f);
                    float pushY = (dy / dist) * (overlap / 2f);

                    float oldPX = player.x, oldPY = player.y;
                    player.x += pushX;
                    player.y += pushY;
                    if (currentLayout != null && !inShopRoom) {
                        float[] r = currentLayout.resolveWallCollision(oldPX, oldPY, player.x, player.y, player.radius);
                        player.x = r[0]; player.y = r[1];
                    }
                    e.x -= pushX;
                    e.y -= pushY;
                    if (currentLayout != null && !inShopRoom) {
                        float[] r = currentLayout.resolveWallCollision(e.x + pushX, e.y + pushY, e.x, e.y, e.radius);
                        e.x = r[0]; e.y = r[1];
                    }
                }
            }

            for (int i = 0; i < enemies.size(); i++) {
                Enemy e1 = enemies.get(i);
                for (int j = i + 1; j < enemies.size(); j++) {
                    Enemy e2 = enemies.get(j);

                    float dx = e1.x - e2.x;
                    float dy = e1.y - e2.y;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    float minDist = e1.radius + e2.radius;

                    if (dist < minDist && dist > 0) {
                        float overlap = minDist - dist;
                        float pushX = (dx / dist) * (overlap / 2f);
                        float pushY = (dy / dist) * (overlap / 2f);

                        e1.x += pushX;
                        e1.y += pushY;
                        e2.x -= pushX;
                        e2.y -= pushY;
                    }
                }
            }
        }
    }

    /**
     * Checks if the player has walked into the yellow shop portal.
     * Uses a separate flag from isTransitioning so both portals
     * can be checked independently.
     */
    private void checkShopPortalCollision() {
        if (!floorManager.isShopPortalOpen()) return;
        if (isTransitioning || enteringShop) return;

        float dist = getDistance(
                player.x, player.y,
                floorManager.getShopPortalX(), floorManager.getShopPortalY()
        );

        if (dist < player.radius + FloorManager.SHOP_PORTAL_RADIUS) {
            enteringShop = true;
            soundManager.playShopEnter();
            isTransitioning = true;
            runOnUiThread(this::triggerShopTransition);
        }
    }

    /**
     * Returns the rangeStart for a given floor.
     * Floor 1-5  →  1
     * Floor 6-10 →  6, Floor 11-15 → 11, etc.
     */
    private int getRangeStart(int floor) {
        if (floor <= 5) return 1;
        return 6 + ((floor - 6) / 5) * 5;
    }

    /** Starts fetching the batch that covers the given floor, unless it is cached or already loading. */
    private void ensureBatchFor(int floor) {
        int rangeStart = getRangeStart(floor);
        generateBatch(rangeStart, rangeStart + 4);
    }

    /**
     * Fires a Gemini batch call for the given range.
     * Caches result keyed by rangeStart. After 3 failed attempts the range uses offline content.
     */
    private void generateBatch(int rangeStart, int rangeEnd) {
        if (isGuest()) return; // guests receive the host's batches in onBatch()
        if (batchCache.containsKey(rangeStart)) return;
        if (!batchesInFlight.add(rangeStart)) return;

        // Check local storage first — avoids a network call on load game. Co-op never touches the solo cache.
        String cachedJson = coop == null ? BatchGenerator.loadRawBatch(this, rangeStart) : null;
        if (cachedJson != null) {
            try {
                BatchResult result = batchGenerator.parseBatch(cachedJson, rangeStart, rangeEnd);
                batchCache.put(rangeStart, result);
                batchesInFlight.remove(rangeStart);
                if (coop != null) coop.publishBatch(rangeStart, cachedJson);
                Log.d("Batch", "=== Loaded batch " + rangeStart + "-" + rangeEnd + " from local storage ===");
                runOnUiThread(() -> applyBatchToCurrentFloorIfReady(rangeStart));
                return;
            } catch (Exception e) {
                Log.e("Batch", "Local batch parse failed: " + e.getMessage() + " — fetching fresh");
            }
        }

        Log.d("Batch", "=== Firing batch for floors " + rangeStart + "-" + rangeEnd + " ===");

        batchGenerator.generateBatch(rangeStart, rangeEnd, new BatchGenerator.OnBatchGeneratedListener() {
            @Override
            public void onSuccess(BatchResult result, String jsonText) {
                if (coop == null) BatchGenerator.saveRawBatch(GameActivity.this, rangeStart, jsonText);
                batchCache.put(rangeStart, result);
                batchesInFlight.remove(rangeStart);
                if (coop != null) coop.publishBatch(rangeStart, jsonText);
                Log.d("Batch", "=== Cached + saved batch for floors " + rangeStart + "-" + rangeEnd + " ===");
                runOnUiThread(() -> applyBatchToCurrentFloorIfReady(rangeStart));
            }

            @Override
            public void onFailure(String error) {
                int failures = batchFailures.merge(rangeStart, 1, Integer::sum);
                Log.e("Batch", "=== Batch " + rangeStart + " failed (" + failures + "): " + error + " ===");
                if (failures >= 3) {
                    // Stop waiting on the AI: offline content keeps enemies, shops and bosses coming
                    batchCache.put(rangeStart, BatchGenerator.fallbackBatch(rangeStart, rangeEnd, fallbackSeed(rangeStart)));
                    batchesInFlight.remove(rangeStart);
                    if (coop != null) coop.publishBatch(rangeStart, "fallback");
                    runOnUiThread(() -> {
                        Toast.makeText(GameActivity.this, "AI content unavailable (" + error
                                + ") — using classic content", Toast.LENGTH_LONG).show();
                        applyBatchToCurrentFloorIfReady(rangeStart);
                    });
                    return;
                }
                // Called on an OkHttp thread, which has no Looper of its own
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    batchesInFlight.remove(rangeStart);
                    if (threadsRunning) generateBatch(rangeStart, rangeEnd);
                }, 3000L * failures);
            }
        });
    }

    /** Offline content seed: shared in co-op so every phone builds the same fallback. */
    private long fallbackSeed(int rangeStart) {
        return coop != null ? coop.seed + rangeStart : System.nanoTime();
    }

    /**
     * Called when a batch lands. If the current floor is covered by that batch
     * and no kills have happened yet, clears and re-spawns enemies so the player
     * sees custom enemies instead of the generic placeholders that were used
     * when the floor was first generated before the batch was ready.
     */
    private void applyBatchToCurrentFloorIfReady(int rangeStart) {
        if (currentLayout == null || isGuest()) return;
        int cur = floorManager.getCurrentFloor();
        if (getRangeStart(cur) != rangeStart) return;        // not our floor's batch
        if (floorManager.getKillsThisFloor() > 0) return;   // combat already started
        List<EnemyTemplate> pool = getEnemyPoolForFloor(cur);
        if (pool.isEmpty()) return;                          // batch had nothing useful

        synchronized (enemies) { enemies.clear(); }
        spawnEnemiesForLayout(currentLayout);
        Log.d("Batch", "Re-spawned floor " + cur + " enemies with fresh templates.");
    }

    /**
     * Resolves enemy attacks whose wind-up just finished: melee strikes and boss slams that reach
     * the player, and shots, which become hostile projectiles.
     * @param now current time in milliseconds
     */
    private void resolveEnemyAttacks(long now) {
        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (e.justEnraged) {
                    e.justEnraged = false;
                    effects.banner(e.x, e.y - e.radius - 60f, "ENRAGED", Color.rgb(255, 90, 40));
                }

                int floor = floorManager.getCurrentFloor();
                for (Enemy.Shot shot : e.pendingShots) {
                    float sx = e.x + shot.dirX * e.radius;
                    float sy = e.y + shot.dirY * e.radius;
                    spawnHostileShot(sx, sy, shot.dirX, shot.dirY, shot.speed, shot.range, shot.damage, shot.radius);
                    if (coop != null) coop.publishEvent("p," + floor + "," + sx + "," + sy + "," + shot.dirX
                            + "," + shot.dirY + "," + shot.speed + "," + shot.range + "," + shot.damage + "," + shot.radius);
                }
                e.pendingShots.clear();

                if (e.strikeDamage > 0) {
                    boolean slam = e.type == Enemy.Type.BOSS && e.strikeReach > e.radius * 2f;
                    float knockback = e.getWeapon().knockback;
                    resolveStrike(e.x, e.y, e.strikeDirX, e.strikeDirY, e.strikeReach, e.strikeArc,
                            e.strikeDamage, knockback, e.radius, slam, now);
                    if (coop != null) coop.publishEvent("s," + floor + "," + e.x + "," + e.y + "," + e.strikeDirX
                            + "," + e.strikeDirY + "," + e.strikeReach + "," + e.strikeArc + "," + e.strikeDamage
                            + "," + knockback + "," + e.radius + "," + (slam ? 1 : 0));
                    e.strikeDamage = 0;
                }
            }
        }
    }

    private void spawnHostileShot(float x, float y, float dirX, float dirY, float speed, float range,
                                  int damage, float radius) {
        Projectile p = new Projectile(x, y, dirX, dirY, speed, range, damage, 0.15f, radius, HOSTILE_SHOT_COLOR);
        p.hostile = true;
        projectiles.add(p);
    }

    /** An enemy strike landing: shows it and hurts this phone's player if they stand inside it. */
    private void resolveStrike(float x, float y, float dirX, float dirY, float reach, float arc,
                               int damage, float knockback, float bodyRadius, boolean slam, long now) {
        if (slam)
            effects.burst(x, y, Color.rgb(255, 120, 40), 40, 16f); // slam shockwave
        else if (arc < 180f)
            effects.slash(x, y, (float) Math.toDegrees(Math.atan2(dirY, dirX)), reach, arc, Color.rgb(255, 90, 60));
        if (isInStrike(x, y, dirX, dirY, reach, arc, bodyRadius))
            hitPlayer(damage, x, y, knockback, now);
    }

    /** True when the player stands inside a strike: within reach and inside its cone. */
    private boolean isInStrike(float x, float y, float dirX, float dirY, float reach, float arc, float bodyRadius) {
        float dx = player.x - x;
        float dy = player.y - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > player.radius + reach) return false;
        if (arc >= 180f || dist < bodyRadius + player.radius) return true;
        float toPlayer = (float) Math.toDegrees(Math.atan2(dy, dx));
        float facing = (float) Math.toDegrees(Math.atan2(dirY, dirX));
        float angleDiff = Math.abs((toPlayer - facing + 540) % 360 - 180);
        return angleDiff <= arc + (float) Math.toDegrees(Math.atan2(player.radius, dist));
    }

    /**
     * Applies an enemy hit unless the player is invulnerable (dashing or just hit).
     * Adds a damage number, screen shake, vibration and knockback away from the source.
     */
    private void hitPlayer(int damage, float fromX, float fromY, float knockback, long now) {
        if (now < invulnerableUntil || playerHealth <= 0) return;
        invulnerableUntil = now + HIT_INVULNERABILITY;
        player.flashUntil = now + 150;
        damagePlayer(damage);
        effects.number(player.x, player.y - player.radius, "-" + damage, Color.rgb(255, 80, 80));
        if (camera != null) camera.startShake(250, 14f);
        runOnUiThread(this::vibrateOnHit);

        float dx = player.x - fromX;
        float dy = player.y - fromY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > 0) {
            float oldPX = player.x;
            float oldPY = player.y;
            player.x += (dx / dist) * knockback * 80f;
            player.y += (dy / dist) * knockback * 80f;
            if (currentLayout != null && !inShopRoom) {
                float[] resolved = currentLayout.resolveWallCollision(
                        oldPX, oldPY, player.x, player.y, player.radius);
                player.x = resolved[0];
                player.y = resolved[1];
            }
        }
    }

    /** Damage number and sparks when the player hits an enemy; crits show gold with a "!". */
    private void showEnemyHit(Enemy e, int damage, boolean crit) {
        if (crit) {
            effects.number(e.x, e.y - e.radius, damage + "!", Color.rgb(255, 200, 40));
            effects.burst(e.x, e.y, Color.rgb(255, 200, 40), 14, 10f);
        } else {
            effects.number(e.x, e.y - e.radius, String.valueOf(damage), Color.WHITE);
            effects.burst(e.x, e.y, Color.rgb(255, 230, 150), 6, 6f);
        }
    }

    /** Interrupts an enemy's wind-up, so a well-timed heavy hit cancels its attack. */
    private void staggerEnemy(Enemy e) {
        if (e.stagger())
            effects.number(e.x, e.y - e.radius - 40f, "STAGGER", Color.rgb(120, 200, 255));
    }

    /**
     * Deals the player's damage to an enemy. A co-op guest only shows the hit and reports it;
     * the host applies it to the real enemy.
     */
    private void damageEnemy(Enemy e, int damage, boolean crit, float pushX, float pushY, boolean stagger) {
        soundManager.playHitEnemy();
        statsManager.onDamageGiven(damage);
        showEnemyHit(e, damage, crit);
        if (isGuest()) {
            e.predictHit(damage);
            coop.sendHit(e.id + "," + damage + "," + pushX + "," + pushY + "," + (stagger ? 1 : 0));
            return;
        }
        e.takeDamage(damage);
        e.knockbackVX = pushX;
        e.knockbackVY = pushY;
        if (stagger) staggerEnemy(e);
    }

    /**
     * Computes the Euclidean distance between two points.
     * @param x1 first X position
     * @param y1 first Y position
     * @param x2 second X position
     * @param y2 second Y position
     * @return the distance between the two points
     */
    private float getDistance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Applies damage to the player following the armour-before-HP priority.
     * Reduces armour durability, breaks depleted armour pieces and triggers Game Over if HP reaches zero.
     * @param dmg amount of damage to apply
     */
    private void damagePlayer(int dmg) {
        runOnUiThread(() -> {
            soundManager.playHitPlayer();
            if (playerArmor > 0 && maxArmor > 0) {
                // Absorb up to playerArmor; remainder spills to health
                int armorAbsorb = Math.min(dmg, playerArmor);
                int healthSpill  = dmg - armorAbsorb;
                oldArmor   = playerArmor;
                playerArmor = playerArmor - armorAbsorb;
                animateArmorBar(oldArmor, playerArmor);
                if (healthSpill > 0) {
                    int healthDmg = Math.min(healthSpill, playerHealth);
                    oldHealth  = playerHealth;
                    playerHealth = Math.max(0, playerHealth - healthSpill);
                    animateHealthBar(oldHealth, playerHealth);
                    statsManager.onDamageTaken(armorAbsorb + healthDmg);
                } else {
                    statsManager.onDamageTaken(armorAbsorb);
                }
            } else {
                int actualDmg = Math.min(dmg, playerHealth);
                oldHealth  = playerHealth;
                playerHealth = Math.max(0, playerHealth - dmg);
                animateHealthBar(oldHealth, playerHealth);
                statsManager.onDamageTaken(actualDmg);
            }

            // Armor durability — each hit removes 1 from every equipped piece
            List<Armor.Slot> brokenSlots = new ArrayList<>();
            for (java.util.Map.Entry<Armor.Slot, Armor> entry :
                    new java.util.ArrayList<>(equippedArmor.entrySet())) {
                Armor piece = entry.getValue();
                piece.durability -= 1;
                if (piece.durability <= 0) brokenSlots.add(entry.getKey());
            }
            for (Armor.Slot slot : brokenSlots) {
                Armor broken = equippedArmor.remove(slot);
                if (broken != null) {
                    inventoryArmors.remove(broken);
                    inventoryArmorIds.remove(armorFirestoreIds.remove(broken));
                    recalculateMaxArmor();
                    showArmorBreakPopup(slot.name());
                }
            }

            if (playerHealth <= 0 && coop != null) {
                // Co-op: the run goes on; a fallen player revives on the next floor
                player.startDeathAnimation();
                statsManager.onPlayerDied();
                Toast.makeText(this, "You fell! You'll revive when your party reaches the next floor.",
                        Toast.LENGTH_LONG).show();
            } else if (playerHealth <= 0) {
                isGamePaused = true;
                player.startDeathAnimation();
                statsManager.onPlayerDied();
                statsManager.flush();
                new android.os.Handler().postDelayed(
                        this::showGameOverDialog, player.getDeathDuration());
            }
        });
    }

    private void showArmorBreakPopup(String slotName) {
        android.view.ViewGroup root = (android.view.ViewGroup) findViewById(R.id.game_root);
        if (root == null) return;

        TextView popup = new TextView(this);
        popup.setText(slotName + " ARMOR BROKEN");
        popup.setTextColor(ContextCompat.getColor(this, R.color.crimson_glow));
        popup.setTextSize(14f);
        popup.setTypeface(getResources().getFont(R.font.cinzel), android.graphics.Typeface.BOLD);
        popup.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_warning, 0, 0, 0);
        popup.setCompoundDrawableTintList(popup.getTextColors());
        popup.setCompoundDrawablePadding(16);
        popup.setPadding(28, 14, 28, 14);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.argb(210, 25, 0, 0));
        bg.setCornerRadius(16f);
        bg.setStroke(1, Color.rgb(200, 50, 50));
        popup.setBackground(bg);
        popup.setGravity(android.view.Gravity.CENTER);

        android.widget.RelativeLayout.LayoutParams params =
                new android.widget.RelativeLayout.LayoutParams(
                        android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
        params.topMargin = 140;
        popup.setLayoutParams(params);
        root.addView(popup);

        new android.os.Handler().postDelayed(() ->
                popup.animate().alpha(0f).setDuration(300)
                        .withEndAction(() -> root.removeView(popup)).start(),
                2200);
    }

    /**
     * Respawns the player from the last checkpoint.
     * Clears enemies, coins and projectiles, then generates a fresh floor layout.
     */
    private void respawnPlayer() {
        floorManager.setCurrentFloor(checkpointFloor);
        activeBoss       = null;
        bossSpawnHandled = false;
        enemiesKilledOnPortalOpen = false;
        enteringShop = false;

        synchronized (enemies)        { enemies.clear(); }
        synchronized (goldCoins)      { goldCoins.clear(); }
        synchronized (projectiles)    { projectiles.clear(); }
        synchronized (treasureChests) { treasureChests.clear(); }

        // Regenerate a fresh layout so rooms aren't pre-explored and enemies respawn
        currentLayout = generateLayout();
        gameDisplay.setFloorLayout(currentLayout);
        if (currentLayout.exitRoom != null) {
            floorManager.setExitRoomCenter(
                    currentLayout.exitRoom.centerX(),
                    currentLayout.exitRoom.centerY());
        }

        // Always place player at entry room center — checkpoint world coords are stale
        player.x = currentLayout.entryRoom.centerX();
        player.y = currentLayout.entryRoom.centerY();
        currentLayout.entryRoom.explored = true;
        player.resetDeathAnimation();

        playerHealth = checkpointHealth;
        playerGold   = checkpointGold;
        // Recalculate max armor from current equipped gear (some pieces may have broken
        // since the checkpoint). Restore to the checkpoint armor value, capped at the new max.
        maxArmor = 0;
        for (Armor a : equippedArmor.values()) maxArmor += a.defense;
        playerArmor = Math.min(checkpointArmor, maxArmor);

        spawnEnemiesForLayout(currentLayout);
        effects.clear();
        invulnerableUntil = 0;

        runOnUiThread(() -> {
            playerHealthBar.setProgress(playerHealth);
            playerArmorBar.setMax(Math.max(1, maxArmor));
            playerArmorBar.setProgress(playerArmor);
            if (txtHealthValue != null) txtHealthValue.setText(playerHealth + " / " + maxHealth);
            if (txtArmorValue  != null) txtArmorValue.setText(playerArmor + " / " + maxArmor);
            updateGoldHUD();
            updateFloorHUD();
        });

        isGamePaused = false;
    }

    /**
     * Removes dead enemies from the list, counts kills and drops coins.
     * Only removes enemies once their death animation has completed.
     */
    private void cleanupDeadEnemies() {
        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (e.isDead && !e.counted) {
                    e.counted = true;
                    if (!isGuest()) floorManager.onEnemyKilled(player.x, player.y); // guests copy the host's count
                    statsManager.onEnemyKilled();
                    effects.burst(e.x, e.y, e.getColor(), e.type == Enemy.Type.BOSS ? 60 : 16, 9f);
                    if (!e.noGoldDrop)
                    {
                        spawnGoldCoins(e.x, e.y, e.getGoldDrop());
                        int xp = e.getXpReward();
                        runOnUiThread(() -> gainXp(xp));
                    }
                }
            }
            enemies.removeIf(e -> e.readyToRemove);
        }

        if (floorManager.isPortalOpen() && !enemiesKilledOnPortalOpen) {
            enemiesKilledOnPortalOpen = true;
            killAllActiveEnemies();
        }

        updateFloorHUD();
    }

    /**
     * Animates the HP bar from its current value to a new value.
     * @param from old HP value
     * @param to new HP value
     */
    private void animateHealthBar(int from, int to) {
        ObjectAnimator anim = ObjectAnimator.ofInt(playerHealthBar, "progress", from, to);
        anim.setDuration(100).start();
        if (txtHealthValue != null) txtHealthValue.setText(to + " / " + maxHealth);
    }

    /**
     * Animates the armour bar from its current value to a new value.
     * @param from old armour value
     * @param to new armour value
     */
    private void animateArmorBar(int from, int to) {
        ObjectAnimator anim = ObjectAnimator.ofInt(playerArmorBar, "progress", from, to);
        anim.setDuration(100).start();
        if (txtArmorValue != null) txtArmorValue.setText(to + " / " + maxArmor);
    }

    /**
     * Attacks with the equipped weapon once its cooldown allows. Aims where the attack stick is
     * dragged, otherwise at the nearest awake enemy in reach, otherwise straight ahead.
     * Runs on the logic thread.
     * @param now current time in milliseconds
     */
    private void tryAttack(long now) {
        Weapon w = player.getWeapon();
        if (now - lastAttackTime < w.cooldown) return;

        if (aimJoystick.isAiming()) {
            aimDX = aimJoystick.getAimX();
            aimDY = aimJoystick.getAimY();
        } else {
            Enemy target = nearestEnemy(w.isProjectile ? w.range : player.radius + w.range + 160f);
            if (target != null) {
                aimDX = target.x - player.x;
                aimDY = target.y - player.y;
            } else {
                double facing = Math.toRadians(player.getFacingAngle());
                aimDX = (float) Math.cos(facing);
                aimDY = (float) Math.sin(facing);
            }
        }
        float len = (float) Math.sqrt(aimDX * aimDX + aimDY * aimDY);
        if (len < 0.01f) return;
        aimDX /= len;
        aimDY /= len;
        swingCount++;

        if (w.isProjectile) {
            fireProjectile(aimDX, aimDY);
            return;
        }
        comboStep = now - lastAttackTime <= w.cooldown + COMBO_WINDOW ? comboStep % 3 + 1 : 1;
        boolean finisher = comboStep == 3;
        swingArc = finisher ? 80f : 55f;
        lastAttackTime = now;
        attackStartTime = now;
        isAttacking = true;
        soundManager.playSwingMelee();
        effects.slash(player.x, player.y, (float) Math.toDegrees(Math.atan2(aimDY, aimDX)),
                player.radius + w.range, swingArc, finisher ? Color.rgb(255, 210, 90) : Color.WHITE);
    }

    /** Closest awake enemy within the given distance, or null. */
    private Enemy nearestEnemy(float maxDist) {
        Enemy best = null;
        float bestDist = maxDist;
        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (e.isDead || !e.isAwake) continue;
                float d = getDistance(player.x, player.y, e.x, e.y) - e.radius;
                if (d < bestDist) { bestDist = d; best = e; }
            }
        }
        return best;
    }

    /**
     * Spin special: hits every enemy around the player, knocks them back and interrupts wind-ups.
     * Grants a moment of invulnerability. Runs on the logic thread.
     * @param now current time in milliseconds
     */
    private void spinAttack(long now) {
        lastSpinTime = now;
        Weapon w = player.getWeapon();
        float reach = player.radius + 180f;
        int damage = Math.round(Math.max(w.damage, 6) * damageMultiplier() * 1.3f);
        int color = Color.rgb(140, 200, 255);
        player.startSpin(reach, color);
        effects.burst(player.x, player.y, color, 10, 6f);
        soundManager.playSwingMelee();
        invulnerableUntil = Math.max(invulnerableUntil, now + 250);

        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (e.isDead) continue;
                float dx = e.x - player.x;
                float dy = e.y - player.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > reach + e.radius) continue;
                e.lastHitTime = now;
                float push = dist > 0 ? 22f / dist : 0f;
                damageEnemy(e, damage, false, dx * push, dy * push, true);
            }
        }
    }

    /**
     * Handles a tap on the melee attack button.
     * Triggers an attack only if the current weapon's cooldown has expired.
     */
    private void attack() {
        long now = System.currentTimeMillis();
        if (player.getWeapon().isProjectile) return;
        if (now - lastAttackTime > player.getWeapon().cooldown) {
            isAttacking = true;
            attackStartTime = now;
            lastAttackTime = now;
        }
    }

    /**
     * Handles a tap on the dash button.
     * Dashes in the joystick direction, or the facing direction when standing still.
     */
    private void dash() {
        long now = System.currentTimeMillis();
        if (isDashing || playerHealth <= 0) return;
        if (now - lastDashTime < DASH_COOLDOWN) return;

        float joyX = joystick.getDX();
        float joyY = joystick.getDY();
        if (joyX == 0 && joyY == 0) {
            double facing = Math.toRadians(player.getFacingAngle());
            joyX = (float) Math.cos(facing);
            joyY = (float) Math.sin(facing);
        }

        float length = (float) Math.sqrt(joyX * joyX + joyY * joyY);
        dashDX = joyX / length;
        dashDY = joyY / length;

        isDashing = true;
        dashStartTime = now;
        lastDashTime = now;
        // A short invulnerable window lets the dash pass through attacks and shots
        invulnerableUntil = Math.max(invulnerableUntil, now + DASH_DURATION + 80);
        soundManager.playDash();
        behaviorTracker.recordDash(dashDX, dashDY);
    }

    /**
     * Checks whether the player has entered the purple exit portal and triggers a floor transition.
     */
    private void checkPortalCollision() {
        if (!floorManager.isPortalOpen()) return;
        if (isTransitioning) return;

        float dist = getDistance(
                player.x, player.y,
                floorManager.getPortalX(), floorManager.getPortalY()
        );

        if (dist < player.radius + FloorManager.PORTAL_RADIUS) {
            if (isGuest()) portalRequested = true; // the host moves the whole party
            else startFloorTransition();
        }
    }

    /** Moves to the next floor, leaving the shop first if needed. Runs on the logic thread. */
    private void startFloorTransition() {
        isTransitioning = true;
        isGamePaused = true;
        soundManager.playPortalEnter();
        runOnUiThread(() -> {
            if (inShopRoom) exitShopRoom();
            else triggerFloorTransition();
        });
    }

    /**
     * Fires a projectile from the player in the given direction if the weapon cooldown has expired.
     * @param dirX X direction (un-normalised)
     * @param dirY Y direction (un-normalised)
     */
    private void fireProjectile(float dirX, float dirY) {
        long now = System.currentTimeMillis();
        if (now - lastAttackTime < player.getWeapon().cooldown) return;
        lastAttackTime = now;

        Weapon w = player.getWeapon();
        Projectile p = new Projectile(
                player.x, player.y,
                dirX, dirY,
                18f,
                w.range,
                Math.round(w.damage * damageMultiplier()),
                w.knockback,
                w.name.equals("Staff") ? 18f : 12f,
                w.color
        );
        projectiles.add(p);
        soundManager.playFireProjectile();
    }

    /**
     * Updates all active projectiles and removes dead ones. Walls stop shots from both sides.
     */
    private void updateProjectiles() {
        synchronized (projectiles) {
            for (Projectile p : projectiles) {
                p.update();
                if (currentLayout != null && !inShopRoom && !currentLayout.isFloor(p.x, p.y)) p.isDead = true;
            }
            projectiles.removeIf(p -> p.isDead);
        }
    }

    /**
     * Checks projectile collisions: player shots against enemies, enemy shots against the player.
     * A projectile stops on its first hit and applies damage and knockback.
     * Enemy shots pass through a dashing (invulnerable) player.
     */
    private void checkProjectileCollisions() {
        long now = System.currentTimeMillis();
        synchronized (projectiles) {
            for (Projectile p : projectiles) {
                if (p.isDead) continue;

                if (p.hostile) {
                    if (now >= invulnerableUntil
                            && getDistance(p.x, p.y, player.x, player.y) < p.getRadius() + player.radius) {
                        p.isDead = true;
                        hitPlayer(p.damage, p.x, p.y, p.knockback, now);
                    }
                    continue;
                }

                synchronized (enemies) {
                    for (Enemy e : enemies) {
                        if (e.isDead) continue;

                        float dist = getDistance(p.x, p.y, e.x, e.y);
                        if (dist < p.getRadius() + e.radius) {
                            boolean crit = Math.random() < CRIT_CHANCE;
                            behaviorTracker.recordAttack(getDistance(player.x, player.y, e.x, e.y));
                            float dx = e.x - p.x;
                            float dy = e.y - p.y;
                            float d = (float) Math.sqrt(dx * dx + dy * dy);
                            float push = d > 0 ? p.knockback * 25f / d : 0f;
                            damageEnemy(e, crit ? p.damage * 2 : p.damage, crit, dx * push, dy * push, crit);

                            p.isDead = true;
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Plays the floor-transition animation: fade to black, display the floor name, fade back.
     * Prepares the next floor while the screen is black.
     */
    private void triggerFloorTransition() {
        if (coop != null && coop.isHost()) coop.publishFloor(floorManager.getCurrentFloor() + 1);
        txtFloorTitle.setText("Floor " + (floorManager.getCurrentFloor() + 1));
        txtFloorTitle.setAlpha(0f);

        floorTransitionOverlay.setVisibility(View.VISIBLE);
        floorTransitionOverlay.setAlpha(0f);
        floorTransitionOverlay.animate()
                .alpha(1f)
                .setDuration(400)
                .withEndAction(() -> {
                    new android.os.Handler().postDelayed(() -> {
                        txtFloorTitle.animate()
                                .alpha(1f)
                                .setDuration(400)
                                .withEndAction(() -> {
                                    new android.os.Handler().postDelayed(() -> {
                                        txtFloorTitle.animate()
                                                .alpha(0f)
                                                .setDuration(300)
                                                .withEndAction(() -> {

                                                    prepareNextFloor();
                                                    isGamePaused = false;

                                                    floorTransitionOverlay.animate()
                                                            .alpha(0f)
                                                            .setDuration(400)
                                                            .withEndAction(() -> {
                                                                floorTransitionOverlay.setVisibility(View.GONE);
                                                                isTransitioning = false;
                                                            }).start();
                                                }).start();
                                    }, 1200);
                                }).start();
                    }, 300);
                }).start();
        floorTransitionOverlay.setClickable(true);
    }

    /**
     * Teleports the player into the shop room.
     * Clears enemies and coins — shop room is always safe.
     */
    private void enterShopRoom() {
        inShopRoom = true;
        shopRoom.isActive = true;
        gameDisplay.setInShopRoom(true);
        // shopBoughtIndices is NOT cleared here — it persists across re-entries within
        // the same floor so already-purchased items stay visually marked as sold.
        // It is cleared in prepareNextFloor() when advancing to the next floor.

        // Move player to spawn corner
        player.x = ShopRoom.SPAWN_X;
        player.y = ShopRoom.SPAWN_Y;

        // Safe zone — clear all enemies and coins
        synchronized (enemies)    { enemies.clear(); }
        synchronized (goldCoins)  { goldCoins.clear(); }
    }

    /**
     * Fades to black, shows "THE SHOP" text, then launches ShopActivity.
     * Game stays paused until the player returns from the shop.
     */
    private void triggerShopTransition() {
        txtFloorTitle.setText("THE SHOP");
        txtFloorTitle.setTextColor(Color.rgb(255, 215, 0));
        txtFloorTitle.setAlpha(0f);

        floorTransitionOverlay.setVisibility(android.view.View.VISIBLE);
        floorTransitionOverlay.setAlpha(0f);
        floorTransitionOverlay.animate()
                .alpha(1f)
                .setDuration(400)
                .withEndAction(() -> {
                    txtFloorTitle.animate()
                            .alpha(1f)
                            .setDuration(400)
                            .withEndAction(() ->
                                    new android.os.Handler().postDelayed(() -> {

                                        // Teleport player into shop room while screen is black
                                        enterShopRoom();

                                        floorTransitionOverlay.animate()
                                                .alpha(0f)
                                                .setDuration(400)
                                                .withEndAction(() -> {
                                                    floorTransitionOverlay.setVisibility(
                                                            android.view.View.GONE);
                                                    txtFloorTitle.setTextColor(
                                                            androidx.core.content.ContextCompat.getColor(
                                                                    GameActivity.this, R.color.gold_bright));
                                                    isTransitioning = false;
                                                }).start();
                                    }, 1000)
                            ).start();
                }).start();
    }

    /**
     * Leaves the shop room and triggers the normal floor transition
     * to the next floor, exactly like walking through the purple portal.
     */
    private void exitShopRoom() {
        inShopRoom = false;
        shopRoom.isActive = false;
        gameDisplay.setInShopRoom(false);
        enteringShop = false;
        isGamePaused = true;
        triggerFloorTransition();
    }

    /**
     * Applies the item effect immediately on purchase.
     * WEAPON: saved to Firestore generated_weapons, added to inventory
     * ARMOR:  saved to Firestore generated_armors (applied in armor system later)
     * CONSUMABLE: heal/armor restore applied instantly
     */
    private void purchaseItem(ShopItem item) {
        switch (item.type) {
            case WEAPON:
                Weapon weapon = item.toWeapon();
                weapons.add(weapon);
                // Co-op items only last for the run, so they're never stored
                if (coop == null) dbManager.saveGeneratedWeapon(
                        userId != null ? userId : "unknown",
                        item.name, item.damage, item.range, item.cooldown,
                        item.rarity, item.color, item.knockback, item.isProjectile,
                        item.description,
                        weaponId -> {
                            if (weaponId != null) {
                                inventoryWeaponIds.add(weaponId);
                                weaponFirestoreIds.put(weapon, weaponId);
                            }
                        }
                );
                break;

            case ARMOR:
                Armor armor = item.toArmor();
                inventoryArmors.add(armor);
                if (coop == null) dbManager.saveGeneratedArmor(
                        userId != null ? userId : "unknown",
                        item.name, item.defense, item.maxDurability,
                        item.rarity, item.color, item.armorSlot,
                        item.description,
                        armorId -> {
                            if (armorId != null) {
                                inventoryArmorIds.add(armorId);
                                armorFirestoreIds.put(armor, armorId);
                            }
                        }
                );
                break;

            case CONSUMABLE:
                boolean found = false;
                for (ConsumableStack stack : consumableInventory) {
                    if (stack.item.name.equals(item.name)) {
                        stack.increment();
                        found = true;
                        break;
                    }
                }
                if (!found) consumableInventory.add(new ConsumableStack(item, 1));
                updatePotionButton();
                break;
        }
    }

    /**
     * Populates the shop item grid with 3 rows of 2 items each.
     * Displays purchased items as "SOLD" and greys out items the player cannot afford.
     * @param dialog the shop dialog
     * @param itemsContainer the container holding the item views
     * @param goldDisplay the gold amount text view
     * @param shopItems the list of shop items to display
     */
    private void populateShopGridFromItems(android.app.Dialog dialog,
                                           android.view.View itemsContainer,
                                           TextView goldDisplay,
                                           List<ShopItem> shopItems) {
        // 2 rows of 3 cards, so the shop fits a landscape screen
        LinearLayout[] rows = {dialog.findViewById(R.id.shop_row_0), dialog.findViewById(R.id.shop_row_1)};
        for (LinearLayout row : rows) row.removeAllViews();

        for (int i = 0; i < Math.min(shopItems.size(), 6); i++) {
            ShopItem item = shopItems.get(i);
            final int index = i;

            android.view.View card = getLayoutInflater()
                    .inflate(R.layout.dialog_shop_item, rows[i / 3], false);

            TextView nameView  = card.findViewById(R.id.item_name);
            TextView typeView  = card.findViewById(R.id.item_type_badge);
            TextView priceView = card.findViewById(R.id.item_price);

            int rarityColor = getRarityColor(item.rarity);
            android.graphics.drawable.GradientDrawable border =
                    new android.graphics.drawable.GradientDrawable();
            border.setColor(ContextCompat.getColor(this, R.color.bg_surface));
            border.setCornerRadius(12f);
            border.setStroke(3, rarityColor);
            card.setBackground(border);

            nameView.setText(item.name);
            nameView.setTextColor(rarityColor);
            typeView.setText(item.type.name());
            priceView.setText(String.valueOf(item.price));

            if (shopBoughtIndices.contains(index)) {
                // Already purchased — dim and disable the card
                card.setAlpha(0.3f);
                priceView.setText("SOLD");
                priceView.setCompoundDrawablesRelative(null, null, null, null);
            } else if (playerGold < item.price) {
                card.setAlpha(0.5f);
                card.setOnClickListener(v ->
                        showItemDetailDialog(dialog, item, card, goldDisplay, shopBoughtIndices, index));
            } else {
                card.setOnClickListener(v ->
                        showItemDetailDialog(dialog, item, card, goldDisplay, shopBoughtIndices, index));
            }

            rows[i / 3].addView(card);
        }
    }

    private void showItemDetailDialog(android.app.Dialog shopDialog,
                                      ShopItem item,
                                      android.view.View cardView,
                                      TextView goldDisplay,
                                      Set<Integer> boughtIndices,
                                      int itemIndex) {

        final android.app.Dialog detail = new android.app.Dialog(
                this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        detail.setContentView(R.layout.dialog_shop_item_detail);
        detail.setCancelable(false);

        if (detail.getWindow() != null)
            detail.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView nameView     = detail.findViewById(R.id.detail_name);
        TextView typeView     = detail.findViewById(R.id.detail_type);
        TextView rarityView   = detail.findViewById(R.id.detail_rarity);
        TextView descView     = detail.findViewById(R.id.detail_description);
        LinearLayout statsView = detail.findViewById(R.id.detail_stats);
        TextView priceView    = detail.findViewById(R.id.detail_price);
        View closeBtn         = detail.findViewById(R.id.btn_close_detail);
        MaterialButton buyBtn = detail.findViewById(R.id.btn_buy_detail);

        int rarityColor = getRarityColor(item.rarity);
        nameView.setText(item.name);
        nameView.setTextColor(rarityColor);
        typeView.setText(item.type.name());
        rarityView.setText(item.rarity.toUpperCase());
        rarityView.setTextColor(rarityColor);
        descView.setText(item.description);
        priceView.setText(String.valueOf(item.price));
        showDetailStats(statsView, item);

        boolean canAfford = playerGold >= item.price;
        buyBtn.setAlpha(canAfford ? 1f : 0.4f);
        buyBtn.setEnabled(canAfford);

        closeBtn.setOnClickListener(v -> detail.dismiss());

        buyBtn.setOnClickListener(v -> {
            if (playerGold < item.price) return;

            playerGold -= item.price;
            statsManager.onCoinsSpent(item.price);
            goldDisplay.setText(String.valueOf(playerGold));
            updateGoldHUD();

            purchaseItem(item);

            boughtIndices.add(itemIndex);
            cardView.setAlpha(0.3f);
            cardView.setOnClickListener(null);

            detail.dismiss();
        });

        detail.show();
        hideSystemUIOnWindow(detail.getWindow());
    }

    /**
     * Opens the shop dialog.
     * Shows a loading spinner if Gemini hasn't finished yet.
     * Populates 6 item cards once items are ready.
     */
    private void showShopDialog() {
        if (coop == null) isGamePaused = true; // a co-op run keeps going for everyone

        final android.app.Dialog dialog = new android.app.Dialog(
                this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        shopDialog = dialog;
        dialog.setContentView(R.layout.dialog_shop);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView goldDisplay       = dialog.findViewById(R.id.shop_gold_display);
        android.view.View loading  = dialog.findViewById(R.id.shop_loading);
        android.view.View container = dialog.findViewById(R.id.shop_items_container);
        MaterialButton btnClose    = dialog.findViewById(R.id.btn_close_shop);

        goldDisplay.setText(String.valueOf(playerGold));

        btnClose.setOnClickListener(v -> {
            ShopParticleView particles = dialog.findViewById(R.id.shop_particles);
            if (particles != null) particles.stopAnimation();
            dialog.dismiss();
            isGamePaused = false;
            shopDialogOpen = false;
            lastShopCloseTime = System.currentTimeMillis();
            hideSystemUI();
        });

        List<ShopItem> items = getShopItemsForFloor(floorManager.getCurrentFloor());

        if (items.isEmpty()) {
            loading.setVisibility(android.view.View.VISIBLE);
            container.setVisibility(android.view.View.GONE);
            // Poll until batch is cached
            pollForBatchThenPopulate(dialog, loading, container, goldDisplay);
        } else {
            loading.setVisibility(android.view.View.GONE);
            container.setVisibility(android.view.View.VISIBLE);
            populateShopGridFromItems(dialog, container, goldDisplay, items);
        }

        dialog.show();
        hideSystemUIOnWindow(dialog.getWindow());
    }

    private void pollForBatchThenPopulate(android.app.Dialog dialog,
                                          android.view.View loadingView,
                                          android.view.View itemsContainer,
                                          TextView goldDisplay) {
        new android.os.Handler().postDelayed(() -> {
            if (!dialog.isShowing()) return;
            List<ShopItem> items =
                    getShopItemsForFloor(floorManager.getCurrentFloor());
            if (!items.isEmpty()) {
                runOnUiThread(() -> {
                    loadingView.setVisibility(android.view.View.GONE);
                    itemsContainer.setVisibility(android.view.View.VISIBLE);
                    populateShopGridFromItems(dialog, itemsContainer, goldDisplay, items);
                });
            } else {
                pollForBatchThenPopulate(dialog, loadingView, itemsContainer, goldDisplay);
            }
        }, 500);
    }

    /**
     * Checks two things while player is in the shop room:
     * 1. Is the player close enough to the shop counter? → open shop UI (Task 9)
     * 2. Is the player touching the exit portal? → go to next floor
     */
    private void checkShopRoomInteractions() {
        if (!inShopRoom) return;

        // Enforce wall collision every frame
        shopRoom.enforceWallCollision(player);

        // Check exit portal
        float distToExit = getDistance(
                player.x, player.y,
                ShopRoom.EXIT_PORTAL_X, ShopRoom.EXIT_PORTAL_Y
        );
        if (distToExit < player.radius + ShopRoom.EXIT_PORTAL_RADIUS && !isTransitioning) {
            if (isGuest()) {
                portalRequested = true;
            } else {
                isTransitioning = true;
                runOnUiThread(this::exitShopRoom);
            }
        }

        float distToShop = getDistance(
                player.x, player.y,
                ShopRoom.SHOP_X, ShopRoom.SHOP_Y
        );
        if (distToShop < player.radius + ShopRoom.SHOP_INTERACT_RADIUS
                && !shopDialogOpen && !isTransitioning
                && System.currentTimeMillis() - lastShopCloseTime > SHOP_REOPEN_COOLDOWN) {
            shopDialogOpen = true;
            runOnUiThread(this::showShopDialog);
        }
    }

    private int getRarityColor(String rarity) {
        if (rarity == null) return ContextCompat.getColor(this, R.color.rarity_common);
        switch (rarity.toUpperCase()) {
            case "RARE":      return ContextCompat.getColor(this, R.color.rarity_rare);
            case "EPIC":      return ContextCompat.getColor(this, R.color.arcane_glow); // rarity_epic is too dark for text
            case "LEGENDARY": return ContextCompat.getColor(this, R.color.rarity_legendary);
            default:          return ContextCompat.getColor(this, R.color.rarity_common);
        }
    }

    private void showDetailStats(LinearLayout box, ShopItem item) {
        switch (item.type) {
            case WEAPON:
                addStatRow(box, R.drawable.ic_crossed_swords, "Damage", String.valueOf(item.damage));
                addStatRow(box, R.drawable.ic_range, "Range", String.valueOf((int) item.range));
                addStatRow(box, R.drawable.ic_hourglass, "Cooldown", item.cooldown + "ms");
                addStatRow(box, R.drawable.ic_knockback, "Knockback", String.valueOf(item.knockback));
                addStatRow(box, R.drawable.ic_target, "Attack", item.isProjectile ? "Projectile" : "Melee");
                break;
            case ARMOR:
                String slot = String.valueOf(item.armorSlot);
                addStatRow(box, R.drawable.ic_shield, "Defense", "+" + item.defense);
                addStatRow(box, R.drawable.ic_anvil, "Durability", String.valueOf((int) item.maxDurability));
                addStatRow(box, slotIcon(slot), "Slot", slot);
                break;
            case CONSUMABLE:
                showConsumableStats(box, item);
                break;
        }
    }

    // One icon + label + value line, reusing the stats screen row
    private void addStatRow(LinearLayout box, int icon, String label, String value) {
        View row = getLayoutInflater().inflate(R.layout.item_stat_row, box, false);
        android.widget.ImageView iconView = row.findViewById(R.id.stat_icon);
        iconView.setImageResource(icon);
        iconView.setVisibility(View.VISIBLE);
        ((TextView) row.findViewById(R.id.stat_label)).setText(label);
        ((TextView) row.findViewById(R.id.stat_value)).setText(value);
        box.addView(row);
    }

    private int slotIcon(String slot) {
        switch (slot.toUpperCase()) {
            case "HEAD":  return R.drawable.ic_helmet;
            case "LEGS":  return R.drawable.ic_leg_armor;
            case "BOOTS": return R.drawable.ic_boots;
            default:      return R.drawable.ic_chest_armor;
        }
    }

    /**
     * Instantly kills all active enemies without dropping coins.
     * Called when the exit portal opens.
     */
    private void killAllActiveEnemies() {
        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (!e.isDead) {
                    e.noGoldDrop = true;
                    e.takeDamage(Integer.MAX_VALUE);
                }
            }
        }
    }

    /**
     * Prepares the next floor: advances FloorManager, clears game objects,
     * generates a new procedural layout, spawns enemies and saves to Firestore.
     */
    private void prepareNextFloor() {
        floorManager.advanceFloor();
        if (coop != null) {
            if (playerHealth <= 0) {
                // Fallen players rejoin at half health
                player.resetDeathAnimation();
                playerHealth = maxHealth / 2;
                playerHealthBar.setProgress(playerHealth);
                if (txtHealthValue != null) txtHealthValue.setText(playerHealth + " / " + maxHealth);
            }
            portalRequested = false;
            appliedSnapshot = null;
            deadEnemyIds.clear();
            if (coop.isHost()) coop.clearEvents();
        }
        // A co-op guest can be pulled to the next floor while shopping
        if (shopDialog != null && shopDialog.isShowing()) shopDialog.dismiss();
        shopDialogOpen = false;
        statsManager.onFloorCleared(floorManager.getCurrentFloor());
        statsManager.flush();
        setCheckpoint(floorManager.getCurrentFloor());

        activeBoss = null;
        bossSpawnHandled = false;

        synchronized (enemies)       { enemies.clear(); }
        synchronized (goldCoins)     { goldCoins.clear(); }
        synchronized (treasureChests){ treasureChests.clear(); }
        synchronized (projectiles)   { projectiles.clear(); }
        effects.clear();

        enemiesKilledOnPortalOpen = false;
        enteringShop = false;
        shopBoughtIndices.clear(); // New floor → new shop stock, reset purchase state

        // Generate a fresh procedural layout for the new floor
        currentLayout = generateLayout();
        gameDisplay.setFloorLayout(currentLayout);
        player.x = currentLayout.entryRoom.centerX();
        player.y = currentLayout.entryRoom.centerY();
        currentLayout.entryRoom.explored = true;
        if (currentLayout.exitRoom != null) {
            floorManager.setExitRoomCenter(
                    currentLayout.exitRoom.centerX(),
                    currentLayout.exitRoom.centerY());
        }
        spawnEnemiesForLayout(currentLayout);
        // This floor's batch should be cached already; start the next range two floors early
        ensureBatchFor(floorManager.getCurrentFloor());
        ensureBatchFor(floorManager.getCurrentFloor() + 2);
        Log.d("FloorLayout", "Floor " + floorManager.getCurrentFloor()
                + " layout: " + currentLayout.rooms.size() + " rooms, "
                + currentLayout.corridors.size() + " corridors");

        updateFloorHUD();
        saveGameToFirestore();
    }

    /**
     * Refreshes the floor number and kill-progress HUD display.
     */
    private void updateFloorHUD() {
        String shown = floorManager.getCurrentFloor() + ":" + floorManager.getKillsThisFloor()
                + ":" + floorManager.getKillsRequired();
        if (shown.equals(floorHudShown)) return;
        floorHudShown = shown;
        runOnUiThread(() -> {
            txtFloorNumber.setText("Floor " + floorManager.getCurrentFloor());
            txtKillProgress.setText(
                    floorManager.getKillsThisFloor() + " / " + floorManager.getKillsRequired()
            );
        });
    }

    /** Handles boss spawn trigger — runs inside the normal game loop. */
    private void checkBossStatus() {
        if (!floorManager.isBossFloor()) return;
        if (!floorManager.shouldSpawnBoss() || bossSpawnHandled) return;

        bossSpawnHandled = true;
        loadBossIdentity();

        if (isGuest()) {
            announceBoss(); // the boss itself arrives in the host's snapshot
            return;
        }

        // Make sure the next range is ready by the time this boss falls
        ensureBatchFor(floorManager.getCurrentFloor() + 1);

        spawnBoss();
    }

    /** Boss name, title and colour for this floor, from the AI batch when it has one. */
    private void loadBossIdentity() {
        BatchResult bossData = getBossDataForFloor(floorManager.getCurrentFloor());
        if (bossData != null) {
            currentBossName  = bossData.bossName;
            currentBossTitle = bossData.bossTitle;
            currentBossColor = bossData.bossColor;
        } else {
            currentBossName  = "THE ANCIENT ONE";
            currentBossTitle = "Ruler of the Void";
            currentBossColor = android.graphics.Color.RED;
        }
    }

    /**
     * Boss death detection — runs every frame regardless of pause state
     * so a boss kill is never missed during a transition or menu open.
     */
    private void checkBossDeathDetection() {
        if (!floorManager.isBossFloor()) return;
        if (activeBoss == null || !activeBoss.isDead) return;
        if (floorManager.isBossDefeated()) return;

        floorManager.onBossDefeated(player.x, player.y);
        soundManager.playBossDeath();
        statsManager.onBossKilled();
        behaviorTracker.onBossKilled();
        spawnTreasureChests(activeBoss.x, activeBoss.y);
    }

    /**
     * Spawns a single boss enemy near the player.
     * All regular spawning stops once the boss is alive.
     */
    private void spawnBoss() {
        float spawnX, spawnY;
        if (currentLayout != null && currentLayout.bossRoom != null) {
            spawnX = currentLayout.bossRoom.centerX();
            spawnY = currentLayout.bossRoom.centerY();
        } else {
            double angle = Math.random() * Math.PI * 2;
            spawnX = player.getX() + (float)(Math.cos(angle) * 500f);
            spawnY = player.getY() + (float)(Math.sin(angle) * 500f);
        }

        activeBoss = new Enemy(spawnX, spawnY, Enemy.Type.BOSS);
        activeBoss.isAwake = true; // Boss is always immediately active
        activeBoss.applyFloorScaling(floorManager.getCurrentFloor());
        BatchResult bossData = getBossDataForFloor(floorManager.getCurrentFloor());
        int floor = floorManager.getCurrentFloor();
        activeBoss.applyBossScaling(
                bossData != null ? bossData.bossHpMultiplier : BatchGenerator.bossHpMultiplier(floor),
                bossData != null ? bossData.bossDmgMultiplier : BatchGenerator.bossDmgMultiplier(floor));
        activeBoss.setBehaviorTracker(behaviorTracker);
        activeBoss.setLayout(currentLayout);

        // Apply boss AI name
        if (currentBossName != null) {
            activeBoss.displayName  = currentBossName;
            activeBoss.displayColor = currentBossColor;
        }

        enemies.add(activeBoss);
        announceBoss();
    }

    /** Sweeps the boss title card across the screen: label, gold rules, name slam, title. */
    private void announceBoss() {
        final String name  = currentBossName  != null ? currentBossName.toUpperCase()  : "THE ANCIENT ONE";
        final String title = currentBossTitle != null ? currentBossTitle : "";
        final int    color = currentBossColor;
        final int    floor = floorManager.getCurrentFloor();

        runOnUiThread(() -> {
            soundManager.playBossSpawn();
            float dp = getResources().getDisplayMetrics().density;
            android.view.animation.DecelerateInterpolator ease =
                    new android.view.animation.DecelerateInterpolator(2f);

            bossLabel.setText("GUARDIAN OF FLOOR " + floor);
            bossName.setText(name);
            bossName.setTextColor(color);
            bossName.setShadowLayer(18f, 0f, 0f, color);
            bossTitle.setText(title);
            bossTitle.setVisibility(title.isEmpty() ? View.GONE : View.VISIBLE);

            // Start state
            bossIntro.animate().cancel();
            bossIntro.setAlpha(1f);
            bossIntro.setVisibility(View.VISIBLE);
            bossBand.setAlpha(0f);
            bossBand.setScaleY(0.4f);
            bossLabel.setAlpha(0f);
            bossLabel.setTranslationY(-12 * dp);
            bossLineTop.setScaleX(0f);
            bossLineBottom.setScaleX(0f);
            bossName.setAlpha(0f);
            bossName.setScaleX(1.4f);
            bossName.setScaleY(1.4f);
            bossTitle.setAlpha(0f);
            bossTitle.setTranslationY(12 * dp);

            bossBand.animate().alpha(1f).scaleY(1f).setStartDelay(0).setDuration(280).setInterpolator(ease).start();
            bossLabel.animate().alpha(1f).translationY(0f).setStartDelay(200).setDuration(320).start();
            bossLineTop.animate().scaleX(1f).setStartDelay(150).setDuration(500).setInterpolator(ease).start();
            bossLineBottom.animate().scaleX(1f).setStartDelay(150).setDuration(500).setInterpolator(ease).start();
            bossName.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(350).setDuration(380)
                    .setInterpolator(ease)
                    .withEndAction(() -> { if (camera != null) camera.startShake(300, 10f); })
                    .start();
            bossTitle.animate().alpha(1f).translationY(0f).setStartDelay(700).setDuration(380).start();

            // Hold, then fade the whole card away
            bossIntro.animate().alpha(0f).setStartDelay(3000).setDuration(500)
                    .withEndAction(() -> bossIntro.setVisibility(View.GONE))
                    .start();
        });
    }

    /**
     * Refreshes the attack UI — always shows AimJoystick for all weapon types.
     */
    private void refreshAttackUI() {
        runOnUiThread(() -> {
            btnAttack.setVisibility(View.GONE);
            aimJoystick.setVisibility(View.VISIBLE);
        });
    }

    /** Spawns 3 chests at 120° intervals around the boss death position on walkable floor. */
    private void spawnTreasureChests(float bossX, float bossY) {
        int floor = floorManager.getCurrentFloor();
        List<ShopItem> pool = getChestItemPool(floor);

        synchronized (treasureChests) {
            treasureChests.clear();
            for (int i = 0; i < 3; i++) {
                float angle = (float)(i * Math.PI * 2.0 / 3.0 + Math.random() * 0.4);
                float cx = bossX, cy = bossY;

                // Start at 400 units out and walk closer until we land on walkable floor
                boolean placed = false;
                for (float d = 400f; d >= 80f; d -= 60f) {
                    float tx = bossX + (float)(Math.cos(angle) * d);
                    float ty = bossY + (float)(Math.sin(angle) * d);
                    if (currentLayout == null || currentLayout.isFloor(tx, ty)) {
                        cx = tx; cy = ty;
                        placed = true;
                        break;
                    }
                }
                if (!placed) {
                    // Last resort: stay near boss
                    cx = bossX + (float)(Math.cos(angle) * 80f);
                    cy = bossY + (float)(Math.sin(angle) * 80f);
                }

                TreasureChest chest = new TreasureChest(cx, cy);
                if (!pool.isEmpty()) {
                    ShopItem reward = pool.get((int)(Math.random() * pool.size()));
                    chest.rewardItem      = reward;
                    chest.rewardItemName  = reward.name;
                    chest.rewardItemColor = getRarityColor(reward.rarity);
                }
                treasureChests.add(chest);
            }
        }
    }

    /** Returns all shop items for the 5-floor range that contains the given floor. */
    private List<ShopItem> getChestItemPool(int floor) {
        int rangeStart = getRangeStart(floor);
        BatchResult result = batchCache.get(rangeStart);
        if (result == null || result.shopByFloor == null) return new ArrayList<>();
        List<ShopItem> pool = new ArrayList<>();
        for (int f = rangeStart; f < rangeStart + 5; f++) {
            List<ShopItem> items = result.shopByFloor.get(f);
            if (items != null) pool.addAll(items);
        }
        return pool;
    }

    /** Opens a chest when player walks close enough; gives the stored reward item. */
    private void checkChestInteraction() {
        synchronized (treasureChests) {
            for (TreasureChest chest : treasureChests) {
                if (chest.opened) continue;
                float dist = getDistance(player.x, player.y, chest.x, chest.y);
                if (dist < player.radius + TreasureChest.OPEN_RADIUS) {
                    chest.opened = true;
                    soundManager.playChestOpen();
                    if (chest.rewardItem != null) {
                        final ShopItem reward = chest.rewardItem;
                        runOnUiThread(() -> purchaseItem(reward));
                    }
                }
            }
        }
    }

    // ─── DIALOGS ──────────────────────────────────────────────────────────────
    private void showMenuDialog() {
        if (coop == null) isGamePaused = true;

        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_menu);

        if(dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        MaterialButton btnResume = dialog.findViewById(R.id.btn_resume);
        MaterialButton btnMainMenu = dialog.findViewById(R.id.btn_main_menu);
        MaterialButton btnSettings = dialog.findViewById(R.id.btn_settings);

        btnResume.setOnClickListener(v -> {
            isGamePaused = false;
            hideSystemUI();
            dialog.dismiss();
        });

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                dialog.dismiss();
                showMenuOnResume = true;
                Intent intent = new Intent(GameActivity.this, SettingsActivity.class);
                intent.putExtra(SettingsActivity.EXTRA_FROM_GAME, true);
                startActivity(intent);
            });
        }

        btnMainMenu.setOnClickListener(v -> {
            leaveCoop();
            SoundManager.stopGameMusic();
            Intent intent = new Intent(GameActivity.this, MenuActivity.class);
            startActivity(intent);
            finish();
        });

        dialog.show();
        hideSystemUIOnWindow(dialog.getWindow());
    }

    private void setupFilterTab(Dialog dialog, int viewId, String filter) {
        TextView tab = dialog.findViewById(viewId);
        tab.setOnClickListener(v -> {
            currentInventoryFilter = filter;
            inventoryPage = 0;

            int[] ids = {R.id.filter_all, R.id.filter_weapons,
                    R.id.filter_armor, R.id.filter_consumables};
            String[] filters = {"ALL", "WEAPONS", "ARMOR", "CONSUMABLES"};

            for (int i = 0; i < ids.length; i++) {
                TextView t = dialog.findViewById(ids[i]);
                boolean sel = filters[i].equals(filter);
                t.setTextColor(ContextCompat.getColor(this, sel ? R.color.gold_bright : R.color.bone_muted));
                if (sel) {
                    android.graphics.drawable.GradientDrawable bg =
                            new android.graphics.drawable.GradientDrawable();
                    bg.setColor(ContextCompat.getColor(this, R.color.bg_surface_alt));
                    bg.setCornerRadius(6f);
                    t.setBackground(bg);
                } else {
                    t.setBackground(null);
                }
            }
            populateInventoryList(dialog);
        });
    }

    private void updateEquippedDisplay(Dialog dialog) {
        TextView wView = dialog.findViewById(R.id.txt_equipped_weapon);
        if (wView != null) {
            Weapon w = player.getWeapon();
            wView.setText(w.name);
            wView.setTextColor(w.color);
        }
        updateArmorSlotView(dialog.findViewById(R.id.txt_equipped_head),  Armor.Slot.HEAD);
        updateArmorSlotView(dialog.findViewById(R.id.txt_equipped_chest), Armor.Slot.CHEST);
        updateArmorSlotView(dialog.findViewById(R.id.txt_equipped_legs),  Armor.Slot.LEGS);
        updateArmorSlotView(dialog.findViewById(R.id.txt_equipped_boots), Armor.Slot.BOOTS);
    }

    private void updateArmorSlotView(TextView view, Armor.Slot slot) {
        if (view == null) return;
        Armor armor = equippedArmor.get(slot);
        if (armor != null) {
            view.setText(armor.name);
            view.setTextColor(getRarityColor(armor.rarity.name()));
        } else {
            view.setText("Empty");
            view.setTextColor(ContextCompat.getColor(this, R.color.bone_muted));
        }
    }

    private void populateInventoryList(Dialog dialog) {
        LinearLayout container = dialog.findViewById(R.id.inventory_list_container);
        if (container == null) return;
        // The page size comes from the grid's measured size, so wait for the first layout
        if (!container.isLaidOut()) {
            container.post(() -> populateInventoryList(dialog));
            return;
        }
        container.removeAllViews();

        String f = currentInventoryFilter;
        List<Object> items = new ArrayList<>();
        if (f.equals("ALL") || f.equals("WEAPONS")) items.addAll(weapons);
        if (f.equals("ALL") || f.equals("ARMOR")) items.addAll(inventoryArmors);
        if (f.equals("ALL") || f.equals("CONSUMABLES")) items.addAll(consumableInventory);
        // Stable sorts, so ties keep the weapons / armor / consumables order
        if (inventorySort.equals("Name"))
            items.sort((a, b) -> itemName(a).compareToIgnoreCase(itemName(b)));
        else if (!inventorySort.equals("Type"))
            items.sort((a, b) -> Float.compare(sortValue(b), sortValue(a)));

        // As many square cells as fit: rows of at least 80dp, at least 4 per row
        float w = Math.max(1, container.getWidth()), h = Math.max(1, container.getHeight());
        int rows = Math.max(1, (int) (h / (80 * getResources().getDisplayMetrics().density)));
        int cols = Math.max(4, (int) Math.ceil(w * rows / h));
        int perPage = rows * cols;
        int pages = Math.max(1, (items.size() + perPage - 1) / perPage);
        inventoryPage = Math.max(0, Math.min(inventoryPage, pages - 1));
        int start = inventoryPage * perPage;

        LinearLayout row = null;
        for (int i = start; i < Math.min(items.size(), start + perPage); i++) {
            if ((i - start) % cols == 0) {
                row = new LinearLayout(this);
                row.setWeightSum(cols);
                container.addView(row);
            }
            Object item = items.get(i);
            if (item instanceof Weapon) addWeaponRow(row, dialog, (Weapon) item);
            else if (item instanceof Armor) addArmorRow(row, dialog, (Armor) item);
            else addConsumableRow(row, dialog, (ConsumableStack) item);
        }

        ((TextView) dialog.findViewById(R.id.txt_inventory_page)).setText((inventoryPage + 1) + " / " + pages);
        View prev = dialog.findViewById(R.id.btn_page_prev);
        View next = dialog.findViewById(R.id.btn_page_next);
        prev.setEnabled(inventoryPage > 0);
        prev.setAlpha(inventoryPage > 0 ? 1f : 0.3f);
        next.setEnabled(inventoryPage < pages - 1);
        next.setAlpha(inventoryPage < pages - 1 ? 1f : 0.3f);

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nothing here...");
            empty.setTextColor(ContextCompat.getColor(this, R.color.bone_muted));
            empty.setTextSize(16f);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, 48, 0, 48);
            container.addView(empty);
        }
    }

    // Higher sorts first; -1 when the stat doesn't apply, so those items drop to the end
    private float sortValue(Object o) {
        switch (inventorySort) {
            case "Rarity":
                String r = o instanceof Weapon ? ((Weapon) o).rarity.name()
                        : o instanceof Armor ? ((Armor) o).rarity.name()
                        : String.valueOf(((ConsumableStack) o).item.rarity).toUpperCase();
                return java.util.Arrays.asList("COMMON", "RARE", "EPIC", "LEGENDARY").indexOf(r);
            case "Damage":     return o instanceof Weapon ? ((Weapon) o).damage : -1;
            case "Defense":    return o instanceof Armor ? ((Armor) o).defense : -1;
            case "Durability": return o instanceof Armor ? ((Armor) o).durability : -1;
            case "Healing":
                return o instanceof ConsumableStack && "heal".equals(((ConsumableStack) o).item.effect)
                        ? ((ConsumableStack) o).item.effectValue : -1;
            default:           return 0;
        }
    }

    private String itemName(Object o) {
        return o instanceof Weapon ? ((Weapon) o).name
                : o instanceof Armor ? ((Armor) o).name : ((ConsumableStack) o).item.name;
    }

    // Share of an item's worth paid when selling; rarer, stronger and less worn items fetch more
    private int sellValue(Object o) {
        if (o instanceof ConsumableStack) return Math.max(1, ((ConsumableStack) o).item.price * 2 / 5);
        if (o instanceof Weapon) {
            Weapon w = (Weapon) o;
            return Math.max(1, Math.round((5 + w.damage) * SELL_RARITY[w.rarity.ordinal()]));
        }
        Armor a = (Armor) o;
        float wear = 0.5f + 0.5f * a.durability / Math.max(1f, a.maxDurability);
        return Math.max(1, Math.round((5 + a.defense * 2) * SELL_RARITY[a.rarity.ordinal()] * wear));
    }

    private void earnGold(int amount, String itemName) {
        playerGold += amount;
        updateGoldHUD();
        soundManager.playCoinPickup();
        Toast.makeText(this, "Sold " + itemName + " for " + amount + " gold", Toast.LENGTH_SHORT).show();
    }

    private void showSortPicker(Dialog dialog, MaterialButton sortBtn) {
        android.app.AlertDialog picker = new android.app.AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Sort by")
                .setSingleChoiceItems(SORT_KEYS, java.util.Arrays.asList(SORT_KEYS).indexOf(inventorySort),
                        (d, which) -> {
                            inventorySort = SORT_KEYS[which];
                            inventoryPage = 0;
                            sortBtn.setText("Sort: " + inventorySort);
                            populateInventoryList(dialog);
                            d.dismiss();
                        })
                .create();
        picker.show();
        hideSystemUIOnWindow(picker.getWindow());
    }

    private void showInventoryDialog() {
        if (coop == null) isGamePaused = true;
        currentInventoryFilter = "ALL";

        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_inventory);
        dialog.setCancelable(false);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        dialog.findViewById(R.id.btn_close_inventory).setOnClickListener(v -> {
            dialog.dismiss();
            isGamePaused = false;
            hideSystemUI();
        });

        MaterialButton sortBtn = dialog.findViewById(R.id.btn_sort_inventory);
        sortBtn.setText("Sort: " + inventorySort);
        sortBtn.setOnClickListener(v -> showSortPicker(dialog, sortBtn));

        inventoryPage = 0;
        dialog.findViewById(R.id.btn_page_prev).setOnClickListener(v -> {
            inventoryPage--;
            populateInventoryList(dialog);
        });
        dialog.findViewById(R.id.btn_page_next).setOnClickListener(v -> {
            inventoryPage++;
            populateInventoryList(dialog);
        });

        setupFilterTab(dialog, R.id.filter_all,          "ALL");
        setupFilterTab(dialog, R.id.filter_weapons,      "WEAPONS");
        setupFilterTab(dialog, R.id.filter_armor,        "ARMOR");
        setupFilterTab(dialog, R.id.filter_consumables,  "CONSUMABLES");

        updateEquippedDisplay(dialog);
        populateInventoryList(dialog);

        dialog.show();
        hideSystemUIOnWindow(dialog.getWindow());
    }

    private void addWeaponRow(LinearLayout row, Dialog dialog, Weapon weapon) {
        View cell = getLayoutInflater().inflate(R.layout.dialog_inventory_item, row, false);

        TextView nameView    = cell.findViewById(R.id.inv_item_name);
        TextView subView     = cell.findViewById(R.id.inv_item_subtitle);
        TextView equippedBadge = cell.findViewById(R.id.inv_equipped_badge);
        ((android.widget.ImageView) cell.findViewById(R.id.inv_item_icon))
                .setImageResource(weapon.isProjectile ? R.drawable.ic_range : R.drawable.ic_sword);

        int rarityColor = getRarityColor(weapon.rarity.name());
        nameView.setText(weapon.name);
        nameView.setTextColor(rarityColor);
        subView.setText("DMG " + weapon.damage);

        boolean isEquipped = player.getWeapon() == weapon;
        equippedBadge.setVisibility(isEquipped ? View.VISIBLE : View.GONE);

        applyRarityBorder(cell, rarityColor);
        cell.setOnClickListener(v -> showWeaponActionDialog(dialog, weapon));
        addCell(row, cell);
    }

    private void addArmorRow(LinearLayout row, Dialog dialog, Armor armor) {
        View cell = getLayoutInflater().inflate(R.layout.dialog_inventory_item, row, false);

        TextView nameView    = cell.findViewById(R.id.inv_item_name);
        TextView subView     = cell.findViewById(R.id.inv_item_subtitle);
        TextView equippedBadge = cell.findViewById(R.id.inv_equipped_badge);
        ((android.widget.ImageView) cell.findViewById(R.id.inv_item_icon))
                .setImageResource(slotIcon(armor.slot.name()));

        int rarityColor = getRarityColor(armor.rarity.name());
        nameView.setText(armor.name);
        nameView.setTextColor(rarityColor);
        subView.setText("DEF " + armor.defense);

        boolean isEquipped = equippedArmor.get(armor.slot) == armor;
        equippedBadge.setVisibility(isEquipped ? View.VISIBLE : View.GONE);

        applyRarityBorder(cell, rarityColor);
        cell.setOnClickListener(v -> showArmorActionDialog(dialog, armor));
        addCell(row, cell);
    }

    private void addConsumableRow(LinearLayout row, Dialog dialog, ConsumableStack stack) {
        View cell = getLayoutInflater().inflate(R.layout.dialog_inventory_item, row, false);

        TextView nameView     = cell.findViewById(R.id.inv_item_name);
        TextView subView      = cell.findViewById(R.id.inv_item_subtitle);
        TextView quantityView = cell.findViewById(R.id.inv_item_quantity);
        boolean heal = "heal".equals(stack.item.effect);
        ((android.widget.ImageView) cell.findViewById(R.id.inv_item_icon))
                .setImageResource(heal ? R.drawable.ic_heart : R.drawable.ic_shield);

        int rarityColor = getRarityColor(stack.item.rarity);
        nameView.setText(stack.item.name);
        nameView.setTextColor(rarityColor);
        subView.setText("+" + stack.item.effectValue + (heal ? " HP" : " AR"));
        quantityView.setVisibility(View.VISIBLE);
        quantityView.setText("x" + stack.quantity);

        applyRarityBorder(cell, rarityColor);
        cell.setOnClickListener(v -> showConsumableActionDialog(dialog, stack));
        addCell(row, cell);
    }

    private void addCell(LinearLayout row, View cell) {
        int gap = Math.round(4 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(gap, gap, gap, gap);
        row.addView(cell, lp);
    }

    private void showWeaponActionDialog(Dialog parentDialog, Weapon weapon) {
        Dialog detail = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        detail.setContentView(R.layout.dialog_item_action);
        detail.setCancelable(false);
        if (detail.getWindow() != null)
            detail.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        bindActionDialog(detail, weapon.name, "WEAPON", weapon.rarity.name(),
                "", weapon.color);
        showWeaponStats(detail.findViewById(R.id.action_stats), weapon);

        MaterialButton btn = detail.findViewById(R.id.btn_action);
        boolean isEquipped = player.getWeapon() == weapon;
        btn.setText(isEquipped ? "Equipped" : "Equip");
        btn.setEnabled(!isEquipped);
        btn.setAlpha(isEquipped ? 0.4f : 1f);

        btn.setOnClickListener(v -> {
            player.equipWeapon(weapon);
            refreshAttackUI();
            updateEquippedDisplay(parentDialog);
            populateInventoryList(parentDialog);
            detail.dismiss();
            Toast.makeText(this, "Equipped: " + weapon.name, Toast.LENGTH_SHORT).show();
        });

        MaterialButton btnSellWeapon = detail.findViewById(R.id.btn_remove);
        boolean isFists = weapon == weapons.get(0);
        int weaponValue = sellValue(weapon);
        btnSellWeapon.setText("Sell  +" + weaponValue);
        btnSellWeapon.setVisibility(isFists ? View.GONE : View.VISIBLE);
        btnSellWeapon.setOnClickListener(v -> {
            if (player.getWeapon() == weapon) {
                player.equipWeapon(weapons.get(0)); // Re-equip Fists
                refreshAttackUI();
            }
            weapons.remove(weapon);
            inventoryWeaponIds.remove(weaponFirestoreIds.remove(weapon));
            earnGold(weaponValue, weapon.name);
            updateEquippedDisplay(parentDialog);
            populateInventoryList(parentDialog);
            detail.dismiss();
        });

        detail.findViewById(R.id.btn_close_action).setOnClickListener(v -> detail.dismiss());
        detail.show();
        hideSystemUIOnWindow(detail.getWindow());
    }

    private void showArmorActionDialog(Dialog parentDialog, Armor armor) {
        Dialog detail = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        detail.setContentView(R.layout.dialog_item_action);
        detail.setCancelable(false);
        if (detail.getWindow() != null)
            detail.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        int rarityColor = getRarityColor(armor.rarity.name());
        bindActionDialog(detail, armor.name, armor.slot.name(), armor.rarity.name(),
                "", rarityColor);
        showArmorStats(detail.findViewById(R.id.action_stats), armor);

        MaterialButton btn = detail.findViewById(R.id.btn_action);
        boolean isEquipped = equippedArmor.get(armor.slot) == armor;
        btn.setText(isEquipped ? "Equipped" : "Equip");
        btn.setEnabled(!isEquipped);
        btn.setAlpha(isEquipped ? 0.4f : 1f);

        btn.setOnClickListener(v -> {
            equipArmorPiece(armor, parentDialog);
            detail.dismiss();
        });

        MaterialButton btnSellArmor = detail.findViewById(R.id.btn_remove);
        int armorValue = sellValue(armor);
        btnSellArmor.setText("Sell  +" + armorValue);
        btnSellArmor.setOnClickListener(v -> {
            // Unequip if currently equipped
            if (equippedArmor.get(armor.slot) == armor) {
                equippedArmor.remove(armor.slot);
                recalculateMaxArmor();
            }
            inventoryArmors.remove(armor);
            inventoryArmorIds.remove(armorFirestoreIds.remove(armor));
            earnGold(armorValue, armor.name);
            updateEquippedDisplay(parentDialog);
            populateInventoryList(parentDialog);
            detail.dismiss();
        });

        detail.findViewById(R.id.btn_close_action).setOnClickListener(v -> detail.dismiss());
        detail.show();
        hideSystemUIOnWindow(detail.getWindow());
    }

    private void showConsumableActionDialog(Dialog parentDialog, ConsumableStack stack) {
        Dialog detail = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        detail.setContentView(R.layout.dialog_item_action);
        detail.setCancelable(false);
        if (detail.getWindow() != null)
            detail.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        int rarityColor = getRarityColor(stack.item.rarity);
        bindActionDialog(detail, stack.item.name, "CONSUMABLE", stack.item.rarity,
                stack.item.description, rarityColor);
        showConsumableStats(detail.findViewById(R.id.action_stats), stack.item);

        MaterialButton btn = detail.findViewById(R.id.btn_action);
        btn.setText("Use  (x" + stack.quantity + ")");

        btn.setOnClickListener(v -> {
            useConsumable(stack, parentDialog);
            detail.dismiss();
        });

        // Sells one from the stack
        MaterialButton btnSellConsumable = detail.findViewById(R.id.btn_remove);
        int potionValue = sellValue(stack);
        btnSellConsumable.setText("Sell one  +" + potionValue);
        btnSellConsumable.setOnClickListener(v -> {
            if (stack.decrement()) consumableInventory.remove(stack);
            earnGold(potionValue, stack.item.name);
            updatePotionButton();
            populateInventoryList(parentDialog);
            detail.dismiss();
        });

        detail.findViewById(R.id.btn_close_action).setOnClickListener(v -> detail.dismiss());
        detail.show();
        hideSystemUIOnWindow(detail.getWindow());
    }

    private void bindActionDialog(Dialog dialog, String name, String type, String rarity,
                                  String description, int nameColor) {
        int rarityColor = getRarityColor(rarity);

        ((TextView) dialog.findViewById(R.id.action_name)).setText(name);
        ((TextView) dialog.findViewById(R.id.action_name)).setTextColor(nameColor);
        ((TextView) dialog.findViewById(R.id.action_type)).setText(type);
        ((TextView) dialog.findViewById(R.id.action_rarity)).setText(rarity);
        ((TextView) dialog.findViewById(R.id.action_rarity)).setTextColor(rarityColor);

        TextView descView = dialog.findViewById(R.id.action_description);
        if (description == null || description.isEmpty()) {
            descView.setVisibility(View.GONE);
        } else {
            descView.setText(description);
            descView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Equips an armour piece and recalculates maxArmor and current armour HP.
     * @param armor the armour piece to equip
     * @param inventoryDialog the inventory dialog to update (may be null)
     */
    private void equipArmorPiece(Armor armor, Dialog inventoryDialog) {
        // Only a never-worn piece adds its defense, so swapping gear back and forth can't refill armor
        int bonus = armor.worn ? 0 : armor.defense;
        armor.worn = true;
        equippedArmor.put(armor.slot, armor);
        // Inline max recalculation to avoid intermediate UI flash from recalculateMaxArmor()
        maxArmor = 0;
        for (Armor a : equippedArmor.values()) maxArmor += a.defense;
        playerArmor = Math.min(maxArmor, playerArmor + bonus);
        runOnUiThread(() -> {
            playerArmorBar.setMax(Math.max(1, maxArmor));
            playerArmorBar.setProgress(playerArmor);
            if (txtArmorValue != null) txtArmorValue.setText(playerArmor + " / " + maxArmor);
            if (inventoryDialog != null && inventoryDialog.isShowing()) {
                updateEquippedDisplay(inventoryDialog);
                populateInventoryList(inventoryDialog);
            }
        });
    }

    /**
     * Recalculates maxArmor from the total defense of all equipped armour pieces
     * and updates the armour bar in the UI.
     */
    private void recalculateMaxArmor() {
        maxArmor = 0;
        for (Armor a : equippedArmor.values()) maxArmor += a.defense;
        playerArmor = Math.max(0, Math.min(playerArmor, maxArmor));
        runOnUiThread(() -> {
            playerArmorBar.setMax(Math.max(1, maxArmor));
            playerArmorBar.setProgress(playerArmor);
            if (txtArmorValue != null) txtArmorValue.setText(playerArmor + " / " + maxArmor);
        });
    }

    /**
     * Uses a consumable item and applies its effect (heal or armour restore).
     * Removes the stack from the inventory when it becomes empty.
     * @param stack the consumable stack to use
     * @param inventoryDialog the inventory dialog to update
     */
    private void useConsumable(ConsumableStack stack, Dialog inventoryDialog) {
        if (playerHealth <= 0) return; // a fallen co-op player waits for the next floor
        ShopItem item = stack.item;
        if ("heal".equals(item.effect)) {
            int newHp = Math.min(maxHealth, playerHealth + item.effectValue);
            effects.number(player.x, player.y - player.radius, "+" + (newHp - playerHealth),
                    Color.rgb(90, 230, 110));
            runOnUiThread(() -> {
                animateHealthBar(playerHealth, newHp);
                playerHealth = newHp;
            });
        } else if ("armor_restore".equals(item.effect)) {
            int newArmor = Math.min(maxArmor, playerArmor + item.effectValue);
            runOnUiThread(() -> {
                animateArmorBar(playerArmor, newArmor);
                playerArmor = newArmor;
            });
        }

        boolean empty = stack.decrement();
        if (empty) consumableInventory.remove(stack);

        runOnUiThread(() -> {
            updatePotionButton();
            if (inventoryDialog != null && inventoryDialog.isShowing()) {
                populateInventoryList(inventoryDialog);
            }
        });
    }

    private void showWeaponStats(LinearLayout box, Weapon weapon) {
        addStatRow(box, R.drawable.ic_crossed_swords, "Damage", String.valueOf(weapon.damage));
        addStatRow(box, R.drawable.ic_range, "Range", String.valueOf((int) weapon.range));
        addStatRow(box, R.drawable.ic_hourglass, "Cooldown", weapon.cooldown + "ms");
        addStatRow(box, R.drawable.ic_knockback, "Knockback", String.valueOf(weapon.knockback));
        addStatRow(box, R.drawable.ic_target, "Attack", weapon.isProjectile ? "Projectile" : "Melee");
    }

    private void showArmorStats(LinearLayout box, Armor armor) {
        addStatRow(box, R.drawable.ic_shield, "Defense", "+" + armor.defense);
        addStatRow(box, R.drawable.ic_anvil, "Durability", String.valueOf((int) armor.maxDurability));
        addStatRow(box, slotIcon(armor.slot.name()), "Slot", armor.slot.name());
    }

    private void showConsumableStats(LinearLayout box, ShopItem item) {
        boolean heal = "heal".equals(item.effect);
        addStatRow(box, heal ? R.drawable.ic_heart : R.drawable.ic_shield, "Restores",
                "+" + item.effectValue + (heal ? " HP" : " Armor"));
    }

    private void applyRarityBorder(View view, int color) {
        android.graphics.drawable.GradientDrawable border =
                new android.graphics.drawable.GradientDrawable();
        border.setColor(ContextCompat.getColor(this, R.color.bg_surface));
        border.setCornerRadius(10f);
        border.setStroke(2, color);
        view.setBackground(border);
    }

    /**
     * Shows the Game Over dialog with options: respawn from checkpoint, or return to menu.
     */
    private void showGameOverDialog() {
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_game_over);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView txtCheckpoint = dialog.findViewById(R.id.txt_checkpoint);
        txtCheckpoint.setText("Last checkpoint: Floor " + checkpointFloor);

        MaterialButton btnRespawn = dialog.findViewById(R.id.btn_respawn);
        btnRespawn.setOnClickListener(v -> {
            respawnPlayer();
            dialog.dismiss();
            hideSystemUI();
        });

        MaterialButton btnMenu = dialog.findViewById(R.id.btn_game_over_menu);
        btnMenu.setOnClickListener(v -> {
            dialog.dismiss();
            if (gameId != null && userId != null) {
                dbManager.deleteGame(userId, gameId, success ->
                        runOnUiThread(() -> {
                            startActivity(new Intent(GameActivity.this, MenuActivity.class));
                            finish();
                        })
                );
            } else {
                startActivity(new Intent(GameActivity.this, MenuActivity.class));
                finish();
            }
        });

        dialog.show();
        hideSystemUIOnWindow(dialog.getWindow());
    }

    /**
     * Saves the current checkpoint state (floor, HP, armour, gold).
     * @param floor current floor number
     */
    private void setCheckpoint(int floor) {
        checkpointFloor  = floor;
        checkpointHealth = playerHealth;
        checkpointArmor  = playerArmor;
        checkpointGold   = playerGold;
    }

    /**
     * Refreshes the gold display in the HUD to the current amount.
     */
    private void updateGoldHUD() {
        runOnUiThread(() -> txtGold.setText(String.valueOf(playerGold)));
    }

    /* ============================================================
       PROGRESSION — XP and levels
       ============================================================ */

    /** XP needed to go from the current level to the next. */
    private int xpForNextLevel() {
        return 20 + (playerLevel - 1) * 15;
    }

    /** Player damage multiplier: +5% per level above 1. */
    private float damageMultiplier() {
        return 1f + (playerLevel - 1) * 0.05f;
    }

    /** Max HP for a level: 100 at level 1, +10 per level. */
    private static int maxHealthForLevel(int level) {
        return 100 + (level - 1) * 10;
    }

    /**
     * Adds XP and handles level-ups (+10 max HP, +20 HP, +5% damage per level).
     * Must run on the UI thread, like every other change to playerHealth.
     */
    private void gainXp(int amount) {
        playerXp += amount;
        boolean leveledUp = false;
        while (playerXp >= xpForNextLevel()) {
            playerXp -= xpForNextLevel();
            playerLevel++;
            leveledUp = true;
        }
        if (leveledUp) {
            maxHealth = maxHealthForLevel(playerLevel);
            playerHealth = Math.min(maxHealth, playerHealth + 20);
            playerHealthBar.setMax(maxHealth);
            animateHealthBar(playerHealthBar.getProgress(), playerHealth);
            effects.banner(player.x, player.y - player.radius - 50f, "LEVEL " + playerLevel,
                    Color.rgb(255, 215, 0));
            effects.burst(player.x, player.y, Color.rgb(255, 215, 0), 30, 10f);
        }
        updateLevelHUD();
    }

    private void updateLevelHUD() {
        if (txtLevel != null)
            txtLevel.setText("Lv " + playerLevel + "  •  " + playerXp + "/" + xpForNextLevel() + " XP");
    }

    /* ============================================================
       QUICK POTION
       ============================================================ */

    /** Drinks the healing potion that best fits the missing HP, without opening the inventory. */
    private void quickHeal() {
        if (isGamePaused || playerHealth <= 0 || playerHealth >= maxHealth) return;
        int missing = maxHealth - playerHealth;
        ConsumableStack best = null;
        for (ConsumableStack stack : consumableInventory) {
            if (!"heal".equals(stack.item.effect)) continue;
            if (best == null) { best = stack; continue; }
            // Prefer the smallest potion that still fills the gap, so big potions aren't wasted
            boolean fits = stack.item.effectValue >= missing;
            boolean bestFits = best.item.effectValue >= missing;
            if (fits && (!bestFits || stack.item.effectValue < best.item.effectValue)) best = stack;
            else if (!fits && !bestFits && stack.item.effectValue > best.item.effectValue) best = stack;
        }
        if (best != null) useConsumable(best, null);
    }

    private void updatePotionButton() {
        int count = 0;
        for (ConsumableStack stack : consumableInventory) {
            if ("heal".equals(stack.item.effect)) count += stack.quantity;
        }
        txtPotionCount.setText(String.valueOf(count));
        btnPotion.setAlpha(count > 0 ? 1f : 0.4f);
    }

    /**
     * Spawns one GoldCoin per drop value at the enemy's death position.
     * Each coin's constructor randomises its own spread offset so they
     * naturally scatter rather than stacking on top of each other.
     */
    private void spawnGoldCoins(float x, float y, int amount) {
        synchronized (goldCoins) {
            for (int i = 0; i < amount; i++) {
                GoldCoin coin = new GoldCoin(x, y);
                // If the coin landed out of bounds (e.g. enemy died near a wall),
                // snap it back to the enemy's exact drop position.
                if (currentLayout != null && !inShopRoom
                        && !currentLayout.isFloor(coin.x, coin.y)) {
                    coin.x = x;
                    coin.y = y;
                }
                goldCoins.add(coin);
            }
        }
    }

    /**
     * Checks if the player is close enough to any coin.
     * Coins are collected on contact — no button press needed.
     */
    private void checkGoldCollection() {
        synchronized (goldCoins) {
            for (GoldCoin coin : goldCoins) {
                if (coin.collected) continue;
                float dist = getDistance(player.x, player.y, coin.x, coin.y);
                if (dist < player.radius + GoldCoin.COLLECT_RADIUS) {
                    statsManager.onCoinCollected();
                    coin.collected = true;
                    soundManager.playCoinPickup();
                    effects.burst(coin.x, coin.y, Color.rgb(255, 215, 0), 4, 3f);
                    playerGold++;
                    runOnUiThread(this::updateGoldHUD);
                }
            }
            goldCoins.removeIf(c -> c.collected);
        }
    }

    /**
     * Starts the game music when the activity becomes visible.
     */
    @Override
    protected void onStart() {
        super.onStart();
        SoundManager.registerGameMusic(this);
        float gameVol = getSharedPreferences("gameSettings", MODE_PRIVATE)
                .getFloat("game_music_volume", 0.85f);
        SoundManager.setGameMusicVolume(gameVol);
    }

    /**
     * Stops the game music when the activity goes to the background.
     */
    @Override
    protected void onStop() {
        super.onStop();
        SoundManager.unregisterGameMusic();
    }

    /**
     * Restores full-screen mode and applies any sound setting changes made in Settings.
     */
    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();

        // Re-apply SFX volume in case user changed it in Settings
        if (soundManager != null) {
            float vol = getSharedPreferences("gameSettings", MODE_PRIVATE)
                    .getFloat("sfx_volume", 1.0f);
            soundManager.setVolume(vol);
        }

        // Re-apply game music volume in case user changed it in Settings
        float gameVol = getSharedPreferences("gameSettings", MODE_PRIVATE)
                .getFloat("game_music_volume", 0.85f);
        SoundManager.setGameMusicVolume(gameVol);

        // CHECK IF WE CAME BACK FROM SETTINGS
        if (showMenuOnResume) {
            showMenuOnResume = false;
            // Slight delay so the activity has time to draw before throwing the dialog up
            new android.os.Handler().postDelayed(this::showMenuDialog, 100);
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    private void hideSystemUIOnWindow(android.view.Window window) {
        if (window == null) return;
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
        // Keep dialog buttons clear of the camera cutout
        View content = window.findViewById(android.R.id.content);
        if (content == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            v.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    /**
     * Stops game threads, flushes statistics and releases sound resources.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        threadsRunning = false;
        leaveCoop();
        if (statsManager != null) statsManager.flush();
        if (soundManager != null) soundManager.release();
    }

    /**
     * Fixed Vibration System.
     * Removed the forced '255' amplitude which causes invisible crashes on devices
     * that don't support amplitude control. Now uses DEFAULT_AMPLITUDE safely.
     */
    private void vibrateOnHit() {
        SharedPreferences prefs = getSharedPreferences("gameSettings", MODE_PRIVATE);
        if (!prefs.getBoolean("vibration_enabled", true)) return;

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.os.VibratorManager vm = (android.os.VibratorManager)
                        getSystemService(VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vm.getDefaultVibrator().vibrate(
                            android.os.VibrationEffect.createOneShot(
                                    80, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                    return;
                }
            }
            // Fallback for older Android / MIUI
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(
                            80, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(80);
                }
            }
        } catch (Exception e) {
            // Last resort — haptic feedback on the game view
            gameDisplay.performHapticFeedback(
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    /**
     * Returns the shop items for a specific floor from the batch cache.
     * @param floor the floor number
     * @return list of shop items (empty if the batch is not yet available)
     */
    private List<ShopItem> getShopItemsForFloor(int floor) {
        int rangeStart = getRangeStart(floor);
        BatchResult result = batchCache.get(rangeStart);
        if (result == null || result.shopByFloor == null) return new ArrayList<>();
        List<ShopItem> items = result.shopByFloor.get(floor);
        return items != null ? items : new ArrayList<>();
    }

    /**
     * Returns all 4 enemy templates for the batch range that covers the given floor.
     * Every floor in a range shares the same pool; distribution across rooms is random.
     */
    private List<EnemyTemplate> getEnemyPoolForFloor(int floor) {
        int rangeStart = getRangeStart(floor);
        if (rangeStart < 0) return new ArrayList<>();
        BatchResult result = batchCache.get(rangeStart);
        if (result == null || result.enemyPool == null || result.enemyPool.isEmpty())
            return new ArrayList<>();
        return result.enemyPool;
    }

    /**
     * Returns the batch data (including boss) for the given floor.
     * @param floor the floor number
     * @return the corresponding BatchResult, or null if not available
     */
    private BatchResult getBossDataForFloor(int floor) {
        int rangeStart = getRangeStart(floor);
        return batchCache.get(rangeStart);
    }

    /**
     * Saves the full current game state to Firestore.
     * Called only when a new floor starts, so loading always resumes at a floor start.
     */
    private void saveGameToFirestore() {
        if (gameId == null || userId == null || pendingLoads > 0) return;

        long playtime = (System.currentTimeMillis() - gameStartTime) / 1000;

        List<String> weaponNames = new ArrayList<>();
        for (Weapon w : weapons) {
            if (!w.name.equals("Fists")) weaponNames.add(w.name);
        }

        // Serialize consumables
        List<Map<String, Object>> consumableMaps = new ArrayList<>();
        for (ConsumableStack stack : consumableInventory) {
            Map<String, Object> m = new HashMap<>();
            m.put("name",        stack.item.name);
            m.put("effect",      stack.item.effect);
            m.put("effectValue", stack.item.effectValue);
            m.put("price",       stack.item.price);
            m.put("colorHex",    stack.item.colorHex);
            m.put("color",       stack.item.color);
            m.put("rarity",      stack.item.rarity);
            m.put("description", stack.item.description);
            m.put("quantity",    stack.quantity);
            consumableMaps.add(m);
        }

        // Equipped armor IDs
        String headId  = null, chestId = null, legsId = null, bootsId = null;
        for (Map.Entry<Armor.Slot, Armor> entry : equippedArmor.entrySet()) {
            String id = armorFirestoreIds.get(entry.getValue());
            switch (entry.getKey()) {
                case HEAD:  headId  = id; break;
                case CHEST: chestId = id; break;
                case LEGS:  legsId  = id; break;
                case BOOTS: bootsId = id; break;
            }
        }

        // Only worn pieces are stored; on load, presence in this map marks a piece as worn
        Map<String, Object> armorDurability = new HashMap<>();
        for (Armor a : inventoryArmors) {
            String id = armorFirestoreIds.get(a);
            if (a.worn && id != null) armorDurability.put(id, a.durability);
        }

        Map<String, Object> save = new HashMap<>();
        save.put("currentFloor",         floorManager.getCurrentFloor());
        save.put("health",               playerHealth);
        save.put("armorValue",           playerArmor);
        save.put("gold",                 playerGold);
        save.put("equippedWeaponId",     weaponFirestoreIds.get(player.getWeapon()));
        save.put("equippedWeaponName",   player.getWeapon().name);
        save.put("equippedHeadId",       headId);
        save.put("equippedChestId",      chestId);
        save.put("equippedLegsId",       legsId);
        save.put("equippedBootsId",      bootsId);
        save.put("inventoryWeaponIds",   inventoryWeaponIds);
        save.put("inventoryWeaponNames", weaponNames);
        save.put("inventoryArmorIds",    inventoryArmorIds);
        save.put("armorDurability",      armorDurability);
        save.put("items",                consumableMaps);
        save.put("playtimeSeconds",      playtime);
        save.put("level",                playerLevel);
        save.put("xp",                   playerXp);
        dbManager.updateGameSave(gameId, save);
    }

    /**
     * Loads a saved game and restores all state.
     * Runs on background thread via DBManager, then applies on UI thread.
     */
    private void loadGameFromFirestore() {
        if (gameId == null) return;

        pendingLoads++;
        dbManager.loadGame(gameId, data -> {
            if (data == null) return;

            runOnUiThread(() -> {
                pendingLoads--;

                // Restore floor
                int savedFloor = data.get("currentFloor") != null
                        ? ((Number) data.get("currentFloor")).intValue() : 1;
                floorManager.setCurrentFloor(savedFloor);

                // Fetch the loaded floor's batch (and the next one) so custom enemies and the
                // shop are ready; applyBatchToCurrentFloorIfReady() re-spawns enemies on arrival.
                ensureBatchFor(savedFloor);
                ensureBatchFor(savedFloor + 2);

                // Regenerate layout for the restored floor (floor 1 layout was built at
                // startup; for loaded games it may be wrong floor, and for new games the
                // layout is fine but enemies were already placed — clear and redo).
                synchronized (enemies) { enemies.clear(); }
                synchronized (goldCoins) { goldCoins.clear(); }
                currentLayout = generateLayout();
                gameDisplay.setFloorLayout(currentLayout);
                currentLayout.entryRoom.explored = true;
                if (currentLayout.exitRoom != null) {
                    floorManager.setExitRoomCenter(
                            currentLayout.exitRoom.centerX(),
                            currentLayout.exitRoom.centerY());
                }
                spawnEnemiesForLayout(currentLayout);

                // Restore health / armor / gold
                playerHealth = data.get("health") != null
                        ? ((Number) data.get("health")).intValue() : 100;
                playerArmor = data.get("armorValue") != null
                        ? ((Number) data.get("armorValue")).intValue() : 0;
                playerGold = data.get("gold") != null
                        ? ((Number) data.get("gold")).intValue() : 0;
                playerLevel = data.get("level") != null
                        ? ((Number) data.get("level")).intValue() : 1;
                playerXp = data.get("xp") != null
                        ? ((Number) data.get("xp")).intValue() : 0;
                maxHealth = maxHealthForLevel(playerLevel);

                if (data.get("playtimeSeconds") != null)
                    gameStartTime -= ((Number) data.get("playtimeSeconds")).longValue() * 1000L;

                // Saves are made at floor start, so the loaded state is also this floor's checkpoint
                player.x = currentLayout.entryRoom.centerX();
                player.y = currentLayout.entryRoom.centerY();
                setCheckpoint(savedFloor);

                // Update HUD bars immediately with restored values.
                // Armor max isn't known yet (armor pieces load async), so temporarily
                // set the bar max to the saved armor value so the bar renders correctly
                // until recalculateMaxArmor() fires once the armor docs arrive.
                playerHealthBar.setMax(maxHealth);
                playerHealthBar.setProgress(playerHealth);
                maxArmor = playerArmor; // temporary; recalculateMaxArmor() will correct this
                playerArmorBar.setMax(Math.max(1, maxArmor));
                playerArmorBar.setProgress(playerArmor);
                if (txtHealthValue != null) txtHealthValue.setText(playerHealth + " / " + maxHealth);
                if (txtArmorValue  != null) txtArmorValue.setText(playerArmor + " / " + maxArmor);
                updateFloorHUD();
                updateGoldHUD();
                updateLevelHUD();

                // Restore inventory weapon IDs and reconstruct Weapon objects
                if (data.containsKey("inventoryWeaponIds")) {
                    java.util.List<String> ids =
                            (java.util.List<String>) data.get("inventoryWeaponIds");

                    if (ids != null && !ids.isEmpty()) {
                        inventoryWeaponIds = new ArrayList<>(ids);

                        final String equippedId = (String) data.get("equippedWeaponId");

                        for (String wid : ids) {
                            pendingLoads++;
                            dbManager.loadGeneratedWeapon(wid, weaponData -> runOnUiThread(() -> {
                                pendingLoads--;
                                Weapon w = StatsManager.weaponFromMap(weaponData);
                                if (w == null) return;
                                weapons.add(w);
                                weaponFirestoreIds.put(w, wid);
                                if (wid.equals(equippedId)) player.equipWeapon(w);
                            }));
                        }
                    } else {
                        // No purchased weapons — just equip from default list (Fists)
                        String equippedName = (String) data.get("equippedWeaponName");
                        if (equippedName != null) {
                            for (Weapon w : weapons) {
                                if (w.name.equals(equippedName)) {
                                    player.equipWeapon(w);
                                    break;
                                }
                            }
                        }
                        refreshAttackUI();
                    }
                }

                // Restore consumables
                if (data.containsKey("items")) {
                    java.util.List<Map<String, Object>> items =
                            (java.util.List<Map<String, Object>>) data.get("items");
                    if (items != null) {
                        consumableInventory.clear();
                        for (Map<String, Object> m : items) {
                            ShopItem si = new ShopItem();
                            si.name        = (String) m.get("name");
                            si.effect      = (String) m.get("effect");
                            si.effectValue = m.get("effectValue") != null
                                    ? ((Number) m.get("effectValue")).intValue() : 0;
                            si.price       = m.get("price") != null
                                    ? ((Number) m.get("price")).intValue() : 0;
                            si.colorHex    = (String) m.get("colorHex");
                            si.rarity      = (String) m.get("rarity");
                            si.description = (String) m.get("description");
                            si.type        = ShopItem.ItemType.CONSUMABLE;
                            if (si.colorHex != null) {
                                try { si.color = Color.parseColor(si.colorHex); }
                                catch (Exception e) { si.color = Color.WHITE; }
                            }
                            int qty = m.get("quantity") != null
                                    ? ((Number) m.get("quantity")).intValue() : 1;
                            consumableInventory.add(new ConsumableStack(si, qty));
                        }
                        updatePotionButton();
                    }
                }

                // Restore armor inventory + equipped state
                if (data.containsKey("inventoryArmorIds")) {
                    java.util.List<String> ids =
                            (java.util.List<String>) data.get("inventoryArmorIds");
                    if (ids != null && !ids.isEmpty()) {
                        inventoryArmorIds = new ArrayList<>(ids);
                        final String savedHeadId  = (String) data.get("equippedHeadId");
                        final String savedChestId = (String) data.get("equippedChestId");
                        final String savedLegsId  = (String) data.get("equippedLegsId");
                        final String savedBootsId = (String) data.get("equippedBootsId");
                        final Map<String, Object> savedDurability =
                                (Map<String, Object>) data.get("armorDurability");

                        // Count-based pattern: collect ALL pieces first, then call
                        // recalculateMaxArmor() exactly once after the last piece arrives.
                        // Calling it on each individual piece would clamp playerArmor to a
                        // partial maxArmor on every callback, corrupting the saved value.
                        final int[] armorLoadedCount = {0};
                        final int totalArmors = ids.size();

                        for (String aid : ids) {
                            pendingLoads++;
                            dbManager.loadGeneratedArmor(aid, armorData -> {
                                Armor a = (armorData != null)
                                        ? StatsManager.armorFromMap(aid, armorData)
                                        : null;
                                runOnUiThread(() -> {
                                    pendingLoads--;
                                    if (a != null) {
                                        inventoryArmors.add(a);
                                        armorFirestoreIds.put(a, aid);
                                        Object durability = savedDurability != null ? savedDurability.get(aid) : null;
                                        if (durability != null) {
                                            a.durability = ((Number) durability).floatValue();
                                            a.worn = true;
                                        }
                                        if (aid.equals(savedHeadId))  equippedArmor.put(Armor.Slot.HEAD,  a);
                                        if (aid.equals(savedChestId)) equippedArmor.put(Armor.Slot.CHEST, a);
                                        if (aid.equals(savedLegsId))  equippedArmor.put(Armor.Slot.LEGS,  a);
                                        if (aid.equals(savedBootsId)) equippedArmor.put(Armor.Slot.BOOTS, a);
                                    }
                                    armorLoadedCount[0]++;
                                    if (armorLoadedCount[0] >= totalArmors) {
                                        // All pieces collected — one final recalculate so
                                        // playerArmor is clamped to the true full maxArmor.
                                        recalculateMaxArmor();
                                    }
                                });
                            });
                        }
                    }
                }
                else {
                    // No inventoryArmorIds field — equip weapon from default list
                    String equippedName = (String) data.get("equippedWeaponName");
                    if (equippedName != null) {
                        for (Weapon w : weapons) {
                            if (w.name.equals(equippedName)) {
                                player.equipWeapon(w);
                                break;
                            }
                        }
                    }
                    refreshAttackUI();
                }
            });
        });
    }
}