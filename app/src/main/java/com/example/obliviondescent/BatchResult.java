package com.example.obliviondescent;

import java.util.List;
import java.util.Map;

/**
 * מחזיקה את כל הנתונים שנוצרו על ידי Gemini AI עבור טווח קומות אחד.
 * כוללת תבניות אויבים, פריטי חנות לפי קומה, ומידע בוס.
 */
public class BatchResult {
    /** מאגר תבניות אויבים ייחודיות לטווח הקומות. */
    public List<EnemyTemplate>       enemyPool;         // 5 unique enemies for this range
    /** מיפוי מספר קומה → 6 פריטי חנות. */
    public Map<Integer, List<ShopItem>> shopByFloor;    // floor number → 6 shop items
    /** שם הבוס שנוצר. */
    public String bossName;
    /** תואר הבוס שנוצר. */
    public String bossTitle;
    /** צבע הבוס. */
    public int    bossColor;

    /**
     * יוצר תוצאת batch חדשה עם כל הנתונים שנוצרו.
     * @param enemyPool מאגר תבניות האויבים
     * @param shopByFloor מיפוי פריטי חנות לפי קומה
     * @param bossName שם הבוס
     * @param bossTitle תואר הבוס
     * @param bossColor צבע הבוס
     */
    public BatchResult(List<EnemyTemplate> enemyPool,
                       Map<Integer, List<ShopItem>> shopByFloor,
                       String bossName, String bossTitle, int bossColor) {
        this.enemyPool   = enemyPool;
        this.shopByFloor = shopByFloor;
        this.bossName    = bossName;
        this.bossTitle   = bossTitle;
        this.bossColor   = bossColor;
    }
}