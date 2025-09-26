package com.android.bks.launcher;

import java.util.ArrayList;
import java.util.List;

/**
 * FolderInfo - container of ApplicationInfo
 */
public class FolderInfo extends ItemInfo {
    public String title;
    public List<ApplicationInfo> contents = new ArrayList<>();

    public FolderInfo(String title) { this.title = title; }
}
