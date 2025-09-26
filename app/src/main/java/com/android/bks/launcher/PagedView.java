package com.android.bks.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.OverScroller;

import androidx.annotation.Nullable;

/**
 * Simplified PagedView: handles horizontal scrolling between child pages.
 * Each child is treated as a "page".
 */
public class PagedView extends ViewGroup {

    private OverScroller scroller;
    private VelocityTracker velocityTracker;
    private int touchSlop, minimumVelocity, maximumVelocity;
    private float lastX;
    private int currentPage = 0;

    public PagedView(Context context) {
        this(context, null);
    }

    public PagedView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        scroller = new OverScroller(context);
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minimumVelocity = vc.getScaledMinimumFlingVelocity();
        maximumVelocity = vc.getScaledMaximumFlingVelocity();
    }

    public int getPageCount() {
        return getChildCount();
    }

    public View getPageAt(int index) {
        return getChildAt(index);
    }

    public void snapToPage(int whichPage) {
        whichPage = Math.max(0, Math.min(whichPage, getChildCount() - 1));
        int dx = whichPage * getWidth() - getScrollX();
        scroller.startScroll(getScrollX(), 0, dx, 0, 400);
        invalidate();
        currentPage = whichPage;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), scroller.getCurrY());
            postInvalidate();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childLeft = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                child.layout(childLeft, 0, childLeft + getWidth(), getHeight());
                childLeft += getWidth();
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Always intercept to handle horizontal swipes
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(ev);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!scroller.isFinished()) scroller.abortAnimation();
                lastX = ev.getX();
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = lastX - ev.getX();
                lastX = ev.getX();
                scrollBy((int) dx, 0);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
                float vx = velocityTracker.getXVelocity();

                int targetPage = currentPage;
                if (Math.abs(vx) > minimumVelocity) {
                    if (vx < 0) targetPage++;
                    else targetPage--;
                } else {
                    targetPage = (getScrollX() + getWidth() / 2) / getWidth();
                }
                snapToPage(targetPage);

                velocityTracker.recycle();
                velocityTracker = null;
                break;
        }
        return true;
    }
}
