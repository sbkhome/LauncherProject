package com.android.bks.launcher;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

/**
 * Workspace is a set of pages (CellLayouts) user can swipe through.
 */
public class Workspace extends PagedView {

    public Workspace(Context context) {
        super(context);
    }

    public Workspace(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void addPage(CellLayout page) {
        addView(page);
    }

    public CellLayout getPageAt(int index) {
        return (CellLayout) super.getPageAt(index);
    }

    public int getPageCount() {
        return super.getPageCount();
    }
}
