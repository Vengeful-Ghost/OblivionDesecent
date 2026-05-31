package com.example.obliviondescent;

import android.graphics.RectF;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * מחזיקה את הפריסה הפרוצדורלית המלאה של קומה אחת: חדרים + מסדרונות.
 *
 * אסטרטגיית יצירה:
 * 1. ממקמת חדר כניסה במוצא העולם.
 * 2. מגדלת שרשרת מפותלת של חדרים פנימיים (מספר גדל עם מספר הקומה).
 * 3. בקומות בוס, מוסיפה חדר בוס גדול בסוף השרשרת.
 * 4. מוסיפה חדר יציאה אחרון — ממקמת בכוח אם כל הנסיונות נכשלים.
 * 5. מפזרת אויבים לפי מכסה הקומה בין החדרים הכשירים.
 */
public class FloorLayout {

    private static final String TAG = "FloorLayout";

    // ── Layout constants ──────────────────────────────────────────────────────
    /** Width of every corridor passage (3 × player radius 50 px). */
    public static final float CORRIDOR_WIDTH  = 150f;
    /** Gap length between two room walls. */
    public static final float CORRIDOR_LENGTH = 350f;
    /** Minimum clearance kept between any two rooms during overlap checks. */
    private static final float ROOM_PADDING   = 100f;

    // ── Contents ─────────────────────────────────────────────────────────────
    public final List<Room>     rooms     = new ArrayList<>();
    public final List<Corridor> corridors = new ArrayList<>();

    public Room entryRoom; // player spawns at its centre
    public Room exitRoom;  // portal(s) spawn here
    public Room bossRoom;  // null on non-boss floors

