package com.example.obliviondescent;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.util.Log;

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

    /** Increments the reference count and starts/resumes the in-game background music. */
    public static synchronized void registerGameMusic(Context context) {
        gameRefCount++;
        if (gamePlayer == null) {
            gamePlayer = MediaPlayer.create(
                    context.getApplicationContext(), R.raw.game_music);
            if (gamePlayer != null) {
                gamePlayer.setLooping(true);
                gamePlayer.setVolume(gameMusicVolume, gameMusicVolume);
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
        if (gamePlayer != null) {
            try { if (gamePlayer.isPlaying()) gamePlayer.stop(); }
            catch (Exception ignored) {}
            gamePlayer.release();
            gamePlayer = null;
        }
    }

    /** Updates the game music volume immediately (0.0 – 1.0). */
    public static synchronized void setGameMusicVolume(float vol) {
        gameMusicVolume = Math.max(0f, Math.min(1f, vol));
        if (gamePlayer != null) {
            try { gamePlayer.setVolume(gameMusicVolume, gameMusicVolume); }
            catch (Exception ignored) {}
        }
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

    // Track long sounds to avoid overlap
    private int portalEnterStream = -1;
    private int shopEnterStream   = -1;
    private int bossSpawnStream   = -1;

    private long lastHitEnemyTime = 0;
    private static final long HIT_ENEMY_COOLDOWN_MS = 100;

    public SoundManager(Context context) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(8)
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

    // ============================================================
    // INTERNAL
    // ============================================================

    private void play(int soundId, float relativeVolume) {
        if (!ready || soundId == 0) return;
        float v = volume * relativeVolume;
        soundPool.play(soundId, v, v, 0, 0, 1.0f);
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        ready = false;
    }
}