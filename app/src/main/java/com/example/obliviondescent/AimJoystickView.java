package com.example.obliviondescent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Attack stick. Holding it attacks continuously at the weapon's pace; GameActivity polls it every tick.
 * A plain hold auto-aims at the nearest enemy; dragging aims manually in the dragged direction,
 * measured from where the thumb landed, and that direction holds until release.
 */
public class AimJoystickView extends View {

    // Drag needed before a manual aim counts, as a share of the stick radius
    private static final float AIM_DEADZONE = 0.22f;

    /* ============================================================
       GEOMETRY
       ============================================================ */
    private float centerX, centerY;
    private float baseRadius  = 96f;
    private float hatRadius   = 40f;
    private float touchX, touchY;
    private float anchorX, anchorY;

    /* ============================================================
       STATE — written on the UI thread, read by the logic thread
       ============================================================ */
    private volatile boolean touched = false;
    private volatile boolean aiming = false;
    private volatile boolean tapPending = false;
    private volatile long downAt = 0;
    private volatile float aimDX = 0f;
    private volatile float aimDY = 0f;
    private volatile boolean aimEnabled = true; // false: a plain button, attacks go where the player faces

    /* ============================================================
       PAINTS
       ============================================================ */
    private Paint basePaint;
    private Paint hatPaint;
    private Paint arrowPaint;   // Direction line
    private Paint labelPaint;   // "ATK" label in center

    /* ============================================================
       CONSTRUCTORS
       ============================================================ */
    public AimJoystickView(Context context) {
        super(context);
        init();
    }

    public AimJoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AimJoystickView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /* ============================================================
       INIT
       ============================================================ */
    private void init() {
        basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setColor(Color.rgb(150, 50, 50));
        basePaint.setAlpha(160);
        basePaint.setStyle(Paint.Style.FILL);

        hatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hatPaint.setColor(Color.rgb(255, 100, 100));
        hatPaint.setAlpha(220);
        hatPaint.setStyle(Paint.Style.FILL);

        arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setColor(Color.WHITE);
        arrowPaint.setAlpha(180);
        arrowPaint.setStrokeWidth(4f);
        arrowPaint.setStyle(Paint.Style.STROKE);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setAlpha(180);
        labelPaint.setTextSize(24f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
    }

    /* ============================================================
       SIZING
       ============================================================ */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        centerX = w / 2f;
        centerY = h / 2f;
        touchX  = centerX;
        touchY  = centerY;
        baseRadius = Math.min(w, h) * 0.45f;
        hatRadius  = baseRadius * 0.42f;
        labelPaint.setTextSize(baseRadius * 0.3f);
    }

    /** True while a finger is on the stick. */
    public boolean isHeld() { return touched; }

    /** True once the finger has been dragged far enough to aim manually; stays true until release. */
    public boolean isAiming() { return touched && aiming; }

    /** How long the current touch has lasted, 0 when not touched. */
    public long heldMs() { return touched ? SystemClock.uptimeMillis() - downAt : 0; }

    /** True once per touch that hasn't attacked yet, so a quick tap still swings after release. */
    public boolean takeTap() {
        boolean tap = tapPending;
        tapPending = false;
        return tap;
    }

    /** Unit aim direction of the current or last manual drag. */
    public float getAimX() { return aimDX; }
    public float getAimY() { return aimDY; }

    /** Switches between an aiming stick and a plain attack button. */
    public void setAimEnabled(boolean enabled) {
        aimEnabled = enabled;
        invalidate();
    }

    /* ============================================================
       DRAW
       ============================================================ */
    @Override
    protected void onDraw(Canvas canvas) {
        // Base ring, brighter while held
        basePaint.setAlpha(touched ? 210 : 160);
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint);

        if (!touched || !aimEnabled) {
            canvas.drawText("ATK", centerX, centerY + labelPaint.getTextSize() * 0.35f, labelPaint);
        } else {
            // Direction line only when aiming manually
            if (isAiming()) canvas.drawLine(centerX, centerY, touchX, touchY, arrowPaint);
            canvas.drawCircle(touchX, touchY, hatRadius, hatPaint);
        }
    }

    /* ============================================================
       TOUCH
       ============================================================ */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:
                touched = true;
                aiming = false;
                tapPending = true;
                downAt = SystemClock.uptimeMillis();
                // Aim is measured from where the thumb lands, not from the stick's centre
                anchorX = event.getX();
                anchorY = event.getY();
                touchX = centerX;
                touchY = centerY;
                invalidate();
                break;

            case MotionEvent.ACTION_MOVE:
                if (!aimEnabled) break;
                float dx = event.getX() - anchorX;
                float dy = event.getY() - anchorY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > baseRadius * AIM_DEADZONE) {
                    aimDX = dx / dist;
                    aimDY = dy / dist;
                    aiming = true;
                }
                // The hat shows the drag, clamped to the base
                float reach = Math.min(dist, baseRadius);
                touchX = dist > 0 ? centerX + dx / dist * reach : centerX;
                touchY = dist > 0 ? centerY + dy / dist * reach : centerY;
                invalidate();
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) tapPending = false;
                touched = false;
                aiming  = false;
                touchX  = centerX;
                touchY  = centerY;
                invalidate();
                break;
        }

        return true;
    }
}