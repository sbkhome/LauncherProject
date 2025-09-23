

package com.android.medianet.launcher.presentation.views.dragndrop;


import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.FrameLayout;

public class DragLayer extends FrameLayout {


    DragController mDragController;

    public DragLayer (Context context, AttributeSet attrs) { super(context, attrs);}

    public void setDragController(DragController controller) { mDragController = controller; }

    @Override
    public boolean dispatchKeyEvent (KeyEvent event) {
        return mDragController.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return mDragController.onControllerInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mDragController.onControllerTouchEvent(ev);
    }


    public boolean dispatchUnhandledMove(View focused, int direction) {
        return false;//mDragController.dispatchUnhandledMove(focused, direction);
    }
}