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
            particlePaint.setColor(p.color);
            particlePaint.setAlpha((int) (255 * (1f - progress)));
            canvas.drawCircle(p.x - offX, p.y - offY, p.size * (1f - progress * 0.5f), particlePaint);
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
        particles.clear();
        texts.clear();
        slashes.clear();
        stains.clear();
    }
}
