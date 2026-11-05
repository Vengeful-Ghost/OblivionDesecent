package com.example.obliviondescent;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;

/**
 * Dialog wrapper that shrinks its card to fit the screen instead of scrolling. A card a little
 * taller than a landscape phone (weapon details, game over, shop) is scaled down to fit whole;
 * only content far too tall to fit, like a long run history, falls back to normal scrolling.
 */
public class FitScrollView extends ScrollView {

    /** Below this the text gets too small to read, so scroll instead. */
    private static final float MIN_SCALE = 0.75f;

    private boolean fitted;

    public FitScrollView(Context context) { super(context); }
    public FitScrollView(Context context, AttributeSet attrs) { super(context, attrs); }
    public FitScrollView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (getChildCount() == 0) return;
        View child = getChildAt(0);
        int available = getHeight() - getPaddingTop() - getPaddingBottom();
        int content = child.getHeight();
        float scale = 1f;
        if (available > 0 && content > available) {
            float fit = (float) available / content;
            if (fit >= MIN_SCALE) scale = fit;
        }
        fitted = scale < 1f;
        // Scale from the top centre so the shrunk card fills the viewport exactly
        child.setPivotX(child.getWidth() / 2f);
        child.setPivotY(0f);
        child.setScaleX(scale);
        child.setScaleY(scale);
        if (fitted && getScrollY() != 0) scrollTo(0, 0);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return !fitted && super.onInterceptTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return !fitted && super.onTouchEvent(ev);
    }

    @Override
    public boolean canScrollVertically(int direction) {
        return !fitted && super.canScrollVertically(direction);
    }
}
