package com.example.obliviondescent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * JoystickView
 *
 * On-screen analog joystick used for player movement input.
 * Produces a normalized direction vector (dx, dy) in range [-1, 1].
 */
public class JoystickView extends View {

    /* ============================================================
       Geometry
       ============================================================ */

    private float centerX, centerY;
    private float baseRadius = 150;
    private float hatRadius = 70;

    /* ============================================================
       Touch state
       ============================================================ */

    private float touchX, touchY;
    private boolean touched = false;

    /* ============================================================
       Rendering
       ============================================================ */

    private Paint basePaint;
    private Paint hatPaint;

    /* ============================================================
       Direction output
       ============================================================ */

    // Normalized direction vector (-1 to 1)
    private float dx = 0f, dy = 0f;

    /* ============================================================
       Constructors
       ============================================================ */

    public JoystickView(Context context) {
        super(context);
        init();
    }

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public JoystickView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /* ============================================================
       Initialization
       ============================================================ */

    private void init() {
        basePaint = new Paint();
        basePaint.setColor(Color.GRAY);
        basePaint.setAlpha(120);

        hatPaint = new Paint();
        hatPaint.setColor(Color.WHITE);
        hatPaint.setAlpha(180);

        // Safe defaults
        centerX = 0;
        centerY = 0;
        touchX = 0;
        touchY = 0;
    }

    /* ============================================================
       View sizing
       ============================================================ */

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        centerX = w / 2f;
        centerY = h / 2f;
        touchX = centerX;
        touchY = centerY;
    }

    /* ============================================================
       Rendering
       ============================================================ */

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw base
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint);

        // Draw hat
        canvas.drawCircle(touchX, touchY, hatRadius, hatPaint);
    }

    /* ============================================================
       Touch handling
       ============================================================ */

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                touched = true;

                float dxRaw = event.getX() - centerX;
                float dyRaw = event.getY() - centerY;
                float distance = (float) Math.sqrt(dxRaw * dxRaw + dyRaw * dyRaw);

                if (distance < baseRadius) {
                    // Inside the joystick base
                    touchX = event.getX();
                    touchY = event.getY();
                } else {
                    // Clamp to the circle edge
                    float ratio = baseRadius / distance;
                    touchX = centerX + dxRaw * ratio;
                    touchY = centerY + dyRaw * ratio;
                }

                // Normalize direction vector (-1 to 1)
                dx = (touchX - centerX) / baseRadius;
                dy = (touchY - centerY) / baseRadius;

                invalidate();
                break;

            case MotionEvent.ACTION_UP:
                touched = false;

                // Reset to center
                touchX = centerX;
                touchY = centerY;
                dx = 0;
                dy = 0;

                invalidate();
                break;
        }

        return true;
    }

    /* ============================================================
       Public getters
       ============================================================ */

    public float getDX() { return dx; }
    public float getDY() { return dy; }
    public boolean isTouched() { return touched; }
}
