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
public class GameActivity extends AppCompatActivity {

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

    /* ============================================================
       GAME ENGINE OBJECTS
       ============================================================ */
    private GameCamera camera;
    private Player player;
    private List<Enemy> enemies = Collections.synchronizedList(new ArrayList<>());

    private List<Weapon> weapons = new ArrayList<>();
    private List<Projectile> projectiles = Collections.synchronizedList(new ArrayList<>());

    /* ============================================================
       GAME STATE
       ============================================================ */
    private boolean layoutReady = false;
    private int screenWidth = 0;
    private int screenHeight = 0;
    private boolean isGamePaused = false;
    private boolean threadsRunning = true;

    private boolean showMenuOnResume = false; // re-open menu after returning from Settings

    // Player Stats
    private int playerHealth = 100;
    private int playerArmor = 100;
    private int oldHealth = 0;
    private int oldArmor = 0;

    // Combat
    private boolean isAttacking = false;
    private long attackStartTime = 0;
    private long lastAttackTime = 0;
    private final long ATTACK_DURATION = 150;
    private final long ATTACK_COOLDOWN = 400;
    private long lastPlayerHitTime = 0;

    private float checkpointX = 0f;
    private float checkpointY = 0f;
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

    private BatchGenerator batchGenerator = new BatchGenerator();

    // Cache: key = rangeStart (1, 6, 11 ...), value = result
    private final java.util.Map<Integer, BatchResult> batchCache =
            new java.util.HashMap<>();

    // Which range is currently in-flight (being fetched)
    private int batchInFlight = -1;
    private int batchFailures = 0;

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

        player = new Player();
        player.equipWeapon(weapons.get(0));

        floorManager = new FloorManager();

        // Fire batch for floors 1-5 (floor 1 now included so it has shop items + named enemies)
        generateBatch(1, 5);

        // Start floor 1 immediately with generic placeholder enemies while the batch loads.
        // When the batch arrives, applyBatchToCurrentFloorIfReady() will re-spawn with
        // AI templates and the shop will auto-populate via pollForBatchThenPopulate().

        currentLayout = FloorLayout.generate(
                floorManager.getCurrentFloor(),
                floorManager.isBossFloor(),
                floorManager.getKillsRequired());
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
        gameId = getIntent().getStringExtra("game_id");
        int slotIndex = getIntent().getIntExtra("slot_index", 1);

        statsManager = new StatsManager(dbManager, userId, gameId);

        if (gameId == null && userId != null) {
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
        menuBtn.setOnClickListener(v -> showMenuDialog());
        btnInventory.setOnClickListener(v -> showInventoryDialog());

        aimJoystick = findViewById(R.id.aim_joystick);
        aimJoystick.setOnFireListener((dx, dy) -> {
            aimDX = dx;
            aimDY = dy;
            if (player.getWeapon().isProjectile) {
                fireProjectile(dx, dy);
            } else {
                // Melee — trigger attack in aimed direction
                long now = System.currentTimeMillis();
                if (now - lastAttackTime > player.getWeapon().cooldown) {
                    isAttacking = true;
                    soundManager.playSwingMelee();
                    attackStartTime = now;
                    lastAttackTime = now;
                }
            }
        });

        playerArmor = 0;
        maxArmor = 0;
        playerArmorBar.setMax(1);
        playerArmorBar.setProgress(0);

        refreshAttackUI();

        startLogicThread();
        startRenderThread();
    }

