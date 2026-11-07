package com.example.obliviondescent;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Visual-only effects with no gameplay impact: hit sparks, death bursts, dash trails,
 * floating damage numbers and banners such as "LEVEL 2".
 * Effects are added from the logic and UI threads and drawn by GameDisplay, so every method is synchronized.
 */
public class GameEffects {

    private static final int MAX_PARTICLES = 400;

    private static class Particle {
        float x, y, vx, vy, size;
        int color;
        long born, life;
        boolean pixel; // a square on the art-pixel grid instead of a soft dot (the dash's sparks)
    }

    private static class FloatingText {
        float x, y, size, rise;
        String text;
        int color;
        long born, life;
    }

    private static class Slash {
        float x, y, angle, radius, arc;
        int color;
        long born;
    }

    private static final long SLASH_LIFE = 180;

    // Marks left on the floor for the rest of the floor: blood, scorch, and bodies that slowly fade
    private static class Stain {
        float x, y, rx, ry, angle;
        int color;
        long born, life; // life 0 = stays until the floor changes
    }

    private static final int MAX_STAINS = 220;
    private static final long CORPSE_LIFE = 9000;

    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatingText> texts = new ArrayList<>();
    private final List<Slash> slashes = new ArrayList<>();
    private final List<Stain> stains = new ArrayList<>();
    private final Random rng = new Random();

    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF slashRect = new RectF();

