package com.example.obliviondescent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * ג'ויסטיק כוון ירייה שמחליף את כפתור ההתקפה כאשר נשק קשתות מצויד.
 * השחקן גורר לכיוון — קו כיוון מראה לאן הירייה תלך.
 * שחרור האצבע יורה את הקליע (ACTION_UP בלבד), מה שמרגיש טבעי לנשק מרחוק.
 */
public class AimJoystickView extends View {

    /* ============================================================
       CALLBACK — GameActivity implements this to fire the projectile
       ============================================================ */
    public interface OnFireListener {
        void onFire(float dx, float dy);
    }

    private OnFireListener fireListener;

    /**
     * מגדירה מאזין שיוזעק בעת שחרור האצבע לירייה.
     * @param listener מאזין הירייה
     */
    public void setOnFireListener(OnFireListener listener) {
        this.fireListener = listener;
    }

    /* ============================================================
       GEOMETRY
       ============================================================ */
    private float centerX, centerY;
    private float baseRadius  = 96f;  // Slightly smaller than movement joystick
    private float hatRadius   = 40f;
    private float touchX, touchY;
    private boolean touched = false;

    /* ============================================================
       DIRECTION
       ============================================================ */
    private float aimDX = 0f;
    private float aimDY = 0f;

    /* ============================================================
       PAINTS
       ============================================================ */
    private Paint basePaint;
    private Paint hatPaint;
    private Paint arrowPaint;   // Direction line
    private Paint labelPaint;   // "AIM" label in center

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
    }

    /* ============================================================
       DRAW
       ============================================================ */
    @Override
    protected void onDraw(Canvas canvas) {
        // Base ring
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint);

        if (!touched) {
            // Idle state — show "AIM" label
            canvas.drawText("AIM", centerX, centerY + 9f, labelPaint);
        } else {
            // Dragging — show direction line from center to hat
            canvas.drawLine(centerX, centerY, touchX, touchY, arrowPaint);

            // Hat (the draggable dot)
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

                // Store normalized direction — used on release to fire
                if (dist > 0) {
                    aimDX = (touchX - centerX) / baseRadius;
                    aimDY = (touchY - centerY) / baseRadius;
                }

                invalidate();
                break;

            case MotionEvent.ACTION_UP:
                // Fire only if the player actually dragged meaningfully
                if (touched && (Math.abs(aimDX) > 0.1f || Math.abs(aimDY) > 0.1f)) {
                    if (fireListener != null) {
                        fireListener.onFire(aimDX, aimDY);
                    }
                }

                // Reset
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