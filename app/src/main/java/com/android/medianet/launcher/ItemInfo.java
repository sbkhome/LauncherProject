package com.android.medianet.launcher;

/**
 * ItemInfo - base class for workspace items (Launcher3-style)
 */
public class ItemInfo {
    public long id;
    public int cellX;
    public int cellY;
    public int spanX = 1;
    public int spanY = 1;
    public String container; // "workspace", "hotseat", "folder", "all_apps"
    public int screen;
}
