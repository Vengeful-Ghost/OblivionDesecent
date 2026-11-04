package com.example.obliviondescent;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Statistics screen.
 * Displays lifetime game data and current-game data,
 * a global leaderboard, and a following list with a search function.
 */
public class StatsActivity extends AppCompatActivity {

    private DBManager dbManager;
    private String userId;

    // Personal stats
    private LinearLayout statsContainer;
    private TextView tabLifetime;
    private TextView tabCurrent;
    private boolean showingLifetime = true;
    private Map<String, Object> lifetimeData = null;
    private Map<String, Object> currentGameData = null;

    // Leaderboard
    private RecyclerView leaderboardRecycler;
    private ProgressBar leaderboardLoading;
    private TextView leaderboardEmpty;
    private TextView tabGlobal;
    private TextView tabFollowing;
    private LinearLayout searchBarContainer;
    private EditText searchInput;
    private boolean showingGlobal = true;

    private LeaderboardAdapter leaderboardAdapter;
    private List<LeaderboardItem> leaderboardItems = new ArrayList<>();

    /**
     * Initialises the statistics screen, sets up tabs and loads data.
     * @param savedInstanceState previously saved instance state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
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

        // Bind views
        statsContainer      = findViewById(R.id.stats_container);
        tabLifetime         = findViewById(R.id.tab_lifetime);
        tabCurrent          = findViewById(R.id.tab_current);
        leaderboardRecycler = findViewById(R.id.leaderboard_recycler);
        leaderboardLoading  = findViewById(R.id.leaderboard_loading);
        leaderboardEmpty    = findViewById(R.id.leaderboard_empty);
        tabGlobal           = findViewById(R.id.tab_global);
        tabFollowing        = findViewById(R.id.tab_following);
        searchBarContainer  = findViewById(R.id.search_bar_container);
        searchInput         = findViewById(R.id.search_input);
        MaterialButton btnSearch = findViewById(R.id.btn_search);
        MaterialButton btnBack   = findViewById(R.id.btn_back_stats);

        btnBack.setOnClickListener(v -> finish());

        // Personal stats tabs
        tabLifetime.setOnClickListener(v -> switchStatTab(true));
        tabCurrent.setOnClickListener(v -> switchStatTab(false));

        // Leaderboard tabs
        tabGlobal.setOnClickListener(v -> switchLeaderboardTab(true));
        tabFollowing.setOnClickListener(v -> switchLeaderboardTab(false));

        // Search
        btnSearch.setOnClickListener(v -> {
            String query = searchInput.getText().toString().trim();
            if (!query.isEmpty()) searchUsers(query);
        });

        // Setup RecyclerView
        leaderboardAdapter = new LeaderboardAdapter(leaderboardItems, this::onLeaderboardItemClick);
        leaderboardRecycler.setLayoutManager(new LinearLayoutManager(this));
        leaderboardRecycler.setAdapter(leaderboardAdapter);

        // Load data
        loadLifetimeStats();
        loadGlobalLeaderboard();
    }

    @Override
    protected void onStart() {
        super.onStart();
        SoundManager.registerMenuMusic(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        SoundManager.unregisterMenuMusic();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    /* ============================================================
       PERSONAL STATS
       ============================================================ */

    /**
     * Switches between the lifetime stats tab and the current-game stats tab.
     * @param lifetime true to show lifetime data, false to show current-game data
     */
    private void switchStatTab(boolean lifetime) {
        showingLifetime = lifetime;

        tabLifetime.setTextColor(getColor(lifetime ? R.color.gold_bright : R.color.bone_muted));
        tabCurrent.setTextColor(getColor(!lifetime ? R.color.gold_bright : R.color.bone_muted));

        if (lifetime) {
            if (lifetimeData != null) populateStats(lifetimeData, true);
            else loadLifetimeStats();
        } else {
            if (currentGameData != null) populateStats(currentGameData, false);
            else loadCurrentGameStats();
        }
    }

    /**
     * Loads the player's lifetime statistics from Firestore.
     */
    private void loadLifetimeStats() {
        if (userId == null) return;
        dbManager.loadPlayerStats(userId, data -> {
            lifetimeData = data;
            runOnUiThread(() -> {
                if (showingLifetime) populateStats(data, true);
            });
        });
    }

    /**
     * Loads the player's current game statistics from Firestore.
     */
    private void loadCurrentGameStats() {
        if (userId == null) return;
        dbManager.loadPlayerGames(userId, slots -> {
            if (!slots.isEmpty()) {
                currentGameData = slots.get(0);
                runOnUiThread(() -> {
                    if (!showingLifetime) populateStats(currentGameData, false);
                });
            } else {
                runOnUiThread(() -> showEmptyStats());
            }
        });
    }

