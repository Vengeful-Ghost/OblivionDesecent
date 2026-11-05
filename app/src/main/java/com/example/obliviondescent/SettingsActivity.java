package com.example.obliviondescent;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Random;

/**
 * Settings screen.
 * Allows control over music and SFX volume, vibration, the attack direction
 * and a sign-out button. Can be opened from the main menu or the in-game pause menu.
 */
public class SettingsActivity extends AppCompatActivity {

    /* ============================================================
       SHARED PREFS KEYS
       ============================================================ */
    public static final String PREFS_NAME    = "gameSettings";
    public static final String KEY_VIBRATION = "vibration_enabled";
    public static final String KEY_ATTACK_FACING = "attack_facing"; // true: attack where the player faces
    // Fight effects, all on by default
    public static final String KEY_SHAKE = "screen_shake", KEY_NUMBERS = "damage_numbers", KEY_GORE = "blood_effects",
            KEY_DANGER = "low_health_effects", KEY_LEAN = "camera_lean";

    // True when this screen was opened from the in-game pause menu — music must NOT play
    public static final String EXTRA_FROM_GAME = "from_game";
    private boolean fromGame = false;

    /* ============================================================
       UI
       ============================================================ */
    private SwitchCompat switchVibration;
    private TextView txtMiuiWarning;
    private TextView attackAim, attackFacing;
    private TextView[] tabs;
    private View[] panes;

    /**
     * Initialises the settings screen, loads saved preferences and sets up sliders.
     * @param savedInstanceState previously saved instance state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        hideSystemUI();
        Wear.apply(findViewById(android.R.id.content));

        // Keep buttons clear of the camera cutout
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            v.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom);
            return insets;
        });

        fromGame = getIntent().getBooleanExtra(EXTRA_FROM_GAME, false);

        switchVibration = findViewById(R.id.switch_vibration);
        txtMiuiWarning  = findViewById(R.id.txt_miui_warning);
        MaterialButton btnBack = findViewById(R.id.btn_back);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean vibrationEnabled = prefs.getBoolean(KEY_VIBRATION, true);

        switchVibration.setChecked(vibrationEnabled);
        txtMiuiWarning.setVisibility(vibrationEnabled ? View.VISIBLE : View.GONE);

        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_VIBRATION, isChecked).apply();
            txtMiuiWarning.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        attackAim    = findViewById(R.id.attack_mode_aim);
        attackFacing = findViewById(R.id.attack_mode_facing);
        showAttackMode(prefs.getBoolean(KEY_ATTACK_FACING, false));
        attackAim.setOnClickListener(v -> {
            prefs.edit().putBoolean(KEY_ATTACK_FACING, false).apply();
            showAttackMode(false);
        });
        attackFacing.setOnClickListener(v -> {
            prefs.edit().putBoolean(KEY_ATTACK_FACING, true).apply();
            showAttackMode(true);
        });

        btnBack.setOnClickListener(v -> finish());

        // One section at a time, picked from the tabs on the left
        tabs = new TextView[]{findViewById(R.id.tab_audio), findViewById(R.id.tab_controls),
                findViewById(R.id.tab_effects), findViewById(R.id.tab_account)};
        panes = new View[]{findViewById(R.id.pane_audio), findViewById(R.id.pane_controls),
                findViewById(R.id.pane_effects), findViewById(R.id.pane_account)};
        for (int i = 0; i < tabs.length; i++) {
            int index = i;
            tabs[i].setOnClickListener(v -> showTab(index));
        }
        showTab(0);

        bindSwitch(R.id.switch_shake, KEY_SHAKE, prefs);
        bindSwitch(R.id.switch_numbers, KEY_NUMBERS, prefs);
        bindSwitch(R.id.switch_gore, KEY_GORE, prefs);
        bindSwitch(R.id.switch_danger, KEY_DANGER, prefs);
        bindSwitch(R.id.switch_lean, KEY_LEAN, prefs);

        View btnPrivacy = findViewById(R.id.btn_privacy_options);
        btnPrivacy.setVisibility(Ads.privacyOptionsRequired(this) ? View.VISIBLE : View.GONE);
        btnPrivacy.setOnClickListener(v -> Ads.showPrivacyOptions(this));

        MaterialButton btnSignOut = findViewById(R.id.btn_sign_out);
        btnSignOut.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this, R.style.AlertDialogDark)
                    .setTitle("Sign Out")
                    .setMessage("Are you sure you want to sign out?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        SoundManager.stopMenuMusic(); // silence before leaving to login screen
                        FirebaseAuth.getInstance().signOut();
                        getSharedPreferences("loginPrefs", MODE_PRIVATE)
                                .edit().clear().apply();
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                    .show();
        });

        SeekBar sfxSlider = findViewById(R.id.seekbar_sfx_volume);
        int savedProgress = (int)(prefs.getFloat("sfx_volume", 1.0f) * 100);
        sfxSlider.setProgress(savedProgress);

        sfxSlider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar,
                                          int progress, boolean fromUser) {
                float vol = progress / 100f;
                prefs.edit().putFloat("sfx_volume", vol).apply();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) {}
        });

        SeekBar menuMusicSlider = findViewById(R.id.seekbar_menu_music_volume);
        int savedMenuProgress = (int)(prefs.getFloat("menu_music_volume", 0.85f) * 100);
        menuMusicSlider.setProgress(savedMenuProgress);
        menuMusicSlider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar,
                                          int progress, boolean fromUser) {
                float vol = progress / 100f;
                prefs.edit().putFloat("menu_music_volume", vol).apply();
                SoundManager.setMenuMusicVolume(vol);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) {}
        });

        SeekBar gameMusicSlider = findViewById(R.id.seekbar_game_music_volume);
        int savedGameProgress = (int)(prefs.getFloat("game_music_volume", 0.85f) * 100);
        gameMusicSlider.setProgress(savedGameProgress);
        gameMusicSlider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar,
                                          int progress, boolean fromUser) {
                float vol = progress / 100f;
                prefs.edit().putFloat("game_music_volume", vol).apply();
                SoundManager.setGameMusicVolume(vol);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) {}
        });

        startBouncingParticles();
    }

    /** Highlights the chosen attack direction, like the tabs on the stats screen. */
    private void showAttackMode(boolean facing) {
        attackAim.setTextColor(getColor(facing ? R.color.bone_muted : R.color.gold_bright));
        attackFacing.setTextColor(getColor(facing ? R.color.gold_bright : R.color.bone_muted));
    }

