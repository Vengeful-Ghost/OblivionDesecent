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
    // Level-up perks: stacks per Perk, and level-ups still waiting for a pick
    private final int[] perks = new int[Perk.values().length];
    private int pendingPerks = 0;
    private boolean perkDialogOpen = false;
    private MaterialButton btnLevelUp;
    private ObjectAnimator levelBadgePulse;
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
    private static final float SPIN_PUSH = 60f; // starting slide speed; enemies glide about four times this far
    private static final long AIM_GRACE = 90; // ms a fresh touch waits for a drag before auto-aiming
    private long lastSpinTime = 0;
    private volatile boolean spinQueued = false;
    // Settings: attack where the player faces (a plain button) instead of aiming with the attack stick
    private volatile boolean attackFacing = false;
    // Reach of the current swing, set by the weapon's moveset
    private float swingReach = 0f;

    // Heavy attack: hold the heavy button to charge, let go to strike; it breaks guards
    private static final long HEAVY_CHARGE = 700, HEAVY_COOLDOWN = 1500;
    private volatile long heavyDownAt = 0; // 0 = not charging
    private volatile boolean heavyReleased = false;
    private volatile long lastHeavyTime = 0;
    private boolean chargeFull = false;
    private DashCooldownView heavyCooldownView;
    private int heavyStepShown = -1;
    private DashCooldownView potionCooldownView;
    private int potionStepShown = -1;

    // The camera leans toward the fight; the low-health heartbeat only plays in combat
    private float leanX = 0f, leanY = 0f;
    private long lastHurtAt = 0, lastHeartbeat = 0, lastBuzz = 0;
    // Settings (SettingsActivity.KEY_*), re-read in onResume
    private volatile boolean showDamageNumbers = true, lowHealthEffects = true, cameraLean = true;
    private static final int BLOOD = Color.rgb(150, 18, 24), BLOOD_STAIN = Color.argb(150, 96, 10, 14);

    // Potions share a cooldown, and their healing flows in over a couple of seconds
    private static final long POTION_COOLDOWN = 8000, HEAL_TIME = 2000;
    private volatile long lastPotionAt = 0;
    private volatile int healLeft = 0, healPerStep = 0;
    private long nextHealStep = 0;
    // Where enemies fell this floor; necromancers raise them (logic thread only)
    private final List<float[]> deathSpots = new ArrayList<>();

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

    // Perfect dodge: an attack meets the player early in a dash
    private long lastPerfectDodge = 0;
    private volatile long perfectCritUntil = 0; // hits are sure crits until then

    // Synergies: Phantom cuts each enemy once per dash; Bulwark regenerates on a timer
    private final Set<Enemy> phantomHits = new HashSet<>();
    private long phantomDash = 0, lastRegen = 0;

    // Hit-stop freezes the world for a few frames; slow-mo runs it at a fraction of real speed
    private volatile long hitStopUntil = 0, slowMoUntil = 0;
    private volatile float slowMoScale = 1f;

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

    // Keys open vault gates
    private int keys = 0;
    private TextView txtKeys;
    private long lastLockedHint = 0;

    // Soul Altar ranks by Altar ordinal, loaded from the player document
    private int[] altar = new int[Altar.values().length];
    private boolean secondWindUsed = false;
    private boolean adReviveUsed = false; // the ad revive works once a life

    // Daily run (0 = not one): seeded, one life, best score per day on a leaderboard
    static final String EXTRA_DAILY_SEED = "daily_seed", EXTRA_DAILY_DAY = "daily_day";
    private long dailySeed = 0;
    private String dailyDay;

    // Ascension: every 10th-floor boss raises it, so floors 11-20 are level 1, 21-30 level 2 and so on
    private int ascension() {
        return Math.max(0, (floorManager.getCurrentFloor() - 1) / 10);
    }

    private Enemy activeBoss = null;
    private boolean bossSpawnHandled = false;

    private List<TreasureChest> treasureChests = Collections.synchronizedList(new ArrayList<>());

    private boolean enteringShop = false;

    private ShopRoom shopRoom = new ShopRoom();
    private boolean inShopRoom = false;

    private boolean shopDialogOpen = false;
    // An ad restock replaces this floor's stock once
    private List<ShopItem> restockedShop;
    private int restockedFloor = -1;

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
    private Enemy.BossKind currentBossKind = Enemy.BossKind.BRUTE;

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
    private int partySize = 1;
    // Guest: enemy id -> when its death showed here (predicted or reported), this floor only
    private final Map<Integer, Long> deadEnemyAt = new java.util.concurrent.ConcurrentHashMap<>();
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
        Wear.apply(findViewById(android.R.id.content));

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
        btnInventory = findViewById(R.id.btn_inventory);
        menuBtn = findViewById(R.id.btn_menu);
        btnDash = findViewById(R.id.btn_dash);
        txtGold = findViewById(R.id.txt_gold);
        txtKeys = findViewById(R.id.txt_keys);
        dashCooldownView = findViewById(R.id.dash_cooldown_view);
        spinCooldownView = findViewById(R.id.spin_cooldown_view);
        btnPotion = findViewById(R.id.btn_potion);
        txtPotionCount = findViewById(R.id.txt_potion_count);
        txtLevel = findViewById(R.id.txt_level);
        gameDisplay.setEffects(effects);
        effects.setFont(getResources().getFont(R.font.pixel));
        shopRoom.setFont(getResources().getFont(R.font.pixel));

        player = new Player();
        player.equipWeapon(weapons.get(0));

        String coopCode = getIntent().getStringExtra(CoopSession.EXTRA_CODE);
        if (coopCode != null) {
            coop = new CoopSession(coopCode,
                    getSharedPreferences("loginPrefs", MODE_PRIVATE).getString("user_id", ""),
                    getIntent().getBooleanExtra(CoopSession.EXTRA_HOST, false),
                    getIntent().getLongExtra(CoopSession.EXTRA_SEED, 0L),
                    getIntent().getIntExtra(CoopSession.EXTRA_SLOT, 0));
            partySize = Math.max(1, getIntent().getIntExtra(CoopSession.EXTRA_PARTY, 1));
            player.setColor(CoopSession.SLOT_COLORS[coop.slot & 3]);
        }
        // Daily run: today's seeded dungeon for everyone, one life, no save
        dailySeed = getIntent().getLongExtra(EXTRA_DAILY_SEED, 0L);
        dailyDay = getIntent().getStringExtra(EXTRA_DAILY_DAY);
        // Weekly challenge: a daily-style seeded run with a twist
        String mod = getIntent().getStringExtra(EXTRA_WEEKLY_MOD);
        if (mod != null) {
            try {
                weeklyMod = WeeklyMod.valueOf(mod);
            } catch (IllegalArgumentException ignored) {
                // a twist from a newer version
            }
        }
        if (weeklyMod == WeeklyMod.GLASS_CANNON) {
            maxHealth = computeMaxHealth();
            playerHealth = maxHealth;
            checkpointHealth = maxHealth;
            playerHealthBar.setMax(maxHealth);
            playerHealthBar.setProgress(playerHealth);
            txtHealthValue.setText(playerHealth + " / " + maxHealth);
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
        spawnRoomChest();
        Log.d("FloorLayout", "Floor 1 layout ready: "
                + currentLayout.rooms.size() + " rooms");

        dbManager = new DBManager();
        SharedPreferences loginPrefs = getSharedPreferences("loginPrefs", MODE_PRIVATE);
        userId = loginPrefs.getString("user_id", null);
        username = loginPrefs.getString("username", "Player");
        // Wardrobe looks (MenuActivity.showWardrobe); co-op keeps its slot colours
        if (userId != null) {
            SharedPreferences look = getSharedPreferences("cosmetics_" + userId, MODE_PRIVATE);
            Cosmetic body = Cosmetic.worn(look, 0), trail = Cosmetic.worn(look, 1);
            if (coop == null && body.color != 0) player.setColor(body.color);
            if (trail.color != 0) player.trailColor = trail.color;
            deathColor = Cosmetic.worn(look, 2).color;
        }
        gameId = getIntent().getStringExtra("game_id");
        int slotIndex = getIntent().getIntExtra("slot_index", 1);

        statsManager = new StatsManager(dbManager, userId, gameId);
        Ads.preload(this);
        if (userId != null)
            dbManager.loadPlayerStats(userId, data -> runOnUiThread(() -> applyAltar(Altar.ranks(data))));

        if (gameId == null && userId != null && !oneOffRun()) {
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
            if (weeklyMod != null)
                effects.banner(player.x, player.y - player.radius - 120f, weeklyMod.title.toUpperCase(),
                        Color.rgb(224, 72, 72));
            // First solo run on this phone: a guided first floor
            if (coop == null && dailySeed == 0 && userId != null
                    && !getSharedPreferences("tutorial_" + userId, MODE_PRIVATE).getBoolean("done", false))
                setTutorialStep(0);
        });

        btnDash.setOnClickListener(v -> dash());
        findViewById(R.id.btn_spin).setOnClickListener(v -> {
            if (System.currentTimeMillis() - lastSpinTime >= spinCooldown()) spinQueued = true;
        });
        // Heavy: charges while held, strikes on release; tick() reads these
        heavyCooldownView = findViewById(R.id.heavy_cooldown_view);
        potionCooldownView = findViewById(R.id.potion_cooldown_view);
        findViewById(R.id.btn_heavy).setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                v.setPressed(true);
                if (playerHealth > 0 && heavyDownAt == 0
                        && System.currentTimeMillis() - lastHeavyTime >= HEAVY_COOLDOWN) {
                    heavyReleased = false;
                    heavyDownAt = System.currentTimeMillis();
                    soundManager.play(SoundManager.Sfx.HEAVY_CHARGE);
                }
            } else if (action == android.view.MotionEvent.ACTION_UP
                    || action == android.view.MotionEvent.ACTION_CANCEL) {
                v.setPressed(false);
                if (heavyDownAt > 0) heavyReleased = true;
                if (action == android.view.MotionEvent.ACTION_UP) v.performClick();
            }
            return true;
        });
        btnPotion.setOnClickListener(v -> quickHeal());
        // A fallen solo player can't open these (closing them would unpause mid-revive)
        menuBtn.setOnClickListener(v -> { if (playerHealth > 0 || coop != null) showMenuDialog(); });
        btnInventory.setOnClickListener(v -> { if (playerHealth > 0 || coop != null) showInventoryDialog(); });
        btnLevelUp = findViewById(R.id.btn_level_up);
        btnLevelUp.setOnClickListener(v -> showPerkDialog());
        findViewById(R.id.txt_tutorial).setOnClickListener(v -> finishTutorial(true));

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

    /** Enemy health multiplier: each extra player adds 60%, so a full party still has to fight. */
    private float partyHealth() {
        return 1f + 0.6f * (partySize - 1);
    }

    /**
     * Layout for the current floor; co-op and daily runs seed it so everyone gets the same dungeon.
     * Event rooms are solo only: a co-op phone would answer them alone.
     */
    private FloorLayout generateLayout() {
        long seed = runSeed() != 0 ? runSeed() * 31 + floorManager.getCurrentFloor() : System.nanoTime();
        return FloorLayout.generate(floorManager.getCurrentFloor(), floorManager.isBossFloor(),
                floorManager.getKillsRequired(), seed, coop == null);
    }

    /** Shared seed of a co-op or daily run; 0 for a solo save. */
    private long runSeed() {
        return coop != null ? coop.seed : dailySeed;
    }

    /** Co-op and daily runs are one-offs: no save file, no stored items, no solo content cache. */
    private boolean oneOffRun() {
        return coop != null || dailySeed != 0;
    }

    /* ============================================================
       CO-OP — the host runs the world, guests mirror it (see CoopSession)
       ============================================================ */

    /** One tick of co-op: exchange state, mirror or publish enemies, follow the host's floor. */
    private void coopTick(long now) {
        int floor = floorManager.getCurrentFloor();
        for (CoopSession.RemotePlayer r : coop.others.values()) {
            r.follow(now);
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
            CoopSession.Received snap;
            while ((snap = coop.snapshots.poll()) != null) applyEnemySnapshot(snap);
            applyCoopEvents(now);
            if (coop.hostFloor > floor && !isTransitioning) startFloorTransition();
        }

        coopTicks++;
        if (coopTicks % 4 == 0) publishSelf(floor);
        if (coop.isHost() && coopTicks % 5 == 0) publishEnemies(floor, now);
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

    /** Guest: copies one of the host's enemy snapshots ("time;floor,kills;enemy;...") onto local stand-ins. */
    private void applyEnemySnapshot(CoopSession.Received snap) {
        try {
            String[] parts = snap.data.split(";");
            long sentAt = Long.parseLong(parts[0]);
            coop.hostClock.heard(sentAt, snap.at);
            String[] head = parts[1].split(",");
            if (Integer.parseInt(head[0]) != floorManager.getCurrentFloor()) return;
            floorManager.syncKills(Integer.parseInt(head[1]), player.x, player.y);

            Set<Integer> seen = new HashSet<>();
            synchronized (enemies) {
                for (int i = 2; i < parts.length; i++) {
                    String[] f = parts[i].split(",");
                    int id = Integer.parseInt(f[0]);
                    int hp = Integer.parseInt(f[5]);
                    seen.add(id);
                    Long diedAt = deadEnemyAt.get(id);
                    if (diedAt != null) {
                        // Still alive on the host well after a predicted kill: the hit was lost, so bring it back
                        if (hp <= 0 || snap.at - diedAt < 1500) continue;
                        deadEnemyAt.remove(id);
                    }
                    Enemy e = findEnemy(id);
                    if (e != null && e.isDead) continue; // still fading out
                    boolean created = e == null;
                    if (created) {
                        if (hp <= 0) continue; // died before this phone saw it
                        e = createMirror(id, Enemy.Type.values()[Integer.parseInt(f[1])], Integer.parseInt(f[2]));
                        enemies.add(e);
                    }
                    e.applyState(f, sentAt, snap.at);
                    // The host's treasure chest just woke up as a mimic
                    if (created && e.mimic) mimicAwoke(e.x, e.y);
                    if (e.isDead) deadEnemyAt.put(id, snap.at);
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
            e.setBossKind(currentBossKind);
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

    /** Host: applies the damage guests dealt ("uid,enemyId,damage,pushX,pushY,hitKind": 1 stagger, 2 heavy). */
    private void applyCoopHits() {
        String hit;
        while ((hit = coop.hits.poll()) != null) {
            try {
                String[] f = hit.split(",");
                synchronized (enemies) {
                    Enemy e = findEnemy(Integer.parseInt(f[1]));
                    if (e == null || e.isDead) continue;
                    int damage = Math.max(0, Integer.parseInt(f[2]));
                    float pushX = Float.parseFloat(f[3]), pushY = Float.parseFloat(f[4]);
                    float push = Math.max(0.01f, (float) Math.sqrt(pushX * pushX + pushY * pushY));
                    e.takeDamage(damage);
                    e.knockbackVX = pushX;
                    e.knockbackVY = pushY;
                    showEnemyHit(e, damage, false, pushX / push, pushY / push);
                    if ("2".equals(f[5]) && e.isGuarding()) e.breakGuard();
                    else if (!"0".equals(f[5])) staggerEnemy(e);
                    e.maybeGuard();
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
            r.body.startSwing(r.angle, 55f, false, 150, false);
            effects.slash(r.body.x, r.body.y, r.angle, r.body.radius + 60f, 55f, Color.WHITE);
        }
    }

    /** A teammate still standing on this floor for a fallen player to watch; null in solo or if none. */
    private CoopSession.RemotePlayer livingTeammate() {
        if (coop == null) return null;
        int floor = floorManager.getCurrentFloor();
        for (CoopSession.RemotePlayer r : coop.others.values()) {
            if (!r.dead && r.floor == floor) return r;
        }
        return null;
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
        // Teammates' newest reported spots, not where their bodies are drawn a moment behind
        for (CoopSession.RemotePlayer r : coop.others.values()) {
            if (r.dead || r.floor != floor) continue;
            float dx = r.newestX - e.x;
            float dy = r.newestY - e.y;
            if (dx * dx + dy * dy < best) {
                best = dx * dx + dy * dy;
                target[0] = r.newestX;
                target[1] = r.newestY;
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

    /** A teammate handed over an item; it goes straight into the inventory. */
    @Override
    public void onGift(String fromUid, String json) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            String name = o.getString("name");
            switch (o.getString("kind")) {
                case "W":
                    weapons.add(new Weapon(name, o.getInt("damage"), (float) o.getDouble("range"),
                            o.getLong("cooldown"), Weapon.Rarity.valueOf(o.getString("rarity")),
                            o.getInt("color"), (float) o.getDouble("knockback"), o.getBoolean("projectile")));
                    break;
                case "A":
                    Armor armor = new Armor(name, o.getInt("defense"), (float) o.getDouble("maxDurability"),
                            Armor.Rarity.valueOf(o.getString("rarity")), o.getInt("color"),
                            Armor.Slot.valueOf(o.getString("slot")));
                    armor.durability = (float) o.getDouble("durability");
                    armor.worn = o.getBoolean("worn");
                    inventoryArmors.add(armor);
                    break;
                default:
                    ShopItem potion = new ShopItem();
                    potion.type = ShopItem.ItemType.CONSUMABLE;
                    potion.name = name;
                    potion.effect = o.getString("effect");
                    potion.effectValue = o.getInt("effectValue");
                    potion.rarity = o.optString("rarity", "COMMON");
                    potion.color = o.getInt("color");
                    potion.price = o.getInt("price");
                    potion.description = o.optString("description", "");
                    purchaseItem(potion); // stacks with matching potions
            }
            CoopSession.RemotePlayer from = coop.others.get(fromUid);
            effects.banner(player.x, player.y - player.radius - 50f,
                    "GIFT FROM " + (from != null ? from.name : "A TEAMMATE").toUpperCase(), Color.rgb(244, 215, 122));
            effects.number(player.x, player.y - player.radius - 10f, name, Color.WHITE);
            soundManager.play(SoundManager.Sfx.PERK_PICK);
        } catch (org.json.JSONException | IllegalArgumentException e) {
            Log.e("Coop", "Bad gift: " + json, e);
        }
    }

    /** The host left or the whole party fell: end the run on this phone. */
    @Override
    public void onRunEnded(String reason) {
        if (isFinishing()) return;
        coopEnded = true;
        isGamePaused = true;
        showGameOverDialog(reason);
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
                backlog += (long) ((current - previous) * timeScale());
                previous = current;

                // Boss death detection runs even while paused to avoid missing kills
                if (layoutReady) checkBossDeathDetection();

                if (isGamePaused || !layoutReady) backlog = 0;
                if (isGamePaused) effects.update(); // the death poof keeps moving while paused
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

    private float timeScale() {
        long now = System.currentTimeMillis();
        if (now < hitStopUntil) return 0f;
        return now < slowMoUntil ? slowMoScale : 1f;
    }

    /** Freezes the world for a moment so a heavy hit lands with weight. */
    private void hitStop(long ms) {
        hitStopUntil = Math.max(hitStopUntil, System.currentTimeMillis() + ms);
    }

    /** Solo only: in co-op the world is shared, so it can't slow down for one player. */
    private void slowMo(long ms, float scale) {
        if (coop != null) return;
        slowMoScale = scale;
        slowMoUntil = System.currentTimeMillis() + ms;
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
            if (has(Synergy.PHANTOM)) phantomCuts(now);
            if (now - dashStartTime > DASH_DURATION) {
                isDashing = false;
            }
        } else if (playerHealth > 0) {
            // Charging a heavy attack slows the walk
            float pace = heavyDownAt > 0 ? 0.45f : 1f;
            player.update(joystick.getDX() * pace, joystick.getDY() * pace);
        }
        // Bulwark: slow regeneration out of combat, up to half health
        if (has(Synergy.BULWARK) && now - lastRegen >= 5000 && playerHealth > 0 && playerHealth < maxHealth / 2) {
            lastRegen = now;
            runOnUiThread(() -> {
                if (playerHealth <= 0 || playerHealth >= maxHealth / 2) return;
                playerHealth++;
                playerHealthBar.setProgress(playerHealth);
                if (txtHealthValue != null) txtHealthValue.setText(playerHealth + " / " + maxHealth);
            });
        }
        // A drunk healing potion flows in over HEAL_TIME
        if (healLeft > 0 && now >= nextHealStep) {
            nextHealStep = now + HEAL_TIME / 16;
            int step = Math.min(healLeft, healPerStep);
            healLeft -= step;
            runOnUiThread(() -> {
                if (playerHealth <= 0) return;
                playerHealth = Math.min(maxHealth, playerHealth + step);
                playerHealthBar.setProgress(playerHealth);
                if (txtHealthValue != null) txtHealthValue.setText(playerHealth + " / " + maxHealth);
            });
        }
        if (currentLayout != null && !inShopRoom) {
            // Flooded Catacombs: wading through water slows the walk; a dash still cuts through
            if (!isDashing && Props.inWater(currentLayout.decor, player.x, player.y)) {
                player.x = prevPlayerX + (player.x - prevPlayerX) * WADE;
                player.y = prevPlayerY + (player.y - prevPlayerY) * WADE;
                if ((player.x != prevPlayerX || player.y != prevPlayerY) && now - lastSplash > 260) {
                    lastSplash = now;
                    effects.burst(player.x, player.y + player.radius * 0.6f, Props.WATER_SHINE, 5, 3f);
                }
            }
            float[] resolved = currentLayout.resolveCollision(
                    prevPlayerX, prevPlayerY, player.x, player.y, player.radius);
            player.x = resolved[0];
            player.y = resolved[1];
        }
        player.setInvulnerable(now < invulnerableUntil);

        // Update dash, spin, heavy and potion cooldown arcs, only when they visibly change
        float dashProgress = Math.min(1f, (float) (now - lastDashTime) / dashCooldown());
        float spinProgress = Math.min(1f, (float) (now - lastSpinTime) / spinCooldown());
        float heavyProgress = Math.min(1f, (float) (now - lastHeavyTime) / HEAVY_COOLDOWN);
        float potionProgress = Math.min(1f, (float) (now - lastPotionAt) / potionCooldown());
        int dashStep = (int) (dashProgress * 50);
        int spinStep = (int) (spinProgress * 50);
        int heavyStep = (int) (heavyProgress * 50);
        int potionStep = (int) (potionProgress * 50);
        if (dashStep != dashStepShown || spinStep != spinStepShown || heavyStep != heavyStepShown
                || potionStep != potionStepShown) {
            dashStepShown = dashStep;
            spinStepShown = spinStep;
            heavyStepShown = heavyStep;
            potionStepShown = potionStep;
            runOnUiThread(() -> {
                if (dashCooldownView != null)
                    dashCooldownView.setProgress(dashProgress);
                if (spinCooldownView != null)
                    spinCooldownView.setProgress(spinProgress);
                if (heavyCooldownView != null)
                    heavyCooldownView.setProgress(heavyProgress);
                if (potionCooldownView != null)
                    potionCooldownView.setProgress(potionProgress);
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
        // In facing mode the stick is a plain button, so it swings at once.
        boolean alive = playerHealth > 0;
        if (aimJoystick.isHeld()) {
            if (attackFacing || aimJoystick.isAiming() || aimJoystick.heldMs() >= AIM_GRACE) {
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
        // Heavy attack: the charge builds while the button is held and strikes on release
        float charge = 0f;
        long chargeStart = heavyDownAt;
        if (chargeStart > 0 && !alive) {
            heavyDownAt = 0;
            chargeFull = false;
        } else if (chargeStart > 0) {
            charge = Math.min(1f, (float) (now - chargeStart) / HEAVY_CHARGE);
            if (charge >= 1f && !chargeFull) {
                chargeFull = true;
                effects.burst(player.x, player.y, Color.rgb(255, 214, 90), 12, 6f);
                buzz(15, 100);
            }
            if (heavyReleased) {
                heavyDownAt = 0;
                heavyReleased = false;
                heavyAttack(now, charge);
                charge = 0f;
            }
        }
        player.setCharge(charge);
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

        // --- 2. Update Camera --- (a fallen co-op player watches a teammate until the next floor)
        // While fighting, the camera leans toward where the player is looking
        boolean fighting = cameraLean && alive
                && (aimJoystick.isAiming() || now - lastAttackTime < 600 || chargeStart > 0);
        double look = Math.toRadians(lookAngle);
        leanX += ((fighting ? (float) Math.cos(look) * 70f : 0f) - leanX) * 0.06f;
        leanY += ((fighting ? (float) Math.sin(look) * 70f : 0f) - leanY) * 0.06f;
        CoopSession.RemotePlayer watched = playerHealth <= 0 ? livingTeammate() : null;
        if (watched != null) camera.update(watched.body.x, watched.body.y);
        else camera.update(player.getX() + leanX, player.getY() + leanY);
        gameDisplay.setSpectating(watched != null ? watched.name : null);

        if (coop != null) coopTick(now);

        // --- 3. Update Enemies (co-op guests only mirror the host's) ---
        long playback = isGuest() ? coop.hostClock.playbackTime(now) : 0L;
        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (isGuest()) {
                    e.followRemote(playback);
                    continue;
                }
                if (!nearestTarget(e)) continue; // every player is down
                if (!e.isAwake) {
                    e.tryWake(target[0], target[1]);
                    if (e.isAwake && e == activeBoss) announceBoss(); // someone walked into the arena
                    continue;
                }
                float prevEX = e.x, prevEY = e.y;
                e.update(target[0], target[1], player.radius);
                if (currentLayout != null && !inShopRoom) {
                    // Water slows enemies too, except bats flying over it
                    if (e.type != Enemy.Type.BAT && Props.inWater(currentLayout.decor, e.x, e.y)) {
                        e.x = prevEX + (e.x - prevEX) * WADE;
                        e.y = prevEY + (e.y - prevEY) * WADE;
                    }
                    float[] resolved = currentLayout.resolveCollision(
                            prevEX, prevEY, e.x, e.y, e.radius);
                    // Thrown hard into a wall or a pillar, it slams; a charger running into one is dazed
                    float kick = Math.abs(e.knockbackVX) + Math.abs(e.knockbackVY);
                    boolean blocked = Math.abs(resolved[0] - e.x) + Math.abs(resolved[1] - e.y) > 1f;
                    if (kick > 7f && blocked) wallSlam(e);
                    else if (blocked && e.crash()) {
                        effects.burst(e.x, e.y, Color.rgb(150, 140, 125), 16, 7f);
                        effects.number(e.x, e.y - e.radius - 20f, "CRASH", Color.rgb(255, 170, 90));
                        soundManager.play(SoundManager.Sfx.THUD);
                        if (camera != null) camera.startShake(140, 8f);
                    }
                    e.x = resolved[0];
                    e.y = resolved[1];
                }
            }
        }

        resolveCollisions();

        // Dynamic music: awake enemies close by push the soundtrack up, a boss maxes it
        float fight = 0f;
        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (e.isDead || !e.isAwake) continue;
                if (e.type == Enemy.Type.BOSS) fight += 3f;
                else if (getDistance(player.x, player.y, e.x, e.y) < 900f) fight += 1f;
            }
        }
        SoundManager.setGameIntensity(inShopRoom ? 0f : Math.min(1f, fight / 3f));
        // A living boss that has noticed you gets its own theme
        boolean bossAlive = activeBoss != null && !activeBoss.isDead && activeBoss.isAwake && !inShopRoom;
        SoundManager.setGameTrack(this, bossAlive ? R.raw.boss_music : R.raw.game_music);

        // Low on health in a fight: the heart pounds and the screen edges throb red
        boolean danger = lowHealthEffects && alive && playerHealth <= maxHealth / 4
                && (fight > 0f || now - lastHurtAt < 4000);
        if (danger && now - lastHeartbeat >= 850) {
            lastHeartbeat = now;
            soundManager.play(SoundManager.Sfx.HEARTBEAT);
            gameDisplay.setHeartbeat(now);
        } else if (!danger && lastHeartbeat != 0) {
            lastHeartbeat = 0;
            gameDisplay.setHeartbeat(0);
        }

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
        checkVault(now);
        tickExtras(now);

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
                // Death pauses the game, but the poof still has to play
                if ((!isGamePaused || player.isDying()) && layoutReady) {
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
        int elites = 0;
        float[] spot = new float[2];

        synchronized (enemies) {
            for (Room r : layout.rooms) {
                if (r.assignedEnemyCount <= 0) continue;
                for (int i = 0; i < r.assignedEnemyCount; i++) {
                    float spawnX = r.x + margin + (float)(Math.random() * Math.max(1f, r.width  - 2f * margin));
                    float spawnY = r.y + margin + (float)(Math.random() * Math.max(1f, r.height - 2f * margin));
                    // A shaped room has walled-off blocks: try again until the spot is open floor, else its middle
                    for (int tries = 0; tries < 12 && r.blockNear(spawnX, spawnY, margin) != null; tries++) {
                        spawnX = r.x + margin + (float)(Math.random() * Math.max(1f, r.width  - 2f * margin));
                        spawnY = r.y + margin + (float)(Math.random() * Math.max(1f, r.height - 2f * margin));
                    }
                    if (r.blockNear(spawnX, spawnY, margin) != null) { spawnX = r.centerX(); spawnY = r.centerY(); }

                    Enemy.Type type = pickEnemyType(floor);
                    Enemy enemy = new Enemy(spawnX, spawnY, type);
                    Props.pushOut(layout.decor, spawnX, spawnY, enemy.radius, spot);
                    enemy.x = spot[0];
                    enemy.y = spot[1];
                    enemy.applyFloorScaling(floor);

                    if (!pool.isEmpty()) {
                        enemy.templateIndex = (int) (Math.random() * pool.size());
                        enemy.applyTemplate(pool.get(enemy.templateIndex));
                    }
                    enemy.scaleHealth(partyHealth());
                    // Elites from floor 4, at most two a floor
                    if (floor >= 4 && elites < 2 && Math.random() < 0.08) {
                        enemy.makeElite();
                        elites++;
                    }
                    // Shield-bearers from floor 3: fighters that close in behind a raised shield
                    if (floor >= 3 && !enemy.shield
                            && (type == Enemy.Type.GRUNT || type == Enemy.Type.BRUTE || type == Enemy.Type.TANK)
                            && Math.random() < 0.2) enemy.makeShield();
                    enemy.ascend(ascension());
                    if (weeklyMod == WeeklyMod.HASTE) enemy.speed *= 1.25f;
                    enemy.setBehaviorTracker(behaviorTracker);
                    enemy.setLayout(layout);
                    // isAwake = false — enemy waits for player to enter trigger radius
                    enemies.add(enemy);
                    // Bats come in swarms
                    for (int b = 0; type == Enemy.Type.BAT && b < 2; b++) {
                        Enemy bat = new Enemy(enemy.x + (float) (Math.random() * 120 - 60),
                                enemy.y + (float) (Math.random() * 120 - 60), Enemy.Type.BAT);
                        bat.applyFloorScaling(floor);
                        bat.scaleHealth(partyHealth());
                        bat.ascend(ascension());
                        if (weeklyMod == WeeklyMod.HASTE) bat.speed *= 1.25f;
                        bat.setBehaviorTracker(behaviorTracker);
                        bat.setLayout(layout);
                        enemies.add(bat);
                    }
                }
            }
        }
        Log.d("FloorLayout", "Pre-placed " + enemies.size() + " enemies for floor " + floor);
    }

    /**
     * Picks a random enemy type for the floor from SPAWN_TABLE: each type joins on its own floor and its
     * chance grows a little every floor down to a cap; grunts fill whatever is left (at least 15%).
     * The floor's biome makes its native enemies half again as likely.
     * @param floor the current floor number
     * @return the selected enemy type
     */
    private Enemy.Type pickEnemyType(int floor) {
        float[] chance = new float[SPAWN_TABLE.length];
        float total = 0f;
        Biome biome = Biome.forFloor(floor);
        for (int i = 0; i < SPAWN_TABLE.length; i++) {
            float[] row = SPAWN_TABLE[i];
            if (floor < row[0]) continue;
            chance[i] = Math.min(row[3], row[1] + row[2] * (floor - row[0]));
            if (biome.favours(SPAWN_TYPES[i])) chance[i] *= 1.5f;
            total += chance[i];
        }
        // Deep floors would crowd grunts out entirely; scale the rest back so grunts keep 15%
        float scale = total > 85f ? 85f / total : 1f;
        float roll = (float) (Math.random() * 100.0);
        for (int i = 0; i < SPAWN_TABLE.length; i++) {
            roll -= chance[i] * scale;
            if (roll < 0f) return SPAWN_TYPES[i];
        }
        return Enemy.Type.GRUNT;
    }

    // Spawn chances in percent: {first floor, chance there, extra per floor, cap}; grunts take the rest.
    // Every type gains 1% a floor; the harder a type is to deal with, the lower its cap.
    private static final Enemy.Type[] SPAWN_TYPES = {Enemy.Type.SPEEDER, Enemy.Type.ARCHER, Enemy.Type.BRUTE,
            Enemy.Type.TANK, Enemy.Type.BAT, Enemy.Type.BOMBER, Enemy.Type.CHARGER, Enemy.Type.NECROMANCER};
    private static final float[][] SPAWN_TABLE = {
            {3, 15f, 1f, 22f},   // speeder
            {3, 8f, 1f, 18f},    // archer
            {6, 10f, 1f, 15f},   // brute
            {10, 5f, 1f, 12f},   // tank
            {2, 4f, 1f, 25f},    // bat
            {4, 3f, 1f, 16f},    // bomber
            {6, 3f, 1f, 14f},    // charger
            {8, 3f, 1f, 10f},    // necromancer
    };

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

        float aimAngle = (float) Math.toDegrees(Math.atan2(aimDY, aimDX));
        Weapon.Kind kind = w.kind();

        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (e.isDead) continue;

                // Each enemy can only be hit once per swing AND must respect weapon cooldown
                if (hitThisSwing.contains(e)) continue;
                if (now - e.lastHitTime < attackCooldown(w)) continue;
                if (!inArc(e, swingReach, aimAngle, swingArc)) continue;

                float dx = e.x - player.x;
                float dy = e.y - player.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                behaviorTracker.recordAttack(dist);

                boolean finisher = comboStep == kind.combo;
                boolean crit = Math.random() < critChance();
                int damage = Math.round(w.damage * damageMultiplier()
                        * (finisher ? kind.finisherDamage : 1f) * (crit ? 2f : 1f));
                e.lastHitTime = now;
                hitThisSwing.add(e);
                float push = dist > 0 ? w.knockback * 25f * (finisher ? kind.finisherPush : 1f) / dist : 0f;
                damageEnemy(e, damage, crit, dx * push, dy * push, finisher || crit, false);
                if (finisher) gameDisplay.punchZoom(160, 0.03f);
            }
        }
    }

    /** True when an enemy is within reach and inside a swing's cone; big bodies are easier to clip. */
    private boolean inArc(Enemy e, float reach, float angle, float arc) {
        float dx = e.x - player.x;
        float dy = e.y - player.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > reach + e.radius) return false;
        if (arc >= 180f) return true;
        float angleDiff = Math.abs(((float) Math.toDegrees(Math.atan2(dy, dx)) - angle + 540) % 360 - 180);
        return angleDiff <= arc + (float) Math.toDegrees(Math.atan2(e.radius, Math.max(1f, dist)));
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
     * Fetches the range's content from the shared pool.
     * Caches result keyed by rangeStart. After 3 failed attempts the range uses offline content.
     */
    private void generateBatch(int rangeStart, int rangeEnd) {
        if (isGuest()) return; // guests receive the host's batches in onBatch()
        if (batchCache.containsKey(rangeStart)) return;
        if (!batchesInFlight.add(rangeStart)) return;

        // Check local storage first — avoids a network call on load game. Co-op never touches the solo cache.
        String cachedJson = !oneOffRun() ? BatchGenerator.loadRawBatch(this, rangeStart) : null;
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

        batchGenerator.fetchBatch(rangeStart, rangeEnd, fallbackSeed(rangeStart), new BatchGenerator.OnBatchGeneratedListener() {
            @Override
            public void onSuccess(BatchResult result, String jsonText) {
                if (!oneOffRun()) BatchGenerator.saveRawBatch(GameActivity.this, rangeStart, jsonText);
                batchCache.put(rangeStart, result);
                batchesInFlight.remove(rangeStart);
                if (coop != null) coop.publishBatch(rangeStart, jsonText);
                Log.d("Batch", "=== Cached + saved batch for floors " + rangeStart + "-" + rangeEnd + " ===");
                runOnUiThread(() -> applyBatchToCurrentFloorIfReady(rangeStart));
            }

            @Override
            public void onFailure(String error) {
                int failures = BatchGenerator.MISSING.equals(error) ? 3 : batchFailures.merge(rangeStart, 1, Integer::sum);
                Log.e("Batch", "=== Batch " + rangeStart + " failed (" + failures + "): " + error + " ===");
                if (failures >= 3) {
                    // Stop waiting on the AI: offline content keeps enemies, shops and bosses coming
                    batchCache.put(rangeStart, BatchGenerator.fallbackBatch(rangeStart, rangeEnd, fallbackSeed(rangeStart)));
                    batchesInFlight.remove(rangeStart);
                    if (coop != null) coop.publishBatch(rangeStart, "fallback");
                    runOnUiThread(() -> {
                        Log.w("Batch", "AI content unavailable (" + error + "), using classic content");
                        effects.banner(player.x, player.y - player.radius - 50f, "CLASSIC CONTENT",
                                Color.rgb(184, 172, 145));
                        applyBatchToCurrentFloorIfReady(rangeStart);
                    });
                    return;
                }
                // Firestore calls back on the main thread; the delay keeps retries spaced out
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    batchesInFlight.remove(rangeStart);
                    if (threadsRunning) generateBatch(rangeStart, rangeEnd);
                }, 3000L * failures);
            }
        });
    }

    /** Offline content and pool pick seed: shared in co-op and daily runs so everyone gets the same. */
    private long fallbackSeed(int rangeStart) {
        return runSeed() != 0 ? runSeed() + rangeStart : System.nanoTime();
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
        if (trialRoom != null) return;                       // a Trial's waves are fighting
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
        List<Enemy> raised = new ArrayList<>();
        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (e.justEnraged) {
                    e.justEnraged = false;
                    effects.banner(e.x, e.y - e.radius - 60f, "ENRAGED", Color.rgb(255, 90, 40));
                    soundManager.play(SoundManager.Sfx.ENRAGE);
                }
                if (e.justBlinked) {
                    e.justBlinked = false;
                    effects.burst(e.x, e.y, Color.rgb(140, 190, 255), 26, 10f);
                    soundManager.play(SoundManager.Sfx.BLINK);
                }
                if (e.justFrenzied) {
                    e.justFrenzied = false;
                    effects.banner(e.x, e.y - e.radius - 60f, "FINAL FURY", Color.rgb(255, 120, 40));
                    soundManager.play(SoundManager.Sfx.ENRAGE);
                    if (camera != null) camera.startShake(300, 12f);
                }
                if (e.pendingSummons > 0) {
                    int before = raised.size();
                    if (e.type == Enemy.Type.NECROMANCER) raiseDead(e, e.pendingSummons, raised);
                    else raiseThralls(e, e.pendingSummons, raised);
                    if (raised.size() > before) soundManager.play(SoundManager.Sfx.SUMMON);
                    e.pendingSummons = 0;
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
                    boolean bomb = e.type == Enemy.Type.BOMBER;
                    boolean slam = bomb || (e.type == Enemy.Type.BOSS && e.strikeReach > e.radius * 2f);
                    if (bomb) {
                        blastEnemies(e);
                        effects.play("fx.blast", e.x, e.y + e.radius * 0.78f, false);
                    }
                    float knockback = e.getWeapon().knockback;
                    int dealt = e.strikeDamage;
                    resolveStrike(e.x, e.y, e.strikeDirX, e.strikeDirY, e.strikeReach, e.strikeArc,
                            e.strikeDamage, knockback, e.radius, slam, now);
                    // Vampiric elites mend from a hit that landed on this phone's player
                    if (lastHurtAt == now && e.eliteTrait() == Enemy.EliteTrait.VAMPIRIC) {
                        e.heal(dealt);
                        effects.number(e.x, e.y - e.radius - 20f, "+" + dealt, Color.rgb(200, 40, 60));
                    }
                    if (coop != null) coop.publishEvent("s," + floor + "," + e.x + "," + e.y + "," + e.strikeDirX
                            + "," + e.strikeDirY + "," + e.strikeReach + "," + e.strikeArc + "," + e.strikeDamage
                            + "," + knockback + "," + e.radius + "," + (slam ? 1 : 0));
                    e.strikeDamage = 0;
                }
            }
            enemies.addAll(raised);
        }
    }

    /** Necromancer: the nearest fallen bodies rise again as thralls, at most five alive at once. */
    private void raiseDead(Enemy necro, int count, List<Enemy> out) {
        int alive = 0;
        for (Enemy e : enemies) if (e.summoned && !e.isDead) alive++;
        int floor = floorManager.getCurrentFloor();
        for (int i = 0; i < count && alive + i < 5; i++) {
            float[] spot = null;
            float best = 700f;
            for (float[] s : deathSpots) {
                float d = getDistance(necro.x, necro.y, s[0], s[1]);
                if (d < best) { best = d; spot = s; }
            }
            if (spot == null) return;
            deathSpots.remove(spot);
            Enemy thrall = new Enemy(spot[0], spot[1], Enemy.Type.GRUNT);
            thrall.applyFloorScaling(floor);
            thrall.scaleHealth(partyHealth());
            thrall.makeThrall();
            thrall.ascend(ascension());
            thrall.isAwake = true;
            thrall.setBehaviorTracker(behaviorTracker);
            thrall.setLayout(currentLayout);
            out.add(thrall);
            effects.burst(spot[0], spot[1], Color.rgb(140, 255, 170), 18, 8f);
        }
    }

    /** A bomber's blast also hurts and throws the enemies around it. Host only, enemies lock held. */
    private void blastEnemies(Enemy bomber) {
        for (Enemy o : enemies) {
            if (o == bomber || o.isDead) continue;
            float dx = o.x - bomber.x, dy = o.y - bomber.y;
            float d = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
            if (d > bomber.strikeReach + o.radius) continue;
            o.takeDamage(Math.max(1, bomber.strikeDamage / 2));
            o.knockbackVX = dx / d * 14f;
            o.knockbackVY = dy / d * 14f;
        }
        effects.stain(bomber.x, bomber.y, 110f, Color.argb(120, 14, 11, 9));
        if (camera != null) camera.startShake(200, 10f);
    }

    /** Summoner boss: raises thralls around itself, at most five alive at once. */
    private void raiseThralls(Enemy boss, int count, List<Enemy> out) {
        int alive = 0;
        for (Enemy e : enemies) if (e.summoned && !e.isDead) alive++;
        int floor = floorManager.getCurrentFloor();
        for (int i = 0; i < count && alive + i < 5; i++) {
            double a = Math.random() * Math.PI * 2;
            float sx = boss.x + (float) Math.cos(a) * (boss.radius + 80f);
            float sy = boss.y + (float) Math.sin(a) * (boss.radius + 80f);
            if (currentLayout != null && !currentLayout.isFloor(sx, sy)) {
                sx = boss.x;
                sy = boss.y;
            }
            Enemy thrall = new Enemy(sx, sy, i % 2 == 0 ? Enemy.Type.GRUNT : Enemy.Type.SPEEDER);
            thrall.applyFloorScaling(floor);
            thrall.scaleHealth(partyHealth());
            thrall.makeThrall();
            thrall.ascend(ascension());
            thrall.isAwake = true;
            thrall.setBehaviorTracker(behaviorTracker);
            thrall.setLayout(currentLayout);
            out.add(thrall);
            effects.burst(sx, sy, Color.rgb(139, 95, 191), 18, 8f);
        }
    }

    private void spawnHostileShot(float x, float y, float dirX, float dirY, float speed, float range,
                                  int damage, float radius) {
        Projectile p = new Projectile(x, y, dirX, dirY, speed, range, damage, 0.15f, radius, HOSTILE_SHOT_COLOR);
        p.hostile = true;
        projectiles.add(p);
        soundManager.play(SoundManager.Sfx.ENEMY_SHOT);
    }

    /** A slam on the floor leaves a scorch mark; a melee strike shows its arc. Hurts this phone's player if inside. */
    private void resolveStrike(float x, float y, float dirX, float dirY, float reach, float arc,
                               int damage, float knockback, float bodyRadius, boolean slam, long now) {
        if (slam) {
            effects.burst(x, y, Color.rgb(255, 120, 40), 40, 16f); // slam shockwave
            effects.stain(x, y, 90f, Color.argb(110, 14, 11, 9));
            soundManager.play(SoundManager.Sfx.THUD);
        } else {
            if (arc < 180f)
                effects.slash(x, y, (float) Math.toDegrees(Math.atan2(dirY, dirX)), reach, arc, Color.rgb(255, 90, 60));
            soundManager.playEnemySwing();
        }
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
        if (playerHealth <= 0) return;
        if (now < invulnerableUntil) {
            checkPerfectDodge(now);
            return;
        }
        if (relic == Relic.AEGIS && now >= aegisReadyAt) {
            aegisReadyAt = now + 12000;
            invulnerableUntil = now + 400;
            int pale = Color.rgb(200, 220, 255);
            effects.banner(player.x, player.y - player.radius - 50f, "WARDED", pale);
            effects.burst(player.x, player.y, pale, 20, 8f);
            soundManager.play(SoundManager.Sfx.BLOCK);
            return;
        }
        damage = Math.max(1, Math.round(damage * (1f - 0.005f * perks[Perk.IRON_SKIN.ordinal()])));
        if (relic == Relic.SKULL) damage = Math.round(damage * 1.1f);
        if (tomeCurse) damage = Math.round(damage * TOME_CURSE);
        invulnerableUntil = now + HIT_INVULNERABILITY;
        lastRegen = now + 5000; // Bulwark pauses for 10s after a hit
        hitStop(60);
        player.flashUntil = now + 150;
        damagePlayer(damage);
        if (showDamageNumbers)
            effects.number(player.x, player.y - player.radius, "-" + damage, Color.rgb(255, 80, 80));
        if (camera != null) camera.startShake(250, 14f);
        buzz(80, 255);
        lastHurtAt = now;

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

    /**
     * Damage number and sparks when the player hits an enemy; crits show gold with a "!".
     * Blood flies out of the far side (nx, ny) and some of it stays on the floor.
     */
    private void showEnemyHit(Enemy e, int damage, boolean crit, float nx, float ny) {
        if (crit) {
            if (showDamageNumbers) effects.number(e.x, e.y - e.radius, damage + "!", Color.rgb(255, 200, 40));
            effects.burst(e.x, e.y, Color.rgb(255, 200, 40), 14, 10f);
        } else {
            if (showDamageNumbers) effects.number(e.x, e.y - e.radius, String.valueOf(damage), Color.WHITE);
            effects.burst(e.x, e.y, Color.rgb(255, 230, 150), 6, 6f);
        }
        // A pixel spark where the blow lands, flying the way it was struck
        effects.play(crit ? "fx.crit" : "fx.hit", e.x - nx * e.radius * 0.3f, e.y - ny * e.radius * 0.3f, nx < 0);
        int blood = e.mimic ? Color.rgb(120, 84, 48) : effects.gore ? BLOOD : Color.rgb(255, 230, 150);
        effects.spray(e.x + nx * e.radius * 0.5f, e.y + ny * e.radius * 0.5f, nx, ny, blood,
                crit ? 14 : 7, crit ? 12f : 8f, 0.5f);
        if (!e.mimic && Math.random() < (crit ? 0.8 : 0.35)) {
            float d = e.radius + 20f + (float) Math.random() * 40f;
            effects.stain(e.x + nx * d, e.y + ny * d, 6f + (float) Math.random() * 8f, BLOOD_STAIN);
        }
    }

    /** A hit glances off a raised guard: sparks, a clang and a small recoil, but no damage. */
    private void showBlocked(Enemy e, float nx, float ny) {
        effects.spray(e.x - nx * e.radius, e.y - ny * e.radius, -nx, -ny, Color.rgb(255, 230, 160), 10, 9f, 0.7f);
        effects.number(e.x, e.y - e.radius - 30f, "BLOCKED", Color.rgb(200, 220, 255));
        soundManager.play(SoundManager.Sfx.BLOCK);
        hitStop(40);
        buzz(20, 120);
        if (getDistance(player.x, player.y, e.x, e.y) > e.radius + player.radius + 160f) return; // a shot
        float oldX = player.x, oldY = player.y;
        player.x -= nx * 18f;
        player.y -= ny * 18f;
        if (currentLayout != null && !inShopRoom) {
            float[] r = currentLayout.resolveWallCollision(oldX, oldY, player.x, player.y, player.radius);
            player.x = r[0];
            player.y = r[1];
        }
    }

    /** Counts daily quest progress and cheers when one is done. */
    private void quest(Quests q, int amount) {
        if (!Quests.add(this, q, amount)) return;
        effects.banner(player.x, player.y - player.radius - 90f, "QUEST COMPLETE", Color.rgb(244, 215, 122));
        soundManager.play(SoundManager.Sfx.PERK_PICK);
    }

    // Kinds already marked as met this session, so a hit doesn't touch the prefs every time
    private final Set<String> bestiaryMet = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Bestiary (MenuActivity.showBestiary): marks an enemy's kind as met, announcing a first meeting, and
     * counts kills. Kinds are archetypes, or BOSS_ + the boss kind; mimics and raised thralls don't count.
     */
    private void bestiary(Enemy e, boolean killed) {
        if (e.mimic || e.summoned || userId == null) return;
        String key = e.type == Enemy.Type.BOSS ? "BOSS_" + e.getBossKind().name() : e.type.name();
        SharedPreferences prefs = getSharedPreferences("bestiary_" + userId, MODE_PRIVATE);
        if (bestiaryMet.add(key) && !prefs.getBoolean("met_" + key, false)) {
            prefs.edit().putBoolean("met_" + key, true).apply();
            effects.banner(e.x, e.y - e.radius - 70f, "NEW IN BESTIARY", Color.rgb(244, 215, 122));
        }
        if (killed) prefs.edit().putInt("kills_" + key, prefs.getInt("kills_" + key, 0) + 1).apply();
    }

    /** A heavy blow smashes through a raised guard. */
    private void showGuardBreak(Enemy e) {
        quest(Quests.GUARDS, 1);
        effects.banner(e.x, e.y - e.radius - 50f, "GUARD BREAK", Color.rgb(255, 210, 90));
        effects.burst(e.x, e.y, Color.rgb(176, 150, 96), 18, 11f);
        soundManager.play(SoundManager.Sfx.GUARD_BREAK);
        hitStop(110);
        buzz(45, 230);
    }

    /** An enemy thrown into a wall or a pillar: extra damage, dust, a thud and a moment dazed. Host only. */
    private void wallSlam(Enemy e) {
        quest(Quests.SLAMS, 1);
        e.knockbackVX = 0f;
        e.knockbackVY = 0f;
        int damage = Math.max(1, Math.round(player.getWeapon().damage * damageMultiplier() * 0.6f));
        e.takeDamage(damage);
        e.stun(500);
        statsManager.onDamageGiven(damage);
        effects.number(e.x, e.y - e.radius - 20f, damage + " SLAM", Color.rgb(255, 170, 90));
        effects.burst(e.x, e.y, Color.rgb(150, 140, 125), 16, 7f);
        effects.stain(e.x, e.y, e.radius * 0.5f, BLOOD_STAIN);
        soundManager.play(SoundManager.Sfx.THUD);
        if (camera != null) camera.startShake(140, 8f);
        buzz(25, 160);
        hitStop(50);
    }

    /** Crates and barrels caught in a swing break apart, now and then spilling coins. Solo only. */
    private void smashProps(float reach, float angle, float arc) {
        if (coop != null || currentLayout == null || inShopRoom) return;
        for (Props.Decor d : currentLayout.decor) {
            if (!Props.breakable(d)) continue;
            float dx = d.x - player.x, dy = d.y - player.y;
            if (dx * dx + dy * dy > (reach + d.a) * (reach + d.a)) continue;
            float angleDiff = Math.abs(((float) Math.toDegrees(Math.atan2(dy, dx)) - angle + 540) % 360 - 180);
            if (arc < 180f && angleDiff > arc + 15f) continue;
            d.broken = true;
            quest(Quests.CRATES, 1);
            effects.burst(d.x, d.y, Color.rgb(120, 84, 48), 14, 8f);
            soundManager.play(SoundManager.Sfx.CRATE_SMASH);
            buzz(15, 90);
            if (Math.random() < 0.3) spawnGoldCoins(d.x, d.y, 1 + (int) (Math.random() * 3));
        }
    }

    /** Interrupts an enemy's wind-up, so a well-timed heavy hit cancels its attack. */
    private void staggerEnemy(Enemy e) {
        if (e.stagger()) {
            effects.number(e.x, e.y - e.radius - 40f, "STAGGER", Color.rgb(120, 200, 255));
            soundManager.play(SoundManager.Sfx.STAGGER);
        }
    }

    /** Phantom synergy: the dash grazes every enemy it passes through, once each, for half damage. */
    private void phantomCuts(long now) {
        if (phantomDash != dashStartTime) {
            phantomDash = dashStartTime;
            phantomHits.clear();
        }
        Weapon w = player.getWeapon();
        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (e.isDead || phantomHits.contains(e)
                        || getDistance(player.x, player.y, e.x, e.y) > player.radius + e.radius + 30f) continue;
                phantomHits.add(e);
                e.lastHitTime = now;
                int damage = Math.max(1, Math.round(w.damage * damageMultiplier() * 0.5f));
                damageEnemy(e, damage, false, dashDX * 12f, dashDY * 12f, false, false);
                effects.slash(e.x, e.y, (float) Math.toDegrees(Math.atan2(dashDY, dashDX)), e.radius + 30f, 40f,
                        Color.rgb(160, 112, 232));
            }
        }
    }

    /**
     * An attack met the player in the first moments of a dash: time slows, the dash recharges
     * and the next hits are sure crits.
     */
    private void checkPerfectDodge(long now) {
        if (now - dashStartTime > DASH_DURATION + 80 || now - lastPerfectDodge < 1000) return;
        lastPerfectDodge = now;
        quest(Quests.DODGES, 1);
        lastDashTime = 0;
        perfectCritUntil = now + 1500;
        // Slow-mo must not leave the player standing in the attack once the dash ends
        invulnerableUntil = Math.max(invulnerableUntil, now + 900);
        int blue = Color.rgb(120, 200, 255);
        effects.banner(player.x, player.y - player.radius - 50f, "PERFECT", blue);
        effects.burst(player.x, player.y, blue, 24, 9f);
        effects.slash(player.x, player.y, 0f, player.radius + 90f, 180f, blue);
        soundManager.play(SoundManager.Sfx.PERFECT_DODGE);
        buzz(30, 200);
        slowMo(600, 0.35f);
    }

    /**
     * Deals the player's damage to an enemy. A raised guard stops it unless the hit is heavy, which breaks
     * the guard. A co-op guest only shows the hit and reports it; the host applies it to the real enemy.
     */
    private void damageEnemy(Enemy e, int damage, boolean crit, float pushX, float pushY, boolean stagger,
                             boolean heavy) {
        bestiary(e, false);
        float dx = e.x - player.x, dy = e.y - player.y;
        float d = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
        boolean guardBroken = false;
        if (e.blocks(player.x, player.y)) {
            if (!heavy) {
                showBlocked(e, dx / d, dy / d);
                return;
            }
            showGuardBreak(e);
            guardBroken = true;
        }
        if (crit) soundManager.play(SoundManager.Sfx.CRIT);
        else soundManager.playHitEnemy();
        statsManager.onDamageGiven(damage);
        showEnemyHit(e, damage, crit, dx / d, dy / d);
        if (crit || stagger) hitStop(crit ? 70 : 45);
        buzz(crit || heavy ? 30 : 12, crit || heavy ? 180 : 70);
        if (crit && has(Synergy.MIDAS) && Math.random() < 0.25) spawnGoldCoins(e.x, e.y, 1);
        weaponTraits(e, damage);
        if (isGuest()) {
            e.predictHit(damage);
            if (e.isDead) deadEnemyAt.put(e.id, System.currentTimeMillis());
            coop.sendHit(e.id + "," + damage + "," + pushX + "," + pushY + "," + (heavy ? 2 : stagger ? 1 : 0));
            return;
        }
        e.takeDamage(damage);
        e.knockbackVX = pushX;
        e.knockbackVY = pushY;
        if (guardBroken) e.breakGuard();
        else if (stagger) staggerEnemy(e);
        e.maybeGuard();
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

            if (playerHealth <= 0 && altar[Altar.SECOND_WIND.ordinal()] > 0 && !secondWindUsed) {
                secondWindUsed = true;
                playerHealth = Math.max(1, maxHealth * 3 / 10);
                animateHealthBar(0, playerHealth);
                invulnerableUntil = System.currentTimeMillis() + 1500;
                effects.banner(player.x, player.y - player.radius - 50f, "SECOND WIND", Color.rgb(244, 215, 122));
                effects.burst(player.x, player.y, Color.rgb(244, 215, 122), 40, 12f);
                soundManager.play(SoundManager.Sfx.REVIVE);
            }
            if (playerHealth <= 0) soundManager.play(SoundManager.Sfx.PLAYER_DEATH);

            if (playerHealth <= 0 && coop != null) {
                // Co-op: the run goes on; a fallen player revives on the next floor
                player.startDeathAnimation();
                effects.poof(player.x, player.y, deathColor != 0 ? deathColor : player.getColor());
                statsManager.onPlayerDied();
                effects.banner(player.x, player.y - player.radius - 50f, "YOU FELL", Color.rgb(224, 72, 72));
                effects.number(player.x, player.y - player.radius - 10f, "Revive on the next floor", Color.WHITE);
            } else if (playerHealth <= 0) {
                isGamePaused = true;
                player.startDeathAnimation();
                effects.poof(player.x, player.y, deathColor != 0 ? deathColor : player.getColor());
                statsManager.onPlayerDied();
                statsManager.flush();
                new android.os.Handler().postDelayed(
                        () -> showGameOverDialog(dailySeed == 0 ? null : weeklyMod != null
                                ? "Your weekly challenge ends here." : "Your daily run ends here."),
                        player.getDeathDuration());
            }
        });
    }

    private void showArmorBreakPopup(String slotName) {
        android.view.ViewGroup root = (android.view.ViewGroup) findViewById(R.id.game_root);
        if (root == null) return;
        soundManager.play(SoundManager.Sfx.ARMOR_BREAK);

        TextView popup = new TextView(this);
        popup.setText(slotName + " ARMOR BROKEN");
        popup.setTextColor(ContextCompat.getColor(this, R.color.crimson_glow));
        popup.setTextSize(14f);
        popup.setTypeface(getResources().getFont(R.font.pixel), android.graphics.Typeface.BOLD);
        popup.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_warning, 0, 0, 0);
        popup.setCompoundDrawableTintList(popup.getTextColors());
        popup.setCompoundDrawablePadding(16);
        popup.setPadding(28, 14, 28, 14);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.argb(210, 25, 0, 0));
        bg.setCornerRadius(0f); // square, like the pixel frames
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
        spawnRoomChest();
        effects.clear();
        invulnerableUntil = 0;
        healLeft = 0;
        deathSpots.clear();
        statsManager.startRun();
        secondWindUsed = false;
        adReviveUsed = false;

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
                    // Guests copy the host's count; mimics, thralls and the Trial's waves don't count at all
                    if (!isGuest() && !e.mimic && !e.summoned && !trialFoes.contains(e))
                        floorManager.onEnemyKilled(player.x, player.y);
                    statsManager.onEnemyKilled();
                    if (!e.summoned) quest(Quests.KILLS, 1);
                    bestiary(e, true);
                    if (relic == Relic.FANG && !e.summoned) healPlayer(2);
                    // Explosive elites go off a moment after they fall
                    if (e.eliteTrait() == Enemy.EliteTrait.EXPLOSIVE) {
                        pendingBlasts.add(new long[]{(long) e.x, (long) e.y, Math.round(e.getDamage() * 0.8f),
                                System.currentTimeMillis() + 700});
                        effects.burst(e.x, e.y, Color.rgb(255, 140, 40), 20, 6f);
                        effects.number(e.x, e.y - e.radius - 30f, "!", Color.rgb(255, 140, 40));
                    }
                    if (e.elite) quest(Quests.ELITES, 1);
                    if (e.type == Enemy.Type.BAT) quest(Quests.BATS, 1);
                    effects.burst(e.x, e.y, e.getColor(), e.type == Enemy.Type.BOSS ? 60 : 16, 9f);
                    effects.play("fx.poof", e.x, e.y + e.radius * 0.78f, false);
                    // The body lies a while over a pool of blood
                    effects.corpse(e.x, e.y, e.radius, e.getColor());
                    if (!e.mimic) effects.stain(e.x, e.y, e.radius * 0.8f, BLOOD_STAIN);
                    if (!e.mimic && !e.summoned && e.type != Enemy.Type.BOSS && e.type != Enemy.Type.BOMBER) {
                        deathSpots.add(new float[]{e.x, e.y});
                        if (deathSpots.size() > 20) deathSpots.remove(0);
                    }
                    hitStop(50);
                    soundManager.play(SoundManager.Sfx.ENEMY_DEATH);
                    if (e.mimic) dropLootChest(e.x, e.y);
                    // Keys: elites, mimics and bosses always drop one, others now and then
                    if (e.elite || e.mimic || e.type == Enemy.Type.BOSS
                            || (!e.summoned && Math.random() < 0.04 + 0.02 * altar[Altar.LOCKSMITH.ordinal()]))
                        gainKey(e.x, e.y);
                    if (!e.noGoldDrop)
                    {
                        spawnGoldCoins(e.x, e.y, withGreed(e.getGoldDrop()));
                        int xp = Math.round(e.getXpReward() * (1f + 0.05f * altar[Altar.WISDOM.ordinal()]));
                        runOnUiThread(() -> gainXp(xp));
                    }
                }
            }
            enemies.removeIf(e -> e.readyToRemove);
        }

        if (floorManager.isPortalOpen() && !enemiesKilledOnPortalOpen) {
            enemiesKilledOnPortalOpen = true;
            killAllActiveEnemies();
            soundManager.play(SoundManager.Sfx.PORTAL_OPEN);
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
     * In facing mode it always attacks straight ahead.
     * Runs on the logic thread.
     * @param now current time in milliseconds
     */
    private void tryAttack(long now) {
        Weapon w = player.getWeapon();
        if (now - lastAttackTime < attackCooldown(w)) return;
        if (!aimAtTarget(w)) return;
        swingCount++;

        if (w.isProjectile) {
            fireProjectile(aimDX, aimDY);
            return;
        }
        // Each kind of weapon has its own combo length, arcs and reach
        Weapon.Kind kind = w.kind();
        comboStep = now - lastAttackTime <= attackCooldown(w) + COMBO_WINDOW ? comboStep % kind.combo + 1 : 1;
        boolean finisher = comboStep == kind.combo;
        swingArc = finisher ? kind.finisherArc : kind.arc;
        swingReach = player.radius + w.range * kind.reach;
        lastAttackTime = now;
        attackStartTime = now;
        isAttacking = true;
        soundManager.playSwingMelee();
        float angle = (float) Math.toDegrees(Math.atan2(aimDY, aimDX));
        player.startSwing(angle, Math.min(swingArc, 110f), kind == Weapon.Kind.SPEAR, finisher ? 190 : 150, false);
        effects.slash(player.x, player.y, angle, swingReach, swingArc,
                finisher ? Color.rgb(255, 210, 90) : Color.WHITE);
        if (finisher && kind == Weapon.Kind.HAMMER) {
            // The hammer's finisher slams the ground all around
            effects.burst(player.x, player.y, Color.rgb(150, 140, 125), 16, 8f);
            if (camera != null) camera.startShake(120, 6f);
            soundManager.play(SoundManager.Sfx.THUD);
        }
        smashProps(swingReach, angle, swingArc);
    }

    /**
     * Aims where the attack stick is dragged, otherwise at the nearest awake enemy in reach, otherwise
     * straight ahead (always straight ahead in facing mode). Sets aimDX/aimDY; false if there is no direction.
     */
    private boolean aimAtTarget(Weapon w) {
        if (aimJoystick.isAiming()) {
            aimDX = aimJoystick.getAimX();
            aimDY = aimJoystick.getAimY();
        } else {
            Enemy target = attackFacing ? null : nearestEnemy(w.isProjectile ? w.range : player.radius + w.range + 160f);
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
        if (len < 0.01f) return false;
        aimDX /= len;
        aimDY /= len;
        return true;
    }

    /**
     * Heavy attack: a slow, wide blow that grows with the charge (1.5x to 3x damage), throws enemies hard
     * and breaks guards. A ranged weapon looses a big charged shot instead. Runs on the logic thread.
     */
    private void heavyAttack(long now, float charge) {
        lastHeavyTime = now;
        chargeFull = false;
        Weapon w = player.getWeapon();
        if (!aimAtTarget(w)) return;
        Weapon.Kind kind = w.kind();
        float power = 1.5f + 1.5f * charge;
        float angle = (float) Math.toDegrees(Math.atan2(aimDY, aimDX));
        lastAttackTime = now;
        swingCount++;
        soundManager.play(SoundManager.Sfx.HEAVY_SWING);
        buzz(40, 200);
        if (w.isProjectile) {
            Projectile p = new Projectile(player.x, player.y, aimDX, aimDY, 22f, w.range * 1.2f,
                    Math.round(w.damage * damageMultiplier() * power), w.knockback * 3f, 24f, Color.rgb(255, 210, 90));
            p.heavy = true;
            projectiles.add(p);
            soundManager.playFireProjectile();
            return;
        }
        float reach = player.radius + w.range * kind.reach * 1.25f + 20f;
        float arc = kind == Weapon.Kind.SPEAR ? 30f : Math.max(70f, kind.finisherArc);
        player.startSwing(angle, Math.min(arc, 120f), kind == Weapon.Kind.SPEAR, 220, true);
        effects.slash(player.x, player.y, angle, reach, arc, Color.rgb(255, 210, 90));
        if (camera != null) camera.startShake(160, 7f);
        smashProps(reach, angle, arc);
        boolean landed = false;
        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (e.isDead || !inArc(e, reach, angle, arc)) continue;
                landed = true;
                e.lastHitTime = now;
                boolean crit = Math.random() < critChance();
                int damage = Math.round(w.damage * damageMultiplier() * power * (crit ? 2f : 1f));
                float dx = e.x - player.x, dy = e.y - player.y;
                float push = Math.max(w.knockback, 0.25f) * 75f / Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
                damageEnemy(e, damage, crit, dx * push, dy * push, true, true);
            }
        }
        if (landed) {
            quest(Quests.HEAVY, 1);
            hitStop(90);
            gameDisplay.punchZoom(200, 0.05f);
            soundManager.play(SoundManager.Sfx.THUD);
        }
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
     * Spin special: an evasive shove. Throws every enemy around the player far back with light damage,
     * interrupts wind-ups and grants a moment of invulnerability. Runs on the logic thread.
     * @param now current time in milliseconds
     */
    private void spinAttack(long now) {
        lastSpinTime = now;
        Weapon w = player.getWeapon();
        boolean storm = has(Synergy.BLADESTORM);
        float reach = player.radius + (storm ? 250f : 200f);
        int damage = Math.max(1, Math.round(Math.max(w.damage, 6) * damageMultiplier() * (storm ? 0.75f : 0.5f)));
        int color = Color.rgb(140, 200, 255);
        player.startSpin(reach, color);
        effects.burst(player.x, player.y, color, 10, 6f);
        effects.slash(player.x, player.y, 0f, reach, 180f, color);
        soundManager.play(SoundManager.Sfx.SPIN);
        invulnerableUntil = Math.max(invulnerableUntil, now + 450);
        smashProps(reach, 0f, 180f);

        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (e.isDead) continue;
                float dx = e.x - player.x;
                float dy = e.y - player.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > reach + e.radius) continue;
                e.lastHitTime = now;
                float push = dist > 0 ? SPIN_PUSH / dist : 0f;
                damageEnemy(e, damage, false, dx * push, dy * push, true, false);
            }
        }
    }

    /**
     * Handles a tap on the dash button.
     * Dashes in the joystick direction, or the facing direction when standing still.
     */
    private void dash() {
        long now = System.currentTimeMillis();
        if (isDashing || playerHealth <= 0) return;
        if (now - lastDashTime < dashCooldown()) return;

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
        if (now - lastAttackTime < attackCooldown(player.getWeapon())) return;
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
                if (currentLayout != null && !inShopRoom && (!currentLayout.isFloor(p.x, p.y)
                        || Props.blocks(currentLayout.decor, p.x, p.y))) p.isDead = true;
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
                    if (getDistance(p.x, p.y, player.x, player.y) < p.getRadius() + player.radius) {
                        if (now < invulnerableUntil) {
                            checkPerfectDodge(now); // dashing through it
                        } else {
                            p.isDead = true;
                            hitPlayer(p.damage, p.x, p.y, p.knockback, now);
                        }
                    }
                    continue;
                }

                synchronized (enemies) {
                    for (Enemy e : enemies) {
                        if (e.isDead) continue;

                        float dist = getDistance(p.x, p.y, e.x, e.y);
                        if (dist < p.getRadius() + e.radius) {
                            boolean crit = Math.random() < critChance();
                            behaviorTracker.recordAttack(getDistance(player.x, player.y, e.x, e.y));
                            float dx = e.x - p.x;
                            float dy = e.y - p.y;
                            float d = (float) Math.sqrt(dx * dx + dy * dy);
                            float push = d > 0 ? p.knockback * 25f / d : 0f;
                            damageEnemy(e, crit ? p.damage * 2 : p.damage, crit, dx * push, dy * push, crit || p.heavy,
                                    p.heavy);

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
        txtFloorTitle.setText(floorTitle(floorManager.getCurrentFloor() + 1));
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

    /** "Floor 6", and on the first floor of a new biome its name and rule underneath, in its fire's colour. */
    private CharSequence floorTitle(int floor) {
        String head = "Floor " + floor;
        if (!Biome.startsAt(floor)) return head;
        Biome biome = Biome.forFloor(floor);
        String name = biome.title.toUpperCase(), rule = biome.rule;
        android.text.SpannableString title = new android.text.SpannableString(head + "\n" + name + "\n" + rule);
        int nameAt = head.length() + 1, ruleAt = nameAt + name.length() + 1;
        title.setSpan(new android.text.style.RelativeSizeSpan(0.42f), nameAt, title.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setSpan(new android.text.style.ForegroundColorSpan(biome.flame), nameAt, ruleAt - 1,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setSpan(new android.text.style.RelativeSizeSpan(0.7f), ruleAt, title.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setSpan(new android.text.style.ForegroundColorSpan(Color.rgb(184, 172, 145)), ruleAt, title.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return title;
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
                // Co-op and daily items only last for the run, so they're never stored
                if (!oneOffRun()) dbManager.saveGeneratedWeapon(
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
                if (!oneOffRun()) dbManager.saveGeneratedArmor(
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
            applyRarityBorder(card, rarityColor, item.name);

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
                        showItemDetailDialog(dialog, item, goldDisplay, shopBoughtIndices, index));
            } else {
                card.setOnClickListener(v ->
                        showItemDetailDialog(dialog, item, goldDisplay, shopBoughtIndices, index));
            }

            rows[i / 3].addView(card);
        }
    }

    private void showItemDetailDialog(android.app.Dialog shopDialog,
                                      ShopItem item,
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
        tintRarityBadge(rarityView, rarityColor);
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
            soundManager.play(SoundManager.Sfx.PURCHASE);

            boughtIndices.add(itemIndex);
            // Redraw every card: items that were affordable may not be any more
            populateShopGridFromItems(shopDialog, shopDialog.findViewById(R.id.shop_items_container),
                    goldDisplay, getShopItemsForFloor(floorManager.getCurrentFloor()));

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

        // Opt-in ad: a fresh stock, once a floor
        MaterialButton restock = dialog.findViewById(R.id.btn_shop_restock);
        int floor = floorManager.getCurrentFloor();
        if (Ads.isReady() && restockedFloor != floor) restock.setVisibility(View.VISIBLE);
        restock.setOnClickListener(v -> Ads.show(this, () -> {
            restockedShop = BatchGenerator.freshStock(floor, System.nanoTime());
            restockedFloor = floor;
            shopBoughtIndices.clear();
            loading.setVisibility(View.GONE);
            container.setVisibility(View.VISIBLE);
            populateShopGridFromItems(dialog, container, goldDisplay, withoutPotions(restockedShop));
            soundManager.play(SoundManager.Sfx.PURCHASE);
        }, () -> {
            restock.setVisibility(View.GONE);
            hideSystemUIOnWindow(dialog.getWindow());
        }));

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
            case WEAPON: {
                addWeaponGrid(box, item.name, item.damage, item.range, item.cooldown, item.knockback,
                        Weapon.kindOf(item.name, item.isProjectile).title(), player.getWeapon());
                addTraitBox(box, Weapon.traitOf(item.name, item.rarity), "LEGENDARY".equalsIgnoreCase(item.rarity));
                break;
            }
            case ARMOR: {
                String slot = String.valueOf(item.armorSlot);
                Armor worn = null;
                try { worn = equippedArmor.get(Armor.Slot.valueOf(slot.toUpperCase())); } catch (IllegalArgumentException ignored) {}
                addStatRow(box, R.drawable.ic_shield, "Defense", compared(item.defense, worn != null ? worn.defense : 0, true, false));
                addStatRow(box, R.drawable.ic_anvil, "Durability", worn == null ? String.valueOf((int) item.maxDurability)
                        : compared(item.maxDurability, worn.maxDurability, true, false));
                addStatRow(box, slotIcon(slot), "Slot", slot);
                break;
            }
            case CONSUMABLE:
                showConsumableStats(box, item);
                break;
        }
    }

    // One icon + label + value line, reusing the stats screen row
    private void addStatRow(LinearLayout box, int icon, String label, CharSequence value) {
        View row = getLayoutInflater().inflate(R.layout.item_stat_row, box, false);
        // Item cards stack up to 7 of these; tighter rows keep the card within a landscape screen
        int padV = Math.round(6 * getResources().getDisplayMetrics().density);
        row.setPadding(row.getPaddingLeft(), padV, row.getPaddingRight(), padV);
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
     * Called when the exit portal opens. A Trial still being fought keeps its waves: they are its prize's price.
     */
    private void killAllActiveEnemies() {
        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (!e.isDead && !trialFoes.contains(e)) {
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
                soundManager.play(SoundManager.Sfx.REVIVE);
                playerHealth = maxHealth / 2;
                secondWindUsed = false;
                playerHealthBar.setProgress(playerHealth);
                if (txtHealthValue != null) txtHealthValue.setText(playerHealth + " / " + maxHealth);
            }
            portalRequested = false;
            coop.snapshots.clear();
            deadEnemyAt.clear();
            if (coop.isHost()) coop.clearEvents();
        }
        // A co-op guest can be pulled to the next floor while shopping
        if (shopDialog != null && shopDialog.isShowing()) shopDialog.dismiss();
        shopDialogOpen = false;
        statsManager.onFloorCleared(floorManager.getCurrentFloor());
        quest(Quests.FLOORS, 1);
        statsManager.flush();
        setCheckpoint(floorManager.getCurrentFloor());

        activeBoss = null;
        bossSpawnHandled = false;

        synchronized (enemies)       { enemies.clear(); }
        synchronized (goldCoins)     { goldCoins.clear(); }
        synchronized (treasureChests){ treasureChests.clear(); }
        synchronized (projectiles)   { projectiles.clear(); }
        effects.clear();
        deathSpots.clear();

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
        spawnRoomChest();
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
            currentBossKind  = bossData.bossKind != null ? bossData.bossKind : Enemy.BossKind.BRUTE;
        } else {
            currentBossName  = "THE ANCIENT ONE";
            currentBossTitle = "Ruler of the Void";
            currentBossColor = android.graphics.Color.RED;
            currentBossKind  = Enemy.BossKind.BRUTE;
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
        quest(Quests.BOSS, 1);
        behaviorTracker.onBossKilled();
        // The killing blow lingers: slow motion (solo) and a camera punch-in
        slowMo(1300, 0.25f);
        gameDisplay.punchZoom(1300, 0.18f);
        if (camera != null) camera.startShake(400, 16f);
        // Every 10th floor's boss raises the Ascension for the floors after it
        if (floorManager.getCurrentFloor() % 10 == 0) {
            effects.banner(player.x, player.y - player.radius - 110f, "ASCENSION " + (ascension() + 1),
                    Color.rgb(176, 96, 255));
            soundManager.play(SoundManager.Sfx.ENRAGE);
        }
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
        activeBoss.setBossKind(currentBossKind);
        // It waits in its arena until a player walks in, instead of wandering to the door
        activeBoss.arena = currentLayout != null ? currentLayout.bossRoom : null;
        activeBoss.isAwake = activeBoss.arena == null;
        activeBoss.applyFloorScaling(floorManager.getCurrentFloor());
        BatchResult bossData = getBossDataForFloor(floorManager.getCurrentFloor());
        int floor = floorManager.getCurrentFloor();
        activeBoss.applyBossScaling(
                (bossData != null ? bossData.bossHpMultiplier : BatchGenerator.bossHpMultiplier(floor)) * partyHealth(),
                bossData != null ? bossData.bossDmgMultiplier : BatchGenerator.bossDmgMultiplier(floor));
        activeBoss.ascend(ascension());
        activeBoss.setBehaviorTracker(behaviorTracker);
        activeBoss.setLayout(currentLayout);

        // Apply boss AI name
        if (currentBossName != null) {
            activeBoss.displayName  = currentBossName;
            activeBoss.displayColor = currentBossColor;
        }

        enemies.add(activeBoss);
        if (activeBoss.isAwake) announceBoss();
    }

    /** Sweeps the boss title card across the screen: label, gold rules, name slam, title. */
    private void announceBoss() {
        if (activeBoss != null) bestiary(activeBoss, false);
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
        runOnUiThread(() -> aimJoystick.setVisibility(View.VISIBLE));
    }

    /** Spawns 3 chests at 120° intervals around the boss death position on walkable floor. */
    private void spawnTreasureChests(float bossX, float bossY) {
        long launch = System.currentTimeMillis() + 250; // the boss starts to fall first

        synchronized (treasureChests) {
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

                TreasureChest chest = new TreasureChest(cx, cy, bossX, bossY, launch + i * 180L);
                chest.boss = true;
                pickChestReward(chest);
                treasureChests.add(chest);
            }
        }
    }

    /** A random item from this floor range's shop pool; left empty until the AI content arrives. */
    private void pickChestReward(TreasureChest chest) {
        List<ShopItem> pool = getChestItemPool(floorManager.getCurrentFloor());
        if (chest.vault || chest.cursed) {
            // Vault and cursed chests: only the rarest items on offer
            List<String> order = java.util.Arrays.asList("COMMON", "RARE", "EPIC", "LEGENDARY");
            int best = -1;
            for (ShopItem s : pool) best = Math.max(best, order.indexOf(String.valueOf(s.rarity).toUpperCase()));
            List<ShopItem> top = new ArrayList<>();
            for (ShopItem s : pool) if (order.indexOf(String.valueOf(s.rarity).toUpperCase()) == best) top.add(s);
            pool = top;
        }
        if (pool.isEmpty()) return;
        ShopItem reward = pool.get((int) (Math.random() * pool.size()));
        chest.rewardItem      = reward;
        chest.rewardItemName  = reward.name;
        chest.rewardItemColor = getRarityColor(reward.rarity);
    }

    /** The treasure room's and the vault's chests, if this floor has them; every phone gets its own. */
    private void spawnRoomChest() {
        synchronized (treasureChests) {
            treasureChests.clear();
            for (Room room : new Room[]{currentLayout.treasureRoom, currentLayout.vaultRoom}) {
                if (room == null) continue;
                TreasureChest chest = new TreasureChest(room.centerX(), room.centerY(),
                        room.centerX(), room.centerY(), 0L);
                chest.landed = true; // already sitting there, no landing burst
                chest.vault = room == currentLayout.vaultRoom;
                chest.mimic = !chest.vault && currentLayout.treasureMimic;
                pickChestReward(chest);
                treasureChests.add(chest);
            }
        }
        setUpEventRooms();
    }

    /** A slain mimic coughs up a real chest with loot. */
    private void dropLootChest(float x, float y) {
        TreasureChest chest = new TreasureChest(x, y, x, y, System.currentTimeMillis());
        pickChestReward(chest);
        synchronized (treasureChests) { treasureChests.add(chest); }
    }

    /** True when a living player on this floor (this phone's or a teammate) is within range. */
    private boolean anyoneNear(float x, float y, float range) {
        if (playerHealth > 0 && getDistance(player.x, player.y, x, y) < range) return true;
        if (coop == null) return false;
        int floor = floorManager.getCurrentFloor();
        for (CoopSession.RemotePlayer r : coop.others.values()) {
            if (!r.dead && r.floor == floor && getDistance(r.newestX, r.newestY, x, y) < range) return true;
        }
        return false;
    }

    /** Host: the treasure chest was a mimic, and it springs to life where it stood. */
    private void awakenMimic(TreasureChest chest) {
        Enemy mimic = new Enemy(chest.x, chest.y, Enemy.Type.BRUTE);
        mimic.applyFloorScaling(floorManager.getCurrentFloor());
        mimic.scaleHealth(1.5f * partyHealth());
        mimic.makeMimic();
        mimic.ascend(ascension());
        mimic.isAwake = true;
        mimic.setBehaviorTracker(behaviorTracker);
        mimic.setLayout(currentLayout);
        synchronized (enemies) { enemies.add(mimic); }
        mimicAwoke(chest.x, chest.y);
    }

    /** Removes the sleeping mimic chest and makes a show of it waking. */
    private void mimicAwoke(float x, float y) {
        synchronized (treasureChests) { treasureChests.removeIf(c -> c.mimic); }
        effects.burst(x, y, Color.rgb(120, 84, 48), 30, 12f);
        effects.banner(x, y - 90f, "MIMIC!", Color.rgb(224, 72, 72));
        if (camera != null) camera.startShake(250, 12f);
        soundManager.play(SoundManager.Sfx.MIMIC);
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
        return withoutPotions(pool);
    }

    /** Plays each chest's landing burst, then opens a chest when the player walks close enough. */
    private void checkChestInteraction() {
        long now = System.currentTimeMillis();
        int gold = Color.rgb(244, 215, 122);
        TreasureChest woke = null, cursed = null;
        synchronized (treasureChests) {
            for (TreasureChest chest : treasureChests) {
                if (!chest.landed) {
                    if (!chest.hasLanded(now)) continue;
                    chest.landed = true;
                    effects.burst(chest.x, chest.y, gold, 18, 9f);
                    effects.burst(chest.x, chest.y + 20, Color.rgb(120, 100, 80), 10, 5f); // dust
                    effects.slash(chest.x, chest.y + 10, 0f, 60f, 180f, gold);
                    if (camera != null) camera.startShake(150, 6f);
                    soundManager.play(SoundManager.Sfx.THUD);
                }
                if (chest.opened) continue;
                if (chest.mimic) {
                    // The host wakes it when anyone comes close; guests see it arrive as an enemy
                    if (!isGuest() && woke == null && anyoneNear(chest.x, chest.y, 170f)) woke = chest;
                    continue;
                }
                float dist = getDistance(player.x, player.y, chest.x, chest.y);
                if (dist < player.radius + TreasureChest.OPEN_RADIUS) {
                    chest.opened = true;
                    quest(Quests.CHESTS, 1);
                    if (chest.rewardItem == null) pickChestReward(chest);
                    soundManager.playChestOpen();
                    effects.burst(chest.x, chest.y - 10, chest.rewardItemColor, 28, 11f);
                    effects.burst(chest.x, chest.y - 10, gold, 14, 7f);
                    effects.slash(chest.x, chest.y, 0f, 80f, 180f, chest.rewardItemColor);
                    if (chest.vault) spawnGoldCoins(chest.x, chest.y + 60f, withGreed(8 + floorManager.getCurrentFloor()));
                    if (chest.cursed) cursed = chest;
                    // Relics: half the vault and cursed chests, a quarter of the boss's
                    if (((chest.vault || chest.cursed) && Math.random() < 0.5) || (chest.boss && Math.random() < 0.25))
                        grantRelic(chest.x, chest.y);
                    if (chest.rewardItem != null) {
                        final ShopItem reward = chest.rewardItem;
                        runOnUiThread(() -> purchaseItem(reward));
                    }
                }
            }
        }
        if (woke != null) awakenMimic(woke);
        if (cursed != null) {
            curseChest(cursed);
            eventDone(cursed.x, cursed.y);
        }
    }

    /** A key for vault gates; every phone gets its own. */
    private void gainKey(float x, float y) {
        keys++;
        soundManager.play(SoundManager.Sfx.KEY);
        effects.banner(x, y - 70f, "+1 KEY", Color.rgb(244, 215, 122));
        updateKeysHUD();
    }

    private void updateKeysHUD() {
        int count = keys;
        runOnUiThread(() -> {
            txtKeys.setText(String.valueOf(count));
            txtKeys.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        });
    }

    /** Opens the floor's vault gate when the player walks up to it holding a key. */
    private void checkVault(long now) {
        FloorLayout layout = currentLayout;
        if (layout == null || inShopRoom || !layout.vaultLocked || playerHealth <= 0) return;
        float[] g = layout.vaultGate;
        float gx = (g[0] + g[2]) / 2f, gy = (g[1] + g[3]) / 2f;
        if (getDistance(player.x, player.y, gx, gy) > player.radius + 120f) return;
        int gold = Color.rgb(244, 215, 122);
        if (keys <= 0) {
            if (now - lastLockedHint > 2500) {
                lastLockedHint = now;
                effects.banner(gx, gy - 60f, "NEEDS A KEY", Color.rgb(184, 172, 145));
                soundManager.play(SoundManager.Sfx.GATE_LOCKED);
            }
            return;
        }
        keys--;
        updateKeysHUD();
        layout.vaultLocked = false;
        effects.burst(gx, gy, gold, 30, 10f);
        effects.banner(gx, gy - 60f, "VAULT OPEN", gold);
        if (camera != null) camera.startShake(200, 8f);
        soundManager.play(SoundManager.Sfx.VAULT_UNLOCK);
    }

    // ─── DIALOGS ──────────────────────────────────────────────────────────────
    private void showMenuDialog() {
        soundManager.play(SoundManager.Sfx.CLICK);
        if (coop == null) isGamePaused = true;

        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_menu);

        if(dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        MaterialButton btnResume = dialog.findViewById(R.id.btn_resume);
        MaterialButton btnMainMenu = dialog.findViewById(R.id.btn_main_menu);
        MaterialButton btnSettings = dialog.findViewById(R.id.btn_settings);

        btnResume.setOnClickListener(v -> {
            soundManager.play(SoundManager.Sfx.CLICK);
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
            soundManager.play(SoundManager.Sfx.CLICK);
            currentInventoryFilter = filter;
            inventoryPage = 0;

            int[] ids = {R.id.filter_all, R.id.filter_weapons,
                    R.id.filter_armor, R.id.filter_consumables, R.id.filter_perks, R.id.filter_relics};
            String[] filters = {"ALL", "WEAPONS", "ARMOR", "CONSUMABLES", "PERKS", "RELICS"};

            for (int i = 0; i < ids.length; i++) {
                TextView t = dialog.findViewById(ids[i]);
                boolean sel = filters[i].equals(filter);
                t.setTextColor(ContextCompat.getColor(this, sel ? R.color.gold_bright : R.color.bone_muted));
                if (sel) {
                    android.graphics.drawable.GradientDrawable bg =
                            new android.graphics.drawable.GradientDrawable();
                    bg.setColor(ContextCompat.getColor(this, R.color.bg_surface_alt));
                    bg.setCornerRadius(0f); // square, like the pixel frames
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
        TextView rView = dialog.findViewById(R.id.txt_equipped_relic);
        if (rView != null) {
            Relic worn = relic;
            rView.setText(worn != null ? worn.title : "No relic");
            rView.setTextColor(worn != null ? Color.rgb(244, 215, 122) : ContextCompat.getColor(this, R.color.bone_muted));
        }
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
        boolean perkTab = f.equals("PERKS");
        boolean relicTab = f.equals("RELICS");
        if (perkTab) {
            for (Perk p : Perk.values()) if (stacks(p) > 0) items.add(p);
            for (Synergy s : Synergy.values()) if (has(s)) items.add(s);
        }
        if (relicTab) items.addAll(relics);
        // Stable sorts, so ties keep the weapons / armor / consumables order; perks and relics keep theirs
        if (!perkTab && !relicTab && inventorySort.equals("Name"))
            items.sort((a, b) -> itemName(a).compareToIgnoreCase(itemName(b)));
        else if (!perkTab && !relicTab && !inventorySort.equals("Type"))
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
            else if (item instanceof Perk) addPerkCell(row, (Perk) item);
            else if (item instanceof Synergy) addSynergyCell(row, (Synergy) item);
            else if (item instanceof Relic) addRelicCell(row, dialog, (Relic) item);
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
            empty.setText(perkTab ? "No blessings yet. Level up to earn one."
                    : relicTab ? "No relics yet. Vaults, boss chests and cursed chests hold them." : "Nothing here...");
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

    private void earnGold(int amount) {
        playerGold += amount;
        updateGoldHUD();
        soundManager.play(SoundManager.Sfx.SELL);
    }

    /** Co-op: a Gift button that hands the item (one, for a potion stack) to a teammate picked from a list. */
    private void setupGiftButton(Dialog detail, Object item, String name, Runnable removeItem) {
        if (coop == null || coop.others.isEmpty()) return;
        MaterialButton gift = detail.findViewById(R.id.btn_gift);
        gift.setVisibility(View.VISIBLE);
        gift.setOnClickListener(v -> {
            List<String> ids = new ArrayList<>(coop.others.keySet());
            String[] names = new String[ids.size()];
            for (int i = 0; i < names.length; i++) {
                CoopSession.RemotePlayer r = coop.others.get(ids.get(i));
                names[i] = r != null ? r.name : "Teammate";
            }
            showChoice("Gift " + name + " to...", names, -1, which -> {
                String json = giftJson(item);
                if (json == null) return;
                coop.sendGift(ids.get(which), json);
                removeItem.run();
            });
        });
    }

    /** The item as JSON for a teammate's phone; null if it can't be written. */
    private String giftJson(Object item) {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            if (item instanceof Weapon) {
                Weapon w = (Weapon) item;
                o.put("kind", "W").put("name", w.name).put("damage", w.damage).put("range", w.range)
                        .put("cooldown", w.cooldown).put("rarity", w.rarity.name()).put("color", w.color)
                        .put("knockback", w.knockback).put("projectile", w.isProjectile);
            } else if (item instanceof Armor) {
                Armor a = (Armor) item;
                o.put("kind", "A").put("name", a.name).put("defense", a.defense)
                        .put("maxDurability", a.maxDurability).put("durability", a.durability)
                        .put("rarity", a.rarity.name()).put("color", a.color).put("slot", a.slot.name())
                        .put("worn", a.worn);
            } else {
                ShopItem s = ((ConsumableStack) item).item;
                o.put("kind", "C").put("name", s.name).put("effect", s.effect).put("effectValue", s.effectValue)
                        .put("rarity", s.rarity).put("color", s.color).put("price", s.price)
                        .put("description", s.description);
            }
            return o.toString();
        } catch (org.json.JSONException e) {
            Log.e("Coop", "Gift not sent", e);
            return null;
        }
    }

    /** Asks before selling, since a sold item is gone for good. */
    private void confirmSell(String itemName, int amount, Runnable sell) {
        Dialog confirm = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        confirm.setContentView(R.layout.dialog_confirm);
        if (confirm.getWindow() != null)
            confirm.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ((android.widget.ImageView) confirm.findViewById(R.id.confirm_icon)).setImageResource(R.drawable.ic_coin);
        ((TextView) confirm.findViewById(R.id.confirm_title)).setText("Sell " + itemName + "?");
        ((TextView) confirm.findViewById(R.id.confirm_message)).setText(
                "You'll get " + amount + " gold, but the item is gone for good.");
        confirm.findViewById(R.id.btn_confirm_no).setOnClickListener(v -> confirm.dismiss());
        confirm.findViewById(R.id.btn_confirm_yes).setOnClickListener(v -> {
            confirm.dismiss();
            sell.run();
        });
        confirm.show();
        hideSystemUIOnWindow(confirm.getWindow());
    }

    /** Pick-one card: options in rows of four, the current one (if any) lit gold. */
    private void showChoice(String title, String[] options, int selected, java.util.function.IntConsumer onPick) {
        soundManager.play(SoundManager.Sfx.CLICK);
        Dialog picker = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        picker.setContentView(R.layout.dialog_choice);
        if (picker.getWindow() != null)
            picker.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ((TextView) picker.findViewById(R.id.choice_title)).setText(title);
        picker.findViewById(R.id.btn_close_choice).setOnClickListener(v -> picker.dismiss());

        LinearLayout list = picker.findViewById(R.id.choice_options);
        int gap = getResources().getDimensionPixelSize(R.dimen.space_sm);
        LinearLayout row = null;
        for (int i = 0; i < options.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) rowLp.topMargin = gap;
                list.addView(row, rowLp);
            }
            TextView option = new TextView(this, null, 0, R.style.OD_Text_Tab);
            option.setText(options[i]);
            option.setMaxLines(1);
            option.setEllipsize(android.text.TextUtils.TruncateAt.END);
            option.setBackgroundResource(i == selected ? R.drawable.btn_login : R.drawable.btn_gothic_secondary);
            option.setTextColor(ContextCompat.getColor(this, i == selected ? R.color.gold_glow : R.color.gold_bright));
            int which = i;
            option.setOnClickListener(v -> {
                picker.dismiss();
                onPick.accept(which);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    getResources().getDimensionPixelSize(R.dimen.touch_min), 1f);
            if (i % 4 > 0) lp.leftMargin = gap;
            row.addView(option, lp);
        }
        // Pad the last row so its options keep the same width
        for (int i = options.length; i % 4 != 0; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
            lp.leftMargin = gap;
            row.addView(new View(this), lp);
        }
        picker.show();
        hideSystemUIOnWindow(picker.getWindow());
    }

    private void showSortPicker(Dialog dialog, MaterialButton sortBtn) {
        showChoice("Sort by", SORT_KEYS, java.util.Arrays.asList(SORT_KEYS).indexOf(inventorySort), which -> {
            inventorySort = SORT_KEYS[which];
            inventoryPage = 0;
            sortBtn.setText("Sort: " + inventorySort);
            populateInventoryList(dialog);
        });
    }

    private void showInventoryDialog() {
        soundManager.play(SoundManager.Sfx.CLICK);
        if (coop == null) isGamePaused = true;
        currentInventoryFilter = "ALL";

        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_inventory);
        dialog.setCancelable(false);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        dialog.findViewById(R.id.btn_close_inventory).setOnClickListener(v -> {
            soundManager.play(SoundManager.Sfx.CLICK);
            dialog.dismiss();
            isGamePaused = false;
            hideSystemUI();
        });

        MaterialButton sortBtn = dialog.findViewById(R.id.btn_sort_inventory);
        sortBtn.setText("Sort: " + inventorySort);
        sortBtn.setOnClickListener(v -> showSortPicker(dialog, sortBtn));

        inventoryPage = 0;
        dialog.findViewById(R.id.btn_page_prev).setOnClickListener(v -> {
            soundManager.play(SoundManager.Sfx.CLICK);
            inventoryPage--;
            populateInventoryList(dialog);
        });
        dialog.findViewById(R.id.btn_page_next).setOnClickListener(v -> {
            soundManager.play(SoundManager.Sfx.CLICK);
            inventoryPage++;
            populateInventoryList(dialog);
        });

        setupFilterTab(dialog, R.id.filter_all,          "ALL");
        setupFilterTab(dialog, R.id.filter_weapons,      "WEAPONS");
        setupFilterTab(dialog, R.id.filter_armor,        "ARMOR");
        setupFilterTab(dialog, R.id.filter_consumables,  "CONSUMABLES");
        setupFilterTab(dialog, R.id.filter_perks,        "PERKS");
        setupFilterTab(dialog, R.id.filter_relics,       "RELICS");

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

        applyRarityBorder(cell, rarityColor, weapon.name);
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

        applyRarityBorder(cell, rarityColor, armor.name);
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
        subView.setText("+" + BatchGenerator.potionPercent(stack.item) + (heal ? "% HP" : "% AR"));
        quantityView.setVisibility(View.VISIBLE);
        quantityView.setText("x" + stack.quantity);

        applyRarityBorder(cell, rarityColor, stack.item.name);
        cell.setOnClickListener(v -> showConsumableActionDialog(dialog, stack));
        addCell(row, cell);
    }

    /** One owned perk: its stacks and their combined effect, in the perk's colour. */
    private void addPerkCell(LinearLayout row, Perk perk) {
        View cell = getLayoutInflater().inflate(R.layout.dialog_inventory_item, row, false);
        android.widget.ImageView icon = cell.findViewById(R.id.inv_item_icon);
        icon.setImageResource(perk.icon);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(perk.color));
        TextView nameView = cell.findViewById(R.id.inv_item_name);
        nameView.setText(perk.title);
        nameView.setTextColor(perk.color);
        ((TextView) cell.findViewById(R.id.inv_item_subtitle)).setText(perk.describe(stacks(perk)));
        TextView quantityView = cell.findViewById(R.id.inv_item_quantity);
        quantityView.setVisibility(View.VISIBLE);
        quantityView.setText("x" + stacks(perk));
        applyRarityBorder(cell, perk.color, perk.title);
        addCell(row, cell);
    }

    /** An awakened synergy, in gold, with what it does. */
    private void addSynergyCell(LinearLayout row, Synergy synergy) {
        int gold = Color.rgb(244, 215, 122);
        View cell = getLayoutInflater().inflate(R.layout.dialog_inventory_item, row, false);
        android.widget.ImageView icon = cell.findViewById(R.id.inv_item_icon);
        icon.setImageResource(R.drawable.ic_trophy);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(gold));
        TextView nameView = cell.findViewById(R.id.inv_item_name);
        nameView.setText(synergy.title);
        nameView.setTextColor(gold);
        ((TextView) cell.findViewById(R.id.inv_item_subtitle)).setText(synergy.effect);
        applyRarityBorder(cell, gold, synergy.title);
        addCell(row, cell);
    }

    /** One relic, in gold, with what it does; the worn one is marked. */
    private void addRelicCell(LinearLayout row, Dialog dialog, Relic r) {
        int gold = Color.rgb(244, 215, 122);
        View cell = getLayoutInflater().inflate(R.layout.dialog_inventory_item, row, false);
        android.widget.ImageView icon = cell.findViewById(R.id.inv_item_icon);
        icon.setImageResource(r.icon);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(gold));
        TextView nameView = cell.findViewById(R.id.inv_item_name);
        nameView.setText(r.title);
        nameView.setTextColor(gold);
        ((TextView) cell.findViewById(R.id.inv_item_subtitle)).setText(r.effect);
        cell.findViewById(R.id.inv_equipped_badge).setVisibility(relic == r ? View.VISIBLE : View.GONE);
        applyRarityBorder(cell, gold, r.title);
        cell.setOnClickListener(v -> showRelicActionDialog(dialog, r));
        addCell(row, cell);
    }

    /** Wear a relic (only one at a time) or take it off. */
    private void showRelicActionDialog(Dialog parentDialog, Relic r) {
        Dialog detail = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        detail.setContentView(R.layout.dialog_item_action);
        detail.setCancelable(false);
        if (detail.getWindow() != null)
            detail.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        bindActionDialog(detail, r.title, "RELIC", "LEGENDARY", r.effect, Color.rgb(244, 215, 122));
        LinearLayout stats = detail.findViewById(R.id.action_stats);
        addStatRow(stats, r.icon, "Effect", r.effect);
        addStatRow(stats, R.drawable.ic_key, "Found in", "Vaults, boss and cursed chests");
        addStatRow(stats, R.drawable.ic_level, "Worn", "One relic at a time");

        MaterialButton btn = detail.findViewById(R.id.btn_action);
        boolean worn = relic == r;
        btn.setText(worn ? "Take off" : "Wear");
        btn.setOnClickListener(v -> {
            relic = worn ? null : r;
            soundManager.play(SoundManager.Sfx.EQUIP);
            updateEquippedDisplay(parentDialog);
            populateInventoryList(parentDialog);
            detail.dismiss();
        });
        detail.findViewById(R.id.btn_remove).setVisibility(View.GONE);
        detail.findViewById(R.id.btn_close_action).setOnClickListener(v -> detail.dismiss());
        detail.show();
        hideSystemUIOnWindow(detail.getWindow());
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

        // The name in its rarity's colour, as in the shop, so rarity reads before anything else
        bindActionDialog(detail, weapon.name, "WEAPON", weapon.rarity.name(),
                "", getRarityColor(weapon.rarity.name()));
        showWeaponStats(detail.findViewById(R.id.action_stats), weapon);

        MaterialButton btn = detail.findViewById(R.id.btn_action);
        boolean isEquipped = player.getWeapon() == weapon;
        btn.setText(isEquipped ? "Equipped" : "Equip");
        btn.setEnabled(!isEquipped);
        btn.setAlpha(isEquipped ? 0.4f : 1f);

        btn.setOnClickListener(v -> {
            player.equipWeapon(weapon);
            soundManager.play(SoundManager.Sfx.EQUIP);
            refreshAttackUI();
            updateEquippedDisplay(parentDialog);
            populateInventoryList(parentDialog);
            detail.dismiss();
        });

        MaterialButton btnSellWeapon = detail.findViewById(R.id.btn_remove);
        boolean isFists = weapon == weapons.get(0);
        int weaponValue = sellValue(weapon);
        btnSellWeapon.setText("Sell  +" + weaponValue);
        btnSellWeapon.setVisibility(isFists ? View.GONE : View.VISIBLE);
        Runnable removeWeapon = () -> {
            if (player.getWeapon() == weapon) {
                player.equipWeapon(weapons.get(0)); // Re-equip Fists
                refreshAttackUI();
            }
            weapons.remove(weapon);
            inventoryWeaponIds.remove(weaponFirestoreIds.remove(weapon));
            updateEquippedDisplay(parentDialog);
            populateInventoryList(parentDialog);
            detail.dismiss();
        };
        btnSellWeapon.setOnClickListener(v -> confirmSell(weapon.name, weaponValue, () -> {
            removeWeapon.run();
            earnGold(weaponValue);
        }));
        if (!isFists) setupGiftButton(detail, weapon, weapon.name, removeWeapon);

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
        Runnable removeArmor = () -> {
            // Unequip if currently equipped
            if (equippedArmor.get(armor.slot) == armor) {
                equippedArmor.remove(armor.slot);
                recalculateMaxArmor();
            }
            inventoryArmors.remove(armor);
            inventoryArmorIds.remove(armorFirestoreIds.remove(armor));
            updateEquippedDisplay(parentDialog);
            populateInventoryList(parentDialog);
            detail.dismiss();
        };
        btnSellArmor.setOnClickListener(v -> confirmSell(armor.name, armorValue, () -> {
            removeArmor.run();
            earnGold(armorValue);
        }));
        setupGiftButton(detail, armor, armor.name, removeArmor);

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
        Runnable removeOne = () -> {
            if (stack.decrement()) consumableInventory.remove(stack);
            updatePotionButton();
            populateInventoryList(parentDialog);
            detail.dismiss();
        };
        btnSellConsumable.setOnClickListener(v -> confirmSell(stack.item.name, potionValue, () -> {
            removeOne.run();
            earnGold(potionValue);
        }));
        setupGiftButton(detail, stack, stack.item.name, removeOne);

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
        tintRarityBadge(dialog.findViewById(R.id.action_rarity), rarityColor);

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
        soundManager.play(SoundManager.Sfx.EQUIP);
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
        long now = System.currentTimeMillis();
        if (now - lastPotionAt < potionCooldown()) {
            effects.number(player.x, player.y - player.radius, "Not ready (" + ((potionCooldown() - (now - lastPotionAt)) / 1000 + 1) + "s)",
                    Color.rgb(200, 190, 170));
            soundManager.play(SoundManager.Sfx.GATE_LOCKED);
            return;
        }
        lastPotionAt = now;
        ShopItem item = stack.item;
        int percent = BatchGenerator.potionPercent(item);
        soundManager.play(SoundManager.Sfx.POTION);
        if ("heal".equals(item.effect)) {
            int amount = Math.max(1, Math.round(maxHealth * percent / 100f));
            healPerStep = Math.max(1, Math.round(amount / 16f));
            healLeft = amount;
            effects.number(player.x, player.y - player.radius, "+" + amount, Color.rgb(90, 230, 110));
            effects.play("fx.heal", player.x, player.y + player.radius * 0.78f, false);
            if (relic == Relic.VIAL && maxArmor > 0) {
                int newArmor = Math.min(maxArmor, playerArmor + Math.max(1, Math.round(maxArmor * percent / 100f)));
                runOnUiThread(() -> {
                    animateArmorBar(playerArmor, newArmor);
                    playerArmor = newArmor;
                });
            }
        } else if ("armor_restore".equals(item.effect)) {
            int newArmor = Math.min(maxArmor, playerArmor + Math.max(1, Math.round(maxArmor * percent / 100f)));
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
        Weapon held = player.getWeapon() == weapon ? null : player.getWeapon();
        addWeaponGrid(box, weapon.name, weapon.damage, weapon.range, weapon.cooldown, weapon.knockback,
                weapon.kind().title(), held);
        addTraitBox(box, weapon.trait(), weapon.rarity == Weapon.Rarity.LEGENDARY);
    }

    // Better and worse than what you hold: the same green and red as every other comparison in the game
    private static final int BETTER = 0xFF7AB85C, WORSE = 0xFFE04848;

    /**
     * A weapon's stats against the one you hold, made to be read in a second: six cells in a two-column grid,
     * each edged and marked green where it beats yours and red where it loses, with the difference ("+6").
     * held is null for the weapon you are holding, which shows plain values.
     */
    private void addWeaponGrid(LinearLayout box, String name, int damage, float range, long cooldown,
                               float knockback, String style, Weapon held) {
        float perSecond = dps(damage, cooldown);
        int[] icons = {R.drawable.ic_crossed_swords, R.drawable.ic_dagger, R.drawable.ic_range,
                R.drawable.ic_hourglass, R.drawable.ic_knockback, R.drawable.ic_target};
        String[] labels = {"Damage", "Per second", "Range", "Cooldown", "Knockback", "Style"};
        CharSequence[] values = new CharSequence[6];
        int[] verdicts = new int[6];
        float[][] pairs = held == null ? null : new float[][]{
                {damage, held.damage}, {perSecond, dps(held.damage, held.cooldown)}, {range, held.range},
                {cooldown, held.cooldown}, {knockback, held.knockback}};
        // the "ms" set small: the pixel font draws it wide, and the cell is only half a pane wide
        android.text.SpannableString ms = new android.text.SpannableString(cooldown + "ms");
        ms.setSpan(new android.text.style.RelativeSizeSpan(0.75f), ms.length() - 2, ms.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        CharSequence[] mine = {String.valueOf(damage), String.format(java.util.Locale.US, "%.1f", perSecond),
                String.valueOf(Math.round(range)), ms, String.format(java.util.Locale.US, "%.1f", knockback)};
        boolean[] decimals = {false, true, false, false, true};
        for (int i = 0; i < 5; i++) {
            if (pairs == null) { values[i] = mine[i]; continue; }
            // a shorter cooldown is the better one; its difference drops the "ms" the value already shows
            float diff = pairs[i][0] - pairs[i][1];
            boolean tied = Math.abs(diff) < (decimals[i] ? 0.05f : 0.5f);
            verdicts[i] = tied ? 0 : (diff > 0) == (i != 3) ? 1 : -1;
            values[i] = tied ? mine[i] : delta(mine[i], diff, decimals[i], verdicts[i] > 0);
        }
        values[5] = style;

        float dp = getResources().getDisplayMetrics().density;
        int gap = Math.round(3 * dp);
        LinearLayout row = null;
        for (int i = 0; i < icons.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                box.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.round(44 * dp)));
            }
            View cell = getLayoutInflater().inflate(R.layout.item_stat_cell, row, false);
            ((android.widget.ImageView) cell.findViewById(R.id.stat_icon)).setImageResource(icons[i]);
            ((TextView) cell.findViewById(R.id.stat_label)).setText(labels[i]);
            TextView value = cell.findViewById(R.id.stat_value);
            value.setText(values[i]);
            value.setTextColor(ContextCompat.getColor(this, R.color.bone_white));
            value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
            if (verdicts[i] != 0) cell.setBackground(verdictCell(verdicts[i] > 0 ? BETTER : WORSE, dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(gap, gap, gap, gap);
            row.addView(cell, lp);
        }
    }

    /**
     * "24" with " +6" after it: the difference bold, green when it is better and red when it is worse, a
     * touch smaller than the value so the pair fits its cell.
     */
    private static CharSequence delta(CharSequence value, float diff, boolean oneDecimal, boolean better) {
        String d = (diff > 0 ? " +" : " -") + (oneDecimal ? String.format(java.util.Locale.US, "%.1f", Math.abs(diff))
                : String.valueOf(Math.round(Math.abs(diff))));
        android.text.SpannableStringBuilder s = new android.text.SpannableStringBuilder(value).append(d);
        int from = value.length(), to = s.length(), flags = android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        s.setSpan(new android.text.style.ForegroundColorSpan(better ? BETTER : WORSE), from, to, flags);
        s.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), from, to, flags);
        s.setSpan(new android.text.style.RelativeSizeSpan(0.85f), from, to, flags);
        return s;
    }

    /** A stat cell's stone, edged in the colour of its verdict. */
    private static android.graphics.drawable.Drawable verdictCell(int color, float dp) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{blend(Color.rgb(38, 34, 30), color, 0.14f), Color.rgb(20, 18, 16)});
        g.setCornerRadius(0f); // square, like the pixel frames
        g.setStroke(Math.round(1.5f * dp), (color & 0x00FFFFFF) | 0xC0000000);
        return g;
    }

    private static int blend(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    /** A rarity badge in its rarity's colour: the word, its edge and a faint wash behind it. */
    private void tintRarityBadge(TextView badge, int color) {
        float dp = getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(blend(Color.rgb(24, 21, 18), color, 0.22f));
        g.setCornerRadius(0f); // square, like the pixel frames
        g.setStroke(Math.round(1.5f * dp), color);
        badge.setBackground(g);
        badge.setTextColor(color);
    }

    /** The weapon's trait, if it has one, in its own box under the stats: always on screen, never scrolled to. */
    private void addTraitBox(LinearLayout box, Weapon.Trait trait, boolean legendary) {
        if (trait == null) return;
        View view = getLayoutInflater().inflate(R.layout.item_trait_box, box, false);
        ((TextView) view.findViewById(R.id.trait_title)).setText(trait.title);
        ((TextView) view.findViewById(R.id.trait_text)).setText(trait.describe(legendary));
        box.addView(view);
    }

    private void showArmorStats(LinearLayout box, Armor armor) {
        Armor worn = equippedArmor.get(armor.slot);
        if (worn == armor) worn = null;
        addStatRow(box, R.drawable.ic_shield, "Defense", worn == null ? "+" + armor.defense
                : compared(armor.defense, worn.defense, true, false));
        addStatRow(box, R.drawable.ic_anvil, "Durability", worn == null ? String.valueOf((int) armor.maxDurability)
                : compared(armor.maxDurability, worn.maxDurability, true, false));
        addStatRow(box, slotIcon(armor.slot.name()), "Slot", armor.slot.name());
    }

    private static float dps(int damage, long cooldown) {
        return damage * 1000f / Math.max(1, cooldown);
    }

    /** A stat next to what's equipped: "18  +4", the difference signed and coloured green when better, red when worse. */
    private CharSequence compared(float value, float equipped, boolean higherIsBetter, boolean oneDecimal) {
        String text = oneDecimal ? String.format(java.util.Locale.US, "%.1f", value) : String.valueOf(Math.round(value));
        float diff = value - equipped;
        if (Math.abs(diff) < (oneDecimal ? 0.05f : 0.5f)) return text;
        String delta = (diff > 0 ? "  +" : "  -") + (oneDecimal
                ? String.format(java.util.Locale.US, "%.1f", Math.abs(diff)) : String.valueOf(Math.round(Math.abs(diff))));
        android.text.SpannableString s = new android.text.SpannableString(text + delta);
        int color = (diff > 0) == higherIsBetter ? 0xFF7AB85C : 0xFFE04848;
        s.setSpan(new android.text.style.ForegroundColorSpan(color), text.length(), s.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private void showConsumableStats(LinearLayout box, ShopItem item) {
        boolean heal = "heal".equals(item.effect);
        int percent = BatchGenerator.potionPercent(item);
        addStatRow(box, heal ? R.drawable.ic_heart : R.drawable.ic_shield, "Restores",
                heal ? percent + "% HP over 2s" : percent + "% armor");
        addStatRow(box, R.drawable.ic_hourglass, "Cooldown", (POTION_COOLDOWN / 1000) + "s, all potions");
    }

    /** A stone cell edged in its rarity colour, worn like the rest of the UI. */
    private void applyRarityBorder(View view, int color, String name) {
        float dp = getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(42, 38, 33), Color.rgb(20, 18, 16)});
        border.setCornerRadius(0f); // square, like the pixel frames
        border.setStroke(Math.round(2 * dp), color);
        view.setBackground(border);
        Wear.wrap(view, false, false, name);
    }

    /**
     * Shows the Game Over card with the run summary: respawn from checkpoint, or return to menu.
     * In co-op (a reason given) the whole run is over, so it only offers the menu.
     */
    private void showGameOverDialog(String coopReason) {
        // The dungeon falls silent and a lament begins
        SoundManager.setGameTrack(this, R.raw.game_over_music);
        gameDisplay.setHeartbeat(0);
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_game_over);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView txtCheckpoint = dialog.findViewById(R.id.txt_checkpoint);
        txtCheckpoint.setText("Last checkpoint: Floor " + checkpointFloor);
        fillRunSummary(dialog.findViewById(R.id.summary_grid));

        // Souls: the part of a lost life that carries over to the Soul Altar; paid when the card closes
        int[] souls = {Math.round(Altar.souls(statsManager.getRunFloors(), statsManager.getRunKills(),
                statsManager.getRunBosses()) * (1f + 0.25f * ascension()))};
        TextView txtSouls = dialog.findViewById(R.id.txt_souls);
        txtSouls.setText("+" + souls[0] + " Souls");
        recordRun();
        Runnable payout = () -> { if (userId != null) dbManager.addSouls(userId, souls[0]); };
        if (dailySeed != 0 && userId != null && dailyDay != null)
            dbManager.submitDailyScore(dailyDay, userId, username, floorManager.getCurrentFloor(),
                    statsManager.getRunKills(), statsManager.getRunSeconds());

        // Opt-in rewarded ads: double the Souls, or (solo saves only) get back up right here once a life
        MaterialButton adSouls = dialog.findViewById(R.id.btn_ad_souls);
        MaterialButton adRevive = dialog.findViewById(R.id.btn_ad_revive);
        if (Ads.isReady() && souls[0] > 0) adSouls.setVisibility(View.VISIBLE);
        if (Ads.isReady() && coopReason == null && !adReviveUsed) adRevive.setVisibility(View.VISIBLE);
        adSouls.setOnClickListener(v -> Ads.show(this, () -> {
            souls[0] *= 2;
            txtSouls.setText("+" + souls[0] + " Souls");
        }, () -> {
            adSouls.setVisibility(View.GONE);
            adRevive.setVisibility(View.GONE);
            hideSystemUIOnWindow(dialog.getWindow());
        }));
        adRevive.setOnClickListener(v -> Ads.show(this, () -> {
            adReviveUsed = true;
            dialog.dismiss();
            hideSystemUI();
            reviveCountdown(3);
        }, () -> {
            adSouls.setVisibility(View.GONE);
            adRevive.setVisibility(View.GONE);
            hideSystemUIOnWindow(dialog.getWindow());
        }));

        MaterialButton btnRespawn = dialog.findViewById(R.id.btn_respawn);
        btnRespawn.setOnClickListener(v -> {
            payout.run();
            respawnPlayer();
            dialog.dismiss();
            hideSystemUI();
        });

        MaterialButton btnMenu = dialog.findViewById(R.id.btn_game_over_menu);
        if (coopReason != null) {
            // Co-op or daily: the run is over, there is no respawn
            ((TextView) dialog.findViewById(R.id.txt_over_title)).setText("RUN OVER");
            ((TextView) dialog.findViewById(R.id.txt_over_message)).setText(coopReason);
            txtCheckpoint.setVisibility(View.GONE);
            btnRespawn.setVisibility(View.GONE);
            btnMenu.setOnClickListener(v -> {
                payout.run();
                dialog.dismiss();
                leaveCoop();
                SoundManager.stopGameMusic();
                startActivity(new Intent(this, MenuActivity.class));
                finish();
            });
            dialog.show();
            hideSystemUIOnWindow(dialog.getWindow());
            return;
        }
        btnMenu.setOnClickListener(v -> {
            payout.run();
            dialog.dismiss();
            SoundManager.stopGameMusic();
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

    /** Keeps the last 10 lives on this phone for the menu's run history (MenuActivity.showHistory). */
    private void recordRun() {
        SharedPreferences prefs = getSharedPreferences("runs_" + userId, MODE_PRIVATE);
        try {
            org.json.JSONArray old = new org.json.JSONArray(prefs.getString("list", "[]"));
            org.json.JSONArray list = new org.json.JSONArray().put(new org.json.JSONObject()
                    .put("t", System.currentTimeMillis())
                    .put("mode", coop != null ? "Co-op" : dailySeed != 0 ? "Daily" : "Solo")
                    .put("floor", floorManager.getCurrentFloor())
                    .put("kills", statsManager.getRunKills())
                    .put("secs", statsManager.getRunSeconds())
                    .put("level", playerLevel)
                    .put("weapon", player.getWeapon().name));
            for (int i = 0; i < old.length() && list.length() < 10; i++) list.put(old.get(i));
            prefs.edit().putString("list", list.toString()).apply();
        } catch (org.json.JSONException e) {
            prefs.edit().remove("list").apply();
        }
    }

    /** Counts 3, 2, 1 over the fallen player (the game stays paused), then revives. */
    private void reviveCountdown(int left) {
        if (left == 0) {
            reviveHere();
            return;
        }
        effects.countdown(player.x, player.y - player.radius - 40f, String.valueOf(left), Color.rgb(244, 215, 122));
        soundManager.play(SoundManager.Sfx.CLICK);
        gameDisplay.postDelayed(() -> reviveCountdown(left - 1), 1000);
    }

    /** Ad revive: back up where you fell at half health, briefly untouchable. */
    private void reviveHere() {
        player.resetDeathAnimation();
        playerHealth = maxHealth / 2;
        playerHealthBar.setProgress(playerHealth);
        if (txtHealthValue != null) txtHealthValue.setText(playerHealth + " / " + maxHealth);
        invulnerableUntil = System.currentTimeMillis() + 2000;
        effects.burst(player.x, player.y, Color.rgb(244, 215, 122), 40, 12f);
        soundManager.play(SoundManager.Sfx.REVIVE);
        effects.banner(player.x, player.y - player.radius - 50f, "REVIVED", Color.rgb(244, 215, 122));
        isGamePaused = false;
        hideSystemUI();
    }

    /** Run summary: eight stat cards, two per row, for this life. */
    private void fillRunSummary(LinearLayout grid) {
        long secs = statsManager.getRunSeconds();
        int perkCount = 0;
        for (int s : perks) perkCount += s;
        int[] icons = {R.drawable.ic_stairs, R.drawable.ic_level, R.drawable.ic_skull, R.drawable.ic_trophy,
                R.drawable.ic_crossed_swords, R.drawable.ic_broken_heart, R.drawable.ic_coin, R.drawable.ic_hourglass};
        String[] labels = {"Floor reached", "Level", "Kills", "Bosses slain",
                "Damage dealt", "Damage taken", "Gold collected", "Time"};
        String[] values = {
                String.valueOf(floorManager.getCurrentFloor()),
                playerLevel + (perkCount > 0 ? "  \u00b7  " + perkCount + " perks" : ""),
                String.format(java.util.Locale.US, "%,d", statsManager.getRunKills()),
                String.valueOf(statsManager.getRunBosses()),
                String.format(java.util.Locale.US, "%,d", statsManager.getRunDamageGiven()),
                String.format(java.util.Locale.US, "%,d", statsManager.getRunDamageTaken()),
                String.format(java.util.Locale.US, "%,d", statsManager.getRunCoins()),
                String.format(java.util.Locale.US, "%d:%02d", secs / 60, secs % 60)};
        float dp = getResources().getDisplayMetrics().density;
        int gap = Math.round(3 * dp);
        LinearLayout row = null;
        for (int i = 0; i < icons.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                grid.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Math.round(46 * dp)));
            }
            View cell = getLayoutInflater().inflate(R.layout.item_stat_cell, row, false);
            ((android.widget.ImageView) cell.findViewById(R.id.stat_icon)).setImageResource(icons[i]);
            ((TextView) cell.findViewById(R.id.stat_label)).setText(labels[i]);
            ((TextView) cell.findViewById(R.id.stat_value)).setText(values[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(gap, gap, gap, gap);
            row.addView(cell, lp);
        }
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
        return 30 + (playerLevel - 1) * 20;
    }

    /** Level-up blessings: small on purpose, they add up over a long run. The stat word shows in its colour. */
    enum Perk {
        MIGHT("Might", 1f, "%", "damage", 0xFFE04848, R.drawable.ic_crossed_swords),
        VIGOR("Vigor", 5f, "", "max HP", 0xFF7AB85C, R.drawable.ic_heart),
        SWIFTNESS("Swiftness", 1f, "%", "move speed", 0xFF4F8FFF, R.drawable.ic_boots),
        KEEN_EYE("Keen Eye", 0.5f, "%", "crit chance", 0xFFFF9A3C, R.drawable.ic_target),
        QUICK_HANDS("Quick Hands", 1f, "%", "attack speed", 0xFFE86AD0, R.drawable.ic_hourglass),
        WHIRLWIND("Whirlwind", 1f, "%", "Spin recharge", 0xFF4FD8E0, R.drawable.ic_knockback),
        SHADOW_STEP("Shadow Step", 1f, "%", "Dash recharge", 0xFFA070E8, R.drawable.ic_range),
        IRON_SKIN("Iron Skin", 0.5f, "%", "damage resist", 0xFFB4BEC8, R.drawable.ic_shield),
        GREED("Greed", 1f, "%", "gold", 0xFFF4D77A, R.drawable.ic_coin);

        final String title, unit, stat;
        final float step;
        final int color, icon;

        Perk(String title, float step, String unit, String stat, int color, int icon) {
            this.title = title;
            this.step = step;
            this.unit = unit;
            this.stat = stat;
            this.color = color;
            this.icon = icon;
        }

        /** "+3% damage" for that many stacks, with the stat in its colour. */
        CharSequence describe(int stacks) {
            float total = step * stacks;
            String amount = "+" + (total == (int) total ? String.valueOf((int) total)
                    : String.format(java.util.Locale.US, "%.1f", total)) + unit + " ";
            android.text.SpannableString text = new android.text.SpannableString(amount + stat);
            text.setSpan(new android.text.style.ForegroundColorSpan(color), amount.length(), text.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return text;
        }
    }

    private int stacks(Perk p) {
        return perks[p.ordinal()];
    }

    /** Two perks at three stacks each awaken a synergy with an effect of its own. */
    private enum Synergy {
        BLADESTORM("Bladestorm", Perk.WHIRLWIND, Perk.MIGHT, "Spin hits harder and reaches a little further"),
        PHANTOM("Phantom", Perk.SHADOW_STEP, Perk.KEEN_EYE, "Dashing through enemies grazes them"),
        BULWARK("Bulwark", Perk.IRON_SKIN, Perk.VIGOR, "Out of combat, regenerate 1 HP every 5 seconds up to half health"),
        MIDAS("Midas Touch", Perk.GREED, Perk.QUICK_HANDS, "Critical hits sometimes shake loose a coin");

        final String title, effect;
        final Perk a, b;

        Synergy(String title, Perk a, Perk b, String effect) {
            this.title = title;
            this.a = a;
            this.b = b;
            this.effect = effect;
        }

        static Synergy of(Perk p) {
            for (Synergy s : values()) if (s.a == p || s.b == p) return s;
            return null;
        }
    }

    private boolean has(Synergy s) {
        return stacks(s.a) >= 3 && stacks(s.b) >= 3;
    }

    /* ============================================================
       RELICS, WEAPON TRAITS, WEEKLY CHALLENGE, WARDROBE, ROOM EVENTS, TUTORIAL
       ============================================================ */

    /** Relics: passive items from vaults, boss chests and cursed chests; one is worn at a time (inventory). */
    enum Relic {
        VIAL("Alchemist's Vial", "Healing potions also restore the same share of armor", R.drawable.ic_heart),
        FANG("Bloodfang", "Every kill heals 2 HP", R.drawable.ic_dagger),
        IDOL("Gilded Idol", "+20% gold", R.drawable.ic_coin),
        SANDGLASS("Sandglass", "Potions recharge 25% faster", R.drawable.ic_hourglass),
        FEATHER("Wind Feather", "Dash recharges 20% faster", R.drawable.ic_boots),
        SKULL("Grim Skull", "+12% damage, but you take 10% more", R.drawable.ic_skull),
        AEGIS("Warden's Aegis", "Blocks one hit every 12 seconds", R.drawable.ic_shield),
        EYE("Hunter's Eye", "+6% critical hit chance", R.drawable.ic_target);

        final String title, effect;
        final int icon;

        Relic(String title, String effect, int icon) {
            this.title = title;
            this.effect = effect;
            this.icon = icon;
        }
    }

    private final java.util.EnumSet<Relic> relics = java.util.EnumSet.noneOf(Relic.class); // changed on the UI thread
    private volatile Relic relic; // the one worn, or null
    private long aegisReadyAt = 0;

    /** Grants a relic not owned yet, if any are left; the first one found is worn at once. */
    private void grantRelic(float x, float y) {
        List<Relic> left = new ArrayList<>();
        for (Relic r : Relic.values()) if (!relics.contains(r)) left.add(r);
        if (left.isEmpty()) return;
        Relic found = left.get((int) (Math.random() * left.size()));
        runOnUiThread(() -> {
            relics.add(found);
            if (relic == null) relic = found;
        });
        int gold = Color.rgb(244, 215, 122);
        effects.banner(x, y - 120f, "RELIC: " + found.title.toUpperCase(), gold);
        effects.burst(x, y, gold, 30, 10f);
        soundManager.play(SoundManager.Sfx.PERK_PICK);
    }

    private long potionCooldown() {
        return relic == Relic.SANDGLASS ? POTION_COOLDOWN * 3 / 4 : POTION_COOLDOWN;
    }

    /** Heals the player outside the potion flow (lifesteal, Bloodfang); on the UI thread like other HP changes. */
    private void healPlayer(int amount) {
        runOnUiThread(() -> {
            if (playerHealth <= 0 || playerHealth >= maxHealth) return;
            playerHealth = Math.min(maxHealth, playerHealth + amount);
            playerHealthBar.setProgress(playerHealth);
            if (txtHealthValue != null) txtHealthValue.setText(playerHealth + " / " + maxHealth);
        });
    }

    /** Burning trait: damage still to come on one enemy, dealt every half second. Logic thread only. */
    private static final class Burn {
        final Enemy enemy;
        final int perTick;
        int left;
        long next;

        Burn(Enemy enemy, int total, long now) {
            this.enemy = enemy;
            this.left = total;
            this.perTick = Math.max(1, total / 4);
            this.next = now + 500;
        }
    }

    private final List<Burn> burns = new ArrayList<>();
    private final List<long[]> pendingBlasts = new ArrayList<>(); // explosive elites: {x, y, damage, when}
    private boolean chaining; // a chained bolt doesn't chain again

    /** The worn weapon's trait (Epic and Legendary only) acting on a hit that landed. */
    private void weaponTraits(Enemy e, int damage) {
        Weapon w = player.getWeapon();
        Weapon.Trait trait = w.trait();
        if (trait == null || chaining) return;
        int power = trait.power(w.rarity == Weapon.Rarity.LEGENDARY);
        switch (trait) {
            case LIFESTEAL:
                healPlayer(Math.max(1, damage * power / 100));
                break;
            case BURNING:
                burns.add(new Burn(e, Math.max(1, damage * power / 100), System.currentTimeMillis()));
                break;
            case CHAIN: {
                if (Math.random() * 100 >= power) break;
                Enemy next = null;
                float best = 320f;
                synchronized (enemies) {
                    for (Enemy o : enemies) {
                        if (o == e || o.isDead) continue;
                        float d = getDistance(e.x, e.y, o.x, o.y);
                        if (d < best) {
                            best = d;
                            next = o;
                        }
                    }
                }
                if (next == null) break;
                int blue = Color.rgb(150, 210, 255);
                effects.slash(e.x, e.y, (float) Math.toDegrees(Math.atan2(next.y - e.y, next.x - e.x)), best, 4f, blue);
                effects.burst(next.x, next.y, blue, 16, 9f);
                chaining = true;
                damageEnemy(next, Math.max(1, damage / 2), false, 0f, 0f, false, false);
                chaining = false;
                break;
            }
            default: // Keen works through critChance()
                break;
        }
    }

    /** The weekly challenge's twist (MenuActivity starts the run); null outside it. */
    enum WeeklyMod {
        GLASS_CANNON("Glass Cannon", "You deal 50% more damage, but have half the health"),
        NO_POTIONS("Dry Throat", "No potions: shops and chests hold none"),
        HASTE("Swift Death", "Every enemy moves 25% faster");

        final String title, rule;

        WeeklyMod(String title, String rule) {
            this.title = title;
            this.rule = rule;
        }
    }

    static final String EXTRA_WEEKLY_MOD = "weekly_mod";
    private WeeklyMod weeklyMod;

    /** Items without potions when the week forbids them. */
    private List<ShopItem> withoutPotions(List<ShopItem> items) {
        if (weeklyMod != WeeklyMod.NO_POTIONS) return items;
        List<ShopItem> kept = new ArrayList<>();
        for (ShopItem s : items) if (s.type != ShopItem.ItemType.CONSUMABLE) kept.add(s);
        return kept;
    }

    /** Wardrobe looks bought with Souls (MenuActivity.showWardrobe): slot 0 body, 1 swing trail, 2 death burst. */
    enum Cosmetic {
        PILGRIM(0, "Pilgrim", 0, 0), EMBER(0, "Ember", 0xFFE0702A, 40), FROST(0, "Frost", 0xFF7FC4F0, 40),
        VOID(0, "Void", 0xFF6A3FA8, 80), GILDED(0, "Gilded", 0xFFE8C25A, 150),
        STEEL(1, "Steel", 0, 0), BLOOD(1, "Blood", 0xFFC81E28, 30), SPIRIT(1, "Spirit", 0xFF6FE3D0, 60),
        SHADOW(1, "Shadow", 0xFF7A4FD0, 100),
        DUST(2, "Dust", 0, 0), ASH(2, "Ash", 0xFF9A938A, 30), SOULFIRE(2, "Soulfire", 0xFF4FD8E0, 60),
        GOLD(2, "Gold", 0xFFF4D77A, 120);

        final int slot, color, price; // color 0 = the game's own look
        final String title;

        Cosmetic(int slot, String title, int color, int price) {
            this.slot = slot;
            this.title = title;
            this.color = color;
            this.price = price;
        }

        /** The look worn in a slot, from the player's wardrobe prefs; the free one if none was picked. */
        static Cosmetic worn(SharedPreferences prefs, int slot) {
            String name = prefs.getString("slot" + slot, null);
            if (name != null) {
                try {
                    Cosmetic c = valueOf(name);
                    if (c.slot == slot) return c;
                } catch (IllegalArgumentException ignored) {
                    // a look from an older version
                }
            }
            for (Cosmetic c : values()) if (c.slot == slot && c.price == 0) return c;
            return PILGRIM;
        }
    }

    private int deathColor = 0; // wardrobe death burst; 0 = the body colour

    // The room event whose offer is open, so walking up asks once per visit
    private volatile Props.Decor eventNear;

    // Trial arena: the gong seals the door and calls up TRIAL_WAVES waves, one after another; clearing the
    // last opens the door and leaves a vault-grade chest. Its foes never count toward the floor's kills.
    private static final int TRIAL_WAVES = 3;
    private volatile Room trialRoom;  // the arena while its trial runs, else null
    private int trialWave = 0;        // waves called so far (logic thread)
    private long trialNextAt = 0;     // when the next wave may rise (logic thread)
    private final List<Enemy> trialFoes = new ArrayList<>(); // guarded by enemies

    // Forgotten library: the tome was read, so enemies hit harder until the floor is left
    private static final float TOME_CURSE = 1.2f;
    private volatile boolean tomeCurse = false;

    // Flooded Catacombs: share of a step kept while wading; splashes are spaced out
    private static final float WADE = 0.6f;
    private long lastSplash = 0;

    /**
     * Readies this floor's event rooms (FloorLayout grows them, Props dresses them): sets the cursed chest
     * on its circle and clears what the last floor's events left behind. Runs with every new layout.
     */
    private void setUpEventRooms() {
        eventNear = null;
        trialRoom = null;
        tomeCurse = false;
        synchronized (enemies) { trialFoes.clear(); }
        if (currentLayout == null) return;
        for (Room room : currentLayout.eventRooms) {
            if (room.event != RoomEvent.CURSED) continue;
            TreasureChest chest = new TreasureChest(room.centerX(), room.centerY(), room.centerX(), room.centerY(), 0L);
            chest.landed = true;
            chest.cursed = true;
            pickChestReward(chest);
            synchronized (treasureChests) { treasureChests.add(chest); }
        }
    }

    /** Marks the event room at a point as over, so its map icon dims. */
    private void eventDone(float x, float y) {
        Room room = currentLayout != null ? currentLayout.getRoomAt(x, y) : null;
        if (room != null && room.event != null) room.eventDone = true;
    }

    /** Walking up to an event room's centrepiece pauses and asks; walking away lets it ask again. */
    private void checkRoomEvents() {
        FloorLayout layout = currentLayout;
        if (coop != null || layout == null || inShopRoom || playerHealth <= 0) return;
        Props.Decor near = null;
        for (Props.Decor d : layout.decor) {
            if (Props.answers(d) && !d.used
                    && getDistance(player.x, player.y, d.x, d.y) < player.radius + Props.reach(d)) near = d;
        }
        if (near == eventNear) return;
        eventNear = near;
        if (near == null) return;
        Props.Decor event = near;
        isGamePaused = true;
        runOnUiThread(() -> {
            switch (event.kind) {
                case Props.SHRINE:   offerShrine(event); break;
                case Props.GAMBLER:  offerGamble(event); break;
                case Props.GONG:     offerTrial(event); break;
                case Props.PRISONER: offerPrisoner(event); break;
                case Props.LECTERN:  offerTome(event); break;
                default:             offerSpring(event); break;
            }
        });
    }

    /** Blood shrine: a quarter of your max HP for a blessing (one more perk pick). */
    private void offerShrine(Props.Decor shrine) {
        int cost = Math.max(1, maxHealth / 4);
        boolean can = playerHealth > cost;
        confirmEvent(R.drawable.ic_heart, "Blood Shrine",
                "Offer " + cost + " HP for a blessing?" + (can ? "" : "\nYou are too weak to pay."),
                "Offer blood", can, () -> {
                    shrine.used = true;
                    eventDone(shrine.x, shrine.y);
                    int old = playerHealth;
                    playerHealth = Math.max(1, playerHealth - cost);
                    animateHealthBar(old, playerHealth);
                    pendingPerks++;
                    updateLevelBadge();
                    int red = Color.rgb(224, 72, 72);
                    effects.burst(shrine.x, shrine.y, red, 30, 10f);
                    effects.banner(player.x, player.y - player.radius - 50f, "BLESSING EARNED", red);
                    soundManager.play(SoundManager.Sfx.PERK_PICK);
                });
    }

    /** The gambler: gold for a random item from this range's stock, of any rarity. */
    private void offerGamble(Props.Decor gambler) {
        int floor = floorManager.getCurrentFloor();
        int price = Math.round(BatchGenerator.priceBase(floor) * 1.5f);
        List<ShopItem> pool = getChestItemPool(floor);
        boolean can = playerGold >= price && !pool.isEmpty();
        confirmEvent(R.drawable.ic_coin, "The Gambler",
                "Pay " + price + " gold for a mystery item? It could be anything." + (can ? "" : "\nYou can't pay."),
                "Gamble", can, () -> {
                    gambler.used = true;
                    eventDone(gambler.x, gambler.y);
                    playerGold -= price;
                    statsManager.onCoinsSpent(price);
                    updateGoldHUD();
                    ShopItem prize = pool.get((int) (Math.random() * pool.size()));
                    purchaseItem(prize);
                    effects.banner(player.x, player.y - player.radius - 50f, prize.name.toUpperCase(),
                            getRarityColor(prize.rarity));
                    soundManager.play(SoundManager.Sfx.PURCHASE);
                });
    }

    /** The Trial arena's gong: strike it to seal the door and face the waves for a vault-grade chest. */
    private void offerTrial(Props.Decor gong) {
        confirmEvent(R.drawable.ic_crossed_swords, "Trial of Blood",
                "Strike the gong? The door seals and " + TRIAL_WAVES + " waves rise, one after another. "
                        + "Clear them all for a vault-grade prize.",
                "Strike", true, () -> {
                    Room room = currentLayout != null ? currentLayout.getRoomAt(gong.x, gong.y) : null;
                    if (room == null) return;
                    gong.used = true;
                    trialWave = 0;
                    trialNextAt = System.currentTimeMillis() + 900;
                    currentLayout.trialSealed = true;
                    trialRoom = room;
                    int crimson = Color.rgb(224, 72, 72);
                    effects.burst(gong.x, gong.y, Color.rgb(244, 215, 122), 26, 10f);
                    effects.banner(gong.x, gong.y - 140f, "THE TRIAL BEGINS", crimson);
                    if (camera != null) camera.startShake(300, 10f);
                    soundManager.play(SoundManager.Sfx.GATE_LOCKED);
                });
    }

    /** Runs a Trial in progress: the next wave rises a moment after the last falls; after the final one it's won. */
    private void tickTrial(long now) {
        Room room = trialRoom;
        if (room == null || isGuest()) return;
        int alive = 0;
        synchronized (enemies) {
            for (Enemy e : trialFoes) if (!e.isDead) alive++;
        }
        if (alive > 0) {
            trialNextAt = now + 1500;
            return;
        }
        if (now < trialNextAt) return;
        if (trialWave < TRIAL_WAVES) spawnTrialWave(room, ++trialWave);
        else winTrial(room);
    }

    /** One Trial wave in a ring round the gong, awake at once; the last wave is led by an elite. */
    private void spawnTrialWave(Room room, int wave) {
        int floor = floorManager.getCurrentFloor();
        int count = Math.min(6, 2 + wave + floor / 6);
        List<EnemyTemplate> pool = getEnemyPoolForFloor(floor);
        int crimson = Color.rgb(224, 72, 72);
        float[] spot = new float[2];
        synchronized (enemies) {
            for (int i = 0; i < count; i++) {
                double a = Math.PI * 2 * i / count + Math.random() * 0.5;
                float x = room.centerX() + (float) Math.cos(a) * room.width * 0.32f;
                float y = room.centerY() + (float) Math.sin(a) * room.height * 0.32f;
                Enemy foe = new Enemy(x, y, pickEnemyType(floor));
                Props.pushOut(currentLayout.decor, x, y, foe.radius, spot);
                foe.x = spot[0];
                foe.y = spot[1];
                foe.applyFloorScaling(floor);
                if (!pool.isEmpty()) {
                    foe.templateIndex = (int) (Math.random() * pool.size());
                    foe.applyTemplate(pool.get(foe.templateIndex));
                }
                if (wave == TRIAL_WAVES && i == 0) foe.makeElite();
                foe.ascend(ascension());
                if (weeklyMod == WeeklyMod.HASTE) foe.speed *= 1.25f;
                foe.isAwake = true;
                foe.setBehaviorTracker(behaviorTracker);
                foe.setLayout(currentLayout);
                enemies.add(foe);
                trialFoes.add(foe);
                effects.burst(foe.x, foe.y, crimson, 24, 10f);
            }
        }
        effects.banner(room.centerX(), room.centerY() - 160f, "WAVE " + wave + " / " + TRIAL_WAVES, crimson);
        if (camera != null) camera.startShake(200, 8f);
        soundManager.play(SoundManager.Sfx.SUMMON);
    }

    /** The last wave fell: the door opens and a vault-grade chest drops by the gong. */
    private void winTrial(Room room) {
        trialRoom = null;
        if (currentLayout != null) currentLayout.trialSealed = false;
        room.eventDone = true;
        float x = room.centerX(), y = room.centerY() + 150f;
        TreasureChest chest = new TreasureChest(x, y, room.centerX(), room.centerY(), System.currentTimeMillis());
        chest.vault = true; // the rarest loot, gold and a chance at a relic
        pickChestReward(chest);
        synchronized (treasureChests) { treasureChests.add(chest); }
        int gold = Color.rgb(244, 215, 122);
        effects.banner(room.centerX(), room.centerY() - 160f, "TRIAL WON", gold);
        effects.burst(room.centerX(), room.centerY(), gold, 30, 11f);
        soundManager.play(SoundManager.Sfx.VAULT_UNLOCK);
    }

    /** The prisoner: spend a key on the shackles and they pay you with a relic (gold once you have them all). */
    private void offerPrisoner(Props.Decor prisoner) {
        boolean can = keys > 0;
        confirmEvent(R.drawable.ic_key, "The Prisoner",
                "\"Break these shackles and I'll share what I stole.\"\nIt takes one key."
                        + (can ? "" : "\nYou have no key."),
                "Use a key", can, () -> {
                    prisoner.used = true;
                    eventDone(prisoner.x, prisoner.y);
                    keys--;
                    updateKeysHUD();
                    int gold = Color.rgb(244, 215, 122);
                    effects.burst(prisoner.x, prisoner.y, Color.rgb(120, 122, 130), 20, 8f);
                    soundManager.play(SoundManager.Sfx.VAULT_UNLOCK);
                    if (relics.size() < Relic.values().length) {
                        grantRelic(prisoner.x, prisoner.y);
                    } else {
                        spawnGoldCoins(prisoner.x, prisoner.y + 60f, withGreed(10 + 2 * floorManager.getCurrentFloor()));
                        effects.banner(prisoner.x, prisoner.y - 120f, "FREED", gold);
                    }
                });
    }

    /** The forbidden tome: a level at once, but enemies hit harder until the floor is left. */
    private void offerTome(Props.Decor lectern) {
        int curse = Math.round((TOME_CURSE - 1f) * 100);
        confirmEvent(R.drawable.ic_level, "Forbidden Tome",
                "Read the tome to gain a level at once? Its curse follows you: enemies hit "
                        + curse + "% harder until you leave this floor.",
                "Read", true, () -> {
                    lectern.used = true;
                    eventDone(lectern.x, lectern.y);
                    tomeCurse = true;
                    gainXp(Math.max(1, xpForNextLevel() - playerXp));
                    int arcane = Color.rgb(181, 140, 224);
                    effects.burst(lectern.x, lectern.y, arcane, 26, 9f);
                    effects.banner(player.x, player.y - player.radius - 110f, "CURSED KNOWLEDGE", arcane);
                });
    }

    /** The healing spring: one drink restores two fifths of your health, then it runs dry. */
    private void offerSpring(Props.Decor spring) {
        int heal = Math.max(1, maxHealth * 2 / 5);
        boolean can = playerHealth < maxHealth;
        confirmEvent(R.drawable.ic_heart, "Healing Spring",
                "Drink to restore " + heal + " HP? The spring runs dry after one drink."
                        + (can ? "" : "\nYou are already whole."),
                "Drink", can, () -> {
                    spring.used = true;
                    eventDone(spring.x, spring.y);
                    int old = playerHealth;
                    playerHealth = Math.min(maxHealth, playerHealth + heal);
                    animateHealthBar(old, playerHealth);
                    int water = Color.rgb(96, 170, 200);
                    effects.burst(player.x, player.y, water, 24, 8f);
                    effects.play("fx.heal", player.x, player.y + player.radius * 0.78f, false);
                    effects.banner(player.x, player.y - player.radius - 50f, "+" + (playerHealth - old) + " HP", water);
                    soundManager.play(SoundManager.Sfx.POTION);
                });
    }

    /** Infernal Forge: when a vent erupts, whoever stands on it burns once; enemies take four times as much. */
    private void tickVents(long now) {
        FloorLayout layout = currentLayout;
        if (layout == null || inShopRoom || layout.biome != Biome.FORGE) return;
        int burn = 6 + floorManager.getCurrentFloor() / 2;
        for (Props.Decor d : layout.decor) {
            if (d.kind != Props.VENT || !Props.venting(d, now)) continue;
            long cycle = Props.ventCycle(d, now);
            if (d.burnedCycle == cycle) continue;
            d.burnedCycle = cycle;
            if (getDistance(player.x, player.y, d.x, d.y) < d.b + player.radius * 0.5f)
                hitPlayer(burn, d.x, d.y, 0.6f, now);
            if (isGuest()) continue; // the host burns the enemies
            synchronized (enemies) {
                for (Enemy e : enemies) {
                    if (e.isDead || e.type == Enemy.Type.BAT
                            || getDistance(e.x, e.y, d.x, d.y) >= d.b + e.radius * 0.5f) continue;
                    e.takeDamage(burn * 4);
                    if (showDamageNumbers)
                        effects.number(e.x, e.y - e.radius, String.valueOf(burn * 4), Props.LAVA);
                }
            }
        }
    }

    /** A room event's offer on the confirm card; the game stays paused until it's answered. */
    private void confirmEvent(int icon, String title, String message, String yesText, boolean canAccept,
                              Runnable accept) {
        Dialog confirm = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        confirm.setContentView(R.layout.dialog_confirm);
        confirm.setCancelable(false);
        if (confirm.getWindow() != null)
            confirm.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ((android.widget.ImageView) confirm.findViewById(R.id.confirm_icon)).setImageResource(icon);
        ((TextView) confirm.findViewById(R.id.confirm_title)).setText(title);
        ((TextView) confirm.findViewById(R.id.confirm_message)).setText(message);
        MaterialButton no = confirm.findViewById(R.id.btn_confirm_no);
        MaterialButton yes = confirm.findViewById(R.id.btn_confirm_yes);
        no.setText("Leave");
        yes.setText(yesText);
        yes.setEnabled(canAccept);
        yes.setAlpha(canAccept ? 1f : 0.4f);
        no.setOnClickListener(v -> {
            confirm.dismiss();
            isGamePaused = false;
            hideSystemUI();
        });
        yes.setOnClickListener(v -> {
            confirm.dismiss();
            accept.run();
            isGamePaused = false;
            hideSystemUI();
        });
        confirm.show();
        hideSystemUIOnWindow(confirm.getWindow());
    }

    /** A cursed chest's price: two elites rise around it. Solo only, so this phone runs the enemies. */
    private void curseChest(TreasureChest chest) {
        int floor = floorManager.getCurrentFloor();
        List<EnemyTemplate> pool = getEnemyPoolForFloor(floor);
        int purple = Color.rgb(176, 60, 220);
        synchronized (enemies) {
            for (int i = 0; i < 2; i++) {
                double a = Math.PI * i + Math.random();
                float x = chest.x + (float) Math.cos(a) * 160f, y = chest.y + (float) Math.sin(a) * 160f;
                if (currentLayout != null && !currentLayout.isFloor(x, y)) {
                    x = chest.x;
                    y = chest.y;
                }
                Enemy elite = new Enemy(x, y, pickEnemyType(floor));
                elite.applyFloorScaling(floor);
                if (!pool.isEmpty()) {
                    elite.templateIndex = (int) (Math.random() * pool.size());
                    elite.applyTemplate(pool.get(elite.templateIndex));
                }
                elite.makeElite();
                elite.ascend(ascension());
                elite.isAwake = true;
                elite.setBehaviorTracker(behaviorTracker);
                elite.setLayout(currentLayout);
                enemies.add(elite);
                effects.burst(x, y, purple, 24, 10f);
            }
        }
        effects.banner(chest.x, chest.y - 90f, "CURSED!", purple);
        if (camera != null) camera.startShake(250, 12f);
        soundManager.play(SoundManager.Sfx.SUMMON);
    }

    // First-run tutorial (solo): the step shown in txt_tutorial, or -1 when off
    private volatile int tutorialStep = -1;
    private long tutorialStepAt;
    private float tutorialFromX, tutorialFromY;
    private static final String[] TUTORIAL = {
            "Drag the move stick to walk",
            "Hold the ATK stick to attack. Drag it to aim",
            "Tap DASH to dodge through attacks",
            "Hold HEAVY, then let go for a crushing blow",
            "Defeat the enemies to open the portal",
            "Step into the portal to go deeper"};

    private void setTutorialStep(int step) {
        tutorialStep = step;
        tutorialStepAt = System.currentTimeMillis();
        tutorialFromX = player.x;
        tutorialFromY = player.y;
        runOnUiThread(() -> {
            TextView hint = findViewById(R.id.txt_tutorial);
            if (step < 0) {
                hint.setVisibility(View.GONE);
                return;
            }
            hint.setText(TUTORIAL[step] + "\n(tap to skip)");
            hint.setVisibility(View.VISIBLE);
        });
    }

    private void finishTutorial(boolean skipped) {
        setTutorialStep(-1);
        if (userId != null)
            getSharedPreferences("tutorial_" + userId, MODE_PRIVATE).edit().putBoolean("done", true).apply();
        if (skipped) return;
        effects.banner(player.x, player.y - player.radius - 60f, "TUTORIAL COMPLETE", Color.rgb(244, 215, 122));
        soundManager.play(SoundManager.Sfx.PERK_PICK);
    }

    /** Moves the tutorial on once the player has done what the current step asks. */
    private void tickTutorial() {
        int step = tutorialStep;
        if (step < 0) return;
        // Reaching floor 2 ends it; a loaded deeper save ends it quietly
        if (floorManager.getCurrentFloor() > 1) {
            finishTutorial(step < TUTORIAL.length - 1);
            return;
        }
        boolean done;
        switch (step) {
            case 0: done = getDistance(player.x, player.y, tutorialFromX, tutorialFromY) > 250f; break;
            case 1: done = lastAttackTime > tutorialStepAt; break;
            case 2: done = lastDashTime > tutorialStepAt; break;
            case 3: done = lastHeavyTime > tutorialStepAt; break;
            case 4: done = floorManager.isPortalOpen(); break;
            default: done = false;
        }
        if (!done) return;
        soundManager.play(SoundManager.Sfx.CLICK);
        setTutorialStep(step + 1);
    }

    /** Per-tick upkeep: burning foes, delayed elite blasts, room events and the tutorial. */
    private void tickExtras(long now) {
        for (java.util.Iterator<Burn> it = burns.iterator(); it.hasNext(); ) {
            Burn b = it.next();
            if (b.enemy.isDead || b.left <= 0) {
                it.remove();
                continue;
            }
            if (now < b.next) continue;
            b.next = now + 500;
            int dmg = Math.min(b.left, b.perTick);
            b.left -= dmg;
            if (showDamageNumbers)
                effects.number(b.enemy.x, b.enemy.y - b.enemy.radius, String.valueOf(dmg), Color.rgb(255, 140, 40));
            effects.burst(b.enemy.x, b.enemy.y, Color.rgb(255, 120, 30), 5, 4f);
            statsManager.onDamageGiven(dmg);
            if (isGuest()) {
                b.enemy.predictHit(dmg);
                if (b.enemy.isDead) deadEnemyAt.put(b.enemy.id, now);
                coop.sendHit(b.enemy.id + "," + dmg + ",0.0,0.0,0");
            } else {
                b.enemy.takeDamage(dmg);
            }
        }
        for (java.util.Iterator<long[]> it = pendingBlasts.iterator(); it.hasNext(); ) {
            long[] b = it.next();
            if (now < b[3]) continue;
            it.remove();
            resolveStrike(b[0], b[1], 1f, 0f, 170f, 180f, (int) b[2], 0.5f, 40f, true, now);
        }
        checkRoomEvents();
        tickTrial(now);
        tickVents(now);
        tickTutorial();
    }

    /** Player damage multiplier: +1% per Might, +2% per Altar Strength; levels give perks, not raw stats. */
    private float damageMultiplier() {
        float m = 1f + 0.01f * stacks(Perk.MIGHT) + 0.02f * altar[Altar.STRENGTH.ordinal()];
        if (relic == Relic.SKULL) m *= 1.12f;
        if (weeklyMod == WeeklyMod.GLASS_CANNON) m *= 1.5f;
        return m;
    }

    /** Max HP: 100, +5 per Vigor, +5 per Altar Vitality; halved by the Glass Cannon week. */
    private int computeMaxHealth() {
        int hp = maxHealth(stacks(Perk.VIGOR), altar[Altar.VITALITY.ordinal()]);
        return weeklyMod == WeeklyMod.GLASS_CANNON ? hp / 2 : hp;
    }

    static int maxHealth(int vigor, int vitality) {
        return 100 + 5 * vigor + 5 * vitality;
    }

    /** New Altar ranks: refresh max HP, topping up a player who was at full health. */
    private void applyAltar(int[] ranks) {
        altar = ranks;
        int oldMax = maxHealth;
        maxHealth = computeMaxHealth();
        if (playerHealth >= oldMax) playerHealth = maxHealth;
        if (checkpointHealth >= oldMax) checkpointHealth = maxHealth;
        playerHealthBar.setMax(maxHealth);
        playerHealthBar.setProgress(playerHealth);
        if (txtHealthValue != null) txtHealthValue.setText(playerHealth + " / " + maxHealth);
    }

    private float critChance() {
        if (System.currentTimeMillis() < perfectCritUntil) return 1f;
        float chance = CRIT_CHANCE + 0.005f * stacks(Perk.KEEN_EYE);
        Weapon w = player.getWeapon();
        if (w.trait() == Weapon.Trait.KEEN) chance += Weapon.Trait.KEEN.power(w.rarity == Weapon.Rarity.LEGENDARY) / 100f;
        if (relic == Relic.EYE) chance += 0.06f;
        return chance;
    }

    private long attackCooldown(Weapon w) {
        return (long) (w.cooldown / (1f + 0.01f * stacks(Perk.QUICK_HANDS)));
    }

    private long spinCooldown() {
        return (long) (SPIN_COOLDOWN / (1f + 0.01f * stacks(Perk.WHIRLWIND)));
    }

    private long dashCooldown() {
        long cooldown = (long) (DASH_COOLDOWN / (1f + 0.01f * stacks(Perk.SHADOW_STEP)));
        return relic == Relic.FEATHER ? cooldown * 4 / 5 : cooldown;
    }

    /** Gold after Greed; the fraction becomes a chance at one more coin. */
    private int withGreed(int gold) {
        float total = gold * (1f + 0.01f * stacks(Perk.GREED) + 0.05f * altar[Altar.FORTUNE.ordinal()]
                + (relic == Relic.IDOL ? 0.2f : 0f));
        int whole = (int) total;
        return whole + (Math.random() < total - whole ? 1 : 0);
    }

    private void applySpeedPerk() {
        player.speed = 10f * (1f + 0.01f * stacks(Perk.SWIFTNESS));
    }

    /**
     * Adds XP and handles level-ups (+20 HP heal and a perk pick per level).
     * Each level-up also earns a perk pick, waiting behind the badge.
     * Must run on the UI thread, like every other change to playerHealth.
     */
    private void gainXp(int amount) {
        playerXp += amount;
        int gained = 0;
        while (playerXp >= xpForNextLevel()) {
            playerXp -= xpForNextLevel();
            playerLevel++;
            gained++;
        }
        if (gained > 0) {
            maxHealth = computeMaxHealth();
            playerHealth = Math.min(maxHealth, playerHealth + 20);
            playerHealthBar.setMax(maxHealth);
            animateHealthBar(playerHealthBar.getProgress(), playerHealth);
            effects.banner(player.x, player.y - player.radius - 50f, "LEVEL " + playerLevel,
                    Color.rgb(255, 215, 0));
            effects.burst(player.x, player.y, Color.rgb(255, 215, 0), 30, 10f);
            soundManager.play(SoundManager.Sfx.LEVEL_UP);
            pendingPerks += gained;
            updateLevelBadge();
        }
        updateLevelHUD();
    }

    /** The pulsing "Level up!" badge, shown while perk picks are waiting. */
    private void updateLevelBadge() {
        if (pendingPerks <= 0) {
            btnLevelUp.setVisibility(View.GONE);
            if (levelBadgePulse != null) levelBadgePulse.cancel();
            return;
        }
        btnLevelUp.setText(pendingPerks > 1 ? "Level up! x" + pendingPerks : "Level up!");
        if (btnLevelUp.getVisibility() == View.VISIBLE) return;
        btnLevelUp.setVisibility(View.VISIBLE);
        levelBadgePulse = ObjectAnimator.ofPropertyValuesHolder(btnLevelUp,
                android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.08f),
                android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.08f));
        levelBadgePulse.setDuration(550);
        levelBadgePulse.setRepeatCount(ObjectAnimator.INFINITE);
        levelBadgePulse.setRepeatMode(ObjectAnimator.REVERSE);
        levelBadgePulse.start();
    }

    /**
     * Level-up cards: three random perks grow in face down, flip one by one from left to right,
     * and only then can one be picked. Solo pauses while choosing; co-op keeps running.
     */
    private void showPerkDialog() {
        if (pendingPerks <= 0 || perkDialogOpen) return;
        perkDialogOpen = true;
        if (coop == null) isGamePaused = true;

        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_level_up);
        dialog.setCancelable(false);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ((TextView) dialog.findViewById(R.id.txt_perk_title)).setText("Level " + playerLevel);

        LinearLayout row = dialog.findViewById(R.id.perk_cards);
        boolean[] ready = {false};
        dealPerkCards(dialog, row, ready);
        // Opt-in ad: one fresh hand per level-up, once the cards have turned
        MaterialButton reroll = dialog.findViewById(R.id.btn_perk_reroll);
        if (Ads.isReady()) reroll.setVisibility(View.VISIBLE);
        reroll.setOnClickListener(v -> {
            if (!ready[0]) return;
            Ads.show(this, () -> {
                reroll.setVisibility(View.GONE);
                row.removeAllViews();
                ready[0] = false;
                dealPerkCards(dialog, row, ready);
            }, () -> {
                reroll.setVisibility(View.GONE);
                hideSystemUIOnWindow(dialog.getWindow());
            });
        });
        dialog.show();
        hideSystemUIOnWindow(dialog.getWindow());
    }

    /** Three random perk cards grow in face down, then flip left to right; ready[0] turns true after the last. */
    private void dealPerkCards(Dialog dialog, LinearLayout row, boolean[] ready) {
        List<Perk> offer = new ArrayList<>(java.util.Arrays.asList(Perk.values()));
        Collections.shuffle(offer);
        float cameraDistance = 8000f * getResources().getDisplayMetrics().density;
        for (int i = 0; i < 3; i++) {
            Perk perk = offer.get(i);
            boolean last = i == 2;
            View card = getLayoutInflater().inflate(R.layout.item_perk_card, row, false);
            bindPerkCard(card, perk);
            card.setCameraDistance(cameraDistance);
            card.setScaleX(0f);
            card.setScaleY(0f);
            card.setOnClickListener(v -> { if (ready[0]) choosePerk(dialog, perk); });
            row.addView(card);
            Wear.apply(card); // after addView, so each slot keeps its own cracks
            card.animate().scaleX(1f).scaleY(1f).setStartDelay(i * 90L).setDuration(320)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
            card.postDelayed(() -> flipCard(card, () -> { if (last) ready[0] = true; }), 650 + i * 380L);
        }
    }

    private void bindPerkCard(View card, Perk perk) {
        android.widget.ImageView icon = card.findViewById(R.id.perk_icon);
        icon.setImageResource(perk.icon);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(perk.color));
        ((TextView) card.findViewById(R.id.perk_name)).setText(perk.title);
        ((TextView) card.findViewById(R.id.perk_desc)).setText(perk.describe(1));
        Synergy synergy = Synergy.of(perk);
        String hint = synergy == null || has(synergy) ? ""
                : "\n" + synergy.title + ": 3x " + synergy.a.title + " + 3x " + synergy.b.title;
        ((TextView) card.findViewById(R.id.perk_owned)).setText("Owned: " + stacks(perk) + hint);
    }

    /** Turns a card edge-on, swaps its back for its front, then turns it face up. */
    private void flipCard(View card, Runnable done) {
        soundManager.play(SoundManager.Sfx.CARD_FLIP);
        card.animate().rotationY(90f).setStartDelay(0).setDuration(170)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    card.findViewById(R.id.perk_back).setVisibility(View.INVISIBLE);
                    card.findViewById(R.id.perk_front).setVisibility(View.VISIBLE);
                    card.setRotationY(-90f);
                    card.animate().rotationY(0f).setStartDelay(0).setDuration(170)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .withEndAction(done).start();
                }).start();
    }

    /** Applies the picked perk; the badge stays while more picks are waiting. */
    private void choosePerk(Dialog dialog, Perk perk) {
        dialog.dismiss();
        perkDialogOpen = false;
        pendingPerks--;
        Synergy synergy = Synergy.of(perk);
        boolean awakened = synergy != null && !has(synergy);
        perks[perk.ordinal()]++;
        awakened = awakened && has(synergy);
        if (perk == Perk.VIGOR) {
            maxHealth = computeMaxHealth();
            int old = playerHealth;
            playerHealth = Math.min(maxHealth, playerHealth + 5);
            playerHealthBar.setMax(maxHealth);
            animateHealthBar(old, playerHealth);
        }
        applySpeedPerk();
        updateLevelBadge();
        effects.banner(player.x, player.y - player.radius - 50f, perk.title, perk.color);
        if (awakened) {
            effects.banner(player.x, player.y - player.radius - 110f, synergy.title.toUpperCase() + " AWAKENS",
                    Color.rgb(244, 215, 122));
            effects.burst(player.x, player.y, Color.rgb(244, 215, 122), 40, 12f);
        }
        soundManager.play(SoundManager.Sfx.PERK_PICK);
        if (coop == null) isGamePaused = false;
        hideSystemUI();
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
        int missing = (maxHealth - playerHealth) * 100 / maxHealth; // in percent, like the potions
        ConsumableStack best = null;
        for (ConsumableStack stack : consumableInventory) {
            if (!"heal".equals(stack.item.effect)) continue;
            if (best == null) { best = stack; continue; }
            // Prefer the smallest potion that still fills the gap, so big potions aren't wasted
            int pct = BatchGenerator.potionPercent(stack.item), bestPct = BatchGenerator.potionPercent(best.item);
            boolean fits = pct >= missing;
            boolean bestFits = bestPct >= missing;
            if (fits && (!bestFits || pct < bestPct)) best = stack;
            else if (!fits && !bestFits && pct > bestPct) best = stack;
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
                if (coin.collected || !coin.landed()) continue; // a coin still bouncing can't be caught
                float dist = getDistance(player.x, player.y, coin.x, coin.y);
                if (dist < player.radius + GoldCoin.COLLECT_RADIUS) {
                    statsManager.onCoinCollected();
                    coin.collected = true;
                    soundManager.playCoinPickup();
                    effects.burst(coin.x, coin.y, Color.rgb(255, 215, 0), 4, 3f);
                    playerGold++;
                    quest(Quests.GOLD, 1);
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

        // Re-apply the attack direction mode in case it changed in Settings
        SharedPreferences settings = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);
        attackFacing = settings.getBoolean(SettingsActivity.KEY_ATTACK_FACING, false);
        aimJoystick.setAimEnabled(!attackFacing);
        GameCamera.shakeEnabled = settings.getBoolean(SettingsActivity.KEY_SHAKE, true);
        effects.gore = settings.getBoolean(SettingsActivity.KEY_GORE, true);
        showDamageNumbers = settings.getBoolean(SettingsActivity.KEY_NUMBERS, true);
        lowHealthEffects = settings.getBoolean(SettingsActivity.KEY_DANGER, true);
        cameraLean = settings.getBoolean(SettingsActivity.KEY_LEAN, true);
        applyStickSettings(settings);

        // CHECK IF WE CAME BACK FROM SETTINGS
        if (showMenuOnResume) {
            showMenuOnResume = false;
            // Slight delay so the activity has time to draw before throwing the dialog up
            new android.os.Handler().postDelayed(this::showMenuDialog, 100);
        }
    }

    /** Settings: stick size and opacity, and the move stick on the right for left-handed players. */
    private void applyStickSettings(SharedPreferences settings) {
        View actions = findViewById(R.id.action_buttons);
        float dp = getResources().getDisplayMetrics().density;
        float size = settings.getInt(SettingsActivity.KEY_STICK_SIZE, 100) / 100f;
        float alpha = settings.getInt(SettingsActivity.KEY_STICK_ALPHA, 100) / 100f;
        boolean left = settings.getBoolean(SettingsActivity.KEY_LEFT_HANDED, false);
        android.widget.RelativeLayout.LayoutParams stick = (android.widget.RelativeLayout.LayoutParams) joystick.getLayoutParams();
        android.widget.RelativeLayout.LayoutParams arc = (android.widget.RelativeLayout.LayoutParams) actions.getLayoutParams();
        stick.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_START);
        stick.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
        stick.addRule(left ? android.widget.RelativeLayout.ALIGN_PARENT_END : android.widget.RelativeLayout.ALIGN_PARENT_START);
        stick.setMarginStart(left ? 0 : Math.round(24 * dp));
        stick.setMarginEnd(left ? Math.round(24 * dp) : 0);
        arc.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_START);
        arc.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
        arc.addRule(left ? android.widget.RelativeLayout.ALIGN_PARENT_START : android.widget.RelativeLayout.ALIGN_PARENT_END);
        arc.setMarginStart(left ? Math.round(20 * dp) : 0);
        arc.setMarginEnd(left ? 0 : Math.round(20 * dp));
        joystick.setLayoutParams(stick);
        actions.setLayoutParams(arc);
        // Mirrored, the arc's buttons still fan out toward the middle of the screen
        actions.setLayoutDirection(left ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        // Both grow from their outer bottom corner, so they never slide off the screen
        joystick.setPivotX(left ? 168 * dp : 0f);
        joystick.setPivotY(168 * dp);
        actions.setPivotX(left ? 0f : 188 * dp);
        actions.setPivotY(184 * dp);
        for (View v : new View[]{joystick, actions}) {
            v.setScaleX(size);
            v.setScaleY(size);
            v.setAlpha(alpha);
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    private void hideSystemUIOnWindow(android.view.Window window) {
        if (window == null) return;
        Wear.apply(window.getDecorView());
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
     * A short buzz, ms long, at a strength of 1-255 where the phone supports it; skipped when vibration
     * is off or another buzz just started.
     */
    private void buzz(long ms, int strength) {
        long now = System.currentTimeMillis();
        if (now - lastBuzz < 50) return;
        lastBuzz = now;
        if (!getSharedPreferences("gameSettings", MODE_PRIVATE).getBoolean("vibration_enabled", true)) return;
        try {
            android.os.Vibrator v = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                    ? ((android.os.VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE)).getDefaultVibrator()
                    : (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            v.vibrate(android.os.VibrationEffect.createOneShot(ms,
                    v.hasAmplitudeControl() ? strength : android.os.VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception e) {
            // Last resort: haptic feedback on the game view
            gameDisplay.post(() -> gameDisplay.performHapticFeedback(
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING));
        }
    }

    /**
     * Returns the shop items for a specific floor from the batch cache.
     * @param floor the floor number
     * @return list of shop items (empty if the batch is not yet available)
     */
    private List<ShopItem> getShopItemsForFloor(int floor) {
        if (floor == restockedFloor && restockedShop != null) return withoutPotions(restockedShop);
        int rangeStart = getRangeStart(floor);
        BatchResult result = batchCache.get(rangeStart);
        if (result == null || result.shopByFloor == null) return new ArrayList<>();
        List<ShopItem> items = result.shopByFloor.get(floor);
        return items != null ? withoutPotions(items) : new ArrayList<>();
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
        List<Integer> perkStacks = new ArrayList<>();
        for (int stack : perks) perkStacks.add(stack);
        save.put("perks",                perkStacks);
        save.put("pendingPerks",         pendingPerks);
        save.put("keys",                 keys);
        List<String> relicNames = new ArrayList<>();
        for (Relic r : relics) relicNames.add(r.name());
        save.put("relics",               relicNames);
        save.put("relic",                relic != null ? relic.name() : null);
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
                spawnRoomChest();

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
                // Perk stacks by Perk order; older saves have none
                Object savedPerks = data.get("perks");
                if (savedPerks instanceof List) {
                    List<?> list = (List<?>) savedPerks;
                    for (int i = 0; i < Math.min(list.size(), perks.length); i++)
                        perks[i] = ((Number) list.get(i)).intValue();
                }
                pendingPerks = data.get("pendingPerks") != null
                        ? ((Number) data.get("pendingPerks")).intValue() : 0;
                keys = data.get("keys") != null ? ((Number) data.get("keys")).intValue() : 0;
                // Relics found and the one worn; names this version doesn't know are skipped
                Object savedRelics = data.get("relics");
                if (savedRelics instanceof List) {
                    for (Object name : (List<?>) savedRelics) {
                        try {
                            relics.add(Relic.valueOf(String.valueOf(name)));
                        } catch (IllegalArgumentException ignored) {
                            // unknown relic
                        }
                    }
                }
                Object wornRelic = data.get("relic");
                for (Relic r : relics) if (r.name().equals(wornRelic)) relic = r;
                updateKeysHUD();
                applySpeedPerk();
                updateLevelBadge();
                maxHealth = computeMaxHealth();
                // A run still at its very start begins at full health; older saves are capped
                if (savedFloor == 1 && playerLevel == 1 && playerXp == 0) playerHealth = maxHealth;
                else playerHealth = Math.min(playerHealth, maxHealth);

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