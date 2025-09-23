/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.medianet.launcher.presentation.views.layouts

import android.view.View
import com.android.launcher3.celllayout.CellPosMapper.CellPos
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.CellAndSpan
import com.android.medianet.launcher.data.model.ItemInfo

class CellInfo(v: View, info: ItemInfo) {
    var cell : View? = v
    var cellX : Int = -1
    var cellY : Int = -1
    var container : Long = -1
    lateinit var layout : CellLayout
    var screenId : Long = -1
    var spanX : Int = -1
    var spanY : Int = -1

    init {
        this.cellX = info.cellX
        this.cellY = info.cellY
        this.spanX = info.spanX
        this.spanY = info.spanY
        this.screenId = info.screenId
        this.container = info.container
        this.layout = v.parent as CellLayout
    }


    override fun toString(): String {
        return "CellInfo(cell=$cell, screenId=$screenId, container=$container)"
    }
}
