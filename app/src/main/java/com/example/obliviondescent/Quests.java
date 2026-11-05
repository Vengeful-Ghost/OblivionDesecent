package com.example.obliviondescent;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Daily quests: three a day, the same for everyone (picked from the UTC date), each paying Souls once.
 * Progress lives on this phone per account, in SharedPreferences "quests_<uid>", and resets every day.
 */
enum Quests {
    KILLS("Slay %d enemies", 60, 25),
    ELITES("Defeat %d elites", 2, 30),
    BOSS("Defeat %d boss", 1, 40),
    FLOORS("Clear %d floors", 4, 25),
    GUARDS("Break %d guards", 4, 25),
    SLAMS("Slam %d enemies into walls", 5, 25),
    HEAVY("Land %d heavy blows", 15, 20),
    CRATES("Smash %d crates or barrels", 8, 15),
    CHESTS("Open %d chests", 3, 25),
    DODGES("Pull off %d perfect dodges", 3, 30),
    GOLD("Collect %d gold", 250, 20),
    BATS("Swat %d bats", 10, 20);

    final String text;
    final int goal, souls;

    Quests(String text, int goal, int souls) {
        this.text = text;
        this.goal = goal;
        this.souls = souls;
    }

    String title() {
        return String.format(Locale.US, text, goal);
    }

    /** Today in UTC as yyyyMMdd, the same day as the Daily Run. */
    static String today() {
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyyMMdd", Locale.US);
        format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return format.format(new java.util.Date());
    }

    /** The day's three quests, the same on every phone. */
    static Quests[] ofDay(String day) {
        List<Quests> all = new ArrayList<>(Arrays.asList(values()));
        Collections.shuffle(all, new Random(Long.parseLong(day) * 31L + 7L));
        return new Quests[]{all.get(0), all.get(1), all.get(2)};
    }

    private static SharedPreferences prefs(Context context) {
        String uid = context.getSharedPreferences("loginPrefs", Context.MODE_PRIVATE).getString("user_id", "guest");
        SharedPreferences prefs = context.getSharedPreferences("quests_" + uid, Context.MODE_PRIVATE);
        String day = today();
        if (!day.equals(prefs.getString("day", ""))) prefs.edit().clear().putString("day", day).apply();
        return prefs;
    }

    /** Counts progress if q is one of today's quests; true the moment it is completed. */
    static boolean add(Context context, Quests q, int amount) {
        if (!Arrays.asList(ofDay(today())).contains(q)) return false;
        SharedPreferences prefs = prefs(context);
        int before = prefs.getInt(q.name(), 0);
        if (before >= q.goal) return false;
        int after = Math.min(q.goal, before + amount);
        prefs.edit().putInt(q.name(), after).apply();
        return after >= q.goal;
    }

    static int progress(Context context, Quests q) {
        return prefs(context).getInt(q.name(), 0);
    }

    static boolean claimed(Context context, Quests q) {
        return prefs(context).getBoolean("claimed_" + q.name(), false);
    }

    static void markClaimed(Context context, Quests q) {
        prefs(context).edit().putBoolean("claimed_" + q.name(), true).apply();
    }
}
