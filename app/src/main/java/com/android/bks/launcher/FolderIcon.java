package com.android.bks.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import java.util.List;

public class FolderIcon extends LinearLayout {

    private FolderInfo folderInfo;

    public FolderIcon(Context ctx) {
        super(ctx);
        folderInfo = new FolderInfo("Google");
        init();
    }

    public FolderIcon(Context ctx, @Nullable AttributeSet attrs) {
        super(ctx, attrs);
        folderInfo = new FolderInfo("Google");
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
    }

    public void setTitle(String title) {
        folderInfo.title = title;
    }

    public void setContents(List<ApplicationInfo> apps) {
        folderInfo.contents.clear();
        if (apps != null) folderInfo.contents.addAll(apps);
        populateFolderApps();
    }

    private void populateFolderApps() {
        removeAllViews();
        for (ApplicationInfo ai : folderInfo.contents) {
            BubbleTextView btv = new BubbleTextView(getContext());
            btv.applyFromApplicationInfo(ai, false);
            addView(btv, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    public FolderInfo getFolderInfo() {
        return folderInfo;
    }
}
