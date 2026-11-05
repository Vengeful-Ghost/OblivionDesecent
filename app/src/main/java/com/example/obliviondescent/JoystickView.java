package com.example.obliviondescent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * On-screen analogue joystick for player movement input.
 * Produces a normalised direction vector (dx, dy) in the range [-1, 1].
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
    private Paint ringPaint;   // iron ring round the stone
    private Paint rimPaint;    // dark edge round the knob
    private final Matrix hatMatrix = new Matrix();
    private Wear.Stone wear;

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
        float dp = getResources().getDisplayMetrics().density;
        basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Worn brass knob, lit from the top left (shader sized in onSizeChanged)
        hatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f * dp);
        ringPaint.setColor(Color.argb(200, 90, 82, 68));

        rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(2f * dp);
        rimPaint.setColor(Color.argb(220, 30, 27, 23));

        wear = new Wear.Stone(getId(), dp, false, true);

        // Safe defaults
        centerX = 0;
        centerY = 0;
        touchX = 0;
        touchY = 0;
    }

    /* ============================================================
       View sizing
       ============================================================ */

    /**
     * Sets the joystick centre position when the view size changes.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        centerX = w / 2f;
        centerY = h / 2f;
        touchX = centerX;
        touchY = centerY;
        // Sized to the view so it looks the same on every screen density
        baseRadius = Math.min(w, h) * 0.32f;
        hatRadius = baseRadius * 0.47f;
        hatPaint.setShader(new RadialGradient(0, 0, hatRadius * 1.4f,
                Color.argb(235, 187, 165, 122), Color.argb(235, 78, 67, 48), Shader.TileMode.CLAMP));
        // Dark stone, lighter towards the top as if lit by a torch
        basePaint.setShader(new RadialGradient(centerX, centerY - baseRadius * 0.3f, baseRadius * 1.3f,
                Color.argb(150, 58, 53, 46), Color.argb(150, 23, 21, 18), Shader.TileMode.CLAMP));
        wear.setBounds((int) (centerX - baseRadius), (int) (centerY - baseRadius),
                (int) (centerX + baseRadius), (int) (centerY + baseRadius));
    }

    /* ============================================================
       Rendering
       ============================================================ */

    /**
     * Draws the joystick base and the movable hat.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        // Stone base in an iron ring, with its own cracks
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint);
        canvas.drawCircle(centerX, centerY, baseRadius - ringPaint.getStrokeWidth() / 2f, ringPaint);
        wear.draw(canvas);

        // Brass knob
        hatMatrix.setTranslate(touchX - hatRadius * 0.3f, touchY - hatRadius * 0.35f);
        hatPaint.getShader().setLocalMatrix(hatMatrix);
        canvas.drawCircle(touchX, touchY, hatRadius, hatPaint);
        canvas.drawCircle(touchX, touchY, hatRadius, rimPaint);
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

    /** @return normalised horizontal direction (-1 to 1) */
    public float getDX() { return dx; }
    /** @return normalised vertical direction (-1 to 1) */
    public float getDY() { return dy; }
    /** @return true if a finger is currently touching the joystick */
    public boolean isTouched() { return touched; }
}
