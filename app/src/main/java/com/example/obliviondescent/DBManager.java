package com.example.obliviondescent;

import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * מנהלת את כל הגישה ל-Firestore.
 * אחראית על אימות, שמירת משחק, סטטיסטיקות, לוח מובילים ומערכת עקוב.
 *
 * אוספים ב-Firestore:
 *   players/{userId}           — סטטיסטיקות לכל הזמן + מזהי חריצי משחק
 *   games/{gameId}             — מצב שמירה לכל ריצה
 *   generated_weapons/{id}     — נשק שנוצר על ידי AI או מערכת
 *   generated_armors/{id}      — שריון שנוצר על ידי AI או מערכת
 *   generated_enemies/{id}     — אויבים שנוצרו על ידי AI או מערכת
 */
public class DBManager {

    private static final String TAG = "DBManager";

    private final FirebaseFirestore db;

    /* ============================================================
       CALLBACKS
       ============================================================ */

    public interface OnUsernameCheckListener {
        void onCheckCompleted(boolean exists);
    }

    public interface OnLoginCheckListener {
        void onLoginCheckCompleted(boolean isValid, String userId);
    }

    public interface OnCompleteListener {
        void onComplete(boolean success);
    }

    public interface OnGameSlotsLoadedListener {
        void onLoaded(List<Map<String, Object>> slots);
    }

    public interface OnGameLoadedListener {
        void onLoaded(Map<String, Object> data); // null if not found
    }

    public interface OnStringResultListener {
        void onResult(String result); // returns generated document ID
    }

    /* ============================================================
       CONSTRUCTOR
       ============================================================ */

    public DBManager() {
        db = FirebaseFirestore.getInstance();
    }

    /* ============================================================
       AUTH — unchanged
       ============================================================ */

    /**
     * מוסיפה משתמש חדש לאוסף users (שיטה ישנה, לגיבוי).
     * @param username שם המשתמש
     * @param password הסיסמה
     */
    public void insertUser(String username, String password) {
        Map<String, Object> user = new HashMap<>();
        user.put("username", username);
        user.put("password", password);

        db.collection("users")
                .document(username)
                .set(user)
                .addOnSuccessListener(v -> Log.d(TAG, "User added"))
                .addOnFailureListener(e -> Log.e(TAG, "insertUser failed", e));
    }

