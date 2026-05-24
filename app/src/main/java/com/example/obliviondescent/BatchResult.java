package com.example.obliviondescent;

import java.util.List;
import java.util.Map;

public class BatchResult {
    public List<EnemyTemplate>       enemyPool;         // 5 unique enemies for this range
    public Map<Integer, List<ShopItem>> shopByFloor;    // floor number → 6 shop items
    public String bossName;
    public String bossTitle;
    public int    bossColor;

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