    /* ============================================================
       GAME LOOP (LOGIC)
       ============================================================ */
    /**
     * Starts the logic thread that runs at ~60 fps.
     * Updates the player, enemies, projectiles, hits, coins, portals and camera.
     */
    private void startLogicThread() {
        new Thread(() -> {
            while (threadsRunning) {
                long now = System.currentTimeMillis();

                // Boss death detection runs even while paused to avoid missing kills
                if (layoutReady) checkBossDeathDetection();

                if (!isGamePaused && layoutReady) {

                    // --- 1. Update Player ---
                    float prevPlayerX = player.x, prevPlayerY = player.y;
                    if (isDashing) {
                        player.update(dashDX * (DASH_POWER / player.speed), dashDY * (DASH_POWER / player.speed));
                        if (now - dashStartTime > DASH_DURATION) {
                            isDashing = false;
                        }
                    } else {
                        player.update(joystick.getDX(), joystick.getDY());
                    }
                    if (currentLayout != null && !inShopRoom) {
                        float[] resolved = currentLayout.resolveWallCollision(
                                prevPlayerX, prevPlayerY, player.x, player.y, player.radius);
                        player.x = resolved[0];
                        player.y = resolved[1];
                    }

                    // Update dash cooldown arc
                    long timeSinceDash = now - lastDashTime;
                    float dashProgress = Math.min(1f, (float) timeSinceDash / DASH_COOLDOWN);
                    runOnUiThread(() -> {
                        if (dashCooldownView != null)
                            dashCooldownView.setProgress(dashProgress);
                    });

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
                    
                    player.setDebugAttack(isAttacking);
                    player.setAimAngle((float) Math.toDegrees(Math.atan2(aimDY, aimDX)));

                    // Mark current room as explored for minimap
                    if (currentLayout != null && !inShopRoom) {
                        Room currentRoom = currentLayout.getRoomAt(player.x, player.y);
                        if (currentRoom != null) currentRoom.explored = true;
                    }

                    // --- 2. Update Camera ---
                    camera.update(player.getX(), player.getY());

                    // --- 3. Update Enemies ---
                    synchronized (enemies) {
                        for (Enemy e : enemies) {
                            if (!e.isAwake) {
                                e.tryWake(player.getX(), player.getY());
                                continue;
                            }
                            float prevEX = e.x, prevEY = e.y;
                            e.update(player.getX(), player.getY());
                            if (currentLayout != null && !inShopRoom) {
                                float[] resolved = currentLayout.resolveWallCollision(
                                        prevEX, prevEY, e.x, e.y, e.radius);
                                e.x = resolved[0];
                                e.y = resolved[1];
                            }
                        }
                    }

                    resolveCollisions();

                    // --- 5. Collision: Attack ---
                    if (isAttacking) {
                        checkAttackCollisions();
                        if (now - attackStartTime > ATTACK_DURATION) isAttacking = false;
                    }

                    updateProjectiles();
                    checkProjectileCollisions();

                    // --- 6. Collision: Player Hit ---
                    checkPlayerHit(now);

                    checkGoldCollection();
                    checkChestInteraction();

                    cleanupDeadEnemies();
                    checkPortalCollision();
                    checkShopPortalCollision();
                    checkShopRoomInteractions();
                    checkBossStatus();
                }

                try { Thread.sleep(16); } catch (InterruptedException ignored) {}
            }
        }).start();
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
                        EnemyTemplate template = pool.get((int)(Math.random() * pool.size()));
                        enemy.applyTemplate(template);
                    }
                    enemy.setBehaviorTracker(behaviorTracker);
                    // isAwake = false — enemy waits for player to enter trigger radius
                    enemies.add(enemy);
                }
            }
        }
        Log.d("FloorLayout", "Pre-placed " + enemies.size() + " enemies for floor " + floor);
    }

    /**
     * Picks a random enemy type appropriate for the current floor.
     * Early floors only introduce GRUNT; later floors add TANK and BRUTE.
     * @param floor the current floor number
     * @return the selected enemy type
     */
    private Enemy.Type pickEnemyType(int floor) {
        double roll = Math.random();
        if (floor <= 2) return Enemy.Type.GRUNT;
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

                if (angleDiff > 45f) continue;
                behaviorTracker.recordAttack(dist);

                e.takeDamage(w.damage);
                e.lastHitTime = now;
                hitThisSwing.add(e);
                soundManager.playHitEnemy();
                statsManager.onDamageGiven(w.damage);
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > 0) {
                    e.knockbackVX = (dx / d) * w.knockback * 25f;
                    e.knockbackVY = (dy / d) * w.knockback * 25f;
                }
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

    /**
     * Fires a Gemini batch call for the given range.
     * Caches result keyed by rangeStart.
     */
    private void generateBatch(int rangeStart, int rangeEnd) {
        if (batchCache.containsKey(rangeStart)) return;
        if (batchInFlight == rangeStart) return;

        // Check local storage first — avoids a network call on load game
        String cachedJson = BatchGenerator.loadRawBatch(this, rangeStart);
        if (cachedJson != null) {
            try {
                BatchResult result = batchGenerator.parseBatch(cachedJson, rangeStart, rangeEnd);
                batchCache.put(rangeStart, result);
                Log.d("Batch", "=== Loaded batch " + rangeStart + "-" + rangeEnd + " from local storage ===");
                runOnUiThread(() -> applyBatchToCurrentFloorIfReady(rangeStart));
                return;
            } catch (Exception e) {
                Log.e("Batch", "Local batch parse failed: " + e.getMessage() + " — fetching fresh");
            }
        }

        batchInFlight = rangeStart;
        Log.d("Batch", "=== Firing batch for floors " + rangeStart + "-" + rangeEnd + " ===");

        batchGenerator.generateBatch(rangeStart, rangeEnd, new BatchGenerator.OnBatchGeneratedListener() {
            @Override
            public void onSuccess(BatchResult result, String jsonText) {
                BatchGenerator.saveRawBatch(GameActivity.this, rangeStart, jsonText);
                batchCache.put(rangeStart, result);
                batchInFlight = -1;
                batchFailures = 0;
                Log.d("Batch", "=== Cached + saved batch for floors " + rangeStart + "-" + rangeEnd + " ===");
                runOnUiThread(() -> applyBatchToCurrentFloorIfReady(rangeStart));
            }

            @Override
            public void onFailure(String error) {
                batchInFlight = -1;
                long delay = Math.min(60000L, 3000L << Math.min(batchFailures++, 5));
                Log.e("Batch", "=== Batch failed: " + error + " — retrying in " + delay / 1000 + "s ===");
                // Called on an OkHttp thread, which has no Looper of its own
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (threadsRunning) generateBatch(rangeStart, rangeEnd);
                }, delay);
            }
        });
    }

    /**
     * Called when a batch lands. If the current floor is covered by that batch
     * and no kills have happened yet, clears and re-spawns enemies so the player
     * sees custom enemies instead of the generic placeholders that were used
     * when the floor was first generated before the batch was ready.
     */
    private void applyBatchToCurrentFloorIfReady(int rangeStart) {
        if (currentLayout == null) return;
        int cur = floorManager.getCurrentFloor();
        if (cur < rangeStart) return;                        // not our floor yet
        if (floorManager.getKillsThisFloor() > 0) return;   // combat already started
        List<EnemyTemplate> pool = getEnemyPoolForFloor(cur);
        if (pool.isEmpty()) return;                          // batch had nothing useful

        synchronized (enemies) { enemies.clear(); }
        spawnEnemiesForLayout(currentLayout);
        Log.d("Batch", "Re-spawned floor " + cur + " enemies with fresh templates.");
    }

    /**
     * Checks whether an enemy has hit the player and applies damage with cooldown.
     * Triggers camera shake, vibration and HP/armour reduction.
     * @param now current time in milliseconds
     */
    private void checkPlayerHit(long now) {
        synchronized (enemies) {
            for (Enemy e : enemies) {
                if (e.isDead) continue;

                if (getDistance(player.x, player.y, e.x, e.y) < player.radius + e.getAttackRange()) {
                    if (now - lastPlayerHitTime > e.getAttackCooldown()) {
                        lastPlayerHitTime = now;
                        damagePlayer(e.getDamage());
                        if (camera != null) camera.startShake(250, 14f);

                        runOnUiThread(this::vibrateOnHit);

                        float dx = player.x - e.x;
                        float dy = player.y - e.y;
                        float dist = (float) Math.sqrt(dx * dx + dy * dy);
                        if (dist > 0) {
                            float oldPX = player.x;
                            float oldPY = player.y;
                            player.x += (dx / dist) * e.getWeapon().knockback * 80f;
                            player.y += (dy / dist) * e.getWeapon().knockback * 80f;
                            if (currentLayout != null && !inShopRoom) {
                                float[] resolved = currentLayout.resolveWallCollision(
                                        oldPX, oldPY, player.x, player.y, player.radius);
                                player.x = resolved[0];
                                player.y = resolved[1];
                            }
                        }
                    }
                }
            }
        }
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

            if (playerHealth <= 0) {
                isGamePaused = true;
                player.startDeathAnimation();
                statsManager.onPlayerDied();
                statsManager.flush();
                saveGameToFirestore();
                new android.os.Handler().postDelayed(
                        this::showGameOverDialog, player.getDeathDuration());
            }
        });
    }

    private void showArmorBreakPopup(String slotName) {
        android.view.ViewGroup root = (android.view.ViewGroup) findViewById(R.id.game_root);
        if (root == null) return;

        TextView popup = new TextView(this);
        popup.setText("⚠ " + slotName + " ARMOR BROKEN");
        popup.setTextColor(Color.rgb(255, 80, 80));
        popup.setTextSize(13f);
        popup.setTypeface(null, android.graphics.Typeface.BOLD);
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
        currentLayout = FloorLayout.generate(
                floorManager.getCurrentFloor(),
                floorManager.isBossFloor(),
                floorManager.getKillsRequired());
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

        runOnUiThread(() -> {
            playerHealthBar.setProgress(playerHealth);
            playerArmorBar.setMax(Math.max(1, maxArmor));
            playerArmorBar.setProgress(playerArmor);
            if (txtHealthValue != null) txtHealthValue.setText(playerHealth + " / 100");
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
                    floorManager.onEnemyKilled(player.x, player.y);
                    statsManager.onEnemyKilled();
                    if (!e.noGoldDrop)
                    {
                        spawnGoldCoins(e.x, e.y, e.getGoldDrop());
                    }
                }
            }
            enemies.removeIf(e -> e.readyToRemove);
        }

        if (floorManager.isPortalOpen() && !enemiesKilledOnPortalOpen) {
            enemiesKilledOnPortalOpen = true;
            killAllActiveEnemies();
        }

        runOnUiThread(this::updateFloorHUD);
    }

    /**
     * Animates the HP bar from its current value to a new value.
     * @param from old HP value
     * @param to new HP value
     */
    private void animateHealthBar(int from, int to) {
        ObjectAnimator anim = ObjectAnimator.ofInt(playerHealthBar, "progress", from, to);
        anim.setDuration(100).start();
        if (txtHealthValue != null) txtHealthValue.setText(to + " / 100");
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
     * Triggers a dash in the current joystick direction if the cooldown has expired.
     */
    private void dash() {
        long now = System.currentTimeMillis();
        if (isDashing) return;
        if (now - lastDashTime < DASH_COOLDOWN) return;

        float joyX = joystick.getDX();
        float joyY = joystick.getDY();
        if (joyX == 0 && joyY == 0) return;

        float length = (float) Math.sqrt(joyX * joyX + joyY * joyY);
        dashDX = joyX / length;
        dashDY = joyY / length;

        isDashing = true;
        dashStartTime = now;
        lastDashTime = now;
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
            isTransitioning = true;
            isGamePaused = true;
            soundManager.playPortalEnter();
            runOnUiThread(this::triggerFloorTransition);
        }
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
                w.damage,
                w.knockback,
                w.name.equals("Staff") ? 18f : 12f,
                w.color
        );
        projectiles.add(p);
        soundManager.playFireProjectile();
    }

    /**
     * Updates all active projectiles and removes dead ones.
     */
    private void updateProjectiles() {
        synchronized (projectiles) {
            for (Projectile p : projectiles) {
                p.update();
            }
            projectiles.removeIf(p -> p.isDead);
        }
    }

    /**
     * Checks projectile collisions with enemies.
     * A projectile stops on its first hit and applies damage and knockback.
     */
    private void checkProjectileCollisions() {
        synchronized (projectiles) {
            for (Projectile p : projectiles) {
                if (p.isDead) continue;

                synchronized (enemies) {
                    for (Enemy e : enemies) {
                        if (e.isDead) continue;

                        float dist = getDistance(p.x, p.y, e.x, e.y);
                        if (dist < p.getRadius() + e.radius) {
                            e.takeDamage(p.damage);
                            soundManager.playHitEnemy();
                            behaviorTracker.recordAttack(getDistance(player.x, player.y, e.x, e.y));
                            statsManager.onDamageGiven(p.damage);

                            float dx = e.x - p.x;
                            float dy = e.y - p.y;
                            float d = (float) Math.sqrt(dx * dx + dy * dy);
                            if (d > 0) {
                                e.knockbackVX = (dx / d) * p.knockback * 25f;
                                e.knockbackVY = (dy / d) * p.knockback * 25f;
                            }

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
                                                    txtFloorTitle.setTextColor(Color.WHITE);
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
                dbManager.saveGeneratedWeapon(
                        userId != null ? userId : "unknown",
                        item.name, item.damage, item.range, item.cooldown,
                        item.rarity, item.color, item.knockback, item.isProjectile,
                        item.description,
                        weaponId -> {
                            if (weaponId != null) {
                                inventoryWeaponIds.add(weaponId);
                                weaponFirestoreIds.put(weapon, weaponId);
                            }
                            saveGameToFirestore();
                        }
                );
                break;

            case ARMOR:
                Armor armor = item.toArmor();
                inventoryArmors.add(armor);
                dbManager.saveGeneratedArmor(
                        userId != null ? userId : "unknown",
                        item.name, item.defense, item.maxDurability,
                        item.rarity, item.color, item.armorSlot,
                        item.description,
                        armorId -> {
                            if (armorId != null) {
                                inventoryArmorIds.add(armorId);
                                armorFirestoreIds.put(armor, armorId);
                            }
                            saveGameToFirestore();
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
                saveGameToFirestore();
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
        LinearLayout row0 = dialog.findViewById(R.id.shop_row_0);
        LinearLayout row1 = dialog.findViewById(R.id.shop_row_1);
        LinearLayout row2 = dialog.findViewById(R.id.shop_row_2);
        row0.removeAllViews();
        row1.removeAllViews();
        row2.removeAllViews();

        LinearLayout[] rows = {row0, row1, row2};

        for (int i = 0; i < Math.min(shopItems.size(), 6); i++) {
            ShopItem item = shopItems.get(i);
            final int index = i;

            android.view.View card = getLayoutInflater()
                    .inflate(R.layout.dialog_shop_item, rows[i / 2], false);

            TextView nameView  = card.findViewById(R.id.item_name);
            TextView typeView  = card.findViewById(R.id.item_type_badge);
            TextView priceView = card.findViewById(R.id.item_price);

            int rarityColor = getRarityColor(item.rarity);
            android.graphics.drawable.GradientDrawable border =
                    new android.graphics.drawable.GradientDrawable();
            border.setColor(Color.parseColor("#1A1A1A"));
            border.setCornerRadius(12f);
            border.setStroke(3, rarityColor);
            card.setBackground(border);

            nameView.setText(item.name);
            nameView.setTextColor(rarityColor);
            typeView.setText(item.type.name());
            priceView.setText("🪙 " + item.price);

            if (shopBoughtIndices.contains(index)) {
                // Already purchased — dim and disable the card
                card.setAlpha(0.3f);
                priceView.setText("SOLD");
            } else if (playerGold < item.price) {
                card.setAlpha(0.5f);
                card.setOnClickListener(v ->
                        showItemDetailDialog(dialog, item, card, goldDisplay, shopBoughtIndices, index));
            } else {
                card.setOnClickListener(v ->
                        showItemDetailDialog(dialog, item, card, goldDisplay, shopBoughtIndices, index));
            }

            rows[i / 2].addView(card);
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
        TextView statsView    = detail.findViewById(R.id.detail_stats);
        TextView priceView    = detail.findViewById(R.id.detail_price);
        TextView closeBtn     = detail.findViewById(R.id.btn_close_detail);
        MaterialButton buyBtn = detail.findViewById(R.id.btn_buy_detail);

        int rarityColor = getRarityColor(item.rarity);
        nameView.setText(item.name);
        nameView.setTextColor(rarityColor);
        typeView.setText(item.type.name());
        rarityView.setText(item.rarity.toUpperCase());
        rarityView.setTextColor(rarityColor);
        descView.setText(item.description);
        priceView.setText("🪙 " + item.price);
        statsView.setText(buildDetailStats(item));

        boolean canAfford = playerGold >= item.price;
        buyBtn.setAlpha(canAfford ? 1f : 0.4f);
        buyBtn.setEnabled(canAfford);

        closeBtn.setOnClickListener(v -> detail.dismiss());

        buyBtn.setOnClickListener(v -> {
            if (playerGold < item.price) return;

            playerGold -= item.price;
            statsManager.onCoinsSpent(item.price);
            goldDisplay.setText("🪙 " + playerGold);
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
        isGamePaused = true;

        final android.app.Dialog dialog = new android.app.Dialog(
                this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_shop);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView goldDisplay       = dialog.findViewById(R.id.shop_gold_display);
        android.view.View loading  = dialog.findViewById(R.id.shop_loading);
        android.view.View container = dialog.findViewById(R.id.shop_items_container);
        MaterialButton btnClose    = dialog.findViewById(R.id.btn_close_shop);

        goldDisplay.setText("🪙 " + playerGold);

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
            isTransitioning = true;
            runOnUiThread(this::exitShopRoom);
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
        if (rarity == null) return Color.parseColor("#888888");
        switch (rarity.toUpperCase()) {
            case "RARE":      return Color.parseColor("#3399FF");
            case "EPIC":      return Color.parseColor("#AA44FF");
            case "LEGENDARY": return Color.parseColor("#FFD700");
            default:          return Color.parseColor("#888888");
        }
    }

    private String buildDetailStats(ShopItem item) {
        switch (item.type) {
            case WEAPON:
                return "⚔  Damage      " + item.damage + "\n" +
                        "📏  Range         " + (int) item.range + "\n" +
                        "⏱  Cooldown    " + item.cooldown + "ms\n" +
                        "💫  Knockback  " + item.knockback + "\n" +
                        "🎯  Attack        " + (item.isProjectile ? "Projectile" : "Melee");
            case ARMOR:
                return "🛡  Defense      +" + item.defense + "\n" +
                        "❤  Durability    " + (int) item.maxDurability + "\n" +
                        "📦  Slot            " + item.armorSlot;
            case CONSUMABLE:
                return ("heal".equals(item.effect) ? "❤  Restores    +" : "🛡  Restores    +")
                        + item.effectValue
                        + ("heal".equals(item.effect) ? " HP" : " Armor");
            default: return "";
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
        statsManager.onFloorCleared(floorManager.getCurrentFloor());
        statsManager.flush();
        setCheckpoint(player.x, player.y, floorManager.getCurrentFloor());

        activeBoss = null;
        bossSpawnHandled = false;

        synchronized (enemies)       { enemies.clear(); }
        synchronized (goldCoins)     { goldCoins.clear(); }
        synchronized (treasureChests){ treasureChests.clear(); }

        enemiesKilledOnPortalOpen = false;
        enteringShop = false;
        shopBoughtIndices.clear(); // New floor → new shop stock, reset purchase state

        // Generate a fresh procedural layout for the new floor
        currentLayout = FloorLayout.generate(
                floorManager.getCurrentFloor(),
                floorManager.isBossFloor(),
                floorManager.getKillsRequired());
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
        Log.d("FloorLayout", "Floor " + floorManager.getCurrentFloor()
                + " layout: " + currentLayout.rooms.size() + " rooms, "
                + currentLayout.corridors.size() + " corridors");

        updateFloorHUD();
        saveGameToFirestore();
    }

    /**
     * Adds a weapon to the player's inventory and saves to Firestore.
     * @param weapon the weapon to add
     */
    public void addWeaponToInventory(Weapon weapon) {
        weapons.add(weapon);
        saveGameToFirestore();
    }

    /**
     * Refreshes the floor number and kill-progress HUD display.
     */
    private void updateFloorHUD() {
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

        // Pre-fire next batch while boss fight plays out
        int nextRangeStart = floorManager.getCurrentFloor() + 1;
        int nextRangeEnd   = floorManager.getCurrentFloor() + 5;
        generateBatch(nextRangeStart, nextRangeEnd);

        spawnBoss();
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
        activeBoss.setBehaviorTracker(behaviorTracker);

        // Apply boss AI name
        if (currentBossName != null) {
            activeBoss.displayName  = currentBossName;
            activeBoss.displayColor = currentBossColor;
        }

        enemies.add(activeBoss);

        // Build announcement text
        final String announceName  = currentBossName  != null ? currentBossName.toUpperCase()  : "⚠ BOSS ⚠";
        final String announceTitle = currentBossTitle != null ? currentBossTitle : "";
        final int    announceColor = currentBossColor;

        runOnUiThread(() -> {
            txtFloorTitle.setText(announceName + (announceTitle.isEmpty()
                    ? "" : "\n" + announceTitle));
            soundManager.playBossSpawn();
            txtFloorTitle.setTextColor(announceColor);
            txtFloorTitle.setAlpha(0f);
            floorTransitionOverlay.setVisibility(android.view.View.VISIBLE);
            floorTransitionOverlay.setAlpha(0f);
            floorTransitionOverlay.setClickable(false);
            floorTransitionOverlay.animate().alpha(0.6f).setDuration(300)
                    .withEndAction(() ->
                            txtFloorTitle.animate().alpha(1f).setDuration(300)
                                    .withEndAction(() ->
                                            new android.os.Handler().postDelayed(() ->
                                                            txtFloorTitle.animate().alpha(0f).setDuration(300)
                                                                    .withEndAction(() -> {
                                                                        floorTransitionOverlay.animate()
                                                                                .alpha(0f).setDuration(300)
                                                                                .withEndAction(() -> {
                                                                                    floorTransitionOverlay.setVisibility(
                                                                                            android.view.View.GONE);
                                                                                    txtFloorTitle.setTextColor(
                                                                                            android.graphics.Color.WHITE);
                                                                                }).start();
                                                                    }).start()
                                                    , 1800)
                                    ).start()
                    ).start();
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
        isGamePaused = true;

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
            saveGameToFirestore();
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

            int[] ids = {R.id.filter_all, R.id.filter_weapons,
                    R.id.filter_armor, R.id.filter_consumables};
            String[] filters = {"ALL", "WEAPONS", "ARMOR", "CONSUMABLES"};

            for (int i = 0; i < ids.length; i++) {
                TextView t = dialog.findViewById(ids[i]);
                boolean sel = filters[i].equals(filter);
                t.setTextColor(sel ? Color.parseColor("#FFD700") : Color.parseColor("#666666"));
                t.setTypeface(null, sel ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL);
                if (sel) {
                    android.graphics.drawable.GradientDrawable bg =
                            new android.graphics.drawable.GradientDrawable();
                    bg.setColor(Color.parseColor("#1A1A1A"));
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
            view.setText("—");
            view.setTextColor(Color.parseColor("#444444"));
        }
    }

    private void populateInventoryList(Dialog dialog) {
        LinearLayout container = dialog.findViewById(R.id.inventory_list_container);
        if (container == null) return;
        container.removeAllViews();

        String f = currentInventoryFilter;

        if (f.equals("ALL") || f.equals("WEAPONS")) {
            for (Weapon weapon : weapons) {
                addWeaponRow(container, dialog, weapon);
            }
        }

        if (f.equals("ALL") || f.equals("ARMOR")) {
            for (Armor armor : inventoryArmors) {
                addArmorRow(container, dialog, armor);
            }
        }

        if (f.equals("ALL") || f.equals("CONSUMABLES")) {
            for (ConsumableStack stack : consumableInventory) {
                addConsumableRow(container, dialog, stack);
            }
        }

        if (container.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Nothing here...");
            empty.setTextColor(Color.parseColor("#555555"));
            empty.setTextSize(14f);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, 48, 0, 48);
            container.addView(empty);
        }
    }

    private void showInventoryDialog() {
        isGamePaused = true;
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

        setupFilterTab(dialog, R.id.filter_all,          "ALL");
        setupFilterTab(dialog, R.id.filter_weapons,      "WEAPONS");
        setupFilterTab(dialog, R.id.filter_armor,        "ARMOR");
        setupFilterTab(dialog, R.id.filter_consumables,  "CONSUMABLES");

        updateEquippedDisplay(dialog);
        populateInventoryList(dialog);

        dialog.show();
        hideSystemUIOnWindow(dialog.getWindow());
    }

    private void addWeaponRow(LinearLayout container, Dialog dialog, Weapon weapon) {
        View row = getLayoutInflater().inflate(R.layout.dialog_inventory_item, container, false);

        TextView nameView    = row.findViewById(R.id.inv_item_name);
        TextView subView     = row.findViewById(R.id.inv_item_subtitle);
        TextView equippedBadge = row.findViewById(R.id.inv_equipped_badge);

        int rarityColor = getRarityColor(weapon.rarity.name());
        nameView.setText(weapon.name);
        nameView.setTextColor(rarityColor);
        subView.setText("WEAPON • " + weapon.rarity.name());

        boolean isEquipped = player.getWeapon() == weapon;
        equippedBadge.setVisibility(isEquipped ? View.VISIBLE : View.GONE);

        applyRarityBorder(row, rarityColor);
        row.setOnClickListener(v -> showWeaponActionDialog(dialog, weapon));
        container.addView(row);
    }

    private void addArmorRow(LinearLayout container, Dialog dialog, Armor armor) {
        View row = getLayoutInflater().inflate(R.layout.dialog_inventory_item, container, false);

        TextView nameView    = row.findViewById(R.id.inv_item_name);
        TextView subView     = row.findViewById(R.id.inv_item_subtitle);
        TextView equippedBadge = row.findViewById(R.id.inv_equipped_badge);

        int rarityColor = getRarityColor(armor.rarity.name());
        nameView.setText(armor.name);
        nameView.setTextColor(rarityColor);
        subView.setText(armor.slot.name() + " • " + armor.rarity.name());

        boolean isEquipped = equippedArmor.get(armor.slot) == armor;
        equippedBadge.setVisibility(isEquipped ? View.VISIBLE : View.GONE);

        applyRarityBorder(row, rarityColor);
        row.setOnClickListener(v -> showArmorActionDialog(dialog, armor));
        container.addView(row);
    }

    private void addConsumableRow(LinearLayout container, Dialog dialog, ConsumableStack stack) {
        View row = getLayoutInflater().inflate(R.layout.dialog_inventory_item, container, false);

        TextView nameView     = row.findViewById(R.id.inv_item_name);
        TextView subView      = row.findViewById(R.id.inv_item_subtitle);
        TextView quantityView = row.findViewById(R.id.inv_item_quantity);

        int rarityColor = getRarityColor(stack.item.rarity);
        nameView.setText(stack.item.name);
        nameView.setTextColor(rarityColor);
        subView.setText("CONSUMABLE • " + ("heal".equals(stack.item.effect) ? "Healing" : "Armor"));
        quantityView.setVisibility(View.VISIBLE);
        quantityView.setText("x" + stack.quantity);

        applyRarityBorder(row, rarityColor);
        row.setOnClickListener(v -> showConsumableActionDialog(dialog, stack));
        container.addView(row);
    }

    private void showWeaponActionDialog(Dialog parentDialog, Weapon weapon) {
        Dialog detail = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        detail.setContentView(R.layout.dialog_item_action);
        detail.setCancelable(false);
        if (detail.getWindow() != null)
            detail.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        bindActionDialog(detail, weapon.name, "WEAPON", weapon.rarity.name(),
                "", buildWeaponStats(weapon), weapon.color);

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
            saveGameToFirestore();
        });

        MaterialButton btnRemoveWeapon = detail.findViewById(R.id.btn_remove);
        boolean isFists = weapon == weapons.get(0);
        btnRemoveWeapon.setVisibility(isFists ? View.GONE : View.VISIBLE);
        btnRemoveWeapon.setOnClickListener(v -> {
            if (player.getWeapon() == weapon) {
                player.equipWeapon(weapons.get(0)); // Re-equip Fists
                refreshAttackUI();
            }
            weapons.remove(weapon);
            inventoryWeaponIds.remove(weaponFirestoreIds.remove(weapon));
            updateEquippedDisplay(parentDialog);
            populateInventoryList(parentDialog);
            saveGameToFirestore();
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
                "", buildArmorStats(armor), rarityColor);

        MaterialButton btn = detail.findViewById(R.id.btn_action);
        boolean isEquipped = equippedArmor.get(armor.slot) == armor;
        btn.setText(isEquipped ? "Equipped" : "Equip");
        btn.setEnabled(!isEquipped);
        btn.setAlpha(isEquipped ? 0.4f : 1f);

        btn.setOnClickListener(v -> {
            equipArmorPiece(armor, parentDialog);
            detail.dismiss();
        });

        MaterialButton btnRemoveArmor = detail.findViewById(R.id.btn_remove);
        btnRemoveArmor.setOnClickListener(v -> {
            // Unequip if currently equipped
            if (equippedArmor.get(armor.slot) == armor) {
                equippedArmor.remove(armor.slot);
                recalculateMaxArmor();
            }
            inventoryArmors.remove(armor);
            inventoryArmorIds.remove(armorFirestoreIds.remove(armor));
            updateEquippedDisplay(parentDialog);
            populateInventoryList(parentDialog);
            saveGameToFirestore();
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
                stack.item.description, buildConsumableStats(stack.item), rarityColor);

        MaterialButton btn = detail.findViewById(R.id.btn_action);
        btn.setText("Use  (x" + stack.quantity + ")");

        btn.setOnClickListener(v -> {
            useConsumable(stack, parentDialog);
            detail.dismiss();
        });

        MaterialButton btnRemoveConsumable = detail.findViewById(R.id.btn_remove);
        btnRemoveConsumable.setOnClickListener(v -> {
            consumableInventory.remove(stack);
            populateInventoryList(parentDialog);
            saveGameToFirestore();
            detail.dismiss();
        });

        detail.findViewById(R.id.btn_close_action).setOnClickListener(v -> detail.dismiss());
        detail.show();
        hideSystemUIOnWindow(detail.getWindow());
    }

    private void bindActionDialog(Dialog dialog, String name, String type, String rarity,
                                  String description, String stats, int nameColor) {
        int rarityColor = getRarityColor(rarity);

        ((TextView) dialog.findViewById(R.id.action_name)).setText(name);
        ((TextView) dialog.findViewById(R.id.action_name)).setTextColor(nameColor);
        ((TextView) dialog.findViewById(R.id.action_type)).setText(type);
        ((TextView) dialog.findViewById(R.id.action_rarity)).setText(rarity);
        ((TextView) dialog.findViewById(R.id.action_rarity)).setTextColor(rarityColor);
        ((TextView) dialog.findViewById(R.id.action_stats)).setText(stats);

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
        saveGameToFirestore();
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
        ShopItem item = stack.item;
        if ("heal".equals(item.effect)) {
            int newHp = Math.min(100, playerHealth + item.effectValue);
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
            if (inventoryDialog != null && inventoryDialog.isShowing()) {
                populateInventoryList(inventoryDialog);
            }
        });
        saveGameToFirestore();
    }

    private String buildWeaponStats(Weapon weapon) {
        return "⚔  Damage      " + weapon.damage + "\n" +
                "📏  Range         " + (int) weapon.range + "\n" +
                "⏱  Cooldown    " + weapon.cooldown + "ms\n" +
                "💫  Knockback  " + weapon.knockback + "\n" +
                "🎯  Attack        " + (weapon.isProjectile ? "Projectile" : "Melee");
    }

    private String buildArmorStats(Armor armor) {
        return "🛡  Defense      +" + armor.defense + "\n" +
                "❤  Durability    " + (int) armor.maxDurability + "\n" +
                "📦  Slot            " + armor.slot.name();
    }

    private String buildConsumableStats(ShopItem item) {
        return ("heal".equals(item.effect) ? "❤  Restores    +" : "🛡  Restores    +")
                + item.effectValue
                + ("heal".equals(item.effect) ? " HP" : " Armor");
    }

    private void applyRarityBorder(View view, int color) {
        android.graphics.drawable.GradientDrawable border =
                new android.graphics.drawable.GradientDrawable();
        border.setColor(Color.parseColor("#1A1A1A"));
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
        txtCheckpoint.setText("🏁  Last checkpoint: Floor " + checkpointFloor);

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
     * Saves the current checkpoint state (position, floor, HP, armour, gold).
     * @param x X position
     * @param y Y position
     * @param floor current floor number
     */
    private void setCheckpoint(float x, float y, int floor) {
        checkpointX      = x;
        checkpointY      = y;
        checkpointFloor  = floor;
        checkpointHealth = playerHealth;
        checkpointArmor  = playerArmor;
        checkpointGold   = playerGold;
    }

    /**
     * Refreshes the gold display in the HUD to the current amount.
     */
    private void updateGoldHUD() {
        runOnUiThread(() -> txtGold.setText("🪙 " + playerGold));
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
    }

    /**
     * Stops game threads, flushes statistics and releases sound resources.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        threadsRunning = false;
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
     * Called on floor transitions, item changes, death and when leaving to the menu.
     */
    private void saveGameToFirestore() {
        if (gameId == null || userId == null || pendingLoads > 0) return;

        long playtime = (System.currentTimeMillis() - gameStartTime) / 1000;
        // A dead player resumes from the checkpoint, exactly like pressing Respawn
        boolean dead = playerHealth <= 0;

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
        save.put("currentFloor",         dead ? checkpointFloor  : floorManager.getCurrentFloor());
        save.put("health",               dead ? checkpointHealth : playerHealth);
        save.put("armorValue",           dead ? checkpointArmor  : playerArmor);
        save.put("gold",                 dead ? checkpointGold   : playerGold);
        save.put("killsThisFloor",       dead ? 0 : floorManager.getKillsThisFloor());
        save.put("bossDefeated",         !dead && floorManager.isBossDefeated());
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
        save.put("checkpointFloor",      checkpointFloor);
        save.put("checkpointX",          checkpointX);
        save.put("checkpointY",          checkpointY);
        save.put("checkpointHealth",     checkpointHealth);
        save.put("checkpointArmor",      checkpointArmor);
        save.put("checkpointGold",       checkpointGold);
        save.put("playtimeSeconds",      playtime);
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
                int savedKills = data.get("killsThisFloor") != null
                        ? ((Number) data.get("killsThisFloor")).intValue() : 0;

                // Kick off a batch fetch for the loaded floor's range so that custom
                // enemy templates are available. applyBatchToCurrentFloorIfReady() will
                // automatically re-spawn enemies with templates once the fetch completes
                // (provided no kills have happened yet, which is always true on load).
                int loadedRangeStart = getRangeStart(savedFloor);
                if (loadedRangeStart >= 0 && !batchCache.containsKey(loadedRangeStart)) {
                    generateBatch(loadedRangeStart, loadedRangeStart + 4);
                }

                // Regenerate layout for the restored floor (floor 1 layout was built at
                // startup; for loaded games it may be wrong floor, and for new games the
                // layout is fine but enemies were already placed — clear and redo).
                synchronized (enemies) { enemies.clear(); }
                synchronized (goldCoins) { goldCoins.clear(); }
                currentLayout = FloorLayout.generate(
                        floorManager.getCurrentFloor(),
                        floorManager.isBossFloor(),
                        Math.max(0, floorManager.getKillsRequired() - savedKills));
                gameDisplay.setFloorLayout(currentLayout);
                currentLayout.entryRoom.explored = true;
                if (currentLayout.exitRoom != null) {
                    floorManager.setExitRoomCenter(
                            currentLayout.exitRoom.centerX(),
                            currentLayout.exitRoom.centerY());
                }
                floorManager.restoreProgress(savedKills, Boolean.TRUE.equals(data.get("bossDefeated")));
                spawnEnemiesForLayout(currentLayout);

                // Restore health / armor / gold
                playerHealth = data.get("health") != null
                        ? ((Number) data.get("health")).intValue() : 100;
                playerArmor = data.get("armorValue") != null
                        ? ((Number) data.get("armorValue")).intValue() : 0;
                playerGold = data.get("gold") != null
                        ? ((Number) data.get("gold")).intValue() : 0;

                // Restore checkpoint
                checkpointFloor = data.get("checkpointFloor") != null
                        ? ((Number) data.get("checkpointFloor")).intValue() : 1;
                checkpointX = data.get("checkpointX") != null
                        ? ((Number) data.get("checkpointX")).floatValue() : 0f;
                checkpointY = data.get("checkpointY") != null
                        ? ((Number) data.get("checkpointY")).floatValue() : 0f;
                checkpointHealth = data.get("checkpointHealth") != null
                        ? ((Number) data.get("checkpointHealth")).intValue() : playerHealth;
                checkpointArmor = data.get("checkpointArmor") != null
                        ? ((Number) data.get("checkpointArmor")).intValue() : playerArmor;
                checkpointGold = data.get("checkpointGold") != null
                        ? ((Number) data.get("checkpointGold")).intValue() : playerGold;

                if (data.get("playtimeSeconds") != null)
                    gameStartTime -= ((Number) data.get("playtimeSeconds")).longValue() * 1000L;

                // Always spawn at entry room — layout is regenerated so saved coords are stale
                player.x = currentLayout.entryRoom.centerX();
                player.y = currentLayout.entryRoom.centerY();

                // Update HUD bars immediately with restored values.
                // Armor max isn't known yet (armor pieces load async), so temporarily
                // set the bar max to the saved armor value so the bar renders correctly
                // until recalculateMaxArmor() fires once the armor docs arrive.
                playerHealthBar.setProgress(playerHealth);
                maxArmor = playerArmor; // temporary; recalculateMaxArmor() will correct this
                playerArmorBar.setMax(Math.max(1, maxArmor));
                playerArmorBar.setProgress(playerArmor);
                if (txtHealthValue != null) txtHealthValue.setText(playerHealth + " / 100");
                if (txtArmorValue  != null) txtArmorValue.setText(playerArmor + " / " + maxArmor);
                updateFloorHUD();
                updateGoldHUD();

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