    /**
     * בודקת אם שם המשתמש קיים ב-Firestore.
     * @param username שם המשתמש לבדיקה
     * @param listener מאזין המקבל true אם קיים
     */
    public void checkUsername(String username, OnUsernameCheckListener listener) {
        db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful())
                        listener.onCheckCompleted(!task.getResult().isEmpty());
                    else {
                        Log.e(TAG, "checkUsername failed", task.getException());
                        listener.onCheckCompleted(false);
                    }
                });
    }

    /**
     * מאמתת פרטי כניסה מול Firebase Auth.
     * @param username שם המשתמש
     * @param password הסיסמה
     * @param listener מאזין המקבל (isValid, userId)
     */
    public void checkUserCredentials(String username, String password, OnLoginCheckListener listener) {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        mAuth.signInWithEmailAndPassword(username + "@app.com", password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        listener.onLoginCheckCompleted(true, mAuth.getCurrentUser().getUid());
                    } else {
                        listener.onLoginCheckCompleted(false, null);
                    }
                });
    }

    /* ============================================================
       PLAYERS — lifetime stats
       ============================================================ */

    /**
     * Creates the player document on first login if it doesn't exist.
     * Safe to call on every login — uses set() with merge so existing data is preserved.
     */
    public void initPlayerDocument(String userId, String username) {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("username",        username);
        defaults.put("floorsCleared",   0);
        defaults.put("enemiesKilled",   0);
        defaults.put("timesDied",       0);
        defaults.put("bossesKilled",    0);
        defaults.put("highestFloor",    1);
        defaults.put("playtimeSeconds", 0L);
        defaults.put("coinsCollected",  0);
        defaults.put("coinsSpent",      0);
        defaults.put("damageGiven",     0);
        defaults.put("damageTaken",     0);
        defaults.put("gameSlots",       new ArrayList<>());

        db.collection("players")
                .document(userId)
                .set(defaults, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener(e -> Log.e(TAG, "initPlayerDocument failed", e));
    }

    /**
     * Increments lifetime stats atomically.
     * Pass 0 for any stat you don't want to update.
     */
    public void incrementPlayerStats(String userId,
                                     int floorsCleared,
                                     int enemiesKilled,
                                     int timesDied,
                                     int bossesKilled,
                                     int coinsCollected,
                                     int coinsSpent,
                                     int damageGiven,
                                     int damageTaken,
                                     long playtimeSeconds) {
        Map<String, Object> updates = new HashMap<>();
        if (floorsCleared  > 0) updates.put("floorsCleared",   FieldValue.increment(floorsCleared));
        if (enemiesKilled  > 0) updates.put("enemiesKilled",   FieldValue.increment(enemiesKilled));
        if (timesDied      > 0) updates.put("timesDied",       FieldValue.increment(timesDied));
        if (bossesKilled   > 0) updates.put("bossesKilled",    FieldValue.increment(bossesKilled));
        if (coinsCollected > 0) updates.put("coinsCollected",  FieldValue.increment(coinsCollected));
        if (coinsSpent     > 0) updates.put("coinsSpent",      FieldValue.increment(coinsSpent));
        if (damageGiven    > 0) updates.put("damageGiven",     FieldValue.increment(damageGiven));
        if (damageTaken    > 0) updates.put("damageTaken",     FieldValue.increment(damageTaken));
        if (playtimeSeconds > 0) updates.put("playtimeSeconds", FieldValue.increment(playtimeSeconds));

        if (!updates.isEmpty()) {
            db.collection("players")
                    .document(userId)
                    .update(updates)
                    .addOnFailureListener(e -> Log.e(TAG, "incrementPlayerStats failed", e));
        }
    }

    /**
     * טוענת את מסמך הסטטיסטיקות של השחקן מ-Firestore.
     * @param userId מזהה המשתמש
     * @param listener מאזין המקבל מפת נתונים גולמית
     */
    public void loadPlayerStats(String userId, OnGameLoadedListener listener) {
        db.collection("players")
                .document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) listener.onLoaded(doc.getData());
                    else              listener.onLoaded(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "loadPlayerStats failed", e);
                    listener.onLoaded(null);
                });
    }

    /**
     * Updates highestFloor only if the new value is greater.
     * Must be done as a transaction to be safe.
     */
    public void updateHighestFloor(String userId, int floor) {
        db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(
                    db.collection("players").document(userId));
            long current = snap.getLong("highestFloor") != null
                    ? snap.getLong("highestFloor") : 1;
            if (floor > current) {
                transaction.update(
                        db.collection("players").document(userId),
                        "highestFloor", floor);
            }
            return null;
        }).addOnFailureListener(e -> Log.e(TAG, "updateHighestFloor failed", e));
    }

    /* ============================================================
       GAMES — save slots (max 3 per player)
       ============================================================ */

    /**
     * Creates a new game save and links it to the player's gameSlots array.
     * Returns the new gameId via listener.
     *
     * inventoryWeaponIds / inventoryArmorIds are lists of generated_weapons /
     * generated_armors document IDs. Empty lists for a new game.
     *
     * items is a list of maps: [{ "name": "Health Potion", "quantity": 3 }]
     * — designed to be flexible for future consumable types.
     */
    public void createGameSave(String userId,
                               int slotIndex,
                               OnStringResultListener listener) {
        // Build the initial save — fresh game defaults
        Map<String, Object> save = new HashMap<>();
        save.put("userId",             userId);
        save.put("slotIndex",          slotIndex);
        save.put("currentFloor",       1);
        save.put("health",             100);
        save.put("armorValue",         0);     // Current numeric armor HP

        // Equipped items — null means default (Fists / no armor)
        save.put("equippedWeaponId",   null);
        save.put("equippedWeaponName", "Fists");
        save.put("equippedHeadId",     null);
        save.put("equippedChestId",    null);
        save.put("equippedLegsId",     null);
        save.put("equippedBootsId",    null);

        // Inventory lists — document IDs referencing generated collections
        save.put("inventoryWeaponIds", new ArrayList<>());
        save.put("inventoryArmorIds",  new ArrayList<>());

        // Consumables: [{ "name": "Health Potion", "quantity": 2, "effect": "heal", "value": 30 }]
        save.put("items",              new ArrayList<>());

        // Checkpoint
        save.put("checkpointFloor",    1);
        save.put("checkpointX",        1000f);
        save.put("checkpointY",        1000f);

        // Meta
        save.put("playtimeSeconds",    0L);
        save.put("lastSaved",          Timestamp.now());

        save.put("gold", 0);

        db.collection("games")
                .add(save)
                .addOnSuccessListener(ref -> {
                    // Link this gameId to the player
                    db.collection("players")
                            .document(userId)
                            .update("gameSlots", FieldValue.arrayUnion(ref.getId()));
                    listener.onResult(ref.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "createGameSave failed", e);
                    listener.onResult(null);
                });
    }

    /**
     * Updates an existing save with current game state.
     * Call this on floor transition and on game over.
     */
    public void updateGameSave(String gameId,
                               int currentFloor,
                               int health,
                               int armorValue,
                               int gold,
                               List<String> inventoryWeaponNames,
                               String equippedWeaponId,
                               String equippedWeaponName,
                               String equippedHeadId,
                               String equippedChestId,
                               String equippedLegsId,
                               String equippedBootsId,
                               List<String> inventoryWeaponIds,
                               List<String> inventoryArmorIds,
                               List<Map<String, Object>> items,
                               int checkpointFloor,
                               float checkpointX,
                               float checkpointY,
                               long playtimeSeconds) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("currentFloor",       currentFloor);
        updates.put("health",             health);
        updates.put("armorValue",         armorValue);
        updates.put("equippedWeaponId",   equippedWeaponId);
        updates.put("equippedWeaponName", equippedWeaponName);
        updates.put("equippedHeadId",     equippedHeadId);
        updates.put("equippedChestId",    equippedChestId);
        updates.put("equippedLegsId",     equippedLegsId);
        updates.put("equippedBootsId",    equippedBootsId);
        updates.put("inventoryWeaponIds", inventoryWeaponIds);
        updates.put("inventoryArmorIds",  inventoryArmorIds);
        updates.put("items",              items);
        updates.put("checkpointFloor",    checkpointFloor);
        updates.put("checkpointX",        checkpointX);
        updates.put("checkpointY",        checkpointY);
        updates.put("playtimeSeconds",    playtimeSeconds);
        updates.put("lastSaved",          Timestamp.now());
        updates.put("gold", gold);
        updates.put("inventoryWeaponNames", inventoryWeaponNames);

        db.collection("games")
                .document(gameId)
                .update(updates)
                .addOnFailureListener(e -> Log.e(TAG, "updateGameSave failed", e));
    }

    /**
     * Loads all game slots for a player.
     * Returns a list of raw maps — GameActivity converts to display format.
     */
    public void loadPlayerGames(String userId, OnGameSlotsLoadedListener listener) {
        db.collection("games")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(query -> {
                    List<Map<String, Object>> slots = new ArrayList<>();
                    for (DocumentSnapshot doc : query.getDocuments()) {
                        Map<String, Object> data = doc.getData();
                        if (data != null) {
                            data.put("gameId", doc.getId()); // inject ID
                            slots.add(data);
                        }
                    }
                    listener.onLoaded(slots);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "loadPlayerGames failed", e);
                    listener.onLoaded(new ArrayList<>());
                });
    }

    /**
     * טוענת שמירה בודדת לפי מזהה.
     * @param gameId מזהה המשחק ב-Firestore
     * @param listener מאזין המקבל מפת נתונים גולמית (null אם לא נמצא)
     */
    public void loadGame(String gameId, OnGameLoadedListener listener) {
        db.collection("games")
                .document(gameId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Map<String, Object> data = doc.getData();
                        if (data != null) data.put("gameId", doc.getId());
                        listener.onLoaded(data);
                    } else {
                        listener.onLoaded(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "loadGame failed", e);
                    listener.onLoaded(null);
                });
    }

    /**
     * Deletes a game save and removes it from the player's gameSlots array.
     */
    public void deleteGame(String userId, String gameId, OnCompleteListener listener) {
        db.collection("games")
                .document(gameId)
                .delete()
                .addOnSuccessListener(v -> {
                    db.collection("players")
                            .document(userId)
                            .update("gameSlots", FieldValue.arrayRemove(gameId))
                            .addOnSuccessListener(v2 -> listener.onComplete(true))
                            .addOnFailureListener(e -> listener.onComplete(false));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "deleteGame failed", e);
                    listener.onComplete(false);
                });
    }

    /* ============================================================
       GENERATED WEAPONS
       Schema matches Weapon.java constructor fields exactly
       so AI output maps directly to this without transformation.
       ============================================================ */

    /**
     * Saves a weapon to generated_weapons.
     * Call with generatedBy = userId for AI weapons, "system" for defaults.
     * Returns the new document ID via listener — store this in the game save.
     */
    public void saveGeneratedWeapon(String generatedBy,
                                    String name,
                                    int damage,
                                    float range,
                                    long cooldown,
                                    String rarity,      // "COMMON" / "RARE" / "EPIC" / "LEGENDARY"
                                    int color,
                                    float knockback,
                                    boolean isProjectile,
                                    String description,
                                    OnStringResultListener listener) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("generatedBy",  generatedBy);
        doc.put("name",         name);
        doc.put("damage",       damage);
        doc.put("range",        range);
        doc.put("cooldown",     cooldown);
        doc.put("rarity",       rarity);
        doc.put("color",        color);
        doc.put("knockback",    knockback);
        doc.put("isProjectile", isProjectile);
        doc.put("description",  description);
        doc.put("createdAt",    Timestamp.now());

        db.collection("generated_weapons")
                .add(doc)
                .addOnSuccessListener(ref -> listener.onResult(ref.getId()))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "saveGeneratedWeapon failed", e);
                    listener.onResult(null);
                });
    }

    /**
     * טוענת נשק שנוצר לפי מזהה ומחזירה מפה גולמית.
     * @param weaponId מזהה מסמך הנשק ב-generated_weapons
     * @param listener מאזין המקבל מפת נתונים גולמית
     */
    public void loadGeneratedWeapon(String weaponId, OnGameLoadedListener listener) {
        db.collection("generated_weapons")
                .document(weaponId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Map<String, Object> data = doc.getData();
                        if (data != null) data.put("weaponId", doc.getId());
                        listener.onLoaded(data);
                    } else {
                        listener.onLoaded(null);
                    }
                })
                .addOnFailureListener(e -> listener.onLoaded(null));
    }

    /* ============================================================
       GENERATED ARMORS
       Schema matches Armor.java constructor fields exactly.
       ============================================================ */

    /**
     * Saves armor to generated_armors.
     * armorType: "HEAD" / "CHEST" / "LEGS" / "BOOTS"
     */
    public void saveGeneratedArmor(String generatedBy,
                                   String name,
                                   int defense,
                                   float maxDurability,
                                   String rarity,
                                   int color,
                                   String armorType,
                                   String description,
                                   OnStringResultListener listener) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("generatedBy",    generatedBy);
        doc.put("name",           name);
        doc.put("defense",        defense);
        doc.put("maxDurability",  maxDurability);
        doc.put("rarity",         rarity);
        doc.put("color",          color);
        doc.put("armorType",      armorType);
        doc.put("description",    description);
        doc.put("createdAt",      Timestamp.now());

        db.collection("generated_armors")
                .add(doc)
                .addOnSuccessListener(ref -> listener.onResult(ref.getId()))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "saveGeneratedArmor failed", e);
                    listener.onResult(null);
                });
    }

    /**
     * טוענת שריון שנוצר לפי מזהה.
     * @param armorId מזהה מסמך השריון ב-generated_armors
     * @param listener מאזין המקבל מפת נתונים גולמית
     */
    public void loadGeneratedArmor(String armorId, OnGameLoadedListener listener) {
        db.collection("generated_armors")
                .document(armorId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Map<String, Object> data = doc.getData();
                        if (data != null) data.put("armorId", doc.getId());
                        listener.onLoaded(data);
                    } else {
                        listener.onLoaded(null);
                    }
                })
                .addOnFailureListener(e -> listener.onLoaded(null));
    }

    /* ============================================================
       GENERATED ENEMIES
       Schema matches Enemy constructor fields.
       Stored separately so AI can generate new enemy types
       without touching the Enemy.java hardcoded switch block.
       ============================================================ */

    /**
     * Saves an AI-generated enemy template.
     * colorR/G/B stored separately so Firestore can hold the int
     * without Android Color dependency on the backend.
     */
    public void saveGeneratedEnemy(String name,
                                   String type,          // "GRUNT" / "SPEEDER" / etc. or custom
                                   int maxHealth,
                                   float speed,
                                   float radius,
                                   String weaponName,
                                   int weaponDamage,
                                   float weaponRange,
                                   long weaponCooldown,
                                   float weaponKnockback,
                                   boolean weaponIsProjectile,
                                   int colorR,
                                   int colorG,
                                   int colorB,
                                   int floorIntroduced,
                                   String description,
                                   OnStringResultListener listener) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("name",              name);
        doc.put("type",              type);
        doc.put("maxHealth",         maxHealth);
        doc.put("speed",             speed);
        doc.put("radius",            radius);
        doc.put("weaponName",        weaponName);
        doc.put("weaponDamage",      weaponDamage);
        doc.put("weaponRange",       weaponRange);
        doc.put("weaponCooldown",    weaponCooldown);
        doc.put("weaponKnockback",   weaponKnockback);
        doc.put("weaponIsProjectile", weaponIsProjectile);
        doc.put("colorR",            colorR);
        doc.put("colorG",            colorG);
        doc.put("colorB",            colorB);
        doc.put("floorIntroduced",   floorIntroduced);
        doc.put("description",       description);
        doc.put("createdAt",         Timestamp.now());

        db.collection("generated_enemies")
                .add(doc)
                .addOnSuccessListener(ref -> listener.onResult(ref.getId()))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "saveGeneratedEnemy failed", e);
                    listener.onResult(null);
                });
    }

    /**
     * Keeps only the most recently saved game slot and deletes the rest.
     * Called once on login to enforce the single-slot rule.
     */
    public void enforeSingleSlot(String userId) {
        db.collection("games")
                .whereEqualTo("userId", userId)
                .orderBy("lastSaved",
                        com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(query -> {
                    java.util.List<com.google.firebase.firestore.DocumentSnapshot> docs =
                            query.getDocuments();
                    // Keep index 0 (most recent), delete the rest
                    for (int i = 1; i < docs.size(); i++) {
                        String gameId = docs.get(i).getId();
                        db.collection("games").document(gameId).delete();
                        db.collection("players").document(userId)
                                .update("gameSlots",
                                        FieldValue.arrayRemove(gameId));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "enforceSingleSlot failed", e));
    }

    /**
     * Loads top 50 players sorted by highestFloor descending.
     * Used for the global leaderboard.
     */
    public void loadGlobalLeaderboard(OnLeaderboardLoadedListener listener) {
        db.collection("players")
                .orderBy("highestFloor",
                        com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(query -> {
                    List<LeaderboardItem> items = new ArrayList<>();
                    int rank = 1;
                    for (DocumentSnapshot doc : query.getDocuments()) {
                        LeaderboardItem item = docToLeaderboardItem(doc, rank++);
                        if (item != null) items.add(item);
                    }
                    listener.onLoaded(items);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "loadGlobalLeaderboard failed", e);
                    listener.onLoaded(new ArrayList<>());
                });
    }

    /**
     * Loads the list of userIds that this user is following.
     */
    public void loadFollowing(String userId, OnStringListResultListener listener) {
        db.collection("follows")
                .document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.get("following") != null) {
                        listener.onResult((List<String>) doc.get("following"));
                    } else {
                        listener.onResult(new ArrayList<>());
                    }
                })
                .addOnFailureListener(e -> listener.onResult(new ArrayList<>()));
    }

    /**
     * Loads player documents by a list of userIds.
     * Used for the Following leaderboard tab.
     */
    public void loadPlayersByIds(List<String> userIds,
                                 OnLeaderboardLoadedListener listener) {
        if (userIds.isEmpty()) {
            listener.onLoaded(new ArrayList<>());
            return;
        }

        db.collection("players")
                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), userIds)
                .get()
                .addOnSuccessListener(query -> {
                    List<LeaderboardItem> items = new ArrayList<>();
                    for (DocumentSnapshot doc : query.getDocuments()) {
                        LeaderboardItem item = docToLeaderboardItem(doc, 0);
                        if (item != null) items.add(item);
                    }
                    // Sort by highestFloor and assign ranks
                    items.sort((a, b) -> b.highestFloor - a.highestFloor);
                    for (int i = 0; i < items.size(); i++) {
                        items.get(i).rank = i + 1;
                    }
                    listener.onLoaded(items);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "loadPlayersByIds failed", e);
                    listener.onLoaded(new ArrayList<>());
                });
    }

    /**
     * Searches for players by partial username match.
     * Excludes the current user from results.
     */
    public void searchUsers(String query, String excludeUserId,
                            OnLeaderboardLoadedListener listener) {
        // Firestore doesn't support native partial text search,
        // so we use a range query on the username field.
        // This matches usernames that start with the query string.
        String end = query + "\uf8ff";

        db.collection("players")
                .orderBy("username")
                .startAt(query)
                .endAt(end)
                .limit(20)
                .get()
                .addOnSuccessListener(query1 -> {
                    List<LeaderboardItem> items = new ArrayList<>();
                    int rank = 1;
                    for (DocumentSnapshot doc : query1.getDocuments()) {
                        if (doc.getId().equals(excludeUserId)) continue;
                        LeaderboardItem item = docToLeaderboardItem(doc, rank++);
                        if (item != null) items.add(item);
                    }
                    listener.onLoaded(items);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "searchUsers failed", e);
                    listener.onLoaded(new ArrayList<>());
                });
    }

    /**
     * Checks if userId is following targetUserId.
     */
    public void isFollowing(String userId, String targetUserId,
                            OnBooleanResultListener listener) {
        db.collection("follows")
                .document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.get("following") != null) {
                        List<String> following = (List<String>) doc.get("following");
                        listener.onResult(following.contains(targetUserId));
                    } else {
                        listener.onResult(false);
                    }
                })
                .addOnFailureListener(e -> listener.onResult(false));
    }

    /**
     * Follows a user — adds targetUserId to the follows document.
     */
    public void followUser(String userId, String targetUserId,
                           OnCompleteListener listener) {
        db.collection("follows")
                .document(userId)
                .update("following", FieldValue.arrayUnion(targetUserId))
                .addOnSuccessListener(v -> listener.onComplete(true))
                .addOnFailureListener(e -> {
                    // Document may not exist yet — create it
                    Map<String, Object> data = new HashMap<>();
                    data.put("following", new ArrayList<>(
                            java.util.Collections.singletonList(targetUserId)));
                    db.collection("follows").document(userId)
                            .set(data)
                            .addOnSuccessListener(v2 -> listener.onComplete(true))
                            .addOnFailureListener(e2 -> listener.onComplete(false));
                });
    }

    /**
     * Unfollows a user — removes targetUserId from the follows document.
     */
    public void unfollowUser(String userId, String targetUserId,
                             OnCompleteListener listener) {
        db.collection("follows")
                .document(userId)
                .update("following", FieldValue.arrayRemove(targetUserId))
                .addOnSuccessListener(v -> listener.onComplete(true))
                .addOnFailureListener(e -> listener.onComplete(false));
    }

    /**
     * Converts a Firestore player document to a LeaderboardItem.
     */
    private LeaderboardItem docToLeaderboardItem(DocumentSnapshot doc, int rank) {
        try {
            Map<String, Object> data = doc.getData();
            if (data == null) return null;

            String username      = data.get("username") != null
                    ? (String) data.get("username") : "Unknown";
            int highestFloor     = data.get("highestFloor") != null
                    ? ((Number) data.get("highestFloor")).intValue() : 0;
            int enemiesKilled    = data.get("enemiesKilled") != null
                    ? ((Number) data.get("enemiesKilled")).intValue() : 0;
            int bossesKilled     = data.get("bossesKilled") != null
                    ? ((Number) data.get("bossesKilled")).intValue() : 0;
            int timesDied        = data.get("timesDied") != null
                    ? ((Number) data.get("timesDied")).intValue() : 0;
            long playtimeSeconds = data.get("playtimeSeconds") != null
                    ? ((Number) data.get("playtimeSeconds")).longValue() : 0L;

            return new LeaderboardItem(doc.getId(), username, highestFloor,
                    enemiesKilled, bossesKilled, timesDied, playtimeSeconds, rank);
        } catch (Exception e) {
            return null;
        }
    }

    public interface OnLeaderboardLoadedListener {
        void onLoaded(List<LeaderboardItem> items);
    }

    public interface OnStringListResultListener {
        void onResult(List<String> ids);
    }

    public interface OnBooleanResultListener {
        void onResult(boolean result);
    }
}