    /**
     * Populates the stats container with data rows.
     * @param data raw data map from Firestore
     * @param isLifetime true for lifetime data, false for current-game data
     */
    private void populateStats(Map<String, Object> data, boolean isLifetime) {
        statsContainer.removeAllViews();
        if (data == null) { showEmptyStats(); return; }

        if (isLifetime) {
            addStatRow(R.drawable.ic_trophy, "Highest Floor",
                    safeInt(data, "highestFloor") + "");
            addStatRow(R.drawable.ic_crossed_swords, "Enemies Killed",
                    safeInt(data, "enemiesKilled") + "");
            addStatRow(R.drawable.ic_skull, "Bosses Killed",
                    safeInt(data, "bossesKilled") + "");
            addStatRow(R.drawable.ic_coin, "Coins Collected",
                    safeInt(data, "coinsCollected") + "");
            addStatRow(R.drawable.ic_coins_spent, "Coins Spent",
                    safeInt(data, "coinsSpent") + "");
            addStatRow(R.drawable.ic_dagger, "Damage Given",
                    safeInt(data, "damageGiven") + "");
            addStatRow(R.drawable.ic_shield, "Damage Taken",
                    safeInt(data, "damageTaken") + "");
            addStatRow(R.drawable.ic_broken_heart, "Times Died",
                    safeInt(data, "timesDied") + "");
            addStatRow(R.drawable.ic_hourglass, "Playtime",
                    StatsManager.secondsToHours(safeLong(data, "playtimeSeconds")) + "h");
            addStatRow(R.drawable.ic_map, "Floors Cleared",
                    safeInt(data, "floorsCleared") + "");
        } else {
            // Current game — read from game save fields
            addStatRow(R.drawable.ic_stairs, "Current Floor",
                    safeInt(data, "currentFloor") + "");
            int level = Math.max(1, safeInt(data, "level"));
            addStatRow(R.drawable.ic_level, "Level", level + "");
            addStatRow(R.drawable.ic_heart, "Health",
                    safeInt(data, "health") + " / " + (100 + (level - 1) * 10));
            addStatRow(R.drawable.ic_shield, "Armor",
                    safeInt(data, "armorValue") + "");
            addStatRow(R.drawable.ic_coin, "Gold",
                    safeInt(data, "gold") + "");
            addStatRow(R.drawable.ic_sword, "Equipped Weapon",
                    safeString(data, "equippedWeaponName"));
        }
    }

    private void showEmptyStats() {
        statsContainer.removeAllViews();
        TextView empty = new TextView(this);
        empty.setText("No data yet.\nStart a game to track stats!");
        empty.setTextColor(getColor(R.color.bone_muted));
        empty.setTextSize(16f);
        empty.setGravity(android.view.Gravity.CENTER);
        empty.setPadding(0, 48, 0, 0);
        statsContainer.addView(empty);
    }

