package com.example.obliviondescent;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.StateListAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.google.android.material.button.MaterialButton;

import java.util.Random;

/**
 * Ages the stone UI: every button and panel gets its own cracks, broken corners, bitten edges and moss,
 * laid over its background and seeded per view, so no two look alike.
 */
final class Wear {

    private Wear() {}

    /** Ages every stone button (MaterialButton, ImageButton) and "stone"-tagged panel under root. */
    static void apply(View root) {
        if (root == null) return;
        boolean stone = "stone".equals(root.getTag());
        boolean button = root instanceof MaterialButton
                && ((MaterialButton) root).getBackgroundTintList() == null;
        boolean icon = root instanceof ImageButton && stone;
        if (button || icon) wrap(root, false, true);
        else if (stone) wrap(root, true, false);
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) apply(g.getChildAt(i));
        }
    }

    /** Lays wear over a view's background; panels wear more, round buttons wear from the rim. */
    static void wrap(View v, boolean panel, boolean roundable) {
        wrap(v, panel, roundable, null);
    }

    /** As above; key names what a view shows when many share one layout (inventory cells, shop cards). */
    static void wrap(View v, boolean panel, boolean roundable, String key) {
        Drawable bg = v.getBackground();
        if (bg == null) return;
        if (bg instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) bg;
            int n = layers.getNumberOfLayers();
            if (n > 0 && layers.getDrawable(n - 1) instanceof Stone) return; // already worn
        }
        float dp = v.getResources().getDisplayMetrics().density;
        // Same view, same cracks every time it's shown: seeded by its id and place in the tree
        long seed = v.getId();
        for (View c = v; c.getParent() instanceof ViewGroup; c = (View) c.getParent())
            seed = seed * 31 + ((ViewGroup) c.getParent()).indexOfChild(c);
        if (key != null) seed = seed * 31 + key.hashCode();
        v.setBackground(new LayerDrawable(new Drawable[]{bg, new Stone(seed, dp, panel, roundable)}));
        // Buttons with translationZ already dip through their XML button_elevation_anim
        if (roundable && v.getTranslationZ() == 0) v.setStateListAnimator(press(v));
    }

    /** A pressed button sinks a little with a light tap, like the menu's button_elevation_anim. */
    private static StateListAnimator press(View v) {
        Animator down = scale(v, 0.95f, 90);
        down.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (v.getContext().getSharedPreferences("gameSettings", Context.MODE_PRIVATE)
                        .getBoolean("vibration_enabled", true))
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        });
        StateListAnimator states = new StateListAnimator();
        states.addState(new int[]{android.R.attr.state_pressed}, down);
        states.addState(new int[0], scale(v, 1f, 140));
        return states;
    }

    private static Animator scale(View v, float to, long ms) {
        return ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.SCALE_X, to),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, to)).setDuration(ms);
    }

    /** The wear itself, laid out from the seed whenever the size changes. */
    static final class Stone extends Drawable {
        private final long seed;
        private final float dp;
        private final boolean panel, roundable;
        private final Path gaps = new Path(), cracks = new Path(), edges = new Path(), moss = new Path();
        private final Paint gapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint crackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Stone(long seed, float dp, boolean panel, boolean roundable) {
            this.seed = seed;
            this.dp = dp;
            this.panel = panel;
            this.roundable = roundable;
            // Missing stone shows the dark gap behind it
            gapPaint.setColor(Color.rgb(7, 6, 5));
            crackPaint.setStyle(Paint.Style.STROKE);
            crackPaint.setStrokeWidth(1.2f * dp);
            crackPaint.setStrokeJoin(Paint.Join.ROUND);
            crackPaint.setStrokeCap(Paint.Cap.ROUND);
            crackPaint.setColor(Color.argb(225, 7, 6, 5));
            // Torchlight catching broken edges and the lip of each crack
            edgePaint.setStyle(Paint.Style.STROKE);
            edgePaint.setStrokeWidth(0.8f * dp);
            edgePaint.setStrokeJoin(Paint.Join.ROUND);
            edgePaint.setStrokeCap(Paint.Cap.ROUND);
            edgePaint.setColor(Color.argb(70, 216, 200, 160));
            mossPaint.setColor(Color.argb(120, 62, 74, 42));
        }

        @Override
        protected void onBoundsChange(Rect b) {
            pixels = null; // the pixel copy is redrawn at the new size
            gaps.rewind();
            cracks.rewind();
            edges.rewind();
            moss.rewind();
            float w = b.width(), h = b.height();
            if (w < 12 * dp || h < 12 * dp) return;
            Random r = new Random(seed);
            if (roundable && Math.abs(w - h) < Math.max(w, h) * 0.15f) {
                disc(b, r);
                return;
            }
            // Damage scales with the slab's height, so small plates get small damage
            float s = Math.min(1.4f, Math.min(w, h) / (44 * dp));

            int corners = panel ? 1 + r.nextInt(2) : r.nextInt(3);
            int first = r.nextInt(4);
            for (int i = 0; i < corners; i++) corner(b, (first + i * (1 + r.nextInt(2))) % 4, r, s);

            int bites = r.nextInt(panel ? 3 : 2);
            for (int i = 0; i < bites; i++) bite(b, r, s);

            int n = (panel ? 2 : 1) + r.nextInt(2);
            for (int i = 0; i < n; i++) {
                boolean top = r.nextBoolean();
                float x = b.left + w * (0.12f + r.nextFloat() * 0.76f);
                double a = (top ? Math.PI / 2 : -Math.PI / 2) + (r.nextFloat() - 0.5f) * 1.2f;
                crack(b, x, top ? b.top : b.bottom, a, r, s, 3 + r.nextInt(3), true);
            }
            // Tall panels also crack in from the sides
            if (panel && r.nextBoolean()) {
                boolean left = r.nextBoolean();
                float y = b.top + h * (0.2f + r.nextFloat() * 0.6f);
                double a = (left ? 0 : Math.PI) + (r.nextFloat() - 0.5f) * 1.0f;
                crack(b, left ? b.left : b.right, y, a, r, s, 3 + r.nextInt(3), true);
            }

            if (r.nextFloat() < (panel ? 0.7f : 0.4f)) {
                float mw = w * (0.15f + r.nextFloat() * 0.2f);
                float x0 = b.left + (w - mw) * r.nextFloat();
                for (float x = x0; x < x0 + mw; x += 3 * dp * s) {
                    float rad = (1.5f + r.nextFloat() * 2.5f) * dp * s;
                    moss.addCircle(x, b.bottom - (2 + r.nextFloat() * 2) * dp, rad, Path.Direction.CW);
                }
            }
        }

        /** Breaks a jagged piece off corner c (0 top-left, then clockwise). */
        private void corner(Rect b, int c, Random r, float s) {
            boolean right = c == 1 || c == 2, bottom = c >= 2;
            float x0 = right ? b.right : b.left, y0 = bottom ? b.bottom : b.top;
            float sx = right ? -1 : 1, sy = bottom ? -1 : 1;
            float cw = Math.min(b.width() / 3f, (7 + r.nextFloat() * 9) * dp * s);
            float ch = Math.min(b.height() / 2.5f, (5 + r.nextFloat() * 7) * dp * s);
            float[] pts = {
                    cw, 0,
                    cw * (0.55f + r.nextFloat() * 0.2f), ch * (0.2f + r.nextFloat() * 0.25f),
                    cw * (0.25f + r.nextFloat() * 0.2f), ch * (0.5f + r.nextFloat() * 0.2f),
                    0, ch};
            gaps.moveTo(x0, y0);
            for (int i = 0; i < pts.length; i += 2) {
                float x = x0 + sx * pts[i], y = y0 + sy * pts[i + 1];
                gaps.lineTo(x, y);
                if (i == 0) edges.moveTo(x, y);
                else edges.lineTo(x, y);
            }
            gaps.close();
            // A crack often runs on from the break
            if (r.nextFloat() < 0.6f) {
                double a = Math.atan2(sy * ch, sx * cw) + (r.nextFloat() - 0.5f) * 0.8f;
                crack(b, x0 + sx * pts[2], y0 + sy * pts[3], a, r, s, 2 + r.nextInt(2), false);
            }
        }

        /** Bites a shallow jagged chunk out of the top or bottom edge. */
        private void bite(Rect b, Random r, float s) {
            boolean top = r.nextBoolean();
            float bw = Math.min(b.width() / 4f, (8 + r.nextFloat() * 12) * dp * s);
            float bd = (1.5f + r.nextFloat() * 2.5f) * dp * s;
            float x = b.left + (b.width() - bw) * (0.15f + r.nextFloat() * 0.7f);
            float y = top ? b.top : b.bottom, sy = top ? 1 : -1;
            gaps.moveTo(x, y);
            edges.moveTo(x, y);
            for (int i = 1; i < 4; i++) {
                float px = x + bw * i / 4f + (r.nextFloat() - 0.5f) * bw * 0.15f;
                float py = y + sy * bd * (0.5f + r.nextFloat() * 0.5f);
                gaps.lineTo(px, py);
                edges.lineTo(px, py);
            }
            gaps.lineTo(x + bw, y);
            edges.lineTo(x + bw, y);
            gaps.close();
        }

        /** A crack wandering from (x, y) in direction a, sometimes splitting once. */
        private void crack(Rect b, float x, float y, double a, Random r, float s, int segs, boolean branch) {
            cracks.moveTo(x, y);
            for (int i = 0; i < segs; i++) {
                a += (r.nextFloat() - 0.5f) * 1.1f;
                float len = (4 + r.nextFloat() * 6) * dp * s;
                x = clamp(x + (float) Math.cos(a) * len, b.left + dp, b.right - dp);
                y = clamp(y + (float) Math.sin(a) * len, b.top + dp, b.bottom - dp);
                cracks.lineTo(x, y);
                if (branch && i == segs / 2 && r.nextBoolean()) {
                    crack(b, x, y, a + (r.nextBoolean() ? 0.8 : -0.8), r, s * 0.7f, 2, false);
                    cracks.moveTo(x, y);
                }
            }
        }

        /** Round buttons wear from the rim: maybe a chip out of the ring, and a crack or two running in. */
        private void disc(Rect b, Random r) {
            float cx = b.exactCenterX(), cy = b.exactCenterY();
            float rad = Math.min(b.width(), b.height()) / 2f;
            float s = rad / (30 * dp);
            if (r.nextFloat() < 0.7f) {
                double a = r.nextFloat() * Math.PI * 2, half = 0.16 + r.nextFloat() * 0.14;
                float[] ring = {1.0f, 1.08f, 1.0f, 0.82f + r.nextFloat() * 0.06f, 0.78f + r.nextFloat() * 0.06f};
                double[] at = {a - half, a, a + half, a + half * 0.35, a - half * 0.4};
                for (int i = 0; i < ring.length; i++) {
                    float x = cx + (float) Math.cos(at[i]) * rad * ring[i];
                    float y = cy + (float) Math.sin(at[i]) * rad * ring[i];
                    if (i == 0) gaps.moveTo(x, y);
                    else gaps.lineTo(x, y);
                    if (i == 2) edges.moveTo(x, y);
                    else if (i > 2) edges.lineTo(x, y);
                }
                gaps.close();
                edges.lineTo(cx + (float) Math.cos(at[0]) * rad, cy + (float) Math.sin(at[0]) * rad);
            }
            int n = 1 + r.nextInt(2);
            for (int i = 0; i < n; i++) {
                double a = r.nextFloat() * Math.PI * 2;
                float x = cx + (float) Math.cos(a) * rad * 0.9f, y = cy + (float) Math.sin(a) * rad * 0.9f;
                crack(b, x, y, a + Math.PI + (r.nextFloat() - 0.5f) * 1.0f, r, s * 0.7f, 2 + r.nextInt(2), false);
            }
        }

        private static float clamp(float v, float lo, float hi) {
            return Math.max(lo, Math.min(hi, v));
        }

        // Pixel art like the frames they wear on (art/tools/ui-frames.mjs): drawn small, one pixel per U dp,
        // without smoothing, then scaled up pixel for pixel. Rebuilt when the view changes size.
        private static final float U = 2f;
        private android.graphics.Bitmap pixels;
        private final Paint blit = new Paint();
        private final android.graphics.RectF dst = new android.graphics.RectF();

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            float px = U * dp;
            if (pixels == null) {
                pixels = android.graphics.Bitmap.createBitmap(Math.max(1, (int) Math.ceil(b.width() / px)),
                        Math.max(1, (int) Math.ceil(b.height() / px)), android.graphics.Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(pixels);
                c.setDrawFilter(new android.graphics.PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG, 0));
                c.scale(1f / px, 1f / px);
                c.translate(-b.left, -b.top);
                c.drawPath(moss, mossPaint);
                c.drawPath(gaps, gapPaint);
                c.drawPath(edges, edgePaint);
                // Lit lip one pixel under each crack, then the crack itself
                c.save();
                c.translate(px, px);
                c.drawPath(cracks, edgePaint);
                c.restore();
                c.drawPath(cracks, crackPaint);
            }
            blit.setFilterBitmap(false);
            dst.set(b.left, b.top, b.left + pixels.getWidth() * px, b.top + pixels.getHeight() * px);
            canvas.save();
            canvas.clipRect(b);
            canvas.drawBitmap(pixels, null, dst, blit);
            canvas.restore();
        }

        // Views fade through their own alpha, so the wear needs none of its own
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
