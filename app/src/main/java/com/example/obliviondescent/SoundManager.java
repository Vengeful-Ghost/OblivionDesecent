package com.example.obliviondescent;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

public class SoundManager {

    private static final String TAG = "SoundManager";

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