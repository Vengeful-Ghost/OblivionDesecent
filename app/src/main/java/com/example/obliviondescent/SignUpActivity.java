package com.example.obliviondescent;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * New-user registration screen.
 * Validates registration fields, checks username uniqueness in Firestore,
 * creates a Firebase Auth account and initialises a new player document.
 */
public class SignUpActivity extends AppCompatActivity
{
    /* =========================
       UI ELEMENTS
       ========================= */

    private TextInputLayout signupUsernameLayout, signupPasswordLayout, signupConfirmLayout;
    private TextInputEditText SignupUsername, SignupPassword, SignupConfirm;
    private MaterialButton btnSignup;
    private TextView tvBackToLogin;

    /* =========================
       FIREBASE
       ========================= */

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private Dialog loadingDialog;

    /* =========================
       ACTIVITY LIFECYCLE
       ========================= */

    /**
     * Initialises the sign-up screen, sets up field validation and button listeners.
     * @param savedInstanceState previously saved instance state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        hideSystemUI();

        // Find all views
        signupUsernameLayout = findViewById(R.id.signupUsernameLayout);
        signupPasswordLayout = findViewById(R.id.signupPasswordLayout);
        signupConfirmLayout  = findViewById(R.id.signupConfirmLayout);
        SignupUsername       = findViewById(R.id.etSignupUsername);
        SignupPassword       = findViewById(R.id.etSignupPassword);
        SignupConfirm        = findViewById(R.id.etSignupConfirm);
        btnSignup            = findViewById(R.id.btnSignup);
        tvBackToLogin        = findViewById(R.id.tvBackToLogin);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Apply focus glow effect
        applyFocusGlow(signupUsernameLayout, R.drawable.input_bg, R.drawable.input_bg_focused);
        applyFocusGlow(signupPasswordLayout, R.drawable.input_bg, R.drawable.input_bg_focused);
        applyFocusGlow(signupConfirmLayout,  R.drawable.input_bg, R.drawable.input_bg_focused);

        // Sign up button
        btnSignup.setOnClickListener(v ->
        {
            String user = SignupUsername.getText() != null ? SignupUsername.getText().toString().trim() : "";
            String pass = SignupPassword.getText() != null ? SignupPassword.getText().toString() : "";
            String confirm = SignupConfirm.getText() != null ? SignupConfirm.getText().toString() : "";

            // --- Validation ---
            if (user.isEmpty())
            {
                signupUsernameLayout.setError("Enter a username");
                return;
            }
            else signupUsernameLayout.setError(null);

            if (pass.length() < 8)
            {
                signupPasswordLayout.setError("Password must be at least 8 characters");
                return;
            }
            else signupPasswordLayout.setError(null);

            if (!pass.equals(confirm))
            {
                signupConfirmLayout.setError("Passwords do not match");
                return;
            }
            else signupConfirmLayout.setError(null);

            // --- Check if username exists ---
            showLoading("Checking username...");
            db.collection("users")
                    .whereEqualTo("username", user)
                    .get()
                    .addOnSuccessListener(querySnapshot ->
                    {
                        if (!querySnapshot.isEmpty())
                        {
                            hideLoading();
                            signupUsernameLayout.setError("Username already exists!");
                        }
                        else
                        {
                            signupUsernameLayout.setError(null);
                            // Keep loading dialog open — message changes for next step
                            updateLoadingMessage("Creating account...");
                            createFirebaseUser(user, pass);
                        }
                    })
                    .addOnFailureListener(e ->
                    {
                        hideLoading();
                        Toast.makeText(SignUpActivity.this,
                                "Error checking username: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        });

        // Back to login
        tvBackToLogin.setOnClickListener(v -> finish());

        startBouncingParticles();
    }

    /* =========================
       FIREBASE USER CREATION
       ========================= */

    /**
     * Creates a new Firebase Auth account and saves the user data to Firestore.
     * @param username the chosen username
     * @param password the chosen password
     */
    private void createFirebaseUser(String username, String password)
    {
        mAuth.createUserWithEmailAndPassword(username + "@app.com", password)
                .addOnCompleteListener(task ->
                {
                    if (task.isSuccessful())
                    {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null)
                        {
                            String userId = firebaseUser.getUid();

                            Map<String, Object> userMap = new HashMap<>();
                            userMap.put("username", username);
                            userMap.put("user_id", userId);

                            db.collection("users")
                                    .document(userId)
                                    .set(userMap)
                                    .addOnSuccessListener(aVoid ->
                                    {
                                        DBManager dbManager = new DBManager();
                                        dbManager.initPlayerDocument(userId, username);
                                        hideLoading();
                                        Toast.makeText(SignUpActivity.this,
                                                "Account created!",
                                                Toast.LENGTH_SHORT).show();
                                        finish();
                                    })
                                    .addOnFailureListener(e ->
                                    {
                                        hideLoading();
                                        Toast.makeText(SignUpActivity.this,
                                                "Error saving user: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    });
                        }
                    }
                    else
                    {
                        hideLoading();
                        Toast.makeText(SignUpActivity.this,
                                "Sign up failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /* =========================
       UI UTILITIES
       ========================= */

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
     * Updates the text shown in the currently open loading dialog.
     * @param message new message to display
     */
    private void updateLoadingMessage(String message) {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            TextView tv = loadingDialog.findViewById(R.id.txt_loading_message);
            if (tv != null) tv.setText(message);
        }
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
    private void applyFocusGlow(final TextInputLayout layout,
                                final int defaultBg,
                                final int focusedBg)
    {
        layout.setOnFocusChangeListener((v, hasFocus) ->
                layout.setBackground(ContextCompat.getDrawable(
                        SignUpActivity.this,
                        hasFocus ? focusedBg : defaultBg)));

        if (layout.getEditText() != null)
        {
            layout.getEditText().setOnFocusChangeListener((v, hasFocus) ->
                    layout.setBackground(ContextCompat.getDrawable(
                            SignUpActivity.this,
                            hasFocus ? focusedBg : defaultBg)));
        }
    }

    /* =========================
       BACKGROUND PARTICLES
       ========================= */

    /**
     * Starts the bounce animation for a single background particle.
     * @param particle the ImageView particle to animate
     */
    private void startSingleBouncingParticle(ImageView particle)
    {
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

        Runnable runnable = new Runnable()
        {
            float x = particle.getX();
            float y = particle.getY();

            @Override
            public void run()
            {
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
     * Starts the bouncing-particle background animation for all particle views.
     */
    private void startBouncingParticles()
    {
        int[] particleIds =
                {
                        R.id.particle1,  R.id.particle2,  R.id.particle3,  R.id.particle4,  R.id.particle5,
                        R.id.particle6,  R.id.particle7,  R.id.particle8,  R.id.particle9,  R.id.particle10,
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

        for (int id : particleIds)
        {
            ImageView particle = findViewById(id);
            particle.getViewTreeObserver()
                    .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener()
                    {
                        @Override
                        public void onGlobalLayout()
                        {
                            particle.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            startSingleBouncingParticle(particle);
                        }
                    });
        }
    }
    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }
}
