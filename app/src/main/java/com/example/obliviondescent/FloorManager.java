package com.example.obliviondescent;

/**
 * Manages all floor-progression state.
 * Tracks the current floor, kill count, portal position and boss states.
 * GameActivity calls onEnemyKilled() on every enemy death.
 * GameActivity calls advanceFloor() after the floor-transition animation completes.
 * GameDisplay reads getPortalX/Y() and isPortalOpen() for rendering.
 */
public class FloorManager {

    /* ============================================================
       CONSTANTS
       ============================================================ */

    // How close the player must be to enter the portal
    public static final float PORTAL_RADIUS = 60f;

    // How far from the player the portal spawns (world units)
    private static final float PORTAL_SPAWN_DISTANCE = 700f;

    public static final float SHOP_PORTAL_RADIUS = 60f;

    /* ============================================================
       FLOOR STATE
       ============================================================ */

    private int currentFloor = 1;

    // How many enemies must die before the portal opens
    private int killsRequired;

    // How many enemies have been killed on this floor so far
    private int killsThisFloor = 0;

    // Whether the exit portal is currently active
    private boolean portalOpen = false;

    // World-space position of the portal
    private float portalX = 0f;
    private float portalY = 0f;

    private boolean bossFloor = false;
    private boolean bossSpawned = false;
    private boolean bossDefeated = false;

    private boolean shopPortalOpen = false;
    private float shopPortalX = 0f;
    private float shopPortalY = 0f;
    private static final float SHOP_PORTAL_SPAWN_DISTANCE = 500f;

    // Exit room center — set by GameActivity after layout generation
    private float exitRoomCenterX = 0f;
    private float exitRoomCenterY = 0f;
    private boolean hasExitRoom   = false;

    // Vertical offset between exit portal and shop portal inside the exit room
    private static final float PORTAL_OFFSET = 120f;

    /* ============================================================
       CONSTRUCTOR
       ============================================================ */

    public FloorManager() {
        // Calculate kills needed for floor 1
        killsRequired = calculateKillsRequired();
        bossFloor = (currentFloor % 5 == 0);
    }

    /* ============================================================
       CALLED EVERY TIME AN ENEMY DIES
       ============================================================ */

    /**
     * GameActivity calls this inside cleanupDeadEnemies().
     * We count the kill and open the portal if the floor target is reached.
     *
     * playerX/Y is passed in so the portal can spawn near the player,
     * not at some distant world coordinate they'd never find.
     */
    public void onEnemyKilled(float playerX, float playerY) {
        killsThisFloor++;

        if (killsThisFloor >= killsRequired && !portalOpen) {
            if (bossFloor && !bossSpawned) {
                // Signal GameActivity to spawn the boss — don't open portal yet
                bossSpawned = true;
            } else if (!bossFloor) {
                openPortal(playerX, playerY);
            }
        }
    }

    /**
     * Called after the boss is defeated. Opens the exit portal.
     * @param playerX current X position of the player
     * @param playerY current Y position of the player
     */
    public void onBossDefeated(float playerX, float playerY) {
        bossDefeated = true;
        openPortal(playerX, playerY);
    }

    /* ============================================================
       PORTAL
       ============================================================ */

    /** Called by GameActivity after each layout is generated. */
    public void setExitRoomCenter(float x, float y) {
        exitRoomCenterX = x;
        exitRoomCenterY = y;
        hasExitRoom     = true;
    }

