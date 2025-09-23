/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.medianet.launcher.presentation.views.dragndrop;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;


public class DragController {

    private static final int DEEP_PRESS_DISTANCE_FACTOR = 3;

    // temporaries to avoid gc thrash
    private final Rect mRectTemp = new Rect();
    private final int[] mCoordinatesTemp = new int[2];

    /**
     * Drag driver for the current drag/drop operation, or null if there is no active DND operation.
     * It's null during accessible drag operations.
     */
    protected DragDriver mDragDriver = null;

    @VisibleForTesting
    /** Options controlling the drag behavior. */
    public DragOptions mOptions;

    /** Coordinate for motion down event */
    protected final Point mMotionDown = new Point();
    /** Coordinate for last touch event **/
    protected final Point mLastTouch = new Point();

    protected final Point mTmpPoint = new Point();

    @VisibleForTesting
    public DragSource mDragSource;

    /** Who can receive drop events */
    private final ArrayList<DropTarget> mDropTargets = new ArrayList<>();
    private final ArrayList<DragListener> mListeners = new ArrayList<>();

    protected DropTarget mLastDropTarget;

    private int mLastTouchClassification;
    protected int mDistanceSinceScroll = 0;

    protected boolean mIsInPreDrag;

    private final int DRAG_VIEW_SCALE_DURATION_MS = 500;


    public interface DragListener {

        void onDragStart(DragSource dragSource, DragOptions options);


        void onDragEnd();
    }



    public boolean dispatchKeyEvent(KeyEvent event) {
        return mDragDriver != null;
    }


    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        /*if (mOptions != null && mOptions.isAccessibleDrag) {
            return false;
        }

        Point dragLayerPos = getClampedDragLayerPos(getX(ev), getY(ev));
        mLastTouch.set(dragLayerPos.x,  dragLayerPos.y);
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            // Remember location of down touch
            mMotionDown.set(dragLayerPos.x,  dragLayerPos.y);
        }

        mLastTouchClassification = ev.getClassification();
        return mDragDriver != null && mDragDriver.onInterceptTouchEvent(ev);*/

        return false;
    }

    /**
     * Clamps the position to the drag layer bounds.
     */
    protected Point getClampedDragLayerPos(float x, float y) {
       /* mActivity.getDragLayer().getLocalVisibleRect(mRectTemp);
        mTmpPoint.x = (int) Math.max(mRectTemp.left, Math.min(x, mRectTemp.right - 1));
        mTmpPoint.y = (int) Math.max(mRectTemp.top, Math.min(y, mRectTemp.bottom - 1));*/
        return mTmpPoint;
    }


    protected float getX(MotionEvent ev) {
        return ev.getX();
    }

    protected float getY(MotionEvent ev) {
        return ev.getY();
    }


    public boolean onControllerTouchEvent(MotionEvent ev) {
        return mDragDriver != null && mDragDriver.onTouchEvent(ev);
    }

}
