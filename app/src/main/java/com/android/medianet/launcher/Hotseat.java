package com.android.medianet.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.List;

/**
 * Hotseat - fixed bottom row (4 slots)
 */
public class Hotseat extends FrameLayout {

    public static final int NUM = 4; // Number of icons in the hotseat

    public Hotseat(Context context) {
        super(context);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private void init() {  }

    public void bindApps(List<ApplicationInfo> apps) {
        removeAllViews();
        for (int i = 0; i < NUM; i++) {
            if (i < apps.size()) {
                ApplicationInfo app = apps.get(i);
                BubbleTextView btv = new BubbleTextView(getContext());
                btv.applyFromApplicationInfo(app, false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);

                addView(btv, lp);
            } else {
                // placeholder empty slot
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                addView(new android.view.View(getContext()), lp);
            }
        }
    }
}
