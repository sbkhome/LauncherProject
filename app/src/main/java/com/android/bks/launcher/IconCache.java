package com.android.bks.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.HashMap;

public class IconCache {
    private final PackageManager mPm;
    private final HashMap<ComponentName, Bitmap> mCache = new HashMap<>();

    public IconCache(Context context) {
        mPm = context.getPackageManager();
    }

    /**
     * Returns a bitmap for the given ResolveInfo. Caches by ComponentName.
     */
    public synchronized Bitmap getIcon(ResolveInfo ri) {
        if (ri == null || ri.activityInfo == null) return null;

        ComponentName cn = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
        Bitmap b = mCache.get(cn);
        if (b != null) return b;

        Drawable d = ri.loadIcon(mPm);
        b = drawableToBitmap(d);
        mCache.put(cn, b);
        return b;
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) return null;

        if (drawable instanceof BitmapDrawable) {
            Bitmap bm = ((BitmapDrawable) drawable).getBitmap();
            return bm;
        }

        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        w = (w > 0) ? w : 1;
        h = (h > 0) ? h : 1;

        Bitmap bitmap = Bitmap.createBitmap(w, h, Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, w, h);
        drawable.draw(canvas);
        return bitmap;
    }
}
