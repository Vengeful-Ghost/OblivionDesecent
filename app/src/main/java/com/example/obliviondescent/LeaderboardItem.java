package com.example.obliviondescent;

/**
 * מודל נתונים עבור שורה בודדת ב-RecyclerView של לוח המובילים.
 * מחזיקה נתוני הצגה ו-userId הנדרש לפעולות עקוב/הפסק עקוב.
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
     * יוצר פריט לוח מובילים חדש.
     * @param userId מזהה המשתמש ב-Firebase
     * @param username שם המשתמש להצגה
     * @param highestFloor הקומה הגבוהה ביותר שהגיע אליה
     * @param enemiesKilled סך אויבים שנהרגו
     * @param bossesKilled סך בוסים שנהרגו
     * @param timesDied מספר פעמי מוות
     * @param playtimeSeconds סך שניות משחק
     * @param rank דירוג בלוח המובילים
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