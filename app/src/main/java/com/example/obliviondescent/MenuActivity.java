package com.example.obliviondescent;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class MenuActivity extends AppCompatActivity {

    private DBManager dbManager;
    private String userId;
    private String existingGameId = null;
    private boolean initialCheckDone = false;
    private Dialog loadingDialog;

    private MaterialButton btnNewGame;
    private MaterialButton btnLoadGame;
    private MaterialButton btnStats;
    private MaterialButton btnSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        hideSystemUI();

        dbManager = new DBManager();

        SharedPreferences prefs = getSharedPreferences("loginPrefs", MODE_PRIVATE);
        userId = prefs.getString("user_id", null);

        TextView tvUsername = findViewById(R.id.tvUsername);
        if (tvUsername != null) tvUsername.setText(prefs.getString("username", ""));

        btnNewGame  = findViewById(R.id.btn_new_game);
        btnLoadGame = findViewById(R.id.btn_load_game);
        btnStats    = findViewById(R.id.btn_stats);
        btnSettings = findViewById(R.id.btn_settings);

        // Load game is grayed until we know a save exists
        setLoadGameEnabled(false);
        checkForExistingGame(true);

        btnNewGame.setOnClickListener(v -> showNewGameConfirmation());

        btnLoadGame.setOnClickListener(v -> {
            if (existingGameId != null) {
                launchGame(existingGameId);
            }
        });

        btnStats.setOnClickListener(v -> {
            Intent intent = new Intent(this, StatsActivity.class);
            startActivity(intent);
        });

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        checkForExistingGame(false);
    }

    /**
     * Checks Firestore for an existing game save for this user.
     * Enables or disables the Load Game button accordingly.
     * showDialog is true only on first load so it doesn't flash on every resume.
     */
    private void checkForExistingGame(boolean showDialog) {
        if (userId == null) return;
        if (showDialog && !initialCheckDone) showLoading("Loading...");

        dbManager.loadPlayerGames(userId, slots -> {
            runOnUiThread(() -> {
                if (showDialog && !initialCheckDone) hideLoading();
                initialCheckDone = true;
                if (!slots.isEmpty()) {
                    existingGameId = (String) slots.get(0).get("gameId");
                    setLoadGameEnabled(true);
                } else {
                    existingGameId = null;
                    setLoadGameEnabled(false);
                }
            });
        });
    }

    private void setLoadGameEnabled(boolean enabled) {
        btnLoadGame.setEnabled(enabled);
        btnLoadGame.setAlpha(enabled ? 1f : 0.4f);
    }

    /**
     * Shows a confirmation dialog before starting a new game.
     * If a save already exists, warns the user it will be deleted.
     */
    private void showNewGameConfirmation() {
        if (existingGameId == null) {
            // No existing save — start immediately
            createNewGame();
            return;
        }

        // Existing save — confirm overwrite
        android.app.AlertDialog.Builder builder =
                new android.app.AlertDialog.Builder(this, R.style.AlertDialogDark);
        builder.setTitle("New Game");
        builder.setMessage("Are you sure? This will remove all previous game data.");
        builder.setPositiveButton("Yes", (dialog, which) -> {
            deleteSaveAndCreateNew();
        });
        builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
        android.app.AlertDialog alert = builder.create();
        alert.show();

        // Style the buttons
        alert.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setTextColor(Color.parseColor("#FF4444"));
        alert.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(Color.parseColor("#AAAAAA"));
    }

    private void deleteSaveAndCreateNew() {
        if (existingGameId == null) {
            createNewGame();
            return;
        }
        showLoading("Deleting save...");
        dbManager.deleteGame(userId, existingGameId, success -> {
            existingGameId = null;
            updateLoadingMessage("Creating new game...");
            createNewGame();
        });
    }

    private void createNewGame() {
        if (userId == null) return;
        if (loadingDialog == null || !loadingDialog.isShowing()) showLoading("Creating new game...");

        dbManager.createGameSave(userId, 1, newGameId -> {
            if (newGameId != null) {
                existingGameId = newGameId;
                hideLoading();
                launchGame(newGameId);
            } else {
                hideLoading();
            }
        });
    }

    private void launchGame(String gameId) {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra("game_id", gameId);
        intent.putExtra("slot_index", 1);
        startActivity(intent);
    }

    private void showLoading(String message) {
        if (loadingDialog != null && loadingDialog.isShowing()) return;
        View view = getLayoutInflater().inflate(R.layout.dialog_loading, null);
        ((TextView) view.findViewById(R.id.txt_loading_message)).setText(message);
        loadingDialog = new Dialog(this, R.style.LoadingDialogStyle);
        loadingDialog.setContentView(view);
        loadingDialog.setCancelable(false);
        loadingDialog.show();
    }

    private void updateLoadingMessage(String message) {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            TextView tv = loadingDialog.findViewById(R.id.txt_loading_message);
            if (tv != null) tv.setText(message);
        }
    }

    private void hideLoading() {
        if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
        loadingDialog = null;
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }
}