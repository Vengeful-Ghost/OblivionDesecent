package com.example.obliviondescent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
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
    private Paint ringPaint;    // iron ring round the stone
    private Paint rustPaint;    // rusted inner ring
    private final Matrix hatMatrix = new Matrix();
    private Wear.Stone wear;

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
        float dp = getResources().getDisplayMetrics().density;
        basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setStyle(Paint.Style.FILL);

        hatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hatPaint.setStyle(Paint.Style.FILL);

        // Torchlit aim line
        arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setColor(Color.argb(200, 217, 163, 90));
        arrowPaint.setStrokeWidth(3f * dp);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStyle(Paint.Style.STROKE);

        // Bone-white label in the game's display font
        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.argb(220, 217, 201, 166));
        labelPaint.setTextSize(24f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(getResources().getFont(R.font.pixel));
        labelPaint.setFakeBoldText(true);
        labelPaint.setShadowLayer(3f * dp, 0, dp, Color.BLACK);

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f * dp);
        ringPaint.setColor(Color.argb(210, 90, 82, 68));

        rustPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rustPaint.setStyle(Paint.Style.STROKE);
        rustPaint.setStrokeWidth(2f * dp);
        rustPaint.setColor(Color.argb(200, 107, 58, 32));

        wear = new Wear.Stone(getId(), dp, false, true);
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
        // Blood-dark stone, lighter towards the top
        basePaint.setShader(new RadialGradient(centerX, centerY - baseRadius * 0.3f, baseRadius * 1.3f,
                Color.rgb(84, 34, 24), Color.rgb(28, 14, 11), Shader.TileMode.CLAMP));
        // Rust-red knob, lit from the top left
        hatPaint.setShader(new RadialGradient(0, 0, hatRadius * 1.4f,
                Color.rgb(176, 72, 48), Color.rgb(58, 22, 16), Shader.TileMode.CLAMP));
        wear.setBounds((int) (centerX - baseRadius), (int) (centerY - baseRadius),
                (int) (centerX + baseRadius), (int) (centerY + baseRadius));
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
        // Stone base in iron and rust, brighter while held
        basePaint.setAlpha(touched ? 215 : 165);
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint);
        canvas.drawCircle(centerX, centerY, baseRadius - ringPaint.getStrokeWidth() / 2f, ringPaint);
        canvas.drawCircle(centerX, centerY, baseRadius - ringPaint.getStrokeWidth() * 1.5f, rustPaint);
        wear.draw(canvas);

        if (!touched || !aimEnabled) {
            canvas.drawText("ATK", centerX, centerY + labelPaint.getTextSize() * 0.35f, labelPaint);
        } else {
            // Direction line only when aiming manually
            if (isAiming()) canvas.drawLine(centerX, centerY, touchX, touchY, arrowPaint);
            hatMatrix.setTranslate(touchX - hatRadius * 0.3f, touchY - hatRadius * 0.35f);
            hatPaint.getShader().setLocalMatrix(hatMatrix);
            canvas.drawCircle(touchX, touchY, hatRadius, hatPaint);
            canvas.drawCircle(touchX, touchY, hatRadius, ringPaint);
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