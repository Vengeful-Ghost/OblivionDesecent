package com.example.obliviondescent;

import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Live link for one co-op run in the Firebase Realtime Database, under runs/{code}.
 * The host simulates enemies and floor progress; every phone owns its own player.
 *
 * meta {host, floor, state}       host        members/{uid}   each player (read access)
 * players/{uid}  player state     each player enemies         host snapshot, ~12x/s
 * events/{id}    strikes, shots   host        hits/{id}       damage dealt by guests
 * batches/{rangeStart}  Gemini JSON or "fallback", host
 *
 * Player states and enemy snapshots carry the sender's clock; remote movement is replayed
 * PLAYBACK_DELAY behind the newest update, so it stays smooth however unevenly packets arrive.
 * Firebase callbacks arrive on the main thread; the game loop polls the queues and volatile fields.
 */
public class CoopSession {

    public static final String EXTRA_CODE = "coop_code";
    public static final String EXTRA_SEED = "coop_seed";
    public static final String EXTRA_HOST = "coop_host";
    public static final String EXTRA_SLOT = "coop_slot";
    public static final String EXTRA_PARTY = "coop_party";

    // Long enough to cover the gap between two updates plus network jitter
    public static final long PLAYBACK_DELAY = 150;

    /** Body colour for each party slot. */
    public static final int[] SLOT_COLORS = {
            Color.rgb(70, 120, 235), Color.rgb(80, 200, 120),
            Color.rgb(240, 150, 50), Color.rgb(190, 90, 230)};

    // Player state flags
    public static final int FLAG_DEAD = 1, FLAG_PORTAL = 2, FLAG_RANGED = 4, FLAG_BLADE = 8;

    private static final String TAG = "CoopSession";

    public interface Listener {
        /** Guests: the host's AI content for a floor range, or "fallback" for the offline set. */
        void onBatch(int rangeStart, String json);
        /** The run is over for everyone: the host left or the whole party fell. */
        void onRunEnded(String reason);
    }

    public interface OnCreatedListener {
        void onCreated(boolean success);
    }

    /** A message plus the time it arrived, so stale ones can be skipped. */
    public static class Received {
        public final long at = System.currentTimeMillis();
        public final String data;

        Received(String data) { this.data = data; }
    }

    /** One sender's clock, as seen from this phone: arrival time minus send time, kept near its low end. */
    public static final class SenderClock {
        private long offset = Long.MAX_VALUE;

        public synchronized void heard(long sentAt, long arrivedAt) {
            long gap = arrivedAt - sentAt;
            // Creeps up a little each time, so it follows a link that got slower
            offset = offset == Long.MAX_VALUE ? gap : Math.min(offset + 2, gap);
        }

        /** The sender-clock moment to show now: PLAYBACK_DELAY behind that sender's newest updates. */
        public synchronized long playbackTime(long now) {
            return (offset == Long.MAX_VALUE ? now : now - offset) - PLAYBACK_DELAY;
        }
    }

    /** Recent positions of one remote body, stamped with its sender's clock. */
    public static final class Track {
        private static final int SIZE = 8;
        private static final long IDLE_GAP = 250, STEP = 100;
        private final long[] times = new long[SIZE];
        private final float[] xs = new float[SIZE], ys = new float[SIZE];
        private int count, newest;

        /** Adds a sample; a long jump (teleport, new floor) restarts the track there. */
        public synchronized void add(long time, float x, float y) {
            if (count > 0) {
                if (time <= times[newest]) return;
                float dx = x - xs[newest], dy = y - ys[newest];
                if (dx * dx + dy * dy > 600f * 600f) count = 0;
                // Unchanged states aren't resent, so after a pause start moving just before this sample
                else if (time - times[newest] > IDLE_GAP) put(time - STEP, xs[newest], ys[newest]);
            }
            put(time, x, y);
        }

        public synchronized void clear() { count = 0; }

        private void put(long time, float x, float y) {
            newest = (newest + 1) % SIZE;
            times[newest] = time;
            xs[newest] = x;
            ys[newest] = y;
            if (count < SIZE) count++;
        }

