package com.example.obliviondescent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class DashCooldownView extends View {

    private Paint arcPaint;
    private RectF arcRect;
    private float progress = 0f; // 0 = full cooldown, 1 = ready

    public DashCooldownView(Context context, AttributeSet attrs) {
        super(context, attrs);
        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(5f);
        arcPaint.setColor(Color.WHITE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float inset = arcPaint.getStrokeWidth() / 2f + 2f;
        arcRect = new RectF(inset, inset, w - inset, h - inset);
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (arcRect == null) return;
        if (progress >= 1f) return; // fully ready — don't draw anything

        // Gray background arc
        arcPaint.setColor(Color.argb(80, 255, 255, 255));
        canvas.drawArc(arcRect, -90, 360, false, arcPaint);

        // White foreground arc — clockwise fill as cooldown recovers
        arcPaint.setColor(Color.WHITE);
        float sweep = 360f * progress;
        canvas.drawArc(arcRect, -90, sweep, false, arcPaint);
    }
}