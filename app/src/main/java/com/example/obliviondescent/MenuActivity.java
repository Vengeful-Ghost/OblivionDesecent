package com.example.obliviondescent;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
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

        findViewById(R.id.btn_tutorial).setOnClickListener(v -> showTutorial());
        findViewById(R.id.btn_altar).setOnClickListener(v -> showAltar());
        findViewById(R.id.btn_daily).setOnClickListener(v -> showDaily());
        Ads.gatherConsent(this);
    }

    /** Soul Altar: spend Souls from past runs on permanent upgrades. */
    private void showAltar() {
        if (userId == null) return;
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_altar);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.findViewById(R.id.btn_close_altar).setOnClickListener(v -> dialog.dismiss());
        dbManager.loadPlayerStats(userId, data -> renderAltar(dialog, data));
        dialog.show();
        hideSystemUIOnWindow(dialog);
    }

    /** Fills the altar with one cell per upgrade, two per row, from the player document. */
    private void renderAltar(Dialog dialog, java.util.Map<String, Object> data) {
        if (!dialog.isShowing()) return;
        Object soulsValue = data != null ? data.get("souls") : null;
        long souls = soulsValue instanceof Number ? ((Number) soulsValue).longValue() : 0;
        int[] ranks = Altar.ranks(data);
        ((TextView) dialog.findViewById(R.id.txt_altar_souls)).setText(String.valueOf(souls));

        // Ascension: chosen here, used by the next new solo game
        int unlocked = data != null && data.get("ascensionUnlocked") instanceof Number
                ? ((Number) data.get("ascensionUnlocked")).intValue() : 0;
        int chosen = data != null && data.get("ascension") instanceof Number
                ? Math.min(unlocked, ((Number) data.get("ascension")).intValue()) : 0;
        dialog.findViewById(R.id.altar_ascension_row).setVisibility(unlocked > 0 ? View.VISIBLE : View.GONE);
        ((TextView) dialog.findViewById(R.id.txt_ascension)).setText("ASCENSION " + chosen + " / " + unlocked
                + "   enemies +" + (15 * chosen) + "% HP, Souls +" + (25 * chosen) + "%");
        dialog.findViewById(R.id.btn_ascension_down).setOnClickListener(v -> setAscension(dialog, data, chosen - 1, unlocked));
        dialog.findViewById(R.id.btn_ascension_up).setOnClickListener(v -> setAscension(dialog, data, chosen + 1, unlocked));

        LinearLayout grid = dialog.findViewById(R.id.altar_grid);
        grid.removeAllViews();
        int gap = getResources().getDimensionPixelSize(R.dimen.space_xs);
        LinearLayout row = null;
        for (Altar a : Altar.values()) {
            if (a.ordinal() % 2 == 0) {
                row = new LinearLayout(this);
                grid.addView(row);
            }
            View cell = getLayoutInflater().inflate(R.layout.item_altar_cell, row, false);
            int rank = ranks[a.ordinal()];
            ((android.widget.ImageView) cell.findViewById(R.id.altar_icon)).setImageResource(a.icon);
            ((TextView) cell.findViewById(R.id.altar_title)).setText(a.title);
            ((TextView) cell.findViewById(R.id.altar_effect)).setText(a.effect);
            ((TextView) cell.findViewById(R.id.altar_rank)).setText("Rank " + rank + " / " + a.maxRank);
            MaterialButton buy = cell.findViewById(R.id.altar_buy);
            if (rank >= a.maxRank) {
                buy.setText("MAX");
                buy.setEnabled(false);
            } else {
                int cost = a.cost(rank);
                boolean afford = souls >= cost;
                buy.setText(String.valueOf(cost));
                buy.setEnabled(afford);
                buy.setAlpha(afford ? 1f : 0.4f);
                buy.setOnClickListener(v -> {
                    buy.setEnabled(false);
                    dbManager.buyAltarRank(userId, a.name(), rank, cost, ok ->
                            dbManager.loadPlayerStats(userId, fresh -> renderAltar(dialog, fresh)));
                });
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(gap, gap, gap, gap);
            row.addView(cell, lp);
        }
    }

    /** Saves the picked Ascension (clamped to what's unlocked) and redraws the altar. */
    private void setAscension(Dialog dialog, java.util.Map<String, Object> data, int level, int unlocked) {
        level = Math.max(0, Math.min(unlocked, level));
        dbManager.setAscension(userId, level);
        java.util.Map<String, Object> updated = new java.util.HashMap<>(data);
        updated.put("ascension", level);
        renderAltar(dialog, updated);
    }

    /** Today's Daily Run: the same seeded dungeon for everyone, with today's top five. */
    private void showDaily() {
        if (userId == null) return;
        java.util.TimeZone utc = java.util.TimeZone.getTimeZone("UTC");
        java.text.SimpleDateFormat dayFormat = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US);
        java.text.SimpleDateFormat shownFormat = new java.text.SimpleDateFormat("d MMM", java.util.Locale.US);
        dayFormat.setTimeZone(utc);
        shownFormat.setTimeZone(utc);
        java.util.Date now = new java.util.Date();
        String day = dayFormat.format(now);

        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_daily);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.findViewById(R.id.btn_close_daily).setOnClickListener(v -> dialog.dismiss());
        ((TextView) dialog.findViewById(R.id.txt_daily_date)).setText(shownFormat.format(now));
        dialog.findViewById(R.id.btn_daily_play).setOnClickListener(v -> {
            dialog.dismiss();
            SoundManager.stopMenuMusic();
            Intent intent = new Intent(this, GameActivity.class);
            intent.putExtra(GameActivity.EXTRA_DAILY_SEED, Long.parseLong(day) * 1_000_003L);
            intent.putExtra(GameActivity.EXTRA_DAILY_DAY, day);
            startActivity(intent);
        });

        dbManager.loadDailyTop(day, rows -> {
            if (!dialog.isShowing()) return;
            LinearLayout board = dialog.findViewById(R.id.daily_board);
            for (int i = 0; i < rows.size(); i++) {
                java.util.Map<String, Object> r = rows.get(i);
                if (userId.equals(r.get("uid")))
                    ((TextView) dialog.findViewById(R.id.txt_daily_best)).setText("Your best: #" + (i + 1)
                            + ", floor " + r.get("floor"));
                if (i >= 5) continue;
                View line = getLayoutInflater().inflate(R.layout.item_stat_row, board, false);
                ((TextView) line.findViewById(R.id.stat_label)).setText((i + 1) + ".  " + r.get("name"));
                ((TextView) line.findViewById(R.id.stat_value)).setText("F" + r.get("floor") + "  " + r.get("kills") + " kills");
                board.addView(line);
            }
            if (rows.isEmpty()) {
                View line = getLayoutInflater().inflate(R.layout.item_stat_row, board, false);
                ((TextView) line.findViewById(R.id.stat_label)).setText("No runs yet. Be the first!");
                ((TextView) line.findViewById(R.id.stat_value)).setText("");
                board.addView(line);
            }
        });
        dialog.show();
        hideSystemUIOnWindow(dialog);
    }

    private void hideSystemUIOnWindow(Dialog dialog) {
        if (dialog.getWindow() != null) dialog.getWindow().getDecorView().setSystemUiVisibility(
                getWindow().getDecorView().getSystemUiVisibility());
    }

    // Icon of each tutorial page
    private static final int[] TUTORIAL_ICONS = {
            R.drawable.ic_boots, R.drawable.ic_sword, R.drawable.ic_warning,
            R.drawable.ic_stairs, R.drawable.ic_key, R.drawable.ic_level
    };

    // Title and text of each tutorial page
    private static final String[][] TUTORIAL = {
            {"Move & Dash",
                    "Drag the left stick to move. You face the way you walk.\n\n"
                    + "Tap D to dash. Nothing can hit you mid-dash. Dash just as a hit lands for a PERFECT dodge: "
                    + "time slows, the dash recharges and your next hits are sure crits."},
            {"Attack",
                    "Hold ATK to keep attacking. It aims at the nearest enemy; drag it to aim yourself.\n\n"
                    + "Keep swinging for a 3-hit combo: the third hit is wider and stronger. Gold numbers are critical hits.\n\n"
                    + "SPIN hits everything around you, then needs a few seconds to recharge."},
            {"Read the Enemy",
                    "A red ! means an enemy is about to strike. The red cone or circle shows where it lands, so step out or dash.\n\n"
                    + "A combo finisher or critical hit during that wind-up staggers the enemy (bosses can't be staggered).\n\n"
                    + "Glowing elites are tougher, but always drop a key."},
            {"Floors & Shop",
                    "Defeat the enemies on a floor to open the purple EXIT portal. Edge arrows point the way.\n\n"
                    + "The gold SHOP portal sells gear for the coins you collect. Every 5th floor has a boss.\n\n"
                    + "Tap the minimap to see the whole floor."},
            {"Treasure & Keys",
                    "Gold-marked rooms hold a treasure chest, but some chests are mimics that bite back.\n\n"
                    + "A gold gate seals a vault. Walk up to it with a key to open it: vaults hold the best loot. "
                    + "Keys drop from elites, mimics and now and then other enemies."},
            {"Grow Stronger",
                    "Kills give XP. When you level up, tap the glowing badge and pick a perk. "
                    + "Two perks at 2 stacks each awaken a synergy.\n\n"
                    + "Every run earns Souls, even the ones you lose: spend them at the Soul Altar on lasting upgrades.\n\n"
                    + "Your run saves at the start of every floor. Try the Daily Run to race everyone on the same dungeon."}
    };

    /** New-player guide: one card, Back / Next flip its page in place. */
    private void showTutorial() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_tutorial);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.findViewById(R.id.btn_close_tutorial).setOnClickListener(v -> dialog.dismiss());

        // One dot per page, the current one lit
        LinearLayout dots = dialog.findViewById(R.id.tutorial_dots);
        int size = getResources().getDimensionPixelSize(R.dimen.space_sm);
        for (int i = 0; i < TUTORIAL.length; i++) {
            View dot = new View(this);
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setBackground(shape);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(size / 2, 0, size / 2, 0);
            dots.addView(dot, lp);
        }

        int[] page = {0};
        Runnable render = () -> {
            int p = page[0];
            ((android.widget.ImageView) dialog.findViewById(R.id.tutorial_icon)).setImageResource(TUTORIAL_ICONS[p]);
            ((TextView) dialog.findViewById(R.id.tutorial_title)).setText(TUTORIAL[p][0]);
            ((TextView) dialog.findViewById(R.id.tutorial_page)).setText((p + 1) + " / " + TUTORIAL.length);
            ((TextView) dialog.findViewById(R.id.tutorial_body)).setText(TUTORIAL[p][1]);
            dialog.findViewById(R.id.btn_tutorial_back).setVisibility(p > 0 ? View.VISIBLE : View.INVISIBLE);
            ((MaterialButton) dialog.findViewById(R.id.btn_tutorial_next)).setText(p + 1 < TUTORIAL.length ? "Next" : "Done");
            for (int i = 0; i < dots.getChildCount(); i++)
                ((android.graphics.drawable.GradientDrawable) dots.getChildAt(i).getBackground())
                        .setColor(getColor(i == p ? R.color.gold_bright : R.color.bone_faint));
        };
        dialog.findViewById(R.id.btn_tutorial_back).setOnClickListener(v -> {
            page[0]--;
            render.run();
        });
        dialog.findViewById(R.id.btn_tutorial_next).setOnClickListener(v -> {
            if (page[0] + 1 >= TUTORIAL.length) {
                dialog.dismiss();
                return;
            }
            page[0]++;
            render.run();
        });
        render.run();
        dialog.show();
        hideSystemUIOnWindow(dialog);
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