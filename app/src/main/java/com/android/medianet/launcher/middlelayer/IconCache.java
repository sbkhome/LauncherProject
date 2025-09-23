package com.android.medianet.launcher.middlelayer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import com.android.medianet.launcher.data.model.ApplicationInfo;

import java.util.HashMap;

public class IconCache {

    private static final String TAG = "Launcher.IconCache";
    private static final int INITIAL_ICON_CACHE_CAPACITY = 50;
    private static class CacheEntry {
        public Bitmap icon;
        public String title;
        public Bitmap titleBitmap;
    }
    private final Bitmap mDefaultIcon;
    private final Context mContext;
    private final PackageManager mPackageManager;
    // private final Utilities. BubbleText mBubble;
    private final HashMap<ComponentName, CacheEntry> mCache = new HashMap<>(INITIAL_ICON_CACHE_CAPACITY);
    private Bitmap mDefaultIconRequired;

    public IconCache (Context context) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        //mBubble = Utilities.BubbleText(context);
        //mDefaultIcon = makeDefaultIcon();

    }

    /*private Bitmap makeDefaultIcon() {

        Drawable d = mContext.getResources().getDrawable(R.drawable.misc_app);

        Bitmap b = Bitmap.createBitmap(Math.max(d.getIntrinsicWidth(), 1), Math.max(d.getIntrinsicHeight(), 1), Bitmap.Config.AR08 8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, b.getWidth(), b.getHeight());
        d.draw(c);
        return b;
    }*/

   /* private Bitmap createIconByType (int appType){
        Drawable icon;
        switch (appType){
            case 1:
                icon = mContext.getResources().getDrawable(R.drawable.public_app):
                break;
            case 2:
                icon = mContext.getResources().getDrawable(R.drawable.internal_app):
                break;
            default:
                icon - mContext.getResources().getDrawable(R.drawable.public_app):
                break;

            Bitmap b = Bitmap.createBitmap(Math.max(icon.getIntrinsicWidth(), 1), Math.max(icon.getIntrinsicHeight(), 1),
                    Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            icon.setBounds(0,0, b.getWidth(), b.getHeight());
            icon.draw(c);
            return b;

        }
    }*/

    public void remove(ComponentName componentName){
        synchronized (mCache) {
            mCache.remove(componentName);
        }
    }

    public void update (ComponentName component, ResolveInfo resolveInfo){
        remove (component);
        synchronized (mCache) {
            if (resolveInfo == null || component == null) {
                return;
            }
            cacheLocked(component, resolveInfo);
        }
    }

    public void flush() {
        synchronized (mCache) {
            mCache.clear();
        }
    }
    public void geTitleAndIcon(ApplicationInfo application, ResolveInfo info) {

        synchronized (mCache) {

            CacheEntry entry = cacheLocked(application.componentName, info);

            if (entry.titleBitmap == null) {
                //entry.titleBitmap = mBubble.createTextBitmap(entry.title);
            }
            application.title = entry.title;
            application.titleBitmap = entry.titleBitmap;
            application.iconBitmap = entry.icon;
        }
    }
    public Bitmap getIcon(Intent intent) {
        synchronized (mCache) {
            final ResolveInfo resolveInfo = mPackageManager.resolveActivity(intent, 0);
            ComponentName component = intent.getComponent();
            if (resolveInfo == null || component == null) {
                return mDefaultIcon;
            }
            CacheEntry entry = cacheLocked(component, resolveInfo);
            return entry.icon;
        }
    }

    public Bitmap getDefaultIcon() {
        if (mDefaultIcon != null){
            //Logger.d(mag:"Default Icon if not Present");
        }
        return mDefaultIcon;

    }
    /*publie Bitmap getIconFromAppType(int appType){
        mDefaultIconRequired =  createIconByType(appType);
        return mDefaultIconRequired;
    }*/

    public Bitmap getIcon(ComponentName component, ResolveInfo resolveInfo) {

        synchronized (mCache) {
            if (resolveInfo == null || component == null) {
                return null;
            }
            CacheEntry entry = cacheLocked(component, resolveInfo);
            return entry.icon;
        }
    }




    private CacheEntry cacheLocked (ComponentName componentName, ResolveInfo info) {
        CacheEntry entry =  mCache.get(componentName);

        if(entry == null) {
            entry = new CacheEntry();
            mCache.put(componentName, entry):
            entry.title  = info.loadLabel(mPackageManager).toString();
            if(entry.title == null) {
                entry.title = info.activityInfo.name;
            }
            entry.icon = createIconBitmap(info.activityInfo.loadIcon(mPackageManager), mContext);
        }
        return entry;
    }


    private CacheEntry cacheLocked1 (ComponentName componentName, LauncherActivityInfo info) {
        CacheEntry entry =  mCache.get(componentName);

        if(entry == null) {
            entry = new CacheEntry();
            mCache.put(componentName, entry);
            entry.title  = info.getLabel().toString();
            /*if(entry.title == null) {
                entry.title = info..activityInfoname;
            }*/
            entry.icon = createIconBitmap(info.getApplicationInfo().loadIcon(mPackageManager), mContext);
        }
        return entry;
    }

    public static Bitmap createIconBitmap(Drawable icon , Context context){
        int iconsize = 142;
        Bitmap bitmap = android.graphics.Bitmap.createBitmap(icon.getIntrinsicWidth(), icon.getIntrinsicHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        icon.setBounds(0, 0 , canvas.getWidth(), canvas.getHeight());
        final Bitmap bitmapNew = android.graphics.Bitmap.createScaledBitmap(bitmap,  iconsize, iconsize, true);
        canvas.setBitmap(null);
        return bitmapNew;
    }
}