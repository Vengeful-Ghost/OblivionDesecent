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

    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatingText> texts = new ArrayList<>();
    private final List<Slash> slashes = new ArrayList<>();
    private final Random rng = new Random();

    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF slashRect = new RectF();

    public GameEffects() {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        textPaint.setShadowLayer(4f, 0f, 2f, Color.BLACK);
        slashPaint.setStyle(Paint.Style.STROKE);
        slashPaint.setStrokeCap(Paint.Cap.ROUND);
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

    /** Large text that floats up slowly, e.g. "LEVEL 3" or "ENRAGED". */
    public synchronized void banner(float x, float y, String text, int color) {
        addText(x, y, text, color, 46f, 50f, 1400);
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
    }
}
