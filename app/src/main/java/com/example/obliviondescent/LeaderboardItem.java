package com.example.obliviondescent;

/**
 * LeaderboardItem
 * Data model for a single row in the leaderboard RecyclerView.
 * Holds both display data and the userId needed for follow/unfollow.
 */
public class LeaderboardItem {
    public String userId;
    public String username;
    public int    highestFloor;
    public int    enemiesKilled;
    public int    bossesKilled;
    public int    timesDied;
    public long   playtimeSeconds;
    public int    rank;

    public LeaderboardItem(String userId, String username, int highestFloor,
                           int enemiesKilled, int bossesKilled,
                           int timesDied, long playtimeSeconds, int rank) {
        this.userId         = userId;
        this.username       = username;
        this.highestFloor   = highestFloor;
        this.enemiesKilled  = enemiesKilled;
        this.bossesKilled   = bossesKilled;
        this.timesDied      = timesDied;
        this.playtimeSeconds = playtimeSeconds;
        this.rank           = rank;
    }
}