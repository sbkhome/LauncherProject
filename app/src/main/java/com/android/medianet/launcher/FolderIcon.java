package com.android.medianet.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;



import java.util.List;

public class FolderIcon extends LinearLayout {

    private final FolderInfo folderInfo;

    public FolderIcon(Context ctx) { super(ctx); folderInfo = new FolderInfo("Google"); init(); }
    public FolderIcon(Context ctx, @Nullable AttributeSet attrs) { super(ctx, attrs); folderInfo = new FolderInfo("Google"); init(); }

    private void init() {
        setOrientation(VERTICAL);
    }

    public void populateGoogleApps() {
        // default apps for folder (dummy)
        ApplicationInfo g1 = new ApplicationInfo(0, "Gmail", null, 0, 0, 0, "folder");
        ApplicationInfo g2 = new ApplicationInfo(0, "Maps", null, 0, 0, 0, "folder");
        ApplicationInfo g3 = new ApplicationInfo(0, "YouTube", null, 0, 0, 0, "folder");
        folderInfo.contents.add(g1);
        folderInfo.contents.add(g2);
        folderInfo.contents.add(g3);

        // represent folder by listing small icons internally (simple)
        List<ApplicationInfo> apps = folderInfo.contents;
        for (ApplicationInfo ai : apps) {
            BubbleTextView btv = new BubbleTextView(getContext());
            btv.applyFromApplicationInfo(ai, false);
            addView(btv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    public FolderInfo getFolderInfo() { return folderInfo; }
}