    private void openPortal(float playerX, float playerY) {
        portalOpen = true;

        if (hasExitRoom) {
            // Portals live inside the exit room
//            boolean hasShop = !bossFloor && currentFloor >= 2;
            boolean hasShop = !bossFloor;
            if (hasShop) {
                // Exit portal slightly above center, shop portal slightly below
                portalX = exitRoomCenterX;
                portalY = exitRoomCenterY - PORTAL_OFFSET;
                shopPortalOpen = true;
                shopPortalX    = exitRoomCenterX;
                shopPortalY    = exitRoomCenterY + PORTAL_OFFSET;
            } else {
                portalX = exitRoomCenterX;
                portalY = exitRoomCenterY;
            }
        } else {
            // Fallback: random position near player (open-world mode)
            double angle = Math.random() * Math.PI * 2;
            portalX = playerX + (float)(Math.cos(angle) * PORTAL_SPAWN_DISTANCE);
            portalY = playerY + (float)(Math.sin(angle) * PORTAL_SPAWN_DISTANCE);

            //Spawn Shop portal only when the floor cleared isn't a boss floor
//            if (!bossFloor  && currentFloor >= 2) {
            if (!bossFloor) {
                shopPortalOpen = true;
                double shopAngle = angle + Math.PI;
                shopPortalX = playerX + (float)(Math.cos(shopAngle) * SHOP_PORTAL_SPAWN_DISTANCE);
                shopPortalY = playerY + (float)(Math.sin(shopAngle) * SHOP_PORTAL_SPAWN_DISTANCE);
            }
        }
    }

    /* ============================================================
       ADVANCE TO NEXT FLOOR
       ============================================================ */

    /**
     * Called by GameActivity AFTER the transition animation finishes.
     * Resets all per-floor state and increments the floor counter.
     */
    public void advanceFloor() {
        currentFloor++;
        killsThisFloor = 0;
        portalOpen = false;
        bossSpawned = false;
        bossDefeated = false;
        killsRequired = calculateKillsRequired();
        bossFloor = (currentFloor % 5 == 0);
        shopPortalOpen = false;
        shopPortalX = 0f;
        shopPortalY = 0f;
        hasExitRoom = false;
    }

    /* ============================================================
       DIFFICULTY SCALING
       ============================================================ */

    /**
     * Calculates the number of kills required to open the portal on the current floor.
     * @return required kill count
     */
    private int calculateKillsRequired() {
        return 6 + currentFloor / 3;
    }

    /**
     * Sets the current floor and resets all floor state. Used when loading a saved game.
     * @param floor the floor number to restore
     */
    // Add this setter so GameActivity can restore floor on load
    public void setCurrentFloor(int floor) {
        this.currentFloor = floor;
        this.killsRequired = calculateKillsRequired();
        bossFloor = (floor % 5 == 0);
        this.killsThisFloor = 0;
        this.portalOpen = false;
        this.shopPortalOpen = false;
        this.shopPortalX = 0f;
        this.shopPortalY = 0f;
        this.bossSpawned = false;
        this.bossDefeated = false;
        this.hasExitRoom = false;
    }

    /**
     * Restores kill progress from a save. Call after setExitRoomCenter() so portals land in the exit room.
     * @param kills enemies already killed on this floor
     * @param bossKilled whether this floor's boss was already defeated
     */
    public void restoreProgress(int kills, boolean bossKilled) {
        killsThisFloor = Math.min(kills, killsRequired);
        if (killsThisFloor < killsRequired) return;
        if (!bossFloor)      openPortal(exitRoomCenterX, exitRoomCenterY);
        else if (bossKilled) onBossDefeated(exitRoomCenterX, exitRoomCenterY);
        else                 bossSpawned = true;
    }

    /* ============================================================
       GETTERS
       ============================================================ */

    public int getCurrentFloor()    { return currentFloor; }
    public boolean isPortalOpen()   { return portalOpen; }
    public float getPortalX()       { return portalX; }
    public float getPortalY()       { return portalY; }
    public int getKillsThisFloor()  { return killsThisFloor; }
    public int getKillsRequired()   { return killsRequired; }
    public boolean isBossFloor()    { return bossFloor; }
    public boolean shouldSpawnBoss(){ return bossSpawned && !bossDefeated; }
    public boolean isBossDefeated() { return bossDefeated; }
    public boolean isShopPortalOpen() { return shopPortalOpen; }
    public float   getShopPortalX()   { return shopPortalX; }
    public float   getShopPortalY()   { return shopPortalY; }
}