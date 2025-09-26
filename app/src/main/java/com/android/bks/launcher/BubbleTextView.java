package com.android.bks.launcher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;



/**
 * Simple icon+label view used across workspace/hotseat/allapps
 */
public class BubbleTextView extends FrameLayout {

    private final ImageView icon;
    private final TextView label;
    private ApplicationInfo info;

    public BubbleTextView(Context ctx) {
        super(ctx);
        icon = new ImageView(ctx);
        label = new TextView(ctx);
        init();
    }
    public BubbleTextView(Context ctx, @Nullable AttributeSet attrs) { super(ctx, attrs); icon = new ImageView(ctx); label = new TextView(ctx); init(); }

    private void init() {
        int pad = dp(6);
        setPadding(pad, pad, pad, pad);
        icon.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        addView(icon);
        label.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        label.setPadding(0, dp(4), 0, 0);
        addView(label);
    }

    public void applyFromApplicationInfo(ApplicationInfo ai, boolean scaleUp) {
        this.info = ai;
        label.setText(ai.title != null ? ai.title : "");

        if (ai.icon != null) {
            icon.setImageBitmap(ai.icon); // <-- use Bitmap from IconCache
        } else {
            // fallback to default icon
            Drawable d = getResources().getDrawable(android.R.drawable.sym_def_app_icon);
            icon.setImageDrawable(d);
        }

        setClickable(true);
    }



    public ApplicationInfo getApplicationInfo() { return info; }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
