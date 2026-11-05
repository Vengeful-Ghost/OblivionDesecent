package com.example.obliviondescent;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.util.Log;

/**
 * Manages all sound effects and background music in the game.
 * Menu music and game music are driven by a reference-count mechanism so that
 * navigating between screens never causes audible gaps.
 * Sound effects are handled via SoundPool for concurrent playback.
 */
public class SoundManager {

    private static final String TAG = "SoundManager";

    // ============================================================
    // MENU MUSIC  (static — shared across all activities)
    // ============================================================
    // Uses a reference-counter so that navigating between menu screens
    // (MenuActivity → StatsActivity → back, etc.) never causes an audible
    // gap: music keeps playing as long as at least one menu screen is visible.
    //
    // Usage pattern in each menu-related activity:
    //   onStart()  → SoundManager.registerMenuMusic(this);
    //   onStop()   → SoundManager.unregisterMenuMusic();
    //
    // Before launching GameActivity or signing out call:
    //   SoundManager.stopMenuMusic();
    // ============================================================

    private static MediaPlayer menuPlayer   = null;
    private static int         menuRefCount = 0;
    private static float       menuMusicVolume = 0.85f;

    /**
     * Increments the reference count and starts/resumes the menu music.
     * Safe to call from multiple activities concurrently — only one
     * MediaPlayer is ever alive at a time.
     */
    public static synchronized void registerMenuMusic(Context context) {
        menuRefCount++;
        if (menuPlayer == null) {
            menuMusicVolume = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
                    .getFloat("menu_music_volume", menuMusicVolume);
            menuPlayer = MediaPlayer.create(
                    context.getApplicationContext(), R.raw.menu_music);
            if (menuPlayer != null) {
                menuPlayer.setLooping(true);
                menuPlayer.setVolume(menuMusicVolume, menuMusicVolume);
            }
        }
        if (menuPlayer != null && !menuPlayer.isPlaying()) {
            try { menuPlayer.start(); } catch (Exception ignored) {}
        }
    }

    /**
     * Decrements the reference count.
     * Pauses playback only when the last menu activity leaves the foreground
     * (i.e. the user has backgrounded the app — not just navigated to another screen).
     */
    public static synchronized void unregisterMenuMusic() {
        menuRefCount = Math.max(0, menuRefCount - 1);
        if (menuRefCount == 0 && menuPlayer != null && menuPlayer.isPlaying()) {
            menuPlayer.pause();
        }
    }

    /**
     * Fully stops and releases the menu music player.
     * Call this before launching GameActivity (New Game / Load Game)
     * or when the user signs out so music doesn't bleed into other screens.
     * The next call to registerMenuMusic() will create a fresh player.
     */
    public static synchronized void stopMenuMusic() {
        menuRefCount = 0;
        if (menuPlayer != null) {
            try { if (menuPlayer.isPlaying()) menuPlayer.stop(); }
            catch (Exception ignored) {}
            menuPlayer.release();
            menuPlayer = null;
        }
    }

    /** Updates the menu music volume immediately (0.0 – 1.0). */
    public static synchronized void setMenuMusicVolume(float vol) {
        menuMusicVolume = Math.max(0f, Math.min(1f, vol));
        if (menuPlayer != null) {
            try { menuPlayer.setVolume(menuMusicVolume, menuMusicVolume); }
            catch (Exception ignored) {}
        }
    }

    // ============================================================
    // GAME MUSIC  (static — shared across GameActivity lifecycle)
    // ============================================================
    // Same reference-count pattern as menu music.
    // Usage in GameActivity:
    //   onStart()  → SoundManager.registerGameMusic(this);
    //   onStop()   → SoundManager.unregisterGameMusic();
    // Before returning to main menu call:
    //   SoundManager.stopGameMusic();
    // ============================================================

    private static MediaPlayer gamePlayer   = null;
    private static int         gameRefCount = 0;
    private static float       gameMusicVolume = 0.85f;
    private static int         gameTrack = R.raw.game_music; // dungeon, boss or game-over music
    // Dynamic music: 0 calm .. 1 full fight; the track swells and quickens with it
    private static float       gameIntensity = 0f;
    private static int         volumeStep = -1, speedStep = -1;

