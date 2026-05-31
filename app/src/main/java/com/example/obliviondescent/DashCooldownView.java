package com.example.obliviondescent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * View that renders the dash cooldown state as a circular arc.
 * progress=0 means full cooldown; progress=1 means fully ready.
 * When progress=1 (ready), nothing is drawn.
 */
public class DashCooldownView extends View {

    private Paint arcPaint;
    private Paint dimPaint;
    private RectF arcRect;
    /** 0 = full cooldown, 1 = ready to dash. */
    private float progress = 0f;

    public DashCooldownView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float dp = context.getResources().getDisplayMetrics().density;

        // Arc paint — thick gold stroke
        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(6f * dp);
        arcPaint.setColor(Color.argb(255, 255, 200, 50));
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        // Dim paint — dark fill to grey out the button while on cooldown
        dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dimPaint.setStyle(Paint.Style.FILL);
        dimPaint.setColor(Color.argb(160, 0, 0, 0));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float inset = arcPaint.getStrokeWidth() / 2f + 2f;
        arcRect = new RectF(inset, inset, w - inset, h - inset);
    }

    /**
     * Updates the cooldown progress and triggers a redraw.
     * @param progress value between 0 (full cooldown) and 1 (ready)
     */
    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (arcRect == null) return;
        if (progress >= 1f) return; // fully ready — draw nothing

        // 1. Dark circle overlay to clearly show the button is on cooldown
        float cx = arcRect.centerX();
        float cy = arcRect.centerY();
        float r  = arcRect.width() / 2f + arcPaint.getStrokeWidth() / 2f;
        canvas.drawCircle(cx, cy, r, dimPaint);

        // 2. Bright gold arc growing clockwise as cooldown recovers
        float sweep = 360f * progress;
        canvas.drawArc(arcRect, -90, sweep, false, arcPaint);
    }
}