    public GameEffects() {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        textPaint.setShadowLayer(4f, 0f, 2f, Color.BLACK);
        slashPaint.setStyle(Paint.Style.STROKE);
        slashPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    // Floating numbers use the game's title font
    public void setFont(Typeface font) {
        textPaint.setTypeface(Typeface.create(font, Typeface.BOLD));
    }

    /**
     * A quick fading arc showing where a swing or strike landed.
     * @param x world X of the attacker
     * @param y world Y of the attacker
     * @param angle centre of the arc in degrees
     * @param radius reach of the swing
     * @param arc half-angle in degrees; 180 draws a full circle
     * @param color arc colour
     */
    public synchronized void slash(float x, float y, float angle, float radius, float arc, int color) {
        Slash s = new Slash();
        s.x = x;
        s.y = y;
        s.angle = angle;
        s.radius = radius;
        s.arc = arc;
        s.color = color;
        s.born = System.currentTimeMillis();
        slashes.add(s);
    }

    /**
     * Sprays particles outward from a point.
     * @param x world X
     * @param y world Y
     * @param color particle colour
     * @param count number of particles
     * @param speed maximum starting speed in world units per tick
     */
    public synchronized void burst(float x, float y, int color, int count, float speed) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < count && particles.size() < MAX_PARTICLES; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            float v = speed * (0.3f + rng.nextFloat() * 0.7f);
            Particle p = new Particle();
            p.x = x;
            p.y = y;
            p.vx = (float) Math.cos(angle) * v;
            p.vy = (float) Math.sin(angle) * v;
            p.size = 3f + rng.nextFloat() * 4f;
            p.color = color;
            p.born = now;
            p.life = 300 + rng.nextInt(300);
            particles.add(p);
        }
    }

    /** The player bursting into motes of their colour and puffs of grey smoke, with a white ring. */
    public synchronized void poof(float x, float y, int color) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 60 && particles.size() < MAX_PARTICLES; i++) {
            boolean smoke = i % 3 == 0;
            double angle = rng.nextDouble() * Math.PI * 2;
            float v = (smoke ? 3f : 9f) * (0.3f + rng.nextFloat() * 0.7f);
            Particle p = new Particle();
            p.x = x + (rng.nextFloat() - 0.5f) * 30f;
            p.y = y + (rng.nextFloat() - 0.5f) * 30f;
            p.vx = (float) Math.cos(angle) * v;
            p.vy = (float) Math.sin(angle) * v - (smoke ? 2f : 0f); // smoke drifts up
            p.size = smoke ? 14f + rng.nextFloat() * 12f : 5f + rng.nextFloat() * 7f;
            p.color = smoke ? Color.rgb(150, 150, 160) : color;
            p.born = now;
            p.life = smoke ? 900 + rng.nextInt(500) : 600 + rng.nextInt(500);
            particles.add(p);
        }
        slash(x, y, 0f, 70f, 180f, Color.WHITE);
    }

    /** Leaves a single slow-fading particle, used for the dash trail. */
    public synchronized void trail(float x, float y, int color) {
        if (particles.size() >= MAX_PARTICLES) return;
        Particle p = new Particle();
        p.x = x + (rng.nextFloat() - 0.5f) * 20f;
        p.y = y + (rng.nextFloat() - 0.5f) * 20f;
        p.size = 10f + rng.nextFloat() * 8f;
        p.color = color;
        p.born = System.currentTimeMillis();
        p.life = 250;
        particles.add(p);
    }

    // -- The dash, drawn live between its baked kick and skid (fx.dash.*) --------------------------------

    // An afterimage: the player's own frame as a silhouette in the dash colour, white for its first moment
    private static class Ghost {
        Sprites.Anim anim;
        int frame, color;
        float x, y;
        boolean flip;
        long born, life;
    }

    // A pixel speed line: its head follows the dasher, its tail runs up to it once the dash ends
    private static class Streak {
        float x0, y0, x1, y1, off, lag;
        int color, width;
        long endAt; // 0 while the dash lasts
    }

    private final List<Ghost> ghosts = new ArrayList<>();
    private final List<Streak> streaks = new ArrayList<>();
    private final Paint pixelPaint = new Paint();
    private static final long STREAK_FADE = 240;
    // The dash's speed lines: offset across the way (world units), how far up the dash the tail starts, colour,
    // thickness in art pixels. The white core rides in the middle, the cooler ones out to the sides.
    private static final float[] LINE_OFF = {0f, -14f, 14f, -26f, 26f, -6f, 8f};
    private static final float[] LINE_LAG = {0f, 0.15f, 0.25f, 0.45f, 0.35f, 0.6f, 0.55f};
    private static final int[] LINE_COLOR = {0xFFFFFFFF, 0xFFB4DCFF, 0xFFB4DCFF, 0xFF7A8CFF, 0xFF7A8CFF, 0xFF7AB8FF, 0xFF7AB8FF};
    private static final int[] LINE_WIDTH = {2, 1, 1, 1, 1, 1, 1};

    /** Leaves an afterimage of a body's frame where it stands (feet at x, y), fading over life ms. */
    public synchronized void ghost(Sprites.Anim anim, int frame, float x, float y, boolean flip, int color, long life) {
        if (anim == null) return;
        if (ghosts.size() >= 24) ghosts.remove(0);
        Ghost g = new Ghost();
        g.anim = anim;
        g.frame = frame;
        g.x = x;
        g.y = y;
        g.flip = flip;
        g.color = color;
        g.born = System.currentTimeMillis();
        g.life = life;
        ghosts.add(g);
    }

    /** A dash starts at (x, y): its speed lines are drawn from there to wherever dashMove takes them. */
    public synchronized void dashStart(float x, float y) {
        dashEnd(x, y); // lines still following an earlier dash let go of it
        for (int i = 0; i < LINE_OFF.length; i++) {
            Streak s = new Streak();
            s.x0 = s.x1 = x;
            s.y0 = s.y1 = y;
            s.off = LINE_OFF[i];
            s.lag = LINE_LAG[i];
            s.color = LINE_COLOR[i];
            s.width = LINE_WIDTH[i];
            streaks.add(s);
        }
    }

    /** The dasher moved: the heads of the lines still following come with it. */
    public synchronized void dashMove(float x, float y) {
        for (Streak s : streaks) if (s.endAt == 0) { s.x1 = x; s.y1 = y; }
    }

    /** The dash is over: its lines stop at (x, y) and their tails run up to them. */
    public synchronized void dashEnd(float x, float y) {
        long now = System.currentTimeMillis();
        for (Streak s : streaks) if (s.endAt == 0) { s.x1 = x; s.y1 = y; s.endAt = now; }
    }

    /** Afterimages and speed lines, drawn under the player so it runs out in front of them. */
    public synchronized void drawDash(Canvas canvas, GameCamera camera) {
        long now = System.currentTimeMillis();
        float offX = camera.getOffsetX(), offY = camera.getOffsetY();
        ghosts.removeIf(g -> now - g.born >= g.life);
        streaks.removeIf(s -> s.endAt > 0 && now - s.endAt >= STREAK_FADE);
        for (Ghost g : ghosts) {
            float k = (float) (now - g.born) / g.life;
            int alpha = (int) (200 * (1f - k) * (1f - k));
            // a white flash as it's left behind, cooling to the dash colour
            Sprites.flash(canvas, g.anim, g.frame, g.x - offX, g.y - offY, g.flip, 1f, k < 0.18f ? Color.WHITE : g.color, alpha);
        }
        float px = Sprites.PX;
        for (Streak s : streaks) {
            float dx = s.x1 - s.x0, dy = s.y1 - s.y0, len = (float) Math.hypot(dx, dy);
            if (len < px * 3) continue;
            float ux = dx / len, uy = dy / len;
            // while dashing the tail trails the start by its lag; after, it eases up to the head
            float u = s.endAt == 0 ? 0f : Math.min(1f, (float) (now - s.endAt) / STREAK_FADE);
            float tail = s.lag + (1f - s.lag) * (1f - (1f - u) * (1f - u));
            int steps = (int) (len / px);
            float fade = 1f - u * 0.6f;
            pixelPaint.setColor(s.color);
            for (int i = (int) (steps * tail); i <= steps; i++) {
                // the line breaks up toward its tail: every pixel near the head, every other one further back
                float along = (float) i / steps;
                if (along < 0.45f && (i & 1) == 1) continue;
                float wx = s.x0 + ux * len * along - uy * s.off, wy = s.y0 + uy * len * along + ux * s.off;
                float sx = Sprites.snap(wx - offX), sy = Sprites.snap(wy - offY);
                pixelPaint.setAlpha((int) (255 * fade * (0.25f + 0.75f * along)));
                canvas.drawRect(sx, sy, sx + px * s.width, sy + px * s.width, pixelPaint);
            }
        }
    }

    /** A spark shed by a dash: a square on the pixel grid that drifts back the way it came and winks out. */
    public synchronized void dashSpark(float x, float y, float vx, float vy, int color) {
        if (particles.size() >= MAX_PARTICLES) return;
        Particle p = new Particle();
        p.x = x + (rng.nextFloat() - 0.5f) * 36f;
        p.y = y + (rng.nextFloat() - 0.5f) * 36f;
        p.vx = vx + (rng.nextFloat() - 0.5f) * 3f;
        p.vy = vy + (rng.nextFloat() - 0.5f) * 3f - 0.8f;
        p.size = rng.nextFloat() < 0.3f ? 2 : 1;
        p.color = color;
        p.pixel = true;
        p.born = System.currentTimeMillis();
        p.life = 260 + rng.nextInt(260);
        particles.add(p);
    }

    /** Small number that floats up and fades, e.g. damage dealt. */
    public synchronized void number(float x, float y, String text, int color) {
        addText(x + (rng.nextFloat() - 0.5f) * 30f, y, text, color, 30f, 70f, 700);
    }

    /**
     * Sprays particles in a cone along (dirX, dirY), e.g. blood and sparks flying off the side that was hit.
     * @param spread half-width of the cone in radians
     */
    public synchronized void spray(float x, float y, float dirX, float dirY, int color, int count, float speed,
                                   float spread) {
        long now = System.currentTimeMillis();
        double base = Math.atan2(dirY, dirX);
        for (int i = 0; i < count && particles.size() < MAX_PARTICLES; i++) {
            double angle = base + (rng.nextFloat() * 2f - 1f) * spread;
            float v = speed * (0.35f + rng.nextFloat() * 0.65f);
            Particle p = new Particle();
            p.x = x;
            p.y = y;
            p.vx = (float) Math.cos(angle) * v;
            p.vy = (float) Math.sin(angle) * v;
            p.size = 2.5f + rng.nextFloat() * 4f;
            p.color = color;
            p.born = now;
            p.life = 250 + rng.nextInt(300);
            particles.add(p);
        }
    }

    /** A splash that stays on the floor: a main blot ringed by a few droplets. */
    /** Settings: blood stains and bodies on the floor. */
    public volatile boolean gore = true;

    public synchronized void stain(float x, float y, float size, int color) {
        if (!gore) return;
        addStain(x, y, size, size * (0.7f + rng.nextFloat() * 0.3f), color, 0);
        int drops = 2 + rng.nextInt(4);
        for (int i = 0; i < drops; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            float d = size * (0.9f + rng.nextFloat() * 0.8f), r = size * (0.12f + rng.nextFloat() * 0.18f);
            addStain(x + (float) Math.cos(a) * d, y + (float) Math.sin(a) * d, r, r, color, 0);
        }
    }

    /** A fallen body, flattened and darkened, that lies a while and then fades away. */
    public synchronized void corpse(float x, float y, float radius, int color) {
        if (!gore) return;
        int dark = Color.rgb(Color.red(color) * 2 / 5, Color.green(color) * 2 / 5, Color.blue(color) * 2 / 5);
        addStain(x, y, radius * 1.05f, radius * 0.6f, dark, CORPSE_LIFE);
    }

    private void addStain(float x, float y, float rx, float ry, int color, long life) {
        if (stains.size() >= MAX_STAINS) stains.remove(0);
        Stain s = new Stain();
        s.x = x;
        s.y = y;
        s.rx = rx;
        s.ry = ry;
        s.angle = rng.nextFloat() * 180f;
        s.color = color;
        s.born = System.currentTimeMillis();
        s.life = life;
        stains.add(s);
    }

    /** Large text that floats up slowly, e.g. "LEVEL 3" or "ENRAGED". */
    public synchronized void banner(float x, float y, String text, int color) {
        addText(x, y, text, color, 46f, 50f, 1400);
    }

    /** Huge text that holds about a second, e.g. a countdown. */
    public synchronized void countdown(float x, float y, String text, int color) {
        addText(x, y, text, color, 96f, 20f, 950);
    }

    private void addText(float x, float y, String text, int color, float size, float rise, long life) {
        FloatingText t = new FloatingText();
        t.x = x;
        t.y = y;
        t.text = text;
        t.color = color;
        t.size = size;
        t.rise = rise;
        t.born = System.currentTimeMillis();
        t.life = life;
        texts.add(t);
    }

    /** Advances particle motion; called once per logic tick. */
    public synchronized void update() {
        long now = System.currentTimeMillis();
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            if (now - p.born >= p.life) { it.remove(); continue; }
            p.x += p.vx;
            p.y += p.vy;
            p.vx *= 0.88f;
            p.vy *= 0.88f;
        }
        Iterator<FloatingText> tit = texts.iterator();
        while (tit.hasNext()) {
            FloatingText t = tit.next();
            if (now - t.born >= t.life) tit.remove();
        }
        slashes.removeIf(s -> now - s.born >= SLASH_LIFE);
        stains.removeIf(s -> s.life > 0 && now - s.born >= s.life);
        sprites.removeIf(s -> s.over(now));
    }

    // Pixel art one-shots from the pixelfx library: hit sparks, death poofs, blasts, heals (see art/sprites.mjs)
    private static class SpriteFx {
        Sprites.Anim anim;
        float x, y;
        boolean flip;
        long born;
        long hold; // a looping effect plays this long (ms), fading out over its last FADE; 0 = once through

        boolean over(long now) {
            return hold > 0 ? now - born >= hold : anim.done(now - born);
        }

        int alpha(long now) {
            long left = hold - (now - born);
            return hold > 0 && left < FADE ? (int) (255 * Math.max(0, left) / FADE) : 255;
        }
    }
    private static final long FADE = 250;

    private final List<SpriteFx> sprites = new ArrayList<>();
    private static final int MAX_SPRITES = 40;

    /**
     * A boss's meteor (its last stand): a warning mark that fills on the floor while a burning rock falls out of
     * the dark onto it; GameActivity takes it off with due() when it lands and hurts whoever stands inside.
     */
    public static final class Blast {
        public float x, y, r;
        public int damage;
        public long born, at;
        public String by;
    }
    private final List<Blast> blasts = new ArrayList<>();
    private static final long METEOR_FALL = 380;
    private final Paint markPaint = new Paint();

    /** Marks a meteor to land at (x, y) in delay ms. */
    public synchronized void mark(float x, float y, float r, int damage, long delay, String by) {
        Blast b = new Blast();
        b.x = x;
        b.y = y;
        b.r = r;
        b.damage = damage;
        b.born = System.currentTimeMillis();
        b.at = b.born + delay;
        b.by = by;
        blasts.add(b);
    }

    /** The meteors landing now, taken off the floor. */
    public synchronized List<Blast> due(long now) {
        List<Blast> out = null;
        for (int i = blasts.size() - 1; i >= 0; i--) {
            if (blasts.get(i).at > now) continue;
            if (out == null) out = new ArrayList<>();
            out.add(blasts.remove(i));
        }
        return out != null ? out : java.util.Collections.<Blast>emptyList();
    }

    /** The warning marks: a crimson disc filling to its edge, a ring that throbs faster as the rock comes down. */
    private void drawMarks(Canvas canvas, float offX, float offY, long now) {
        for (Blast b : blasts) {
            float k = Math.min(1f, (now - b.born) / (float) Math.max(1, b.at - b.born));
            float sx = Sprites.snap(b.x - offX), sy = Sprites.snap(b.y - offY);
            float ry = b.r * 0.55f; // laid on the floor, like the rest
            markPaint.setStyle(Paint.Style.FILL);
            markPaint.setColor(Color.argb((int) (40 + 70 * k), 224, 40, 30));
            float inner = b.r * (0.25f + 0.75f * k);
            canvas.drawOval(sx - inner, sy - inner * 0.55f, sx + inner, sy + inner * 0.55f, markPaint);
            float throb = 0.5f + 0.5f * (float) Math.sin(now / (110.0 - 80.0 * k));
            markPaint.setStyle(Paint.Style.STROKE);
            markPaint.setStrokeWidth(Sprites.PX * (k > 0.7f ? 2 : 1));
            markPaint.setColor(Color.argb((int) (150 + 105 * throb), 255, 70 + (int) (120 * k * throb), 40));
            canvas.drawOval(sx - b.r, sy - ry, sx + b.r, sy + ry, markPaint);
            // a cross hair at its heart
            float c = Sprites.PX * 3;
            canvas.drawLine(sx - c, sy, sx + c, sy, markPaint);
            canvas.drawLine(sx, sy - c * 0.55f, sx, sy + c * 0.55f, markPaint);
        }
    }

    /** The rocks coming down onto their marks, out of the top right, in their last METEOR_FALL ms. */
    private void drawMeteors(Canvas canvas, float offX, float offY, long now) {
        Sprites.Anim rock = Sprites.get("fx.meteor");
        for (Blast b : blasts) {
            long left = b.at - now;
            if (left > METEOR_FALL) continue;
            float f = 1f - left / (float) METEOR_FALL, d = 760f * (1f - f);
            float mx = b.x + 0.42f * d - offX, my = b.y - 0.91f * d - offY;
            if (rock != null) {
                Sprites.draw(canvas, rock, rock.at(now), mx, my, false, 1.3f, 0, 255);
            } else {
                markPaint.setStyle(Paint.Style.FILL);
                markPaint.setColor(Color.rgb(255, 120, 40));
                canvas.drawCircle(mx, my, 18f, markPaint);
            }
        }
    }

    /** Plays a baked effect once with its pivot (its ground point) at x, y; mirrored when flip. */
    public synchronized void play(String name, float x, float y, boolean flip) {
        play(name, x, y, flip, 0);
    }

    /** Plays a baked effect with its pivot at x, y: a looping one for holdMs (then it fades), any other once. */
    public synchronized void play(String name, float x, float y, boolean flip, long holdMs) {
        Sprites.Anim anim = Sprites.get(name);
        if (anim == null) return;
        if (sprites.size() >= MAX_SPRITES) sprites.remove(0);
        SpriteFx s = new SpriteFx();
        s.anim = anim;
        s.x = x;
        s.y = y;
        s.flip = flip;
        s.born = System.currentTimeMillis();
        s.hold = anim.loop ? Math.max(1, holdMs > 0 ? holdMs : anim.durationMs()) : 0;
        sprites.add(s);
    }

    /** Floor marks, drawn before coins and bodies so everything stands on them. */
    public synchronized void drawFloor(Canvas canvas, GameCamera camera) {
        long now = System.currentTimeMillis();
        float offX = camera.getOffsetX();
        float offY = camera.getOffsetY();
        int w = canvas.getWidth(), h = canvas.getHeight();
        for (Stain s : stains) {
            float sx = s.x - offX, sy = s.y - offY;
            if (sx < -s.rx || sy < -s.rx || sx > w + s.rx || sy > h + s.rx) continue;
            int alpha = Color.alpha(s.color);
            if (s.life > 0) {
                // Bodies hold for most of their life, then fade
                float age = (float) (now - s.born) / s.life;
                alpha = (int) (alpha * Math.min(1f, (1f - age) / 0.35f));
            }
            stainPaint.setColor(s.color);
            stainPaint.setAlpha(alpha);
            canvas.save();
            canvas.rotate(s.angle, sx, sy);
            canvas.drawOval(sx - s.rx, sy - s.ry, sx + s.rx, sy + s.ry, stainPaint);
            canvas.restore();
        }
        if (!blasts.isEmpty()) drawMarks(canvas, offX, offY, now);
    }

    /**
     * Draws all live effects.
     * @param canvas the canvas to draw on
     * @param camera camera used to convert world positions to screen positions
     */
    public synchronized void draw(Canvas canvas, GameCamera camera) {
        long now = System.currentTimeMillis();
        float offX = camera.getOffsetX();
        float offY = camera.getOffsetY();
        if (!blasts.isEmpty()) drawMeteors(canvas, offX, offY, now);

        // Slashes widen slightly and thin out as they fade
        for (Slash s : slashes) {
            float progress = Math.min(1f, (float) (now - s.born) / SLASH_LIFE);
            float r = s.radius * (0.85f + 0.15f * progress);
            float cx = s.x - offX, cy = s.y - offY;
            slashPaint.setColor(s.color);
            slashPaint.setAlpha((int) (230 * (1f - progress)));
            slashPaint.setStrokeWidth(14f * (1f - progress) + 2f);
            if (s.arc >= 180f) {
                canvas.drawCircle(cx, cy, r, slashPaint);
            } else {
                slashRect.set(cx - r, cy - r, cx + r, cy + r);
                canvas.drawArc(slashRect, s.angle - s.arc, s.arc * 2f, false, slashPaint);
            }
        }

        for (Particle p : particles) {
            float progress = Math.min(1f, (float) (now - p.born) / p.life);
            if (p.pixel) {
                // winks white now and then, and flickers before it goes
                if (progress > 0.7f && ((now / 40) & 1) == 0) continue;
                float px = Sprites.PX * p.size, sx = Sprites.snap(p.x - offX), sy = Sprites.snap(p.y - offY);
                pixelPaint.setColor((now / 70 + p.born) % 5 == 0 ? Color.WHITE : p.color);
                pixelPaint.setAlpha((int) (255 * (1f - progress * 0.5f)));
                canvas.drawRect(sx, sy, sx + px, sy + px, pixelPaint);
                continue;
            }
            particlePaint.setColor(p.color);
            particlePaint.setAlpha((int) (255 * (1f - progress)));
            canvas.drawCircle(p.x - offX, p.y - offY, p.size * (1f - progress * 0.5f), particlePaint);
        }

        for (SpriteFx s : sprites) {
            long age = now - s.born;
            if (s.over(now)) continue;
            Sprites.draw(canvas, s.anim, s.anim.at(age), s.x - offX, s.y - offY, s.flip, 0, s.alpha(now));
        }

        for (FloatingText t : texts) {
            float progress = Math.min(1f, (float) (now - t.born) / t.life);
            textPaint.setColor(t.color);
            textPaint.setAlpha((int) (255 * (1f - progress * progress)));
            textPaint.setTextSize(t.size);
            canvas.drawText(t.text, t.x - offX, t.y - offY - t.rise * progress, textPaint);
        }
    }

    /** Removes every effect, e.g. when a new floor starts. */
    public synchronized void clear() {
        blasts.clear();
        particles.clear();
        texts.clear();
        slashes.clear();
        stains.clear();
        sprites.clear();
        ghosts.clear();
        streaks.clear();
    }
}
