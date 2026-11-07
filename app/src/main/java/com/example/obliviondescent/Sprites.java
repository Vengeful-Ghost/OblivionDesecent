package com.example.obliviondescent;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// ===========================================================================================================
//  >>> THE ART LIVES IN art/, NOT HERE <<<  Sprites are drawn in code under art/src/fx and listed in
//  art/sprites.mjs. After changing any of them, export from the repo root, then rebuild the app:
//
//      node art/tools/export.mjs
//
//  That rewrites app/src/main/assets/px/ (px0.png, px1.png... and sprites.json), which this class loads.
//  Preview without exporting:  node art/tools/look.mjs <name prefix>   ->   art/records/look/<prefix>.png
// ===========================================================================================================

/**
 * The game's pixel art, baked from art/ by art/tools/export.mjs into assets/px: sheets, and the animations
 * cut from them. The world is drawn into a buffer at one pixel per {@link #PX} world units (see PixelCanvas),
 * so a sprite drawn here lands on that buffer at exactly its own size, snapped to whole pixels.
 * Drawing happens on the UI thread only, so the shared paints need no locking.
 */
final class Sprites {

    /** World units (screen pixels) per art pixel. */
    static final float PX = 4f;

    /** One frame: where it sits on its sheet, and its top-left in art pixels from the pivot (the feet). */
    static final class Frame {
        final Bitmap page;
        final Rect src;
        final int ox, oy;

        Frame(Bitmap page, Rect src, int ox, int oy) {
            this.page = page;
            this.src = src;
            this.ox = ox;
            this.oy = oy;
        }
    }

    static final class Anim {
        final float fps;
        final boolean loop;
        final Frame[] base, dye; // dye is null when nothing in it takes a colour
        /** How far above the pivot the tallest frame reaches, in world units (negative is up). */
        final float top;

        Anim(float fps, boolean loop, Frame[] base, Frame[] dye) {
            this.fps = fps;
            this.loop = loop;
            this.base = base;
            this.dye = dye;
            int min = 0;
            for (Frame[] layer : new Frame[][]{base, dye})
                if (layer != null) for (Frame f : layer) if (f != null) min = Math.min(min, f.oy);
            top = min * PX;
        }

        int frames() { return base.length; }

        long durationMs() { return (long) (base.length * 1000f / fps); }

        /**
         * The frame showing ms into the animation: loops wrap, one-shots hold their last frame. ms may be the
         * wall clock (about 1.7e12): counted in long frames, so it never clamps to one frame as an int would.
         */
        int at(long ms) {
            long i = (long) (Math.max(0L, ms) * (double) fps / 1000.0);
            return (int) (loop ? i % base.length : Math.min(i, base.length - 1));
        }

        boolean done(long ms) { return !loop && ms >= durationMs(); }
    }

    private static final Map<String, Anim> ANIMS = new HashMap<>();
    private static boolean loaded = false;

    private static final Paint PLAIN = crisp();
    private static final Map<Integer, Paint> DYES = new HashMap<>(), FLASHES = new HashMap<>();
    private static final RectF DST = new RectF();

    private Sprites() {}

    /** The game's pixel font (Pixelify Sans), for paints that draw text in the world; null before load(). */
    static android.graphics.Typeface font;

    /** Loads every sheet and animation once; later calls return at once. A missing pack leaves the old shapes. */
    static synchronized void load(Context context) {
        if (loaded) return;
        loaded = true;
        try {
            font = context.getResources().getFont(R.font.pixel);
        } catch (Exception e) {
            Log.w("Sprites", "Pixel font missing", e);
        }
        try {
            JSONObject json = new JSONObject(readAsset(context, "px/sprites.json"));
            JSONArray files = json.getJSONArray("pages");
            Bitmap[] pages = new Bitmap[files.length()];
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inScaled = false;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            for (int i = 0; i < pages.length; i++) {
                try (InputStream in = context.getAssets().open("px/" + files.getString(i))) {
                    pages[i] = BitmapFactory.decodeStream(in, null, opts);
                }
            }
            JSONObject anims = json.getJSONObject("anims");
            for (java.util.Iterator<String> it = anims.keys(); it.hasNext(); ) {
                String name = it.next();
                JSONObject a = anims.getJSONObject(name);
                ANIMS.put(name, new Anim((float) a.getDouble("fps"), a.getInt("loop") != 0,
                        frames(a.getJSONArray("f"), pages), a.has("d") ? frames(a.getJSONArray("d"), pages) : null));
            }
        } catch (Exception e) {
            Log.w("Sprites", "No pixel art pack; drawing shapes instead", e);
            ANIMS.clear();
        }
    }

