package com.android.medianet.launcher;

import android.content.Intent;

/**
 * ApplicationInfo - stands for an app/shortcut on workspace/hotseat
 */
public class ApplicationInfo extends ItemInfo {
    public String title;
    public Intent intent; // optional, may be null

    public ApplicationInfo() {}

    public ApplicationInfo(long id, String title, Intent intent, int screen, int cellX, int cellY, String container) {
        this.id = id;
        this.title = title;
        this.intent = intent;
        this.screen = screen;
        this.cellX = cellX;
        this.cellY = cellY;
        this.container = container;
    }
}