    // ─────────────────────────────────────────────────────────────────────────
    // FACTORY
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a complete floor layout.
     *
     * @param floor       Current floor number (1-based).
     * @param isBossFloor Whether a boss room should be attached before the exit.
     * @param killCount   Total enemies that must be distributed across rooms.
     */
    public static FloorLayout generate(int floor, boolean isBossFloor, int killCount) {
        FloorLayout layout = new FloorLayout();
        Random rng = new Random();

        // Inner room count scales with floor, capped so layouts don't get absurd.
        // floor 1 → 2-3  |  floor 5 → 4-6  |  floor 10 → 7-9  |  floor 20+ → capped 12
        int innerCount = Math.min(12, Math.max(2, 1 + floor / 2 + rng.nextInt(3)));

        // ── 1. Entry room ─────────────────────────────────────────────────────
        float es = 4 * Room.TILE;
        Room entry = new Room(0, -es * 0.5f, -es * 0.5f, es, es, Room.Type.ENTRY);
        layout.rooms.add(entry);
        layout.entryRoom = entry;

        // ── 2. Inner rooms ────────────────────────────────────────────────────
        Room current  = entry;
        int  preferred = Room.EAST;

        for (int i = 0; i < innerCount; i++) {
            Room.Type t  = randomRoomType(rng);
            float[]   sz = sizeForType(t, rng);

            Room next = tryGrow(layout, rng, current, preferred, sz[0], sz[1], t);
            if (next == null) next = tryGrowFromAny(layout, rng, sz[0], sz[1], t);
            if (next != null) {
                current   = next;
                preferred = windingDirection(preferred, rng);
            }
        }

        // ── 3. Boss room (boss floors only) ───────────────────────────────────
        if (isBossFloor) {
            float bs   = 16 * Room.TILE;
            Room  boss = tryGrow(layout, rng, current, Room.EAST, bs, bs, Room.Type.BOSS);
            if (boss == null) boss = tryGrowFromAny(layout, rng, bs, bs, Room.Type.BOSS);
            if (boss == null) boss = forceGrow(layout, current, bs, bs, Room.Type.BOSS);
            if (boss != null) {
                layout.bossRoom = boss;
                current = boss;
            }
        }

        // ── 4. Exit room ──────────────────────────────────────────────────────
        float xs   = 4 * Room.TILE;
        Room  exit = tryGrow(layout, rng, current, Room.EAST, xs, xs, Room.Type.EXIT);
        if (exit == null) exit = tryGrowFromAny(layout, rng, xs, xs, Room.Type.EXIT);
        if (exit == null) exit = forceGrow(layout, current, xs, xs, Room.Type.EXIT);
        layout.exitRoom = exit;

        // ── 5. Enemy distribution ─────────────────────────────────────────────
        distributeEnemies(layout, killCount, rng);

        Log.d(TAG, "Generated floor " + floor + ": " + layout.rooms.size()
                + " rooms, " + layout.corridors.size() + " corridors, "
                + killCount + " enemies distributed.");

        return layout;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROWTH HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tries to grow a new room off {@code src} in {@code preferred} direction
     * (and the other three directions as fallbacks). Returns the new room on
     * success, or null if all directions are blocked or would cause overlap.
     */
    private static Room tryGrow(FloorLayout layout, Random rng,
                                Room src, int preferred,
                                float w, float h, Room.Type type) {
        for (int d : shuffledDirs(rng, preferred)) {
            if (src.sideUsed[d]) continue;
            Room     candidate = new Room(layout.rooms.size(), 0, 0, w, h, type);
            Corridor corr      = computeConnection(src, d, candidate);
            if (corr != null && !overlaps(layout, candidate, corr, src)) {
                src.sideUsed[d]              = true;
                candidate.sideUsed[opp(d)]   = true;
                layout.rooms.add(candidate);
                layout.corridors.add(corr);
                return candidate;
            }
        }
        return null;
    }

    /**
     * Same as {@link #tryGrow} but picks the source room randomly from all
     * already-placed rooms instead of using a specific one. Used as a fallback
     * when the current chain tip has no available directions.
     */
    private static Room tryGrowFromAny(FloorLayout layout, Random rng,
                                       float w, float h, Room.Type type) {
        List<Room> shuffled = new ArrayList<>(layout.rooms);
        Collections.shuffle(shuffled, rng);
        for (Room src : shuffled) {
            Room result = tryGrow(layout, rng, src, -1, w, h, type);
            if (result != null) return result;
        }
        return null;
    }

    /**
     * Last-resort placement — ignores overlap, guaranteeing a room is always
     * attached (used to ensure an exit room always exists).
     */
    private static Room forceGrow(FloorLayout layout, Room src,
                                  float w, float h, Room.Type type) {
        for (int d : new int[]{Room.EAST, Room.SOUTH, Room.NORTH, Room.WEST}) {
            if (src.sideUsed[d]) continue;
            Room     candidate = new Room(layout.rooms.size(), 0, 0, w, h, type);
            Corridor corr      = computeConnection(src, d, candidate);
            if (corr != null) {
                src.sideUsed[d]            = true;
                candidate.sideUsed[opp(d)] = true;
                layout.rooms.add(candidate);
                layout.corridors.add(corr);
                return candidate;
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CORRIDOR CALCULATOR
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the corridor connecting {@code src}'s given side to {@code dst},
     * and sets {@code dst}'s (x, y) so the two rooms align cleanly at the door.
     * Returns null only if the direction index is invalid.
     */
    private static Corridor computeConnection(Room src, int dir, Room dst) {
        final float CW = CORRIDOR_WIDTH, CL = CORRIDOR_LENGTH;
        float cx, cy, cw, ch;

        switch (dir) {
            case Room.EAST: {
                float doorY = src.y + src.height * 0.5f;
                cx = src.x + src.width;    cy = doorY - CW * 0.5f;
                cw = CL;                   ch = CW;
                dst.x = cx + CL;           dst.y = doorY - dst.height * 0.5f;
                break;
            }
            case Room.SOUTH: {
                float doorX = src.x + src.width * 0.5f;
                cx = doorX - CW * 0.5f;    cy = src.y + src.height;
                cw = CW;                   ch = CL;
                dst.x = doorX - dst.width * 0.5f; dst.y = cy + CL;
                break;
            }
            case Room.WEST: {
                float doorY = src.y + src.height * 0.5f;
                cx = src.x - CL;           cy = doorY - CW * 0.5f;
                cw = CL;                   ch = CW;
                dst.x = cx - dst.width;    dst.y = doorY - dst.height * 0.5f;
                break;
            }
            case Room.NORTH: {
                float doorX = src.x + src.width * 0.5f;
                cx = doorX - CW * 0.5f;    cy = src.y - CL;
                cw = CW;                   ch = CL;
                dst.x = doorX - dst.width * 0.5f; dst.y = cy - dst.height;
                break;
            }
            default: return null;
        }

        return new Corridor(cx, cy, cw, ch, src.id, dst.id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ENEMY DISTRIBUTION
    // ─────────────────────────────────────────────────────────────────────────

    private static void distributeEnemies(FloorLayout layout, int killCount, Random rng) {
        // Collect rooms that can hold enemies
        List<Room> eligible = new ArrayList<>();
        for (Room r : layout.rooms) {
            if (r.possibleEnemyCount > 0) eligible.add(r);
        }
        if (eligible.isEmpty()) return;

        int remaining = killCount;
        int maxTries  = killCount * 30;

        // Random assignment respecting each room's capacity
        for (int t = 0; t < maxTries && remaining > 0; t++) {
            Room r = eligible.get(rng.nextInt(eligible.size()));
            if (r.assignedEnemyCount < r.possibleEnemyCount) {
                r.assignedEnemyCount++;
                remaining--;
            }
        }

        // If quota still unmet, increase room caps until satisfied
        while (remaining > 0) {
            Room r = eligible.get(rng.nextInt(eligible.size()));
            r.possibleEnemyCount++;
            r.assignedEnemyCount++;
            remaining--;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OVERLAP CHECK
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns true if {@code candidate} or its {@code corridor} would overlap
     * any existing room (except {@code skip}, which is the room being connected
     * to), or any existing corridor.
     */
    private static boolean overlaps(FloorLayout layout, Room candidate,
                                    Corridor corridor, Room skip) {
        RectF cr = pad(candidate.getBounds(), ROOM_PADDING);
        RectF co = corridor.getBounds();

        for (Room r : layout.rooms) {
            if (r == skip) continue;
            RectF rb = r.getBounds();
            if (RectF.intersects(cr, rb)) return true;
            if (RectF.intersects(co, rb)) return true;
        }
        for (Corridor c : layout.corridors) {
            if (RectF.intersects(cr, c.getBounds())) return true;
        }
        return false;
    }

    private static RectF pad(RectF r, float p) {
        return new RectF(r.left - p, r.top - p, r.right + p, r.bottom + p);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // QUERY HELPERS  (used by collision, minimap, enemy spawning)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * מחזירה את החדר שמכיל את נקודת העולם הנתונה, או null אם אין.
     * @param px מיקום X בעולם המשחק
     * @param py מיקום Y בעולם המשחק
     * @return החדר המכיל את הנקודה, או null
     */
    public Room getRoomAt(float px, float py) {
        for (Room r : rooms) if (r.contains(px, py)) return r;
        return null;
    }

    /**
     * בודקת אם הנקודה נמצאת על רצפה עבירה (בתוך חדר או מסדרון).
     * @param px מיקום X בעולם המשחק
     * @param py מיקום Y בעולם המשחק
     * @return true אם הנקודה על רצפה עבירה
     */
    public boolean isFloor(float px, float py) {
        for (Room r     : rooms)     if (r.contains(px, py)) return true;
        for (Corridor c : corridors) if (c.contains(px, py)) return true;
        return false;
    }

    /**
     * Block-and-slide wall collision. Checks all 4 cardinal points at `radius`
     * distance from the center so the full body is kept on walkable floor.
     * Falls back to single-axis slides, then stays put if fully blocked.
     */
    /**
     * פותרת התנגשות קירות עם החלקה על צירים.
     * בודקת 4 נקודות קרדינליות ברדיוס r מהמרכז.
     * @param oldX מיקום X הקודם
     * @param oldY מיקום Y הקודם
     * @param newX מיקום X המבוקש
     * @param newY מיקום Y המבוקש
     * @param radius רדיוס הגוף לבדיקה
     * @return מערך [x, y] של המיקום המותר
     */
    public float[] resolveWallCollision(float oldX, float oldY,
                                        float newX, float newY, float radius) {
        if (isOnFloor(newX, newY, radius)) return new float[]{newX, newY};
        if (isOnFloor(newX, oldY, radius)) return new float[]{newX, oldY};
        if (isOnFloor(oldX, newY, radius)) return new float[]{oldX, newY};
        return new float[]{oldX, oldY};
    }

    private boolean isOnFloor(float cx, float cy, float r) {
        return isFloor(cx,     cy    )
            && isFloor(cx + r, cy    )
            && isFloor(cx - r, cy    )
            && isFloor(cx,     cy + r)
            && isFloor(cx,     cy - r);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTILITY
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns the four direction indices shuffled, with {@code preferred} first. */
    private static int[] shuffledDirs(Random rng, int preferred) {
        List<Integer> list = new ArrayList<>();
        list.add(Room.NORTH); list.add(Room.EAST);
        list.add(Room.SOUTH); list.add(Room.WEST);
        Collections.shuffle(list, rng);
        if (preferred >= 0 && preferred <= 3) {
            list.remove(Integer.valueOf(preferred));
            list.add(0, preferred);
        }
        return new int[]{list.get(0), list.get(1), list.get(2), list.get(3)};
    }

    private static int opp(int dir) { return (dir + 2) % 4; }

    /**
     * Keeps the path winding: 66 % chance to continue in the same direction,
     * 33 % chance to turn 90° (randomly left or right).
     */
    private static int windingDirection(int current, Random rng) {
        if (rng.nextInt(3) == 0) {
            // Turn: +1 or -1 in direction index (with wrap), giving ±90°
            return (current + 1 + rng.nextInt(2) * 2) % 4;
        }
        return current;
    }

    private static Room.Type randomRoomType(Random rng) {
        int roll = rng.nextInt(100);
        if (roll < 18) return Room.Type.HALLWAY; // 18 %
        if (roll < 58) return Room.Type.SMALL;   // 40 %
        if (roll < 86) return Room.Type.MEDIUM;  // 28 %
        return Room.Type.LARGE;                   // 14 %
    }

    private static float[] sizeForType(Room.Type type, Random rng) {
        final float T = Room.TILE;
        switch (type) {
            case HALLWAY: {
                float narrow = 2 * T;
                float length = (6 + rng.nextInt(5)) * T; // 6-10 tiles long
                return rng.nextBoolean() ? new float[]{narrow, length}
                                         : new float[]{length, narrow};
            }
            case SMALL:  return new float[]{(4 + rng.nextInt(3)) * T,
                                            (4 + rng.nextInt(3)) * T}; // 4-6 tiles
            case MEDIUM: return new float[]{(5 + rng.nextInt(4)) * T,
                                            (5 + rng.nextInt(4)) * T}; // 5-8 tiles
            case LARGE:  return new float[]{(8 + rng.nextInt(5)) * T,
                                            (7 + rng.nextInt(5)) * T}; // 8-12 tiles
            default:     return new float[]{4 * T, 4 * T};
        }
    }
}
