package com.android.bks.launcher;

import android.content.Context;

public final class IconCacheHolder {
    private static volatile IconCache sIconCache;

    public static IconCache get(Context ctx) {
        if (sIconCache == null) {
            synchronized (IconCacheHolder.class) {
                if (sIconCache == null) {
                    sIconCache = new IconCache(ctx.getApplicationContext());
                }
            }
        }
        return sIconCache;
    }
}