    /** Increments the reference count and starts/resumes the in-game background music. */
    public static synchronized void registerGameMusic(Context context) {
        gameRefCount++;
        if (gamePlayer == null) {
            gamePlayer = MediaPlayer.create(
                    context.getApplicationContext(), gameTrack);
            if (gamePlayer != null) {
                gamePlayer.setLooping(true);
                gamePlayer.setVolume(gameVolume(), gameVolume());
                speedStep = -1; // a fresh player plays at normal speed
            }
        }
        if (gamePlayer != null && !gamePlayer.isPlaying()) {
            try { gamePlayer.start(); } catch (Exception ignored) {}
        }
    }

    /** Decrements the reference count; pauses only when the last game screen leaves the foreground. */
    public static synchronized void unregisterGameMusic() {
        gameRefCount = Math.max(0, gameRefCount - 1);
        if (gameRefCount == 0 && gamePlayer != null && gamePlayer.isPlaying()) {
            gamePlayer.pause();
        }
    }

    /**
     * Fully stops and releases the game music player.
     * Call this when returning to the main menu.
     * The next call to registerGameMusic() will create a fresh player.
     */
    public static synchronized void stopGameMusic() {
        gameRefCount = 0;
        gameTrack = R.raw.game_music;
        if (gamePlayer != null) {
            try { if (gamePlayer.isPlaying()) gamePlayer.stop(); }
            catch (Exception ignored) {}
            gamePlayer.release();
            gamePlayer = null;
        }
    }

    /** Switches the in-game music (dungeon, boss or game over); nothing happens if it is already playing. */
    public static synchronized void setGameTrack(Context context, int track) {
        if (track == gameTrack) return;
        gameTrack = track;
        if (gamePlayer == null) return;
        try { gamePlayer.stop(); } catch (Exception ignored) {}
        gamePlayer.release();
        gamePlayer = MediaPlayer.create(context.getApplicationContext(), track);
        if (gamePlayer == null) return;
        gamePlayer.setLooping(true);
        gamePlayer.setVolume(gameVolume(), gameVolume());
        speedStep = -1;
        if (gameRefCount > 0) {
            try { gamePlayer.start(); } catch (Exception ignored) {}
        }
    }

    /** Updates the game music volume immediately (0.0 – 1.0). */
    public static synchronized void setGameMusicVolume(float vol) {
        gameMusicVolume = Math.max(0f, Math.min(1f, vol));
        if (gamePlayer != null) {
            try { gamePlayer.setVolume(gameVolume(), gameVolume()); }
            catch (Exception ignored) {}
        }
    }

    // The volume setting, softened while things are calm
    private static float gameVolume() {
        return gameMusicVolume * (0.6f + 0.4f * gameIntensity);
    }

    /**
     * Eases the music toward a fight intensity (0 calm .. 1 boss); called every logic tick.
     * The player is only touched when the volume or speed visibly changes.
     */
    public static synchronized void setGameIntensity(float target) {
        gameIntensity += (target - gameIntensity) * 0.02f;
        if (gamePlayer == null) return;
        try {
            int volume = Math.round(gameIntensity * 40);
            if (volume != volumeStep) {
                volumeStep = volume;
                gamePlayer.setVolume(gameVolume(), gameVolume());
            }
            // Up to 6% faster in a fight; setting the speed on a paused player would start it
            int speed = Math.round(gameIntensity * 6);
            if (speed != speedStep && gamePlayer.isPlaying()) {
                speedStep = speed;
                gamePlayer.setPlaybackParams(gamePlayer.getPlaybackParams().setSpeed(1f + speed / 100f).setPitch(1f));
            }
        } catch (Exception ignored) {}
    }

    private SoundPool soundPool;
    private float volume = 1.0f;
    private boolean ready = false;

