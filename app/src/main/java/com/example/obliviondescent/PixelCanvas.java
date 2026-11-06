package com.example.obliviondescent;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * What the world is drawn into: a small bitmap at one pixel per Sprites.PX world units, with smoothing turned
 * off, so shapes and sprites alike come out as crisp pixel art once it is scaled up to the screen. Callers
 * keep drawing in world (screen) coordinates; begin() sets up the scale.
 * Text is the exception: it is held back and drawn at full resolution on top, so names and numbers stay sharp.
 */
final class PixelCanvas extends Canvas {

    private final Bitmap bitmap;
    private final RectF screen = new RectF();
    private final PaintFlagsDrawFilter noSmoothing =
            new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG, 0);

    private static final class Text {
        String text;
        float x, y;
        final Paint paint = new Paint();
        final Matrix matrix = new Matrix();
    }

    private final List<Text> texts = new ArrayList<>();
    private int textCount = 0;
    private final Matrix scratch = new Matrix();

    PixelCanvas(int screenWidth, int screenHeight) {
        this(Bitmap.createBitmap(Math.max(1, (int) Math.ceil(screenWidth / Sprites.PX)),
                Math.max(1, (int) Math.ceil(screenHeight / Sprites.PX)), Bitmap.Config.ARGB_8888));
    }

    private PixelCanvas(Bitmap bitmap) {
        super(bitmap);
        this.bitmap = bitmap;
        screen.set(0, 0, bitmap.getWidth() * Sprites.PX, bitmap.getHeight() * Sprites.PX);
    }

    /** Clears the buffer for a new frame and scales it to world units; returns the count to restore to. */
    int begin() {
        textCount = 0;
        setDrawFilter(null);
        bitmap.eraseColor(Color.BLACK);
        int count = save();
        scale(1f / Sprites.PX, 1f / Sprites.PX);
        setDrawFilter(noSmoothing);
        return count;
    }

    /** Scales the frame up onto the screen, pixel for pixel, then draws the held-back text over it. */
    void present(Canvas target, Paint blit) {
        target.drawBitmap(bitmap, null, screen, blit);
        for (int i = 0; i < textCount; i++) {
            Text t = texts.get(i);
            target.save();
            scratch.set(t.matrix);
            scratch.postScale(Sprites.PX, Sprites.PX);
            target.concat(scratch);
            target.drawText(t.text, t.x, t.y, t.paint);
            target.restore();
        }
    }

    // Callers cull against the canvas size in world units, as they did on the screen canvas
    @Override
    public int getWidth() {
        return (int) screen.width();
    }

    @Override
    public int getHeight() {
        return (int) screen.height();
    }

    @Override
    @SuppressWarnings("deprecation") // getMatrix still reports a bitmap canvas's matrix
    public void drawText(String text, float x, float y, Paint paint) {
        if (textCount == texts.size()) texts.add(new Text());
        Text t = texts.get(textCount++);
        t.text = text;
        t.x = x;
        t.y = y;
        t.paint.set(paint);
        getMatrix(t.matrix);
    }
}
