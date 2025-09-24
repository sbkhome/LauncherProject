package com.android.medianet.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

/**
 * CellLayout - single page grid (4x4)
 */
public class CellLayout extends FrameLayout {

    private static final int ROWS = 4;
    private static final int COLUMNS = 4;

    public CellLayout(Context ctx) { super(ctx); }
    public CellLayout(Context ctx, @Nullable AttributeSet attrs) { super(ctx, attrs); }

    /**
     * Add child to grid cell (cellX [0..3], cellY [0..3]).
     * We'll compute margin for placement on layout pass; to keep it simple,
     * use LayoutParams with margins based on current width/height if available.
     */
    public void addViewToCell(View child, int cellX, int cellY) {
        int w = getWidth();
        int h = getHeight();
        int cellW = (w > 0) ? w / COLUMNS : LayoutParams.MATCH_PARENT;
        int cellH = (h > 0) ? h / ROWS : LayoutParams.MATCH_PARENT;

        LayoutParams lp = new LayoutParams(cellW, cellH);
        lp.leftMargin = (cellW > 0) ? cellX * cellW : 0;
        lp.topMargin = (cellH > 0) ? cellY * cellH : 0;
        addView(child, lp);
    }
}
