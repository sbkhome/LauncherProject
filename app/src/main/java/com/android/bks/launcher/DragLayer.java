package com.android.bks.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

/**
 * DragLayer - root overlay that intercepts touches; handles swipe-up to reveal All Apps
 */
public class DragLayer extends FrameLayout {

    public interface OnSwipeUpListener { void onSwipeUp(); }

    private OnSwipeUpListener swipeUpListener;
    private float startY;

    public DragLayer(Context ctx) { super(ctx); init(); }
    public DragLayer(Context ctx, @Nullable AttributeSet attrs) { super(ctx, attrs); init(); }

    private void init() {
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setOnSwipeUpListener(OnSwipeUpListener l) { this.swipeUpListener = l; }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
                float dy = startY - event.getY();
                if (dy > 200 && swipeUpListener != null) {
                    swipeUpListener.onSwipeUp();
                }
                break;
        }
        return super.onTouchEvent(event);
    }
}
