package com.android.medianet.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

/**
 * Workspace - holds pages horizontally
 */
public class Workspace extends HorizontalScrollView {

    private LinearLayout pages;

    public Workspace(Context ctx) { super(ctx); init(); }
    public Workspace(Context ctx, @Nullable AttributeSet attrs) { super(ctx, attrs); init(); }

    private void init() {
        pages = new LinearLayout(getContext());
        pages.setOrientation(LinearLayout.HORIZONTAL);
        addView(pages, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setHorizontalScrollBarEnabled(false);
    }

    public void addPage(CellLayout page) {
        pages.addView(page, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public int getPageCount() { return pages.getChildCount(); }

    public CellLayout getPageAt(int idx) { return (CellLayout) pages.getChildAt(idx); }
}
