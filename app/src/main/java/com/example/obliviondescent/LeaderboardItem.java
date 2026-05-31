package com.example.obliviondescent;

/**
 * Data model for a single row in the leaderboard RecyclerView.
 * Holds display data and the userId required for follow/unfollow operations.
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

    /**
     * Creates a new leaderboard item.
     * @param userId Firebase user ID
     * @param username display username
     * @param highestFloor highest floor reached
     * @param enemiesKilled total enemies killed
     * @param bossesKilled total bosses killed
     * @param timesDied number of deaths
     * @param playtimeSeconds total playtime in seconds
     * @param rank leaderboard rank
     */
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