    private static Frame[] frames(JSONArray list, Bitmap[] pages) throws Exception {
        Frame[] out = new Frame[list.length()];
        for (int i = 0; i < out.length; i++) {
            JSONArray f = list.getJSONArray(i);
            int x = f.getInt(1), y = f.getInt(2), w = f.getInt(3), h = f.getInt(4);
            out[i] = w == 0 ? null : new Frame(pages[f.getInt(0)], new Rect(x, y, x + w, y + h), f.getInt(5), f.getInt(6));
        }
        return out;
    }

    private static String readAsset(Context context, String path) throws Exception {
        try (InputStream in = context.getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    /** The named animation, or null when the pack doesn't have it (callers fall back to their shapes). */
    static Anim get(String name) {
        return ANIMS.get(name);
    }

    // Eight ways to face, clockwise from east on screen (y down): E, SE, S, SW, W, NW, N, NE. The pack draws
    // bodies in five views (art/sprites.mjs: "player.walk" side on, then ".se", ".s", ".ne", ".n"); the
    // three left-facing ways are those mirrored.
    private static final String[] VIEW_OF = {"", ".se", ".s", ".se", "", ".ne", ".n", ".ne"};
    private static final boolean[] MIRROR = {false, false, false, true, true, true, false, false};
    private static final Map<String, Anim[]> VIEWS = new HashMap<>();

    /**
     * Which of the eight ways a body looking along (dx, dy) faces, given the way it faced before: it only
     * turns once the look is well past the halfway line between two ways, so it doesn't flicker on it.
     */
    static int facing(int before, float dx, float dy) {
        if (dx * dx + dy * dy < 1e-6f) return before;
        double deg = Math.toDegrees(Math.atan2(dy, dx));
        double off = Math.abs(((deg - before * 45.0) % 360 + 540) % 360 - 180); // degrees from the current way
        if (before >= 0 && off < 30) return before;
        return (int) Math.floorMod(Math.round(deg / 45.0), 8);
    }

    /** The name of base's view for way ("fx.dash.kick" facing south-west is "fx.dash.kick.se", mirrored). */
    static String viewName(String base, int way) {
        return base + VIEW_OF[way & 7];
    }

    /** True when facing way (from {@link #facing}) is drawn as its mirror image. */
    static boolean mirrored(int way) {
        return MIRROR[way & 7];
    }

    /** The body animation base ("player.walk") as seen facing way; its side view when the pack has no other. */
    static Anim get(String base, int way) {
        Anim[] views = VIEWS.get(base);
        if (views == null) {
            views = new Anim[8];
            Anim side = ANIMS.get(base);
            for (int i = 0; i < 8; i++) {
                Anim a = ANIMS.get(base + VIEW_OF[i]);
                views[i] = a != null ? a : side;
            }
            if (side != null) VIEWS.put(base, views); // before the pack loads, try again next time
        }
        return views[way & 7];
    }

    private static final Map<String, Bitmap> TEXTURES = new HashMap<>();

    /** A one-frame sprite cut out on its own, for a repeating BitmapShader; null when the pack lacks it. */
    static Bitmap texture(String name) {
        Bitmap b = TEXTURES.get(name);
        if (b == null) {
            Anim a = ANIMS.get(name);
            if (a == null || a.base[0] == null) return null;
            Frame f = a.base[0];
            b = Bitmap.createBitmap(f.page, f.src.left, f.src.top, f.src.width(), f.src.height());
            TEXTURES.put(name, b);
        }
        return b;
    }

    /**
     * A filter that brings a texture's greys to a colour: its mid grey (160) becomes the colour exactly,
     * lighter and darker greys a little brighter and darker than it.
     */
    static android.graphics.ColorFilter tint(int color) {
        float k = 1f / 160f;
        return new android.graphics.ColorMatrixColorFilter(new float[]{
                android.graphics.Color.red(color) * k, 0, 0, 0, 0,
                0, android.graphics.Color.green(color) * k, 0, 0, 0,
                0, 0, android.graphics.Color.blue(color) * k, 0, 0,
                0, 0, 0, 1, 0});
    }

    /** A paint that scales bitmaps pixel for pixel. A plain new Paint() smooths them (it filters by default). */
    static Paint crisp() {
        Paint p = new Paint();
        p.setFilterBitmap(false);
        p.setDither(false);
        return p;
    }

    /** Rounds a world coordinate to a whole art pixel, so sprites never land between two. */
    static float snap(float v) {
        return Math.round(v / PX) * PX;
    }

    /**
     * Draws frame i of a with its pivot at world (x, y), mirrored to face left when flip. The dye layer takes
     * dyeColor (multiplied, so its shading stays); alpha 0..255 fades the whole sprite.
     */
    static void draw(Canvas canvas, Anim a, int i, float x, float y, boolean flip, int dyeColor, int alpha) {
        draw(canvas, a, i, x, y, flip, 1f, dyeColor, alpha);
    }

    /** As draw, at scale times the sprite's own size (1 keeps it pixel-exact). */
    static void draw(Canvas canvas, Anim a, int i, float x, float y, boolean flip, float scale, int dyeColor, int alpha) {
        if (a == null || i < 0 || i >= a.base.length) return;
        PLAIN.setAlpha(alpha);
        blit(canvas, a.base[i], x, y, flip, scale, PLAIN);
        if (a.dye != null) {
            Paint p = paintFor(DYES, dyeColor, PorterDuff.Mode.MULTIPLY);
            p.setAlpha(alpha);
            blit(canvas, a.dye[i], x, y, flip, scale, p);
        }
    }

    /** The frame's silhouette in one colour: a hit flash, a white death flash. */
    static void flash(Canvas canvas, Anim a, int i, float x, float y, boolean flip, float scale, int color, int alpha) {
        if (a == null || i < 0 || i >= a.base.length) return;
        Paint p = paintFor(FLASHES, color, PorterDuff.Mode.SRC_ATOP);
        p.setAlpha(alpha);
        blit(canvas, a.base[i], x, y, flip, scale, p);
        if (a.dye != null) blit(canvas, a.dye[i], x, y, flip, scale, p);
    }

    private static Paint ragePaint;

    /**
     * Frame i redrawn in molten crimson (a boss's last stand): its own shading kept, its darks gone to black-red
     * and its lights to hot orange. Drawn over the normal frame at alpha, so it can burn in.
     */
    static void rage(Canvas canvas, Anim a, int i, float x, float y, boolean flip, float scale, int alpha) {
        if (a == null || i < 0 || i >= a.base.length || alpha <= 0) return;
        if (ragePaint == null) {
            Paint p = crisp();
            p.setColorFilter(new android.graphics.ColorMatrixColorFilter(new float[]{
                    0.75f, 0.45f, 0.20f, 0, 22,
                    0.12f, 0.18f, 0.05f, 0, 0,
                    0.05f, 0.05f, 0.06f, 0, 0,
                    0, 0, 0, 1, 0}));
            ragePaint = p;
        }
        ragePaint.setAlpha(alpha);
        blit(canvas, a.base[i], x, y, flip, scale, ragePaint);
        if (a.dye != null) blit(canvas, a.dye[i], x, y, flip, scale, ragePaint);
    }

    private static void blit(Canvas canvas, Frame f, float x, float y, boolean flip, float scale, Paint paint) {
        if (f == null) return;
        float px = PX * scale, sx = snap(x), sy = snap(y);
        float left = sx + f.ox * px, top = sy + f.oy * px;
        DST.set(left, top, left + f.src.width() * px, top + f.src.height() * px);
        if (flip) {
            canvas.save();
            canvas.scale(-1f, 1f, sx, sy);
            canvas.drawBitmap(f.page, f.src, DST, paint);
            canvas.restore();
        } else {
            canvas.drawBitmap(f.page, f.src, DST, paint);
        }
    }

    private static Paint paintFor(Map<Integer, Paint> cache, int color, PorterDuff.Mode mode) {
        color |= 0xFF000000;
        Paint p = cache.get(color);
        if (p == null) {
            if (cache.size() > 96) cache.clear(); // colours come from a handful of bodies; never let it grow
            p = crisp();
            p.setColorFilter(new PorterDuffColorFilter(color, mode));
            cache.put(color, p);
        }
        return p;
    }
}