        /** Position at a sender time, between the samples around it; runs on at most STEP past the newest. */
        public synchronized boolean sample(long time, float[] out) {
            if (count == 0) return false;
            int i = newest;
            for (int n = 1; n < count && times[i] > time; n++) i = (i + SIZE - 1) % SIZE;
            int from = i, to = i;
            if (times[i] <= time) {
                if (i != newest) {
                    to = (i + 1) % SIZE;
                } else if (count > 1) {
                    from = (i + SIZE - 1) % SIZE;
                    time = Math.min(time, times[i] + STEP);
                }
            }
            float f = from == to ? 0f : (float) (time - times[from]) / (times[to] - times[from]);
            out[0] = xs[from] + (xs[to] - xs[from]) * f;
            out[1] = ys[from] + (ys[to] - ys[from]) * f;
            return true;
        }
    }

    /** Another player in the run, drawn from their latest published state. */
    public static class RemotePlayer {
        public final Player body = new Player();
        // Written by Firebase callbacks on the UI thread, read by the game loop
        public volatile String name = "";
        public volatile int color, hp, maxHp = 100, floor, swing, weaponColor;
        public volatile float angle;
        public volatile boolean dead, portal, ranged;
        // Newest reported position: enemies chase this, while the body is drawn a moment behind it
        public volatile float newestX, newestY;
        public int shownSwing;
        private final SenderClock clock = new SenderClock();
        private final Track track = new Track();
        private final float[] pos = new float[2];
        private int weaponKind = -1;

        /** Parses "x,y,angle,hp,maxHp,flags,swing,floor,slot,weaponColor,time,name". */
        void apply(String value, long arrivedAt) {
            String[] f = value.split(",", 12);
            if (f.length < 12) return;
            try {
                float x = Float.parseFloat(f[0]);
                float y = Float.parseFloat(f[1]);
                angle   = Float.parseFloat(f[2]);
                hp      = Integer.parseInt(f[3]);
                maxHp   = Integer.parseInt(f[4]);
                int flags = Integer.parseInt(f[5]);
                swing   = Integer.parseInt(f[6]);
                int newFloor = Integer.parseInt(f[7]);
                color   = SLOT_COLORS[Integer.parseInt(f[8]) & 3];
                weaponColor = Integer.parseInt(f[9]);
                long sentAt = Long.parseLong(f[10]);
                name    = f[11];
                dead    = (flags & FLAG_DEAD) != 0;
                portal  = (flags & FLAG_PORTAL) != 0;
                ranged  = (flags & FLAG_RANGED) != 0;
                body.setColor(color);
                body.setAimAngle(angle);

                if (newFloor != floor) track.clear();
                floor = newFloor;
                clock.heard(sentAt, arrivedAt);
                track.add(sentAt, x, y);
                newestX = x;
                newestY = y;

                int kind = ranged ? 2 : (flags & FLAG_BLADE) != 0 ? 1 : 0;
                if (kind != weaponKind) {
                    weaponKind = kind;
                    body.equipWeapon(new Weapon("", 0, kind == 1 ? 80f : 30f, 0,
                            Weapon.Rarity.COMMON, weaponColor, 0f, ranged));
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Bad player state: " + value);
            }
        }

        /** Moves the drawn body along the replayed track. Called every game tick. */
        public void follow(long now) {
            if (track.sample(clock.playbackTime(now), pos)) {
                body.x = pos[0];
                body.y = pos[1];
            }
        }
    }

    public final String code;
    public final String uid;
    public final long seed;
    public final int slot;
    private final boolean host;

    public final Map<String, RemotePlayer> others = new ConcurrentHashMap<>();
    public final ConcurrentLinkedQueue<Received> events = new ConcurrentLinkedQueue<>();
    public final ConcurrentLinkedQueue<String> hits = new ConcurrentLinkedQueue<>();
    // Guests: every enemy snapshot in arrival order, "time;floor,kills;enemy;enemy..."
    public final ConcurrentLinkedQueue<Received> snapshots = new ConcurrentLinkedQueue<>();
    public final SenderClock hostClock = new SenderClock();
    public volatile int hostFloor = 1;

    private final DatabaseReference run;
    private final List<Runnable> detachers = new ArrayList<>();
    private Listener listener;
    private boolean metaSeen = false;
    private boolean ended = false;
    private volatile boolean left = false;
    private volatile boolean joined; // guests can't write until they are a member

    // Connection health, shown on screen so sync problems are visible
    private volatile String lastError;
    private volatile String hostUid;
    private volatile long lastHostUpdate = 0;

    // Last values written, so unchanged state isn't sent again (game loop thread only)
    private String lastSelf, lastEnemies;
    private long lastSelfAt;

