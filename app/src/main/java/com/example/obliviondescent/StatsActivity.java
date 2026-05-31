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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * מנהלת את מסך הסטטיסטיקות.
 * מציגה נתוני משחק עולמיים (לכל הזמן) ונתוני משחק נוכחי,
 * לוח מובילים גלובלי ורשימת עוקבים עם אפשרות חיפוש.
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
     * מאתחל את מסך הסטטיסטיקות, מגדיר טאבים ומטעין נתונים.
     * @param savedInstanceState מצב שמור מהפעלה קודמת
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        hideSystemUI();

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
     * עוברת בין טאב הסטטיסטיקות לכל הזמן וסטטיסטיקות משחק נוכחי.
     * @param lifetime האם להציג נתוני כל הזמן
     */
    private void switchStatTab(boolean lifetime) {
        showingLifetime = lifetime;

        tabLifetime.setTextColor(lifetime
                ? Color.parseColor("#FFD700") : Color.parseColor("#666666"));
        tabLifetime.setTypeface(null, lifetime
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

        tabCurrent.setTextColor(!lifetime
                ? Color.parseColor("#FFD700") : Color.parseColor("#666666"));
        tabCurrent.setTypeface(null, !lifetime
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

        if (lifetime) {
            if (lifetimeData != null) populateStats(lifetimeData, true);
            else loadLifetimeStats();
        } else {
            if (currentGameData != null) populateStats(currentGameData, false);
            else loadCurrentGameStats();
        }
    }

    /**
     * טוענת סטטיסטיקות לכל הזמן של השחקן מ-Firestore.
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
     * טוענת סטטיסטיקות המשחק הנוכחי של השחקן מ-Firestore.
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
     * ממלאת את מיכל הסטטיסטיקות בשורות נתונים.
     * @param data מפת הנתונים מ-Firestore
     * @param isLifetime האם מדובר בנתוני כל הזמן (לעומת משחק נוכחי)
     */
    private void populateStats(Map<String, Object> data, boolean isLifetime) {
        statsContainer.removeAllViews();
        if (data == null) { showEmptyStats(); return; }

        if (isLifetime) {
            addStatRow("🏆  Highest Floor",
                    safeInt(data, "highestFloor") + "");
            addStatRow("⚔  Enemies Killed",
                    safeInt(data, "enemiesKilled") + "");
            addStatRow("💀  Bosses Killed",
                    safeInt(data, "bossesKilled") + "");
            addStatRow("🪙  Coins Collected",
                    safeInt(data, "coinsCollected") + "");
            addStatRow("💸  Coins Spent",
                    safeInt(data, "coinsSpent") + "");
            addStatRow("🗡  Damage Given",
                    safeInt(data, "damageGiven") + "");
            addStatRow("🛡  Damage Taken",
                    safeInt(data, "damageTaken") + "");
            addStatRow("💔  Times Died",
                    safeInt(data, "timesDied") + "");
            addStatRow("🕐  Playtime",
                    StatsManager.secondsToHours(safeLong(data, "playtimeSeconds")) + "h");
            addStatRow("🗺  Floors Cleared",
                    safeInt(data, "floorsCleared") + "");
        } else {
            // Current game — read from game save fields
            addStatRow("🏔  Current Floor",
                    safeInt(data, "currentFloor") + "");
            addStatRow("❤  Health",
                    safeInt(data, "health") + " / 100");
            addStatRow("🛡  Armor",
                    safeInt(data, "armorValue") + "");
            addStatRow("🪙  Gold",
                    safeInt(data, "gold") + "");
            addStatRow("⚔  Equipped Weapon",
                    safeString(data, "equippedWeaponName"));
        }
    }

    private void showEmptyStats() {
        statsContainer.removeAllViews();
        TextView empty = new TextView(this);
        empty.setText("No data yet.\nStart a game to track stats!");
        empty.setTextColor(Color.parseColor("#555555"));
        empty.setTextSize(13f);
        empty.setGravity(android.view.Gravity.CENTER);
        empty.setPadding(0, 48, 0, 0);
        statsContainer.addView(empty);
    }

    private void addStatRow(String label, String value) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_stat_row, statsContainer, false);

        ((TextView) row.findViewById(R.id.stat_label)).setText(label);
        ((TextView) row.findViewById(R.id.stat_value)).setText(value);

        // Divider
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#1A1A1A"));
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
     * עוברת בין טאב הלוח הגלובלי לטאב העוקבים.
     * @param global האם להציג לוח מובילים גלובלי
     */
    private void switchLeaderboardTab(boolean global) {
        showingGlobal = global;

        tabGlobal.setTextColor(global
                ? Color.parseColor("#FFD700") : Color.parseColor("#666666"));
        tabGlobal.setTypeface(null, global
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

        tabFollowing.setTextColor(!global
                ? Color.parseColor("#FFD700") : Color.parseColor("#666666"));
        tabFollowing.setTypeface(null, !global
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

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
     * מחפשת משתמשים לפי שם משתמש ומציגה תוצאות בלוח המובילים.
     * @param query מחרוזת החיפוש
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
     * מטפלת בלחיצה על פריט בלוח המובילים ופותחת דיאלוג פרטי משתמש.
     * @param item הפריט שנלחץ
     */
    private void onLeaderboardItemClick(LeaderboardItem item) {
        showUserDetailDialog(item);
    }

    /**
     * מציגה דיאלוג פרטי משתמש עם אפשרות עקוב/הפסק עקוב.
     * @param item נתוני המשתמש להצגה
     */
    private void showUserDetailDialog(LeaderboardItem item) {
        android.app.Dialog dialog = new android.app.Dialog(
                this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_user_stats);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView nameView    = dialog.findViewById(R.id.user_detail_name);
        TextView closeBtn    = dialog.findViewById(R.id.btn_close_user_detail);
        LinearLayout statsContainer = dialog.findViewById(R.id.user_detail_stats);
        MaterialButton followBtn = dialog.findViewById(R.id.btn_follow);

        nameView.setText(item.username);
        closeBtn.setOnClickListener(v -> dialog.dismiss());

        // Populate stats
        addUserStatRow(statsContainer, "🏆  Highest Floor",   item.highestFloor + "");
        addUserStatRow(statsContainer, "⚔  Enemies Killed",   item.enemiesKilled + "");
        addUserStatRow(statsContainer, "💀  Bosses Killed",    item.bossesKilled + "");
        addUserStatRow(statsContainer, "💔  Times Died",       item.timesDied + "");
        addUserStatRow(statsContainer, "🕐  Playtime",
                StatsManager.secondsToHours(item.playtimeSeconds) + "h");

        // Follow button
        if (item.userId.equals(userId)) {
            followBtn.setVisibility(View.GONE); // Can't follow yourself
        } else {
            dbManager.isFollowing(userId, item.userId, isFollowing -> {
                runOnUiThread(() -> {
                    followBtn.setText(isFollowing ? "Unfollow" : "Follow");
                    followBtn.setStrokeColor(
                            android.content.res.ColorStateList.valueOf(
                                    isFollowing ? Color.parseColor("#FF4444")
                                            : Color.parseColor("#FFD700")));
                });
            });

            followBtn.setOnClickListener(v -> {
                dbManager.isFollowing(userId, item.userId, isFollowing -> {
                    if (isFollowing) {
                        dbManager.unfollowUser(userId, item.userId, success -> {
                            runOnUiThread(() -> {
                                followBtn.setText("Follow");
                                followBtn.setStrokeColor(
                                        android.content.res.ColorStateList.valueOf(
                                                Color.parseColor("#FFD700")));
                            });
                        });
                    } else {
                        dbManager.followUser(userId, item.userId, success -> {
                            runOnUiThread(() -> {
                                followBtn.setText("Unfollow");
                                followBtn.setStrokeColor(
                                        android.content.res.ColorStateList.valueOf(
                                                Color.parseColor("#FF4444")));
                            });
                        });
                    }
                });
            });
        }

        dialog.show();
        hideSystemUIOnWindow(dialog.getWindow());
    }

    private void addUserStatRow(LinearLayout container, String label, String value) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_stat_row, container, false);
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