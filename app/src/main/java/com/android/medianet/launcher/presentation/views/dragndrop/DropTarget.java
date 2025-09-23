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

import android.graphics.Rect;


/**
 * Interface defining an object that can receive a drag.
 *
 */
public interface DropTarget {

    boolean isDropEnabled();

    void onDrop(DragSource dragSource, int x , int y , int xoffset, int yoffset, DragView dragview, Object dragInfo);

    void onDragEnter(DragSource dragSource, int x , int y , int xoffset, int yoffset, DragView dragview, Object dragInfo);


    void onDragOver(DragSource dragSource, int x , int y , int xoffset, int yoffset, DragView dragview, Object dragInfo);


    void onDragExit(DragSource dragSource, int x , int y , int xoffset, int yoffset, DragView dragview, Object dragInfo);


    void acceptDrop(DragSource dragSource, int x , int y , int xoffset, int yoffset, DragView dragview, Object dragInfo);

    void getHitRectRelativeToDragLayer(Rect outRect);
}