    // Sound IDs
    private int idDash;
    private int idFireProjectile;
    private int idHitEnemy;
    private int idHitPlayer;
    private int idPortalEnter;
    private int idShopEnter;
    private int idSwingMelee;
    private int idBossDeath;
    private int idBossSpawn;
    private int idChestOpen;
    private int idCoinPickup;

    /** The synthesised effects (tools/sfx/make-sfx.mjs), each with its volume and minimum gap between plays. */
    public enum Sfx {
        SPIN(R.raw.spin_attack, 1f, 0), PERFECT_DODGE(R.raw.perfect_dodge, 1f, 0),
        LEVEL_UP(R.raw.level_up, 0.9f, 0), PERK_PICK(R.raw.perk_pick, 0.9f, 0), CARD_FLIP(R.raw.card_flip, 0.7f, 0),
        KEY(R.raw.key_pickup, 0.9f, 0), VAULT_UNLOCK(R.raw.vault_unlock, 1f, 0), GATE_LOCKED(R.raw.gate_locked, 0.5f, 400),
        MIMIC(R.raw.mimic_awaken, 1f, 0), POTION(R.raw.potion_drink, 0.8f, 0), PURCHASE(R.raw.purchase, 0.9f, 0),
        SELL(R.raw.sell, 0.8f, 0), EQUIP(R.raw.equip, 0.8f, 0), CLICK(R.raw.ui_click, 0.5f, 40),
        CRIT(R.raw.crit_hit, 0.9f, 80), ENEMY_DEATH(R.raw.enemy_death, 0.7f, 60), STAGGER(R.raw.stagger, 0.7f, 100),
        ENEMY_SHOT(R.raw.enemy_shot, 0.35f, 90),
        ARMOR_BREAK(R.raw.armor_break, 1f, 0), PLAYER_DEATH(R.raw.player_death, 1f, 0), REVIVE(R.raw.revive, 1f, 0),
        SUMMON(R.raw.summon, 0.8f, 300), ENRAGE(R.raw.enrage, 1f, 0), BLINK(R.raw.blink, 0.8f, 100),
        THUD(R.raw.thud, 0.9f, 80), PORTAL_OPEN(R.raw.portal_open, 0.9f, 0), HEARTBEAT(R.raw.heartbeat, 0.8f, 300),
        BLOCK(R.raw.block, 0.8f, 60), GUARD_BREAK(R.raw.guard_break, 1f, 0), HEAVY_CHARGE(R.raw.heavy_charge, 0.6f, 0),
        HEAVY_SWING(R.raw.heavy_swing, 1f, 0), CRATE_SMASH(R.raw.crate_smash, 0.8f, 50);

        final int res;
        final float volume;
        final long gapMs;

        Sfx(int res, float volume, long gapMs) {
            this.res = res;
            this.volume = volume;
            this.gapMs = gapMs;
        }
    }

    private final int[] sfxIds = new int[Sfx.values().length];
    private final long[] sfxLast = new long[Sfx.values().length];

    // Track long sounds to avoid overlap
    private int portalEnterStream = -1;
    private int shopEnterStream   = -1;
    private int bossSpawnStream   = -1;

    private long lastHitEnemyTime = 0;
    private static final long HIT_ENEMY_COOLDOWN_MS = 100;