    /** Shows one section and lights its tab. */
    private void showTab(int index) {
        for (int i = 0; i < tabs.length; i++) {
            panes[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
            tabs[i].setTextColor(getColor(i == index ? R.color.gold_bright : R.color.bone_muted));
        }
    }

    /** A switch saved straight to a setting that is on by default. */
    private void bindSwitch(int id, String key, SharedPreferences prefs) {
        SwitchCompat toggle = findViewById(id);
        toggle.setChecked(prefs.getBoolean(key, true));
        toggle.setOnCheckedChangeListener((button, on) -> prefs.edit().putBoolean(key, on).apply());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Only play music when opened from the main menu, not from the in-game pause menu
        if (!fromGame) SoundManager.registerMenuMusic(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!fromGame) SoundManager.unregisterMenuMusic();
    }

    /* ============================================================
       PARTICLE ANIMATION
       ============================================================ */
    private void startBouncingParticles() {
        int[] particleIds = {
                R.id.particle1,  R.id.particle2,  R.id.particle3,  R.id.particle4,  R.id.particle5,
                R.id.particle6,  R.id.particle7,  R.id.particle8,  R.id.particle9,  R.id.particle10,
                R.id.particle11, R.id.particle12, R.id.particle13, R.id.particle14, R.id.particle15,
                R.id.particle16, R.id.particle17, R.id.particle18, R.id.particle19, R.id.particle20,
                R.id.particle21, R.id.particle22, R.id.particle23, R.id.particle24, R.id.particle25,
                R.id.particle26, R.id.particle27, R.id.particle28, R.id.particle29, R.id.particle30
        };

        for (int id : particleIds) {
            ImageView particle = findViewById(id);
            if (particle == null) continue;
            particle.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            particle.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            startSingleBouncingParticle(particle);
                        }
                    }
            );
        }
    }

    private void startSingleBouncingParticle(ImageView particle) {
        Handler handler = new Handler(Looper.getMainLooper());
        Random random = new Random();

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int screenWidth  = size.x;
        int screenHeight = size.y;

        final float[] dx = { 1 + random.nextFloat() * 3 };
        final float[] dy = { 1 + random.nextFloat() * 3 };
        if (random.nextBoolean()) dx[0] = -dx[0];
        if (random.nextBoolean()) dy[0] = -dy[0];

        Runnable runnable = new Runnable() {
            float x = particle.getX();
            float y = particle.getY();

            @Override
            public void run() {
                x += dx[0];
                y += dy[0];
                if (x <= 0 || x + particle.getWidth()  >= screenWidth)  dx[0] = -dx[0];
                if (y <= 0 || y + particle.getHeight() >= screenHeight) dy[0] = -dy[0];
                particle.setX(x);
                particle.setY(y);
                handler.postDelayed(this, 16);
            }
        };
        handler.post(runnable);
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }
}