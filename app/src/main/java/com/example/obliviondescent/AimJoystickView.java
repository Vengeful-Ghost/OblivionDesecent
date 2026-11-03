package com.example.obliviondescent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Attack stick. Holding it attacks continuously at the weapon's pace; GameActivity polls it every tick.
 * A plain hold auto-aims at the nearest enemy; dragging aims manually in the dragged direction.
 */
public class AimJoystickView extends View {

    /* ============================================================
       GEOMETRY
       ============================================================ */
    private float centerX, centerY;
    private float baseRadius  = 96f;
    private float hatRadius   = 40f;
    private float touchX, touchY;

    /* ============================================================
       STATE — written on the UI thread, read by the logic thread
       ============================================================ */
    private volatile boolean touched = false;
    private volatile float aimDX = 0f;
    private volatile float aimDY = 0f;

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

    /** True when the finger is dragged far enough from the centre to aim manually. */
    public boolean isAiming() { return touched && aimDX * aimDX + aimDY * aimDY > 0.09f; }

    public float getAimX() { return aimDX; }
    public float getAimY() { return aimDY; }

    /* ============================================================
       DRAW
       ============================================================ */
    @Override
    protected void onDraw(Canvas canvas) {
        // Base ring, brighter while held
        basePaint.setAlpha(touched ? 210 : 160);
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint);

        if (!touched) {
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
        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                touched = true;

                float rawDX = event.getX() - centerX;
                float rawDY = event.getY() - centerY;
                float dist  = (float) Math.sqrt(rawDX * rawDX + rawDY * rawDY);

                if (dist < baseRadius) {
                    touchX = event.getX();
                    touchY = event.getY();
                } else {
                    // Clamp to edge of base
                    float ratio = baseRadius / dist;
                    touchX = centerX + rawDX * ratio;
                    touchY = centerY + rawDY * ratio;
                }

                // Normalized drag, -1..1 on each axis
                if (dist > 0) {
                    aimDX = (touchX - centerX) / baseRadius;
                    aimDY = (touchY - centerY) / baseRadius;
                }

                invalidate();
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touched = false;
                touchX  = centerX;
                touchY  = centerY;
                aimDX   = 0f;
                aimDY   = 0f;
                invalidate();
                break;
        }

        return true;
    }
}