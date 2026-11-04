package com.example.obliviondescent;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Main menu screen of the game.
 * Displays options for new game, load game, statistics and settings.
 * Checks for an existing save and starts the menu music.
 */
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
    private ListenerRegistration inviteListener;

    /**
     * Initialises the main menu, loads user data and checks for an existing save.
     * @param savedInstanceState previously saved instance state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        hideSystemUI();

        // Keep buttons clear of the camera cutout
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            v.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom);
            return insets;
        });

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

        findViewById(R.id.btn_coop).setOnClickListener(v ->
                startActivity(new Intent(this, LobbyActivity.class)));

        findViewById(R.id.btn_tutorial).setOnClickListener(v -> showTutorial(0));
    }

    // Title and text of each tutorial page
    private static final String[][] TUTORIAL = {
            {"Move & Dash",
                    "Drag the left stick to move. You face the way you walk.\n\n"
                    + "Tap D to dash. Nothing can hit you mid-dash, so use it to slip through attacks."},
            {"Attack",
                    "Hold ATK to keep attacking. It aims at the nearest enemy; drag it to aim yourself.\n\n"
                    + "Keep swinging for a 3-hit combo: the third hit is wider and stronger. Gold numbers are critical hits.\n\n"
                    + "SPIN hits everything around you, then needs a few seconds to recharge."},
            {"Read the Enemy",
                    "A red ! means an enemy is about to strike. The red cone or circle shows where it lands, so step out or dash.\n\n"
                    + "A combo finisher or critical hit during that wind-up staggers the enemy and cancels its attack (bosses can't be staggered)."},
            {"Floors & Shop",
                    "Defeat the enemies on a floor to open the purple EXIT portal. Edge arrows point the way.\n\n"
                    + "The gold SHOP portal sells weapons, armor and potions for the coins you collect.\n\n"
                    + "Every 5th floor has a boss guarding treasure chests."},
            {"Grow Stronger",
                    "Kills give XP. Each level adds max HP and damage.\n\n"
                    + "Tap \u2665 to drink a healing potion. Armor absorbs hits first but wears down with every hit.\n\n"
                    + "Your run saves at the start of every floor, and if you fall you can respawn there."}
    };

    /** Shows one page of the new-player guide, with Back / Next to flip through it. */
    private void showTutorial(int page) {
        android.app.AlertDialog.Builder builder =
                new android.app.AlertDialog.Builder(this, R.style.AlertDialogDark)
                        .setTitle(TUTORIAL[page][0] + "  (" + (page + 1) + "/" + TUTORIAL.length + ")")
                        .setMessage(TUTORIAL[page][1])
                        .setNegativeButton("Close", null);
        if (page + 1 < TUTORIAL.length)
            builder.setPositiveButton("Next", (dialog, which) -> showTutorial(page + 1));
        if (page > 0)
            builder.setNeutralButton("Back", (dialog, which) -> showTutorial(page - 1));
        builder.show();
    }

    /**
     * Starts the menu music and listens for party invites while the menu is visible.
     */
    @Override
    protected void onStart() {
        super.onStart();
        SoundManager.registerMenuMusic(this);
        if (userId != null) inviteListener = dbManager.listenToInvites(userId, this::showInvitePopup);
    }

    /**
     * Stops the menu music and the invite listener when the screen goes to the background.
     */
    @Override
    protected void onStop() {
        super.onStop();
        SoundManager.unregisterMenuMusic();
        if (inviteListener != null) {
            inviteListener.remove();
            inviteListener = null;
        }
    }

    /** Asks whether to join a friend's party; either answer removes the invite. */
    private void showInvitePopup(String inviteId, String fromName, String code) {
        new android.app.AlertDialog.Builder(this, R.style.AlertDialogDark)
                .setTitle("Party invite")
                .setMessage((fromName != null ? fromName : "A friend") + " invited you to a co-op run.")
                .setCancelable(false)
                .setPositiveButton("Join", (dialog, which) -> {
                    dbManager.deleteInvite(inviteId);
                    Intent intent = new Intent(this, LobbyActivity.class);
                    intent.putExtra(LobbyActivity.EXTRA_JOIN_CODE, code);
                    startActivity(intent);
                })
                .setNegativeButton("Decline", (dialog, which) -> dbManager.deleteInvite(inviteId))
                .show();
    }

    /**
     * Restores full-screen mode and refreshes the existing-save check.
     */
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

    /**
     * Enables or disables the Load Game button.
     * @param enabled true to enable the button, false to disable it
     */
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
                .setTextColor(getColor(R.color.crimson_glow));
        alert.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(getColor(R.color.bone_dim));
    }

    /**
     * Deletes the existing save and then creates a new game.
     */
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

    /**
     * Creates a new game save in Firestore and launches GameActivity.
     */
    private void createNewGame() {
        if (userId == null) return;
        if (loadingDialog == null || !loadingDialog.isShowing()) showLoading("Creating new game...");

        // Clear all locally stored batch files so the new game gets a fresh API call
        BatchGenerator.clearAllBatches(this);

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

    /**
     * Launches GameActivity with the given game save ID.
     * @param gameId the Firestore save document ID
     */
    private void launchGame(String gameId) {
        SoundManager.stopMenuMusic(); // silence music before entering the game
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