    public CoopSession(String code, String uid, boolean host, long seed, int slot) {
        this.code = code;
        this.uid  = uid;
        this.host = host;
        this.seed = seed;
        this.slot = slot;
        this.joined = host; // the lobby already made the host a member
        this.run  = FirebaseDatabase.getInstance().getReference("runs").child(code);
    }

    public boolean isHost() { return host; }

    /**
     * Host, before sending the party in: creates the run with the host as its owner and first member.
     * The database rules only let the first writer of meta become the host.
     */
    public static void createRun(String code, String hostId, OnCreatedListener listener) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("host",  hostId);
        meta.put("floor", 1);
        meta.put("state", "playing");
        Map<String, Object> updates = new HashMap<>();
        updates.put("meta", meta);
        updates.put("members/" + hostId, true);
        FirebaseDatabase.getInstance().getReference("runs").child(code)
                .updateChildren(updates)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) Log.e(TAG, "createRun failed", task.getException());
                    listener.onCreated(task.isSuccessful());
                });
    }

    /** Joins the run and starts listening. Guests register as members first, which unlocks reading. */
    public void start(Listener listener) {
        this.listener = listener;
        if (host) {
            // If the host's connection drops, the run disappears and every guest is told
            run.onDisconnect().removeValue();
            attach();
            return;
        }
        run.child("members").child(uid).setValue(true).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                end("Couldn't join the run. Check the database rules.");
                return;
            }
            run.child("players").child(uid).onDisconnect().removeValue();
            joined = true;
            attach();
        });
    }

    private void attach() {
        if (left) return; // left before the member write finished
        listenValue(run.child("meta"), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) {
                    if (metaSeen) end("The host left the run.");
                    return;
                }
                metaSeen = true;
                hostUid = snap.child("host").getValue(String.class);
                Long floor = snap.child("floor").getValue(Long.class);
                if (floor != null) hostFloor = floor.intValue();
                if ("over".equals(snap.child("state").getValue(String.class)))
                    end("Your whole party has fallen on floor " + hostFloor + ".");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                end("Lost the connection to the run (" + error.getMessage() + ").");
            }
        });

        listenChildren(run.child("players"), new Children() {
            @Override
            void onChild(DataSnapshot snap) {
                String key = snap.getKey();
                String value = snap.getValue(String.class);
                if (key == null || value == null || key.equals(uid)) return;
                if (key.equals(hostUid)) lastHostUpdate = System.currentTimeMillis();
                RemotePlayer player = others.get(key);
                if (player == null) {
                    player = new RemotePlayer();
                    others.put(key, player);
                }
                player.apply(value, System.currentTimeMillis());
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snap) {
                if (snap.getKey() != null) others.remove(snap.getKey());
            }
        });

        if (host) {
            listenChildren(run.child("hits"), new Children() {
                @Override
                void onChild(DataSnapshot snap) {
                    String value = snap.getValue(String.class);
                    if (value != null) hits.add(value);
                    snap.getRef().removeValue();
                }
            });
            return;
        }

        listenValue(run.child("enemies"), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                String value = snap.getValue(String.class);
                if (value == null) return;
                snapshots.add(new Received(value));
                // A paused game loop doesn't drain the queue; only recent snapshots matter
                while (snapshots.size() > 20) snapshots.poll();
                lastHostUpdate = System.currentTimeMillis();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                lastError = error.getMessage();
                Log.e(TAG, "enemies listener cancelled: " + error.getMessage());
            }
        });

        listenChildren(run.child("events"), new Children() {
            @Override
            void onChild(DataSnapshot snap) {
                String value = snap.getValue(String.class);
                if (value != null) events.add(new Received(value));
                lastHostUpdate = System.currentTimeMillis();
            }
        });

        listenChildren(run.child("batches"), new Children() {
            @Override
            void onChild(DataSnapshot snap) {
                String value = snap.getValue(String.class);
                if (value == null || snap.getKey() == null || ended) return;
                try {
                    listener.onBatch(Integer.parseInt(snap.getKey()), value);
                } catch (NumberFormatException ignored) { }
            }
        });
    }

    /* ============================================================
       WRITES
       ============================================================ */

    /** This phone's player: "x,y,angle,hp,maxHp,flags,swing,floor,slot,weaponColor,time,name". */
    public void publishSelf(float x, float y, float angle, int hp, int maxHp, int flags,
                            int swing, int floor, int weaponColor, String name) {
        String state = (int) x + "," + (int) y + "," + (int) angle + ","
                + hp + "," + maxHp + "," + flags + "," + swing + "," + floor + ","
                + slot + "," + weaponColor;
        long now = System.currentTimeMillis();
        // Unchanged state is resent once a second as a heartbeat
        if (state.equals(lastSelf) && now - lastSelfAt < 1000) return;
        lastSelf = state;
        lastSelfAt = now;
        write(run.child("players").child(uid), state + "," + now + "," + name);
    }

    /** Guests: damage dealt to an enemy, applied by the host. */
    public void sendHit(String hit) {
        write(run.child("hits").push(), uid + "," + hit);
    }

    // Host only
    public void publishEnemies(String snapshot) {
        if (snapshot.equals(lastEnemies)) return;
        lastEnemies = snapshot;
        write(run.child("enemies"), System.currentTimeMillis() + ";" + snapshot);
    }
    public void publishEvent(String event)           { write(run.child("events").push(), event); }
    public void clearEvents()                        { write(run.child("events"), null); }
    public void publishFloor(int floor)              { write(run.child("meta").child("floor"), floor); }
    public void publishState(String state)           { write(run.child("meta").child("state"), state); }
    public void publishBatch(int rangeStart, String json) {
        write(run.child("batches").child(String.valueOf(rangeStart)), json);
    }

    /** Writes nothing once this phone has left, so a late game tick can't recreate deleted data. */
    private void write(DatabaseReference ref, Object value) {
        if (left || !joined) return;
        ref.setValue(value).addOnFailureListener(e -> lastError = e.getMessage());
    }

    /* ============================================================
       STATUS — drawn under the minimap
       ============================================================ */

    /** False after a database error, or when a guest hasn't heard from the host for 2 seconds. */
    public boolean isSyncHealthy(long now) {
        if (lastError != null) return false;
        return host || (lastHostUpdate > 0 && now - lastHostUpdate <= 2000);
    }

    /** One short line about the connection. */
    public String statusLine(long now) {
        if (lastError != null) return "Sync error: " + lastError;
        if (!host && lastHostUpdate == 0) return "Connecting to the host...";
        if (!host && now - lastHostUpdate > 2000)
            return "No updates from the host for " + (now - lastHostUpdate) / 1000 + "s";
        return "Co-op • " + (others.size() + 1) + " players";
    }

    /* ============================================================
       QUERIES — called from the game loop
       ============================================================ */

    /** True when a living player on this floor is standing in the exit portal. */
    public boolean anyPortalRequest(int floor) {
        for (RemotePlayer r : others.values()) {
            if (r.portal && !r.dead && r.floor == floor) return true;
        }
        return false;
    }

    /** True when every other player on this floor is down (or nobody else is here). */
    public boolean allOthersDown(int floor) {
        for (RemotePlayer r : others.values()) {
            if (!r.dead && r.floor == floor) return false;
        }
        return true;
    }

    /**
     * Stops listening and leaves: the host deletes the whole run, a guest removes only themselves.
     * @return false if this phone had already left
     */
    public boolean leave() {
        if (left) return false;
        left = true;
        ended = true;
        for (Runnable detach : detachers) detach.run();
        detachers.clear();
        if (host) {
            run.removeValue();
        } else {
            run.child("players").child(uid).removeValue();
            run.child("members").child(uid).removeValue();
        }
        return true;
    }

    private void end(String reason) {
        if (ended) return;
        ended = true;
        if (listener != null) listener.onRunEnded(reason);
    }

    private void listenValue(DatabaseReference ref, ValueEventListener l) {
        ref.addValueEventListener(l);
        detachers.add(() -> ref.removeEventListener(l));
    }

    private void listenChildren(DatabaseReference ref, ChildEventListener l) {
        ref.addChildEventListener(l);
        detachers.add(() -> ref.removeEventListener(l));
    }

    /** Child listener that treats added and changed children the same way. */
    private abstract class Children implements ChildEventListener {
        abstract void onChild(DataSnapshot snap);

        @Override public void onChildAdded(@NonNull DataSnapshot snap, String previous)   { onChild(snap); }
        @Override public void onChildChanged(@NonNull DataSnapshot snap, String previous) { onChild(snap); }
        @Override public void onChildRemoved(@NonNull DataSnapshot snap) { }
        @Override public void onChildMoved(@NonNull DataSnapshot snap, String previous) { }
        @Override public void onCancelled(@NonNull DatabaseError error) {
            lastError = error.getMessage();
            Log.e(TAG, "listener cancelled: " + error.getMessage());
        }
    }
}
