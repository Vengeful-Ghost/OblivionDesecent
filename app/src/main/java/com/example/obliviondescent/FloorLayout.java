package com.example.obliviondescent;

import android.graphics.RectF;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Holds the complete procedural layout for one floor: rooms and corridors.
 *
 * Generation strategy:
 * 1. Places an entry room at the world origin.
 * 2. Grows a winding chain of inner rooms (count scales with floor number).
 * 3. On boss floors, appends a large boss room at the end of the chain.
 * 4. Appends a final exit room — force-placed if all normal attempts fail.
 * 5. Distributes enemies across eligible rooms according to the floor quota.
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

    // Navigation graph: rooms are nodes 0..R-1, corridors are nodes R..R+C-1
    private List<List<Integer>> neighbours;

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

        layout.buildNavigation();

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
     * Returns the room that contains the given world-space point, or null if none.
     * @param px X position in world space
     * @param py Y position in world space
     * @return the room containing the point, or null
     */
    public Room getRoomAt(float px, float py) {
        for (Room r : rooms) if (r.contains(px, py)) return r;
        return null;
    }

    /**
     * Returns true if the point lies on walkable floor (inside a room or corridor).
     * @param px X position in world space
     * @param py Y position in world space
     * @return true if the point is on walkable floor
     */
    public boolean isFloor(float px, float py) {
        for (Room r     : rooms)     if (r.contains(px, py)) return true;
        for (Corridor c : corridors) if (c.contains(px, py)) return true;
        return false;
    }

    /**
     * Block-and-slide wall collision. Checks all 4 cardinal points at {@code radius}
     * distance from the centre so the full body stays on walkable floor.
     * Falls back to single-axis slides, then stays put if fully blocked.
     *
     * @param oldX previous X position
     * @param oldY previous Y position
     * @param newX requested X position
     * @param newY requested Y position
     * @param radius body radius used for the cardinal-point checks
     * @return array [x, y] of the allowed position
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

    // ───────────────────────────────────────────────────────────────────────
    // NAVIGATION  (used by enemies to reach the player through doors)
    // ───────────────────────────────────────────────────────────────────────

    private void buildNavigation() {
        int roomCount = rooms.size();
        neighbours = new ArrayList<>();
        for (int i = 0; i < roomCount + corridors.size(); i++) neighbours.add(new ArrayList<>());
        // Room ids equal their index in rooms (assigned as rooms.size() on creation)
        for (int j = 0; j < corridors.size(); j++) {
            Corridor c = corridors.get(j);
            int node = roomCount + j;
            neighbours.get(node).add(c.roomIdA);
            neighbours.get(node).add(c.roomIdB);
            neighbours.get(c.roomIdA).add(node);
            neighbours.get(c.roomIdB).add(node);
        }
    }

    /**
     * Returns where a body should walk next to reach the target through doors and corridors,
     * or null when it shares a room or corridor with the target and can walk straight there.
     * @param fromX current X position
     * @param fromY current Y position
     * @param toX target X position
     * @param toY target Y position
     * @param radius body radius, so wide bodies line up with the door before entering
     * @return the next waypoint [x, y], or null for a straight path
     */
    public float[] nextWaypoint(float fromX, float fromY, float toX, float toY, float radius) {
        if (neighbours == null) return null;
        int from = nodeAt(fromX, fromY);
        int to   = nodeAt(toX, toY);
        if (from < 0 || to < 0 || from == to) return null;
        int next = firstStep(from, to);
        if (next < 0) return null;

        int roomCount = rooms.size();
        if (from >= roomCount) {
            // In a corridor: walk into the next room
            return doorPoint(rooms.get(next), corridors.get(from - roomCount), radius);
        }

        // In a room: line up with the door first so the body fits, then walk through to the far room
        Room room = rooms.get(from);
        Corridor corridor = corridors.get(next - roomCount);
        float[] door = doorPoint(room, corridor, radius);
        float tolerance = Math.max(2f, CORRIDOR_WIDTH * 0.5f - radius - 5f);
        boolean horizontal = corridor.width >= corridor.height;
        float offset = horizontal ? Math.abs(fromY - door[1]) : Math.abs(fromX - door[0]);
        if (offset > tolerance) return door;
        int otherId = corridor.roomIdA == room.id ? corridor.roomIdB : corridor.roomIdA;
        return doorPoint(rooms.get(otherId), corridor, radius);
    }

    private int nodeAt(float px, float py) {
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).contains(px, py)) return i;
        }
        for (int j = 0; j < corridors.size(); j++) {
            if (corridors.get(j).contains(px, py)) return rooms.size() + j;
        }
        return -1;
    }

    /** Breadth-first search; returns the node right after {@code from} on the shortest path to {@code to}. */
    private int firstStep(int from, int to) {
        int[] previous = new int[neighbours.size()];
        Arrays.fill(previous, -1);
        previous[from] = from;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(from);
        while (!queue.isEmpty()) {
            int node = queue.poll();
            if (node == to) break;
            for (int next : neighbours.get(node)) {
                if (previous[next] == -1) {
                    previous[next] = node;
                    queue.add(next);
                }
            }
        }
        if (previous[to] == -1) return -1;
        int step = to;
        while (previous[step] != from) step = previous[step];
        return step;
    }

    /** Point just inside the room, centred on its door into the corridor. */
    private static float[] doorPoint(Room room, Corridor corridor, float radius) {
        float inset = radius + 20f;
        if (corridor.width >= corridor.height) {
            float doorY = corridor.y + corridor.height * 0.5f;
            boolean roomIsWest = room.x + room.width <= corridor.x + 1f;
            return new float[]{roomIsWest ? room.x + room.width - inset : room.x + inset, doorY};
        }
        float doorX = corridor.x + corridor.width * 0.5f;
        boolean roomIsNorth = room.y + room.height <= corridor.y + 1f;
        return new float[]{doorX, roomIsNorth ? room.y + room.height - inset : room.y + inset};
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
