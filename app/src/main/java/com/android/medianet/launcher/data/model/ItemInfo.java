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

package com.android.medianet.launcher.data.model;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.medianet.launcher.data.db.LauncherSettings;

import java.util.Optional;

/**
 * Represents an item in the launcher.
 */
public class ItemInfo {
    private static final String TAG = "ItemInfo";
    public static final boolean DEBUG = false;
    public static final int NO_ID = -1;
    public long id = NO_ID;
    public int itemType;
    public long container = NO_ID;
    public long screenId = -1;
    public int cellX = -1;
    public int cellY = -1;
    public int spanX = 1;
    public int spanY = 1;
    public CharSequence title;
    public CharSequence appTitle;
    public CharSequence contentDescription;
    private ComponentName mComponentName;

    public ItemInfo() {
    }

    protected ItemInfo(ItemInfo info) {
        copyFrom(info);
    }

    public void copyFrom(@NonNull final ItemInfo info) {
        id = info.id;
        title = info.title;
        cellX = info.cellX;
        cellY = info.cellY;
        spanX = info.spanX;
        spanY = info.spanY;
        screenId = info.screenId;
        itemType = info.itemType;
        container = info.container;
        contentDescription = info.contentDescription;
        mComponentName = info.getTargetComponent();
    }

    @Nullable
    public Intent getIntent() {
        return null;
    }

    @Nullable
    public ComponentName getTargetComponent() {
        return Optional.ofNullable(getIntent()).map(Intent::getComponent).orElse(mComponentName);
    }

    @Nullable
    public String getTargetPackage() {
        ComponentName component = getTargetComponent();
        Intent intent = getIntent();

        return component != null
                ? component.getPackageName()
                : intent != null
                        ? intent.getPackage()
                        : null;
    }

    public void writeToValues(ContentValues cv) {
        //ContentValues cv  = new ContentValues();
        cv.put(LauncherSettings.Favorites.ITEM_TYPE, itemType);
                cv.put(LauncherSettings.Favorites.CONTAINER, container);
                cv.put(LauncherSettings.Favorites.SCREEN, screenId);
                cv.put(LauncherSettings.Favorites.CELLX, cellX);
                cv.put(LauncherSettings.Favorites.CELLY, cellY);
                cv.put(LauncherSettings.Favorites.SPANX, spanX);
                cv.put(LauncherSettings.Favorites.SPANY, spanY);
    }

    public void readFromValues(@NonNull final ContentValues values) {
        itemType = values.getAsInteger(LauncherSettings.Favorites.ITEM_TYPE);
        container = values.getAsInteger(LauncherSettings.Favorites.CONTAINER);
        screenId = values.getAsInteger(LauncherSettings.Favorites.SCREEN);
        cellX = values.getAsInteger(LauncherSettings.Favorites.CELLX);
        cellY = values.getAsInteger(LauncherSettings.Favorites.CELLY);
        spanX = values.getAsInteger(LauncherSettings.Favorites.SPANX);
        spanY = values.getAsInteger(LauncherSettings.Favorites.SPANY);
    }

    /**
     * Write the fields of this item to the DB
     */
    public void onAddToDatabase(ContentValues cv) {
        writeToValues(cv);
    }


    @NonNull
    protected String dumpProperties() {
        return "id=" + id
                + " type=" + LauncherSettings.Favorites.itemTypeToString(itemType)
                + " targetComponent=" + getTargetComponent()
                + " screen=" + screenId
                + " cell(" + cellX + "," + cellY + ")"
                + " span(" + spanX + "," + spanY + ")"
                + " title=" + title;
    }



    @NonNull
    public ItemInfo makeShallowCopy() {
        ItemInfo itemInfo = new ItemInfo();
        itemInfo.copyFrom(this);
        return itemInfo;
    }

    public void setTitle(@Nullable final CharSequence title) {
        this.title = title;
    }

}