    private void addStatRow(int icon, String label, String value) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_stat_row, statsContainer, false);

        android.widget.ImageView iconView = row.findViewById(R.id.stat_icon);
        iconView.setImageResource(icon);
        iconView.setVisibility(View.VISIBLE);
        ((TextView) row.findViewById(R.id.stat_label)).setText(label);
        ((TextView) row.findViewById(R.id.stat_value)).setText(value);

        // Divider
        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.divider_gold));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        divider.setLayoutParams(lp);

        statsContainer.addView(row);
        statsContainer.addView(divider);
    }

    /* ============================================================
       LEADERBOARD
       ============================================================ */

    /**
     * Switches between the global leaderboard tab and the following tab.
     * @param global true to show the global leaderboard, false to show the following list
     */
    private void switchLeaderboardTab(boolean global) {
        showingGlobal = global;

        tabGlobal.setTextColor(getColor(global ? R.color.gold_bright : R.color.bone_muted));
        tabFollowing.setTextColor(getColor(!global ? R.color.gold_bright : R.color.bone_muted));

        searchBarContainer.setVisibility(global ? View.GONE : View.VISIBLE);

        if (global) loadGlobalLeaderboard();
        else        loadFollowingLeaderboard();
    }

    private void loadGlobalLeaderboard() {
        showLeaderboardLoading(true);
        dbManager.loadGlobalLeaderboard(items -> {
            runOnUiThread(() -> {
                showLeaderboardLoading(false);
                leaderboardItems.clear();
                leaderboardItems.addAll(items);
                leaderboardAdapter.notifyDataSetChanged();
                leaderboardEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void loadFollowingLeaderboard() {
        if (userId == null) return;
        showLeaderboardLoading(true);
        dbManager.loadFollowing(userId, followingIds -> {
            if (followingIds.isEmpty()) {
                runOnUiThread(() -> {
                    showLeaderboardLoading(false);
                    leaderboardItems.clear();
                    leaderboardAdapter.notifyDataSetChanged();
                    leaderboardEmpty.setText("You're not following anyone yet.\nSearch for a username above.");
                    leaderboardEmpty.setVisibility(View.VISIBLE);
                });
                return;
            }
            dbManager.loadPlayersByIds(followingIds, items -> {
                runOnUiThread(() -> {
                    showLeaderboardLoading(false);
                    leaderboardItems.clear();
                    leaderboardItems.addAll(items);
                    leaderboardAdapter.notifyDataSetChanged();
                    leaderboardEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                });
            });
        });
    }

    /**
     * Searches for users by username and displays results in the leaderboard.
     * @param query the search string
     */
    private void searchUsers(String query) {
        showLeaderboardLoading(true);
        dbManager.searchUsers(query, userId, items -> {
            runOnUiThread(() -> {
                showLeaderboardLoading(false);
                leaderboardItems.clear();
                leaderboardItems.addAll(items);
                leaderboardAdapter.notifyDataSetChanged();
                leaderboardEmpty.setText("No users found.");
                leaderboardEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void showLeaderboardLoading(boolean show) {
        leaderboardLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        leaderboardRecycler.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    /**
     * Handles a tap on a leaderboard item and opens a user-detail dialog.
     * @param item the tapped leaderboard entry
     */
    private void onLeaderboardItemClick(LeaderboardItem item) {
        showUserDetailDialog(item);
    }

    /**
     * Shows a user-detail dialog with follow/unfollow functionality.
     * @param item the user data to display
     */
    private void showUserDetailDialog(LeaderboardItem item) {
        android.app.Dialog dialog = new android.app.Dialog(
                this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_user_stats);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView nameView    = dialog.findViewById(R.id.user_detail_name);
        View closeBtn        = dialog.findViewById(R.id.btn_close_user_detail);
        LinearLayout statsContainer = dialog.findViewById(R.id.user_detail_stats);
        MaterialButton followBtn = dialog.findViewById(R.id.btn_follow);

        nameView.setText(item.username);
        closeBtn.setOnClickListener(v -> dialog.dismiss());

        // Populate stats
        addUserStatRow(statsContainer, R.drawable.ic_trophy, "Highest Floor", item.highestFloor + "");
        addUserStatRow(statsContainer, R.drawable.ic_crossed_swords, "Enemies Killed", item.enemiesKilled + "");
        addUserStatRow(statsContainer, R.drawable.ic_skull, "Bosses Killed", item.bossesKilled + "");
        addUserStatRow(statsContainer, R.drawable.ic_broken_heart, "Times Died", item.timesDied + "");
        addUserStatRow(statsContainer, R.drawable.ic_hourglass, "Playtime",
                StatsManager.secondsToHours(item.playtimeSeconds) + "h");

        // Follow button
        if (item.userId.equals(userId)) {
            followBtn.setVisibility(View.GONE); // Can't follow yourself
        } else {
            dbManager.isFollowing(userId, item.userId, isFollowing -> {
                runOnUiThread(() -> showFollowState(followBtn, isFollowing));
            });

            followBtn.setOnClickListener(v -> {
                dbManager.isFollowing(userId, item.userId, isFollowing -> {
                    if (isFollowing) {
                        dbManager.unfollowUser(userId, item.userId, success -> {
                            runOnUiThread(() -> showFollowState(followBtn, false));
                        });
                    } else {
                        dbManager.followUser(userId, item.userId, success -> {
                            runOnUiThread(() -> showFollowState(followBtn, true));
                        });
                    }
                });
            });
        }

        dialog.show();
        hideSystemUIOnWindow(dialog.getWindow());
    }

    // Follow = gold primary button, Unfollow = crimson danger button
    private void showFollowState(MaterialButton btn, boolean following) {
        btn.setText(following ? "Unfollow" : "Follow");
        btn.setBackgroundResource(following ? R.drawable.btn_gothic_danger : R.drawable.btn_login);
        btn.setTextColor(getColor(following ? R.color.crimson_glow : R.color.gold_glow));
    }

    private void addUserStatRow(LinearLayout container, int icon, String label, String value) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_stat_row, container, false);
        android.widget.ImageView iconView = row.findViewById(R.id.stat_icon);
        iconView.setImageResource(icon);
        iconView.setVisibility(View.VISIBLE);
        ((TextView) row.findViewById(R.id.stat_label)).setText(label);
        ((TextView) row.findViewById(R.id.stat_value)).setText(value);
        container.addView(row);
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private int safeInt(Map<String, Object> data, String key) {
        if (data == null || data.get(key) == null) return 0;
        return ((Number) data.get(key)).intValue();
    }

    private long safeLong(Map<String, Object> data, String key) {
        if (data == null || data.get(key) == null) return 0L;
        return ((Number) data.get(key)).longValue();
    }

    private String safeString(Map<String, Object> data, String key) {
        if (data == null || data.get(key) == null) return "—";
        return (String) data.get(key);
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    private void hideSystemUIOnWindow(android.view.Window window) {
        if (window == null) return;
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
}