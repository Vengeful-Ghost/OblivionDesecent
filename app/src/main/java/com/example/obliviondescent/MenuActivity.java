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
    private SoundManager sounds;

    /**
     * Initialises the main menu, loads user data and checks for an existing save.
     * @param savedInstanceState previously saved instance state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        hideSystemUI();
        Wear.apply(findViewById(android.R.id.content));

        // Keep buttons clear of the camera cutout
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            v.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom);
            return insets;
        });

        dbManager = new DBManager();
        sounds = new SoundManager(this);
        sounds.setVolume(getSharedPreferences("gameSettings", MODE_PRIVATE).getFloat("sfx_volume", 1.0f));

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

        btnNewGame.setOnClickListener(v -> { click(); showNewGameConfirmation(); });

        btnLoadGame.setOnClickListener(v -> {
            click();
            if (existingGameId != null) {
                launchGame(existingGameId);
            }
        });

        btnStats.setOnClickListener(v -> {
            click();
            Intent intent = new Intent(this, StatsActivity.class);
            startActivity(intent);
        });

        btnSettings.setOnClickListener(v -> {
            click();
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btn_coop).setOnClickListener(v -> {
            click();
            startActivity(new Intent(this, LobbyActivity.class));
        });

        findViewById(R.id.btn_tutorial).setOnClickListener(v -> { click(); showTutorial(); });
        findViewById(R.id.btn_altar).setOnClickListener(v -> { click(); showAltar(); });
        findViewById(R.id.btn_daily).setOnClickListener(v -> { click(); showDaily(false); });
        findViewById(R.id.btn_quests).setOnClickListener(v -> { click(); showQuests(); });
        findViewById(R.id.btn_history).setOnClickListener(v -> { click(); showHistory(); });
        findViewById(R.id.btn_bestiary).setOnClickListener(v -> { click(); showBestiary(); });
        findViewById(R.id.btn_weekly).setOnClickListener(v -> { click(); showDaily(true); });
        Ads.gatherConsent(this);
    }

    private void click() {
        sounds.play(SoundManager.Sfx.CLICK);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sounds.release();
    }

    /** Soul Altar: spend Souls from past runs on permanent upgrades. */
    private void showAltar() {
        if (userId == null) return;
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_altar);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.findViewById(R.id.btn_close_altar).setOnClickListener(v -> { click(); dialog.dismiss(); });
        dialog.findViewById(R.id.btn_wardrobe).setOnClickListener(v -> { click(); showWardrobe(); });
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
                    dbManager.buyAltarRank(userId, a.name(), rank, cost, ok -> {
                        sounds.play(ok ? SoundManager.Sfx.PURCHASE : SoundManager.Sfx.GATE_LOCKED);
                        dbManager.loadPlayerStats(userId, fresh -> renderAltar(dialog, fresh));
                    });
                });
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(gap, gap, gap, gap);
            row.addView(cell, lp);
        }
        Wear.apply(grid);
    }

    /**
     * Today's Daily Run, or this week's challenge with its twist: the same seeded dungeon for everyone,
     * one life, and the top five. The week runs Monday to Sunday (ISO weeks, UTC).
     */
    private void showDaily(boolean weekly) {
        if (userId == null) return;
        java.util.TimeZone utc = java.util.TimeZone.getTimeZone("UTC");
        java.text.SimpleDateFormat dayFormat = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US);
        java.text.SimpleDateFormat shownFormat = new java.text.SimpleDateFormat("d MMM", java.util.Locale.US);
        dayFormat.setTimeZone(utc);
        shownFormat.setTimeZone(utc);
        java.util.Date now = new java.util.Date();
        String day = dayFormat.format(now);
        java.util.Calendar calendar = java.util.Calendar.getInstance(utc, java.util.Locale.US);
        calendar.setFirstDayOfWeek(java.util.Calendar.MONDAY);
        calendar.setMinimalDaysInFirstWeek(4);
        calendar.setTime(now);
        int week = calendar.get(java.util.Calendar.WEEK_OF_YEAR), weekYear = calendar.getWeekYear();
        GameActivity.WeeklyMod[] mods = GameActivity.WeeklyMod.values();
        GameActivity.WeeklyMod mod = mods[(weekYear * 53 + week) % mods.length];
        String key = weekly ? String.format(java.util.Locale.US, "W%d-%02d", weekYear, week) : day;
        long seed = weekly ? (weekYear * 100L + week) * 1_000_033L : Long.parseLong(day) * 1_000_003L;

        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_daily);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.findViewById(R.id.btn_close_daily).setOnClickListener(v -> { click(); dialog.dismiss(); });
        ((TextView) dialog.findViewById(R.id.txt_daily_date)).setText(weekly ? "Week " + week : shownFormat.format(now));
        if (weekly) {
            ((TextView) dialog.findViewById(R.id.txt_daily_title)).setText("WEEKLY CHALLENGE");
            ((TextView) dialog.findViewById(R.id.txt_daily_desc)).setText("This week: " + mod.title + ". "
                    + mod.rule + ". The same dungeon for everyone, one life, no saves.");
            ((TextView) dialog.findViewById(R.id.txt_daily_board_title)).setText("THIS WEEK'S BEST");
        }
        dialog.findViewById(R.id.btn_daily_play).setOnClickListener(v -> {
            click();
            dialog.dismiss();
            SoundManager.stopMenuMusic();
            Intent intent = new Intent(this, GameActivity.class);
            intent.putExtra(GameActivity.EXTRA_DAILY_SEED, seed);
            intent.putExtra(GameActivity.EXTRA_DAILY_DAY, key);
            if (weekly) intent.putExtra(GameActivity.EXTRA_WEEKLY_MOD, mod.name());
            startActivity(intent);
        });

        dbManager.loadDailyTop(key, rows -> {
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

    /** A titled list card (dialog_list) with a close button. */
    private Dialog listDialog(String title, String note) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_list);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ((TextView) dialog.findViewById(R.id.txt_list_title)).setText(title);
        ((TextView) dialog.findViewById(R.id.txt_list_note)).setText(note);
        dialog.findViewById(R.id.btn_close_list).setOnClickListener(v -> { click(); dialog.dismiss(); });
        return dialog;
    }

    /** Today's three quests: progress made in any mode, and Souls to claim once each is done. */
    private void showQuests() {
        if (userId == null) return;
        Dialog dialog = listDialog("DAILY QUESTS", "New ones at 00:00 UTC");
        LinearLayout rows = dialog.findViewById(R.id.list_rows);
        for (Quests q : Quests.ofDay(Quests.today())) {
            View row = getLayoutInflater().inflate(R.layout.item_quest, rows, false);
            int done = Quests.progress(this, q);
            ((TextView) row.findViewById(R.id.quest_title)).setText(q.title());
            ((TextView) row.findViewById(R.id.quest_progress)).setText(done + " / " + q.goal);
            android.widget.ProgressBar bar = row.findViewById(R.id.quest_bar);
            bar.setMax(q.goal);
            bar.setProgress(done);
            MaterialButton claim = row.findViewById(R.id.quest_claim);
            boolean claimed = Quests.claimed(this, q);
            claim.setText(claimed ? "Claimed" : "+" + q.souls + " Souls");
            claim.setEnabled(!claimed && done >= q.goal);
            claim.setAlpha(claim.isEnabled() ? 1f : 0.45f);
            claim.setOnClickListener(v -> {
                claim.setEnabled(false);
                claim.setAlpha(0.45f);
                claim.setText("Claimed");
                Quests.markClaimed(this, q);
                dbManager.addSouls(userId, q.souls);
                sounds.play(SoundManager.Sfx.PURCHASE);
            });
            rows.addView(row);
        }
        dialog.show();
        hideSystemUIOnWindow(dialog);
    }

    /** The last runs played on this phone, newest first (GameActivity.recordRun). */
    private void showHistory() {
        Dialog dialog = listDialog("RUN HISTORY", "Last 10 runs");
        LinearLayout rows = dialog.findViewById(R.id.list_rows);
        java.text.SimpleDateFormat when = new java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.US);
        org.json.JSONArray list;
        try {
            list = new org.json.JSONArray(getSharedPreferences("runs_" + userId, MODE_PRIVATE).getString("list", "[]"));
        } catch (org.json.JSONException e) {
            list = new org.json.JSONArray();
        }
        for (int i = 0; i < list.length(); i++) {
            org.json.JSONObject r = list.optJSONObject(i);
            if (r == null) continue;
            long secs = r.optLong("secs");
            View line = getLayoutInflater().inflate(R.layout.item_stat_row, rows, false);
            TextView label = line.findViewById(R.id.stat_label);
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.text_small));
            label.setText(when.format(new java.util.Date(r.optLong("t"))) + "   " + r.optString("mode")
                    + "   " + r.optString("weapon") + "   Lv " + r.optInt("level"));
            ((TextView) line.findViewById(R.id.stat_value)).setText("Floor " + r.optInt("floor") + "   "
                    + r.optInt("kills") + " kills   " + secs / 60 + ":"
                    + String.format(java.util.Locale.US, "%02d", secs % 60));
            rows.addView(line);
        }
        if (list.length() == 0) {
            View line = getLayoutInflater().inflate(R.layout.item_stat_row, rows, false);
            ((TextView) line.findViewById(R.id.stat_label)).setText("No runs yet. Your fallen runs will show here.");
            ((TextView) line.findViewById(R.id.stat_value)).setText("");
            rows.addView(line);
        }
        dialog.show();
        hideSystemUIOnWindow(dialog);
    }

    // Bestiary, in the order the dungeon introduces them: {key (GameActivity.bestiary), name, how to fight it}
    private static final String[][] BESTIARY = {
            {"GRUNT", "Grunt", "Slow club swings. Step out of the wind-up, then hit back. A raised guard needs a heavy blow."},
            {"BAT", "Bat", "Fragile and fast, it comes in flocks. One clean hit drops it."},
            {"SPEEDER", "Speeder", "Darts in for quick stabs. Swing as it closes, or dash through it."},
            {"ARCHER", "Archer", "Keeps its distance and fires aimed arrows. Close the gap or put a wall between you."},
            {"BOMBER", "Bomber", "Runs at you and blows up. Kill it early or dash clear of the blast."},
            {"BRUTE", "Brute", "A heavy axe, and it guards. Charge a heavy attack to break the guard."},
            {"CHARGER", "Charger", "Lowers its horns and rushes in a line. Sidestep, and let it crash into a wall."},
            {"NECROMANCER", "Necromancer", "Raises the fallen as thralls. Kill it first, before its army grows."},
            {"TANK", "Tank", "Huge health and a crushing hammer. Strike after its slam; never trade blows."},
            {"THIEF", "Treasure Thief", "Turns up now and then with a purse of gold. Every hit spills coins; catch it before it slips away and the purse is yours, with a key."},
            {"BOSS_BRUTE", "Warlord", "Slams and charges. Below half health it whirls; below a quarter the ground quakes."},
            {"BOSS_SUMMONER", "Summoner", "Keeps away, raises thralls and fires bursts. Cut through to it."},
            {"BOSS_SORCERER", "Sorcerer", "Blinks beside you and fires aimed fans; wounded, it spins storms of bolts."},
            {"BOSS_HUNTER", "Hunter", "Leaps on you and looses volleys, then calls its hounds."},
    };

    /** Every kind met in the dungeon with its kills, four to a row; tap one to read how to fight it. */
    private void showBestiary() {
        if (userId == null) return;
        SharedPreferences prefs = getSharedPreferences("bestiary_" + userId, MODE_PRIVATE);
        int found = 0;
        for (String[] b : BESTIARY) if (prefs.getBoolean("met_" + b[0], false)) found++;
        Dialog dialog = listDialog("BESTIARY", found + " / " + BESTIARY.length + " found");
        LinearLayout rows = dialog.findViewById(R.id.list_rows);
        float dp = getResources().getDisplayMetrics().density;
        int gap = Math.round(3 * dp);
        TextView tip = new TextView(this, null, 0, R.style.OD_Text);
        tip.setGravity(android.view.Gravity.CENTER);
        tip.setTextColor(getColor(R.color.bone_dim));
        tip.setPadding(0, Math.round(8 * dp), 0, 0);
        tip.setText("Tap a creature you've met to learn how to fight it.");
        LinearLayout row = null;
        for (int i = 0; i < BESTIARY.length; i++) {
            String[] b = BESTIARY[i];
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                rows.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.round(44 * dp)));
            }
            boolean met = prefs.getBoolean("met_" + b[0], false);
            View cell = getLayoutInflater().inflate(R.layout.item_stat_cell, row, false);
            ((android.widget.ImageView) cell.findViewById(R.id.stat_icon))
                    .setImageResource(b[0].startsWith("BOSS_") ? R.drawable.ic_trophy : R.drawable.ic_skull);
            ((TextView) cell.findViewById(R.id.stat_value)).setText(met ? b[1] : "???");
            ((TextView) cell.findViewById(R.id.stat_label)).setText(met ? prefs.getInt("kills_" + b[0], 0) + " slain" : "Not met");
            cell.setAlpha(met ? 1f : 0.45f);
            if (met) cell.setOnClickListener(v -> { click(); tip.setText(b[1] + ": " + b[2]); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(gap, gap, gap, gap);
            row.addView(cell, lp);
        }
        // Fillers keep the last row's cells the same width as the others
        while (row != null && row.getChildCount() < 4)
            row.addView(new View(this), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        rows.addView(tip);
        dialog.show();
        hideSystemUIOnWindow(dialog);
    }

    /** Wardrobe: looks bought with Souls for the body, the swing trail and the death burst. */
    private void showWardrobe() {
        if (userId == null) return;
        dbManager.loadPlayerStats(userId, data -> {
            Object soulsValue = data != null ? data.get("souls") : null;
            long souls = soulsValue instanceof Number ? ((Number) soulsValue).longValue() : 0;
            java.util.Set<String> owned = new java.util.HashSet<>();
            Object ownedValue = data != null ? data.get("cosmetics") : null;
            if (ownedValue instanceof java.util.List)
                for (Object o : (java.util.List<?>) ownedValue) owned.add(String.valueOf(o));
            renderWardrobe(souls, owned);
        });
    }

    /** One row per slot; tap an owned look to wear it, or an affordable one to buy and wear it. */
    private void renderWardrobe(long souls, java.util.Set<String> owned) {
        Dialog dialog = listDialog("WARDROBE", souls + " Souls");
        LinearLayout rows = dialog.findViewById(R.id.list_rows);
        SharedPreferences look = getSharedPreferences("cosmetics_" + userId, MODE_PRIVATE);
        float dp = getResources().getDisplayMetrics().density;
        int gap = Math.round(3 * dp);
        String[] slots = {"Body", "Swing trail", "Death burst"};
        int[] icons = {R.drawable.ic_helmet, R.drawable.ic_sword, R.drawable.ic_skull};
        for (int slot = 0; slot < slots.length; slot++) {
            TextView label = new TextView(this, null, 0, R.style.OD_Text_Caption);
            label.setText(slots[slot]);
            rows.addView(label);
            LinearLayout row = new LinearLayout(this);
            rows.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.round(44 * dp)));
            GameActivity.Cosmetic worn = GameActivity.Cosmetic.worn(look, slot);
            for (GameActivity.Cosmetic c : GameActivity.Cosmetic.values()) {
                if (c.slot != slot) continue;
                boolean have = c.price == 0 || owned.contains(c.name());
                View cell = getLayoutInflater().inflate(R.layout.item_stat_cell, row, false);
                android.widget.ImageView icon = cell.findViewById(R.id.stat_icon);
                icon.setImageResource(icons[slot]);
                if (c.color != 0) icon.setImageTintList(android.content.res.ColorStateList.valueOf(c.color));
                ((TextView) cell.findViewById(R.id.stat_value)).setText(c.title);
                ((TextView) cell.findViewById(R.id.stat_label)).setText(
                        c == worn ? "Worn" : have ? "Owned" : c.price + " Souls");
                cell.setAlpha(have || souls >= c.price ? 1f : 0.45f);
                cell.setOnClickListener(v -> {
                    click();
                    if (have) {
                        look.edit().putString("slot" + c.slot, c.name()).apply();
                        dialog.dismiss();
                        showWardrobe();
                    } else if (souls >= c.price) {
                        dbManager.buyCosmetic(userId, c.name(), c.price, ok -> {
                            sounds.play(ok ? SoundManager.Sfx.PURCHASE : SoundManager.Sfx.GATE_LOCKED);
                            if (ok) look.edit().putString("slot" + c.slot, c.name()).apply();
                            dialog.dismiss();
                            showWardrobe();
                        });
                    } else {
                        sounds.play(SoundManager.Sfx.GATE_LOCKED);
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                lp.setMargins(gap, gap, gap, gap);
                row.addView(cell, lp);
            }
            while (row.getChildCount() < 5)
                row.addView(new View(this), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        }
        dialog.show();
        hideSystemUIOnWindow(dialog);
    }

    private void hideSystemUIOnWindow(Dialog dialog) {
        if (dialog.getWindow() == null) return;
        dialog.getWindow().getDecorView().setSystemUiVisibility(
                getWindow().getDecorView().getSystemUiVisibility());
        Wear.apply(dialog.getWindow().getDecorView());
    }

    // Icon of each tutorial page, one per page of TUTORIAL (showTutorial falls back to the last for any extra)
    private static final int[] TUTORIAL_ICONS = {
            R.drawable.ic_boots, R.drawable.ic_sword, R.drawable.ic_warning,
            R.drawable.ic_stairs, R.drawable.ic_key, R.drawable.ic_map, R.drawable.ic_level
    };

    // Title and text of each tutorial page
    private static final String[][] TUTORIAL = {
            {"Move & Dash",
                    "Drag the left stick to move. You face the way you walk.\n\n"
                    + "Tap D to dash. Nothing can hit you mid-dash. Dash just as a hit lands for a PERFECT dodge: "
                    + "time slows, the dash recharges and your next hits are sure crits."},
            {"Attack",
                    "Hold ATK to keep attacking. It aims at the nearest enemy; drag it to aim yourself.\n\n"
                    + "Every weapon swings its own way: daggers are quick, axes sweep wide, spears reach far and "
                    + "hammers end their combo with a slam. Gold numbers are critical hits.\n\n"
                    + "Hold the anvil to charge a HEAVY blow, let go to strike. SPIN hits everything around you."},
            {"Read the Enemy",
                    "A red ! means an enemy is about to strike. The red cone or circle shows where it lands, so step out or dash.\n\n"
                    + "A bright arc means it is guarding: hit it from behind, or break the guard with a heavy blow.\n\n"
                    + "A finisher or critical hit during a wind-up staggers the enemy. Glowing elites always drop a key."},
            {"Floors & Shop",
                    "Defeat the enemies on a floor to open the purple EXIT portal. Edge arrows point the way.\n\n"
                    + "The gold SHOP portal sells gear for the coins you collect. Every 5th floor has a boss.\n\n"
                    + "Tap the minimap to see the whole floor."},
            {"Treasure & Keys",
                    "Gold-marked rooms hold a treasure chest, but some chests are mimics that bite back.\n\n"
                    + "A gold gate seals a vault. Walk up to it with a key to open it: vaults hold the best loot. "
                    + "Keys drop from elites, mimics and now and then other enemies."},
            {"Event Rooms & Depths",
                    "Side rooms off the path hold events: a blood shrine, a gambler, a cursed crypt, a trial arena, "
                    + "a prisoner, a forgotten library or a healing spring. Walk up to what waits inside.\n\n"
                    + "Every 5 floors, past each boss, the dungeon changes: flooded catacombs slow your steps, "
                    + "the forge's vents erupt, and in the void the light reaches less far."},
            {"Grow Stronger",
                    "Kills give XP. When you level up, tap the glowing badge and pick a perk. "
                    + "Two perks at 3 stacks each awaken a synergy.\n\n"
                    + "Every run earns Souls, even the ones you lose: spend them at the Soul Altar on lasting upgrades.\n\n"
                    + "Your run saves at the start of every floor. Try the Daily Run to race everyone on the same dungeon."}
    };

    /** New-player guide: one card, Back / Next flip its page in place. */
    private void showTutorial() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_tutorial);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.findViewById(R.id.btn_close_tutorial).setOnClickListener(v -> { click(); dialog.dismiss(); });

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
            ((android.widget.ImageView) dialog.findViewById(R.id.tutorial_icon))
                    .setImageResource(TUTORIAL_ICONS[Math.min(p, TUTORIAL_ICONS.length - 1)]);
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
            sounds.play(SoundManager.Sfx.CARD_FLIP);
            page[0]--;
            render.run();
        });
        dialog.findViewById(R.id.btn_tutorial_next).setOnClickListener(v -> {
            sounds.play(SoundManager.Sfx.CARD_FLIP);
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
        Dialog invite = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        invite.setContentView(R.layout.dialog_confirm);
        invite.setCancelable(false);
        if (invite.getWindow() != null)
            invite.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ((android.widget.ImageView) invite.findViewById(R.id.confirm_icon)).setImageResource(R.drawable.ic_crossed_swords);
        ((TextView) invite.findViewById(R.id.confirm_title)).setText("Party invite");
        ((TextView) invite.findViewById(R.id.confirm_message)).setText(
                (fromName != null ? fromName : "A friend") + " invited you to a co-op run.");
        MaterialButton decline = invite.findViewById(R.id.btn_confirm_no);
        MaterialButton join = invite.findViewById(R.id.btn_confirm_yes);
        decline.setText("Decline");
        join.setText("Join");
        // Joining isn't destructive, so it gets the primary look instead of the card's red one
        join.setBackgroundResource(R.drawable.btn_login);
        join.setTextColor(getColor(R.color.gold_glow));
        decline.setOnClickListener(v -> {
            click();
            invite.dismiss();
            dbManager.deleteInvite(inviteId);
        });
        join.setOnClickListener(v -> {
            click();
            invite.dismiss();
            dbManager.deleteInvite(inviteId);
            Intent intent = new Intent(this, LobbyActivity.class);
            intent.putExtra(LobbyActivity.EXTRA_JOIN_CODE, code);
            startActivity(intent);
        });
        invite.show();
        hideSystemUIOnWindow(invite);
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

        // Existing save — confirm overwrite on the game's own confirm card
        Dialog confirm = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        confirm.setContentView(R.layout.dialog_confirm);
        if (confirm.getWindow() != null)
            confirm.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ((android.widget.ImageView) confirm.findViewById(R.id.confirm_icon)).setImageResource(R.drawable.ic_warning);
        ((TextView) confirm.findViewById(R.id.confirm_title)).setText("Start a new run?");
        ((TextView) confirm.findViewById(R.id.confirm_message)).setText(
                "Your current save will be deleted for good.");
        MaterialButton no = confirm.findViewById(R.id.btn_confirm_no);
        MaterialButton yes = confirm.findViewById(R.id.btn_confirm_yes);
        no.setText("Keep save");
        yes.setText("New run");
        no.setOnClickListener(v -> { click(); confirm.dismiss(); });
        yes.setOnClickListener(v -> {
            click();
            confirm.dismiss();
            deleteSaveAndCreateNew();
        });
        confirm.show();
        hideSystemUIOnWindow(confirm);
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