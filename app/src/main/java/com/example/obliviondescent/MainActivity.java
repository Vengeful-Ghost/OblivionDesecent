package com.example.obliviondescent;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Random;

/**
 * Login screen of the application.
 * Handles credential validation against Firebase, auto-login
 * for users who checked "remember me", and a background particle animation.
 */
public class MainActivity extends AppCompatActivity {

    /* ============================================================
       UI ELEMENTS
       ============================================================ */

    private TextInputLayout usernameLayout, passwordLayout;
    private TextInputEditText Username, Password;
    private MaterialButton btnLogin;
    private TextView tvSignUp;
    private CheckBox checkRememberMe;
    private Dialog loadingDialog;

    /* ============================================================
       SHARED PREFERENCES KEYS
       ============================================================ */

    private static final String PREFS_NAME = "loginPrefs";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_REMEMBER = "remember";
    private static final String KEY_USER_ID = "user_id";

    /* ============================================================
       ACTIVITY LIFECYCLE
       ============================================================ */

    /**
     * Initialises the screen, checks for auto-login and sets up button listeners.
     * @param savedInstanceState previously saved instance state
     */
    @SuppressLint("WrongViewCast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        hideSystemUI();

        // Handle edge-to-edge padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.loginRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Find views
        usernameLayout = findViewById(R.id.usernameLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        Username = findViewById(R.id.Username);
        Password = findViewById(R.id.Password);
        btnLogin = findViewById(R.id.btnLogin);
        tvSignUp = findViewById(R.id.SignUp);
        checkRememberMe = findViewById(R.id.checkRememberMe);

        // Apply focus glow
        applyFocusGlow(usernameLayout, R.drawable.input_bg, R.drawable.input_bg_focused);
        applyFocusGlow(passwordLayout, R.drawable.input_bg, R.drawable.input_bg_focused);

        // Initialize DB
        DBManager db = new DBManager();

        // Load preferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean remember = prefs.getBoolean(KEY_REMEMBER, false);

        if (remember) {
            Intent intent = new Intent(MainActivity.this, MenuActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // Login button
        btnLogin.setOnClickListener(v -> {
            String user = Username.getText() != null ? Username.getText().toString().trim() : "";
            String pass = Password.getText() != null ? Password.getText().toString() : "";

            if (user.isEmpty()) {
                usernameLayout.setError("Enter a username");
                return;
            } else {
                usernameLayout.setError(null);
            }

            if (pass.length() < 8) {
                passwordLayout.setError("Password must be at least 8 characters");
                return;
            } else {
                passwordLayout.setError(null);
            }

            showLoading("Signing in...");
            db.checkUsername(user, exists -> {
                if (!exists) {
                    hideLoading();
                    Toast.makeText(MainActivity.this, "Username not found", Toast.LENGTH_SHORT).show();
                    return;
                }

                db.checkUserCredentials(user, pass, (isValid, userId) -> {
                    hideLoading();
                    if (isValid) {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean(KEY_REMEMBER, checkRememberMe.isChecked());
                        editor.putString(KEY_USERNAME, user);
                        editor.putString(KEY_USER_ID, userId);
                        editor.apply();

                        db.enforeSingleSlot(userId);

                        Intent intent = new Intent(MainActivity.this, MenuActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(MainActivity.this, "Incorrect password", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });

        // Sign up navigation
        tvSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SignUpActivity.class);
            startActivity(intent);
        });

        // Background animation
        startBouncingParticles();
    }

    /* ============================================================
       INPUT FIELD VISUAL EFFECTS
       ============================================================ */

    /**
     * Shows a loading dialog with a custom message.
     * @param message text to display in the dialog
     */
    private void showLoading(String message) {
        if (loadingDialog != null && loadingDialog.isShowing()) return;
        View view = getLayoutInflater().inflate(R.layout.dialog_loading, null);
        ((TextView) view.findViewById(R.id.txt_loading_message)).setText(message);
        loadingDialog = new Dialog(this, R.style.LoadingDialogStyle);
        loadingDialog.setContentView(view);
        loadingDialog.setCancelable(false);
        loadingDialog.show();
    }

    /**
     * Hides the loading dialog if it is currently showing.
     */
    private void hideLoading() {
        if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
        loadingDialog = null;
    }

    /**
     * Applies a glow effect to an input field when it gains focus.
     * @param layout the input layout to style
     * @param defaultBg background drawable resource for the unfocused state
     * @param focusedBg background drawable resource for the focused state
     */
    private void applyFocusGlow(final TextInputLayout layout, final int defaultBg, final int focusedBg) {

        layout.setOnFocusChangeListener((v, hasFocus) ->
                layout.setBackground(ContextCompat.getDrawable(
                        MainActivity.this,
                        hasFocus ? focusedBg : defaultBg
                ))
        );

        if (layout.getEditText() != null) {
            layout.getEditText().setOnFocusChangeListener((v, hasFocus) ->
                    layout.setBackground(ContextCompat.getDrawable(
                            MainActivity.this,
                            hasFocus ? focusedBg : defaultBg
                    ))
            );
        }
    }

    /* ============================================================
       PARTICLE BACKGROUND ANIMATION
       ============================================================ */

    /**
     * Starts the bouncing-particle background animation for all particle views on screen.
     */
    private void startBouncingParticles() {
        int[] particleIds = {
                R.id.particle1, R.id.particle2, R.id.particle3, R.id.particle4, R.id.particle5,
                R.id.particle6, R.id.particle7, R.id.particle8, R.id.particle9, R.id.particle10,
                R.id.particle11, R.id.particle12, R.id.particle13, R.id.particle14, R.id.particle15,
                R.id.particle16, R.id.particle17, R.id.particle18, R.id.particle19, R.id.particle20,
                R.id.particle21, R.id.particle22, R.id.particle23, R.id.particle24, R.id.particle25,
                R.id.particle26, R.id.particle27, R.id.particle28, R.id.particle29, R.id.particle30,
                R.id.particle31, R.id.particle32, R.id.particle33, R.id.particle34, R.id.particle35,
                R.id.particle36, R.id.particle37, R.id.particle38, R.id.particle39, R.id.particle40,
                R.id.particle41, R.id.particle42, R.id.particle43, R.id.particle44, R.id.particle45,
                R.id.particle46, R.id.particle47, R.id.particle48, R.id.particle49, R.id.particle50,
                R.id.particle51, R.id.particle52, R.id.particle53, R.id.particle54, R.id.particle55,
                R.id.particle56, R.id.particle57, R.id.particle58, R.id.particle59, R.id.particle60
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

    /**
     * Starts the bounce animation for a single particle, reflecting off screen edges.
     * @param particle the ImageView particle to animate
     */
    private void startSingleBouncingParticle(ImageView particle) {
        Handler handler = new Handler(Looper.getMainLooper());
        Random random = new Random();

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        int screenWidth = size.x;
        int screenHeight = size.y;

        final float[] dx = {1 + random.nextFloat() * 3};
        final float[] dy = {1 + random.nextFloat() * 3};

        if (random.nextBoolean()) dx[0] = -dx[0];
        if (random.nextBoolean()) dy[0] = -dy[0];

        Runnable runnable = new Runnable() {
            float x = particle.getX();
            float y = particle.getY();

            @Override
            public void run() {
                x += dx[0];
                y += dy[0];

                if (x <= 0 || x + particle.getWidth() >= screenWidth) dx[0] = -dx[0];
                if (y <= 0 || y + particle.getHeight() >= screenHeight) dy[0] = -dy[0];

                particle.setX(x);
                particle.setY(y);

                handler.postDelayed(this, 16);
            }
        };

        handler.post(runnable);
    }

    /**
     * Hides the system UI (navigation and status bars) for a full-screen experience.
     */
    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }
}