    /**
     * Creates a new sound manager and loads all sound effects into the SoundPool.
     * @param context application context used to load sound resources
     */
    public SoundManager(Context context) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(16)
                .setAudioAttributes(attrs)
                .build();

        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) {
                Log.d(TAG, "Sound loaded: " + sampleId);
            }
        });

        // Load all sounds
        idDash           = soundPool.load(context, R.raw.dash,            1);
        idFireProjectile = soundPool.load(context, R.raw.fire_projectile, 1);
        idHitEnemy       = soundPool.load(context, R.raw.hit_enemy,       1);
        idHitPlayer      = soundPool.load(context, R.raw.hit_player,      1);
        idPortalEnter    = soundPool.load(context, R.raw.portal_enter,    1);
        idShopEnter      = soundPool.load(context, R.raw.shop_enter,      1);
        idSwingMelee     = soundPool.load(context, R.raw.swing_melee,     1);
        idBossDeath      = soundPool.load(context, R.raw.boss_death,      1);
        idBossSpawn      = soundPool.load(context, R.raw.boss_spawn,      1);
        idChestOpen      = soundPool.load(context, R.raw.chest_open,      1);
        idCoinPickup     = soundPool.load(context, R.raw.coin_pickup,     1);
        for (Sfx s : Sfx.values()) sfxIds[s.ordinal()] = soundPool.load(context, s.res, 1);

        ready = true;
    }

    /** Volume: 0.0 to 1.0 */
    public void setVolume(float vol) {
        this.volume = Math.max(0f, Math.min(1f, vol));
    }

    public float getVolume() { return volume; }

    // ============================================================
    // PLAY METHODS
    // ============================================================

    /** Called the instant a melee attack is triggered */
    public void playSwingMelee() {
        play(idSwingMelee, 1.0f);
    }

    /** Called when melee damage actually lands on an enemy */
    public void playHitEnemy() {
        long now = System.currentTimeMillis();
        if (now - lastHitEnemyTime < HIT_ENEMY_COOLDOWN_MS) return;
        lastHitEnemyTime = now;
        play(idHitEnemy, 1.0f);
    }

    /** Called when a projectile is fired */
    public void playFireProjectile() {
        play(idFireProjectile, 0.85f);
    }

    /** Called when the player takes damage */
    public void playHitPlayer() {
        play(idHitPlayer, 1.0f);
    }

    /** Called when the dash starts */
    public void playDash() {
        play(idDash, 0.9f);
    }

    /** Called when player touches the purple portal — stop any existing */
    public void playPortalEnter() {
        if (portalEnterStream != -1) soundPool.stop(portalEnterStream);
        portalEnterStream = soundPool.play(idPortalEnter,
                volume, volume, 1, 0, 1.0f);
    }

    /** Called when player touches the yellow shop portal */
    public void playShopEnter() {
        if (shopEnterStream != -1) soundPool.stop(shopEnterStream);
        shopEnterStream = soundPool.play(idShopEnter,
                volume, volume, 1, 0, 1.0f);
    }

    /** Called when boss announcement overlay appears */
    public void playBossSpawn() {
        if (bossSpawnStream != -1) soundPool.stop(bossSpawnStream);
        bossSpawnStream = soundPool.play(idBossSpawn,
                volume, volume, 1, 0, 1.0f);
    }

    /** Called when boss HP hits zero */
    public void playBossDeath() {
        // Stop boss spawn sound if still playing
        if (bossSpawnStream != -1) {
            soundPool.stop(bossSpawnStream);
            bossSpawnStream = -1;
        }
        play(idBossDeath, 1.0f);
    }

    /** Called the instant a chest is opened */
    public void playChestOpen() {
        play(idChestOpen, 1.0f);
    }

    /** Called each time a coin is collected */
    public void playCoinPickup() {
        play(idCoinPickup, 0.7f); // slightly quieter — plays frequently
    }

    /** An enemy's melee strike: the player's swing, lower and quieter. */
    public void playEnemySwing() {
        play(idSwingMelee, 0.5f, 0.75f);
    }

    /** Plays one synthesised effect, skipped if the same one played within its gap. */
    public void play(Sfx sfx) {
        long now = System.currentTimeMillis();
        if (sfx.gapMs > 0 && now - sfxLast[sfx.ordinal()] < sfx.gapMs) return;
        sfxLast[sfx.ordinal()] = now;
        play(sfxIds[sfx.ordinal()], sfx.volume);
    }

    // ============================================================
    // INTERNAL
    // ============================================================

    private void play(int soundId, float relativeVolume) {
        play(soundId, relativeVolume, 1.0f);
    }

    private void play(int soundId, float relativeVolume, float rate) {
        if (!ready || soundId == 0) return;
        float v = volume * relativeVolume;
        soundPool.play(soundId, v, v, 0, 0, rate);
    }

    /**
     * Releases all SoundPool resources. Must be called in onDestroy.
     */
    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        ready = false;
    }
}