package com.android.bks.launcher;

import android.content.Intent;
import android.graphics.Bitmap;

/**
 * ApplicationInfo - stands for an app/shortcut on workspace/hotseat
 */
public class ApplicationInfo extends ItemInfo {
    public String title;
    public Intent intent; // optional, may be null
    public Bitmap icon;   // <-- add this

    public ApplicationInfo() {}

    public ApplicationInfo(long id, String title, Intent intent, int screen,
                           int cellX, int cellY, String container, Bitmap icon) {
        this.id = id;
        this.title = title;
        this.intent = intent;
        this.screen = screen;
        this.cellX = cellX;
        this.cellY = cellY;
        this.container = container;
        this.icon = icon;  // <-- initialize icon
    }
}
