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
 * 2. Grows inner rooms (count scales with floor): mostly a winding path, but more and more often a
 *    branch off an earlier room, so deeper floors sprawl with rooms of up to four doors.
 * 3. Hangs the boss room (boss floors) and the exit room off the room farthest from the entry.
 * 4. Solo runs: grows up to two event rooms (RoomEvent) as dead ends off ordinary rooms, each at
 *    its event's fixed size. They never hold enemies.
 * 5. Sometimes turns an ordinary room into an enemy-free treasure room with a single chest,
 *    and from floor 2 may seal a dead end as a vault that needs a key.
 * 6. Distributes enemies across eligible rooms according to the floor quota, then places decorations
 *    in the floor's biome (Biome).
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
    public Room treasureRoom;      // enemy-free room with one chest; null on most floors
    public boolean treasureMimic;  // that chest is a mimic
    public Room vaultRoom;         // enemy-free dead end behind a gate a key opens; null on most floors
    public Corridor vaultCorridor; // the corridor the gate seals
    public float[] vaultGate;      // gate line across that corridor's open end: {x1, y1, x2, y2}
    public volatile boolean vaultLocked; // per phone: the gate blocks the corridor until a key opens it
    public final List<Room> eventRooms = new ArrayList<>(); // dead ends holding a RoomEvent (solo only)
    public Corridor trialCorridor; // the Trial arena's corridor, sealed while its waves fight
    public float[] trialGate;      // gate line across that corridor's arena end: {x1, y1, x2, y2}
    public volatile boolean trialSealed;
    public Biome biome = Biome.CRYPT;
    public List<Props.Decor> decor = new ArrayList<>(); // drawn by GameDisplay; barrels, crates and pillars are solid

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
     * @param seed        Same seed, same layout: co-op phones build identical floors.
     * @param withEvents  Grow event rooms (solo runs; co-op has none).
     */
    public static FloorLayout generate(int floor, boolean isBossFloor, int killCount, long seed,
                                       boolean withEvents) {
        FloorLayout layout = new FloorLayout();
        layout.biome = Biome.forFloor(floor);
        Random rng = new Random(seed);

        // Inner room count scales with floor, capped so layouts don't get absurd.
        // floor 1 → 3-4  |  floor 5 → 4-6  |  floor 10 → 7-9  |  floor 20+ → capped 14
        int innerCount = Math.min(14, Math.max(3, 2 + floor / 2 + rng.nextInt(3)));
        // Deeper floors branch more often, so the player can take wrong turns
        float branchChance = Math.min(0.6f, 0.15f + floor * 0.045f);

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

            Room next = i > 0 && rng.nextFloat() < branchChance
                    ? tryGrowFromAny(layout, rng, sz[0], sz[1], t) : null;
            if (next == null) next = growWinding(layout, rng, current, preferred, sz[0], sz[1], t);
            if (next == null) next = tryGrowFromAny(layout, rng, sz[0], sz[1], t);
            if (next != null) {
                current   = next;
                preferred = windingDirection(preferred, rng);
            }
        }

        // ── 3. Boss room (boss floors only) ───────────────────────────────────
        // The boss and the exit hang off the room farthest from the entry; side paths are detours
        List<Room> deepest = deepestFirst(layout);
        if (isBossFloor) {
            float bs   = 11 * Room.TILE; // room to dodge a boss, not a field to cross
            Room  boss = growAtEnd(layout, rng, deepest, bs, bs, Room.Type.BOSS);
            if (boss != null) {
                layout.bossRoom = boss;
                deepest.add(0, boss);
            }
        }

        // ── 4. Exit room ──────────────────────────────────────────────────────
        float xs   = 4 * Room.TILE;
        layout.exitRoom = growAtEnd(layout, rng, deepest, xs, xs, Room.Type.EXIT);

        // ── 5. Event rooms (solo) ─────────────────────────────────────────────
        if (withEvents) placeEventRooms(layout, rng, floor);

        // ── 5b. Room shapes: not every hall is a plain box ────────────────────
        shapeRooms(layout, rng);

        layout.buildNavigation();

        // Treasure room: an enemy-free room with one chest, likelier on boss floors
        if (rng.nextFloat() < (isBossFloor ? 0.8f : 0.2f)) pickTreasureRoom(layout, rng);
        // Vault: a locked dead end with better loot, from floor 2
        if (floor >= 2 && rng.nextFloat() < 0.35f) pickVault(layout, rng);

        // ── 6. Enemy distribution ─────────────────────────────────────────────
        distributeEnemies(layout, killCount, rng);
        layout.decor = Props.decorate(layout);
        settleDecor(layout);

        Log.d(TAG, "Generated floor " + floor + ": " + layout.rooms.size()
                + " rooms, " + layout.corridors.size() + " corridors, "
                + killCount + " enemies distributed.");

        return layout;
    }

    /** Rooms ordered by how many doors away from the entry they are, farthest first. */
    private static List<Room> deepestFirst(FloorLayout layout) {
        int[] depth = new int[layout.rooms.size()];
        Arrays.fill(depth, -1);
        depth[0] = 0; // the entry is room 0
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        while (!queue.isEmpty()) {
            int room = queue.poll();
            for (Corridor c : layout.corridors) {
                int other = c.roomIdA == room ? c.roomIdB : c.roomIdB == room ? c.roomIdA : -1;
                if (other >= 0 && depth[other] < 0) {
                    depth[other] = depth[room] + 1;
                    queue.add(other);
                }
            }
        }
        List<Room> list = new ArrayList<>(layout.rooms);
        list.sort((a, b) -> depth[b.id] - depth[a.id]);
        return list;
    }

    /** Grows a room off the deepest room with space for it; forced past overlaps as a last resort. */
    private static Room growAtEnd(FloorLayout layout, Random rng, List<Room> deepest,
                                  float w, float h, Room.Type type) {
        for (Room src : deepest) {
            Room r = tryGrow(layout, rng, src, -1, w, h, type);
            if (r != null) return r;
        }
        for (Room src : deepest) {
            Room r = forceGrow(layout, src, w, h, type);
            if (r != null) return r;
        }
        return null;
    }

    /**
     * Grows this floor's event rooms: one on most floors, a second now and then from floor 4. Each is a
     * dead end at its event's fixed size, hung off an ordinary room, so it has exactly one doorway.
     */
    private static void placeEventRooms(FloorLayout layout, Random rng, int floor) {
        int count = rng.nextFloat() < 0.6f ? 1 : 0;
        if (count == 1 && floor >= 4 && rng.nextFloat() < 0.3f) count = 2;
        java.util.EnumSet<RoomEvent> taken = java.util.EnumSet.noneOf(RoomEvent.class);
        for (int i = 0; i < count; i++) {
            RoomEvent event = RoomEvent.pick(rng, floor, taken);
            if (event == null) return;
            taken.add(event);
            Room room = growEventRoom(layout, rng, event.tilesW * Room.TILE, event.tilesH * Room.TILE);
            if (room == null) continue; // no space left for a room that big
            room.event = event;
            layout.eventRooms.add(room);
            if (event == RoomEvent.TRIAL) sealableDoor(layout, room);
        }
    }

    /** An EVENT room off a random ordinary room; never off the entry, exit, boss or another event room. */
    private static Room growEventRoom(FloorLayout layout, Random rng, float w, float h) {
        List<Room> sources = new ArrayList<>();
        for (Room r : layout.rooms) {
            if (r.type != Room.Type.ENTRY && r.type != Room.Type.EXIT
                    && r.type != Room.Type.BOSS && r.type != Room.Type.EVENT && r.type != Room.Type.JUNCTION) sources.add(r);
        }
        Collections.shuffle(sources, rng);
        for (Room src : sources) {
            Room r = tryGrow(layout, rng, src, -1, w, h, Room.Type.EVENT);
            if (r != null) return r;
        }
        return null;
    }

    /** The Trial arena's gate: across its corridor's arena end, so a sealed door keeps the fight inside. */
    private static void sealableDoor(FloorLayout layout, Room arena) {
        for (Corridor c : layout.corridors) {
            if (c.roomIdA != arena.id && c.roomIdB != arena.id) continue;
            if (c.width >= c.height) {
                float x = arena.x > c.x ? c.x + c.width : c.x;
                layout.trialGate = new float[]{x, c.y, x, c.y + c.height};
            } else {
                float y = arena.y > c.y ? c.y + c.height : c.y;
                layout.trialGate = new float[]{c.x, y, c.x + c.width, y};
            }
            layout.trialCorridor = c;
            return;
        }
    }

    /** Turns an ordinary room, preferably a dead end, into the treasure room; keeps rooms for enemies. */
    private static void pickTreasureRoom(FloorLayout layout, Random rng) {
        List<Room> candidates = new ArrayList<>(), deadEnds = new ArrayList<>();
        for (Room r : layout.rooms) {
            if (r.possibleEnemyCount <= 0) continue; // entry, exit and boss rooms
            candidates.add(r);
            int doors = 0;
            for (boolean used : r.sideUsed) if (used) doors++;
            if (doors == 1) deadEnds.add(r);
        }
        if (candidates.size() < 2) return;
        List<Room> pool = deadEnds.isEmpty() ? candidates : deadEnds;
        layout.treasureRoom = pool.get(rng.nextInt(pool.size()));
        layout.treasureRoom.possibleEnemyCount = 0;
        layout.treasureMimic = rng.nextFloat() < 0.25f;
    }

    /** Seals an ordinary dead end behind a gate; it keeps no enemies and enough rooms stay for them. */
    private static void pickVault(FloorLayout layout, Random rng) {
        List<Room> deadEnds = new ArrayList<>();
        int fighting = 0;
        for (Room r : layout.rooms) {
            if (r.possibleEnemyCount <= 0) continue; // entry, exit, boss and treasure rooms
            fighting++;
            int doors = 0;
            for (boolean used : r.sideUsed) if (used) doors++;
            if (doors == 1) deadEnds.add(r);
        }
        if (deadEnds.isEmpty() || fighting < 3) return;
        Room vault = deadEnds.get(rng.nextInt(deadEnds.size()));
        for (Corridor c : layout.corridors) {
            if (c.roomIdA != vault.id && c.roomIdB != vault.id) continue;
            // The gate stands at the corridor's far end from the vault
            if (c.width >= c.height) {
                float x = vault.x > c.x ? c.x : c.x + c.width;
                layout.vaultGate = new float[]{x, c.y, x, c.y + c.height};
            } else {
                float y = vault.y > c.y ? c.y : c.y + c.height;
                layout.vaultGate = new float[]{c.x, y, c.x + c.width, y};
            }
            layout.vaultCorridor = c;
            layout.vaultRoom = vault;
            layout.vaultLocked = true;
            vault.possibleEnemyCount = 0;
            return;
        }
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

    /** A landing where a corridor turns: just big enough for the two doors and a body to round the corner. */
    private static final float BEND = 300f;

    /**
     * Grows the next room, sometimes down a passage that turns: one landing makes an L, two make a staircase
     * that runs off on the diagonal (out, across, out again). Each landing is a small JUNCTION room, so walls,
     * the map, enemies' paths and co-op seeding all work as for any room. Falls back to a straight corridor,
     * undoing any landings, when the winding way doesn't fit.
     */
    private static Room growWinding(FloorLayout layout, Random rng, Room src, int preferred,
                                    float w, float h, Room.Type type) {
        float roll = rng.nextFloat();
        int bends = roll < 0.22f ? 1 : roll < 0.36f ? 2 : 0;
        if (bends > 0) {
            int rooms = layout.rooms.size(), corridors = layout.corridors.size();
            boolean[] sides = src.sideUsed.clone();
            int out = preferred >= 0 ? preferred : rng.nextInt(4);
            int across = rng.nextBoolean() ? (out + 1) % 4 : (out + 3) % 4;
            Room at = src;
            for (int i = 0; i < bends && at != null; i++)
                at = growExact(layout, at, i == 0 ? out : across, BEND, BEND, Room.Type.JUNCTION);
            Room room = at == null ? null : growExact(layout, at, bends == 1 ? across : out, w, h, type);
            if (room != null) return room;
            // undo the landings that led nowhere
            while (layout.rooms.size() > rooms) layout.rooms.remove(layout.rooms.size() - 1);
            while (layout.corridors.size() > corridors) layout.corridors.remove(layout.corridors.size() - 1);
            System.arraycopy(sides, 0, src.sideUsed, 0, 4);
        }
        return tryGrow(layout, rng, src, preferred, w, h, type);
    }

    /** Grows a room off src in exactly direction dir, or null when that side is taken or it would overlap. */
    private static Room growExact(FloorLayout layout, Room src, int dir, float w, float h, Room.Type type) {
        if (src.sideUsed[dir]) return null;
        Room candidate = new Room(layout.rooms.size(), 0, 0, w, h, type);
        Corridor corr = computeConnection(src, dir, candidate);
        if (corr == null || overlaps(layout, candidate, corr, src)) return null;
        src.sideUsed[dir] = true;
        candidate.sideUsed[opp(dir)] = true;
        layout.rooms.add(candidate);
        layout.corridors.add(corr);
        return candidate;
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
    // ROOM SHAPES
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gives ordinary rooms a shape of their own by walling off parts of their rectangle: a corner or two
     * clipped, a whole wing missing (an L), all four corners cut (a cross), or square columns in a hall.
     * Corner blocks stop short of a cross through the middle 1.25 tiles wide, so every door and the room's
     * centre stay open; columns stand a tile in from the walls. A third of the rooms stay plain boxes.
     * Seeded like the rest of the floor, so co-op phones build the same shapes.
     */
    private static void shapeRooms(FloorLayout layout, Random rng) {
        final float T = Room.TILE;
        for (Room r : layout.rooms) {
            if (r.type != Room.Type.SMALL && r.type != Room.Type.MEDIUM && r.type != Room.Type.LARGE) continue;
            int w = Math.round(r.width / T), h = Math.round(r.height / T);
            int cw = (int) Math.floor((w - 1.25f) / 2f), ch = (int) Math.floor((h - 1.25f) / 2f);
            if (cw < 1 || ch < 1) continue;
            float roll = rng.nextFloat();
            if (roll < 0.33f) continue;
            if (roll < 0.55f) {
                // one corner clipped, or two opposite ones
                int first = rng.nextInt(4), n = 1 + rng.nextInt(2);
                for (int i = 0; i < n; i++) corner(r, (first + i * 2) % 4, 1 + rng.nextInt(cw), 1 + rng.nextInt(ch));
            } else if (roll < 0.75f) {
                corner(r, rng.nextInt(4), cw, ch);                       // an L: a whole wing gone
            } else if (roll < 0.88f || w < 7 || h < 7) {
                for (int c = 0; c < 4; c++) corner(r, c, cw, ch);       // a cross
            } else {
                // a pillared hall: square columns a tile in from each wall, a second pair in wide halls
                int[] cols = w >= 10 ? new int[]{1, 3, w - 4, w - 2} : new int[]{1, w - 2};
                for (int i : cols)
                    for (int j : new int[]{1, h - 2})
                        r.blocks.add(new RectF(r.x + i * T, r.y + j * T, r.x + (i + 1) * T, r.y + (j + 1) * T));
            }
        }
    }

    /** Walls off a bw x bh tile corner of the room: 0 north-west, 1 north-east, 2 south-east, 3 south-west. */
    private static void corner(Room r, int c, int bw, int bh) {
        float w = bw * Room.TILE, h = bh * Room.TILE;
        float x = c == 1 || c == 2 ? r.x + r.width - w : r.x, y = c >= 2 ? r.y + r.height - h : r.y;
        r.blocks.add(new RectF(x, y, x + w, y + h));
    }

    /**
     * Props that landed in or against a room's walled-off blocks: a torch on a north wall that is now
     * solid moves down onto the block's own front face; anything else there is taken away.
     */
    private static void settleDecor(FloorLayout layout) {
        List<Props.Decor> list = layout.decor;
        for (int i = list.size() - 1; i >= 0; i--) {
            Props.Decor d = list.get(i);
            Room r = null;
            for (Room room : layout.rooms) if (room.inRect(d.x, d.y)) { r = room; break; }
            if (r == null || r.blocks.isEmpty()) continue;
            // things lying flat on the floor reach out by their size, so they mustn't spill onto a block
            boolean flat = d.kind == Props.PUDDLE || d.kind == Props.BLOOD_POOL || d.kind == Props.MOSS_PATCH
                    || d.kind == Props.HAY || d.kind == Props.RIFT || d.kind == Props.VENT || d.kind == Props.RUG;
            // a side-wall torch climbs about a body's height above where it's placed, so it needs that much clear
            boolean sideTorch = d.kind == Props.TORCH && d.b != Room.NORTH;
            RectF b = r.blockNear(d.x, d.y, sideTorch ? 90f : d.kind == Props.TORCH ? 10f : flat ? 45f + d.a : 45f);
            if (b == null) continue;
            if (d.kind == Props.TORCH && d.b == Room.NORTH && b.bottom < r.y + r.height - Room.TILE
                    && r.blockNear(d.x, b.bottom + 34f, 0f) == null) {
                list.set(i, new Props.Decor(Props.TORCH, d.x, b.bottom + 34f, d.a, d.b));
            } else {
                list.remove(i);
            }
        }
    }

    /** True when the straight line between two points in a room runs through one of its blocks. */
    private static boolean blocked(Room r, float x0, float y0, float x1, float y1) {
        if (r.blocks.isEmpty()) return false;
        for (int i = 1; i < 16; i++) {
            float t = i / 16f;
            if (r.blockNear(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, 40f) != null) return true;
        }
        return false;
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
        for (Corridor c : corridors) if (c.contains(px, py) && !gated(c)) return true;
        return false;
    }

    /** A corridor behind a shut gate: a locked vault, or the Trial arena while its waves fight. */
    private boolean gated(Corridor c) {
        return (vaultLocked && c == vaultCorridor) || (trialSealed && c == trialCorridor);
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

    /**
     * True when something of radius r lying at (x, y), a coin or a chest, shows on open floor: on walkable
     * floor with r to spare all round, below the brick face a north wall (or a walled-off block) draws
     * WALL_FACE down from its line, and off the barrels, crates and pillars.
     */
    public boolean clearForItem(float x, float y, float r) {
        if (!isOnFloor(x, y, r) || !isFloor(x, y - Props.WALL_FACE - r * 0.5f)) return false;
        float[] out = new float[2];
        Props.pushOut(decor, x, y, r, out);
        return out[0] == x && out[1] == y;
    }

    /**
     * Where an item meant for (x, y), thrown from (fromX, fromY), should come to rest: there if it's clear,
     * otherwise the first clear spot back along its path, otherwise the nearest clear spot round the thrower.
     */
    public float[] itemSpot(float x, float y, float r, float fromX, float fromY) {
        if (clearForItem(x, y, r)) return new float[]{x, y};
        for (int i = 1; i <= 6; i++) {
            float k = 1f - i / 6f, px = fromX + (x - fromX) * k, py = fromY + (y - fromY) * k;
            if (clearForItem(px, py, r)) return new float[]{px, py};
        }
        for (float ring = 40f; ring <= 280f; ring += 40f)
            for (int a = 0; a < 12; a++) {
                double t = a * Math.PI / 6;
                float px = fromX + (float) Math.cos(t) * ring, py = fromY + (float) Math.sin(t) * ring;
                if (clearForItem(px, py, r)) return new float[]{px, py};
            }
        return new float[]{fromX, fromY};
    }

    /** True when a body of radius r at (cx, cy) is fully on walkable floor. */
    boolean isOnFloor(float cx, float cy, float r) {
        return isFloor(cx,     cy    )
            && isFloor(cx + r, cy    )
            && isFloor(cx - r, cy    )
            && isFloor(cx,     cy + r)
            && isFloor(cx,     cy - r);
    }

    /**
     * Wall collision, then a push out of solid decorations (barrels, crates, pillars)
     * that is only kept if it doesn't shove the body into a wall.
     */
    public float[] resolveCollision(float oldX, float oldY, float newX, float newY, float radius) {
        float[] p = resolveWallCollision(oldX, oldY, newX, newY, radius);
        float[] out = new float[2];
        Props.pushOut(decor, p[0], p[1], radius, out);
        if (out[0] == p[0] && out[1] == p[1]) return p;
        return resolveWallCollision(p[0], p[1], out[0], out[1], radius);
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
        if (from >= 0 && from == to && from < rooms.size()) {
            // Same room, but a block in the way (an L's corner, a column): go round by the open middle
            Room r = rooms.get(from);
            float cx = r.centerX(), cy = r.centerY();
            if (blocked(r, fromX, fromY, toX, toY) && Math.hypot(fromX - cx, fromY - cy) > 60f) return new float[]{cx, cy};
            return null;
        }
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
        // Sized so a room has space to fight in but crossing it never feels like a journey: about a phone
        // screen for a small room, under two for the largest (a screen shows roughly 12 x 5 tiles)
        switch (type) {
            case HALLWAY: {
                float narrow = 2 * T;
                float length = (4 + rng.nextInt(3)) * T; // 4-6 tiles long
                return rng.nextBoolean() ? new float[]{narrow, length}
                                         : new float[]{length, narrow};
            }
            case SMALL:  return new float[]{(3 + rng.nextInt(2)) * T,
                                            (3 + rng.nextInt(2)) * T}; // 3-4 tiles
            case MEDIUM: return new float[]{(4 + rng.nextInt(3)) * T,
                                            (4 + rng.nextInt(3)) * T}; // 4-6 tiles
            case LARGE:  return new float[]{(6 + rng.nextInt(3)) * T,
                                            (5 + rng.nextInt(3)) * T}; // 6-8 x 5-7 tiles
            default:     return new float[]{4 * T, 4 * T};
        }
    }
}
