package com.example.obliviondescent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.Random;

public class ShopParticleView extends View {

    private static final int COUNT = 30;
    private float[] x, y, vx, vy, r;
    private int[] color;
    private Paint paint;
    private boolean running = false;

    public ShopParticleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        x = new float[COUNT]; y = new float[COUNT];
        vx = new float[COUNT]; vy = new float[COUNT];
        r = new float[COUNT]; color = new int[COUNT];

        int[] palette = {
                Color.argb(90, 255, 215, 0),
                Color.argb(70, 160, 80, 220),
                Color.argb(50, 255, 255, 255),
                Color.argb(60, 80, 120, 255),
        };

        Random rnd = new Random();
        for (int i = 0; i < COUNT; i++) {
            x[i] = rnd.nextFloat() * 1000f;
            y[i] = rnd.nextFloat() * 600f;
            double angle = rnd.nextDouble() * Math.PI * 2;
            float speed = 0.5f + rnd.nextFloat() * 1.5f;
            vx[i] = (float)(Math.cos(angle) * speed);
            vy[i] = (float)(Math.sin(angle) * speed);
            r[i] = 2f + rnd.nextFloat() * 5f;
            color[i] = palette[rnd.nextInt(palette.length)];
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            Random rnd = new Random();
            for (int i = 0; i < COUNT; i++) {
                x[i] = rnd.nextFloat() * w;
                y[i] = rnd.nextFloat() * h;
            }
            running = true;
            postInvalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        if (!running) return;
        int w = getWidth(), h = getHeight();
        for (int i = 0; i < COUNT; i++) {
            x[i] += vx[i];
            y[i] += vy[i];
            if (x[i] < 0 || x[i] > w) vx[i] = -vx[i];
            if (y[i] < 0 || y[i] > h) vy[i] = -vy[i];
            paint.setColor(color[i]);
            canvas.drawCircle(x[i], y[i], r[i], paint);
        }
        postInvalidateDelayed(16);
    }

    public void stopAnimation() { running = false; }
}