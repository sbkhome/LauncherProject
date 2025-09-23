/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.medianet.launcher.middlelayer;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.medianet.launcher.data.model.ApplicationInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Stores the list of all applications for the all apps view.
 */
@SuppressWarnings("NewApi")
public class AllAppsList {

    private static final String TAG = "AllAppsList";
    public static final int DEFAULT_APPLICATIONS_NUMBER = 42;
    private static final Consumer<ApplicationInfo> NO_OP_CONSUMER = a -> { };
    private Consumer<ApplicationInfo> mRemoveListener = NO_OP_CONSUMER;
    public final ArrayList<ApplicationInfo> data = new ArrayList<>(DEFAULT_APPLICATIONS_NUMBER);

    private IconCache mIconCache;
    private boolean mDataChanged = false;
    private int mFlags;


    public AllAppsList(IconCache iconCache) {
        mIconCache = iconCache;
    }

    public boolean getAndResetChangeFlag() {
        boolean result = mDataChanged;
        mDataChanged = false;
        return result;
    }

    /**
     * Sets or clears the provided flag
     */
    public void setFlags(int flagMask, boolean enabled) {
        if (enabled) {
            mFlags |= flagMask;
        } else {
            mFlags &= ~flagMask;
        }
        mDataChanged = true;
    }

    /**
     * Returns the model flags
     */
    public int getFlags() {
        return mFlags;
    }


    /**
     * Add the supplied ApplicationInfo objects to the list, and enqueue it into the
     * list to broadcast when notify() is called.
     *
     * If the app is already in the list, doesn't add it.
     */
    public void add(ApplicationInfo info, ResolveInfo resolveInfo) {
        add(info, resolveInfo, true);
    }

    public void add(ApplicationInfo info, ResolveInfo resolveInfo, boolean loadIcon) {

        if (findAppInfo(info.componentName) != null) {
            return;
        }
        if (loadIcon) {
            mIconCache.geTitleAndIcon(info, resolveInfo);
        } else {
            info.title = "";
        }

        data.add(info);
        mDataChanged = true;
    }


    private void removeApp(int index) {
        ApplicationInfo removed = data.remove(index);
        if (removed != null) {
            mDataChanged = true;
            mRemoveListener.accept(removed);
        }
    }

    public void clear() {
        data.clear();
        mDataChanged = false;
    }

    /**
     * Add the icons for the supplied apk called packageName.
     */
    public void addPackage(
            Context context, String packageName) {

        List<ResolveInfo> resolveInfoList =  findActivitiesForPackage(context, packageName);

        for (ResolveInfo resolveInfo : resolveInfoList) {
            add(new ApplicationInfo(resolveInfo, mIconCache), resolveInfo);
        }
    }

    public List<ResolveInfo> findActivitiesForPackage(Context context, String packageName){
        final PackageManager pkgmanager = context.getPackageManager();
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN ,null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mainIntent.setPackage(packageName);

        List<ResolveInfo> resolveInfoList =  pkgmanager.queryIntentActivities(mainIntent, 0);
        return resolveInfoList;
    }

    /**
     * Remove the apps for the given apk identified by packageName.
     */
    public void removePackage(String packageName, UserHandle user) {
        final List<ApplicationInfo> data = this.data;
        for (int i = data.size() - 1; i >= 0; i--) {
            ApplicationInfo info = data.get(i);
            if (packageName.equals(info.componentName.getPackageName())) {
                removeApp(i);
            }
        }
    }



  /*  public void updateIconsAndLabels(HashSet<String> packages, UserHandle user) {
        for (AppInfo info : data) {
            if (info.user.equals(user) && packages.contains(info.componentName.getPackageName())) {
                mIconCache.updateTitleAndIcon(info);
                info.sectionName = mIndex.computeSectionName(info.title);
                mDataChanged = true;
            }
        }
    }*/

    /**
     * Add and remove icons for this package which has been updated.
     */
    public List<ResolveInfo> updatePackage(
            Context context, String packageName, UserHandle user) {
        final List<ResolveInfo> matches = findActivitiesForPackage(context, packageName);
        if (matches.size() > 0) {
            // Find disabled/removed activities and remove them from data and add them
            // to the removed list.
            for (int i = data.size() - 1; i >= 0; i--) {
                final ApplicationInfo applicationInfo = data.get(i);
                if (packageName.equals(applicationInfo.componentName.getPackageName())) {
                    if (!findActivity(matches, applicationInfo.componentName)) {

                        removeApp(i);
                    }
                }
            }

            // Find enabled activities and add them to the adapter
            // Also updates existing activities with new labels/icons
            for (final ResolveInfo info : matches) {
                ApplicationInfo applicationInfo = findAppInfo(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
                if (applicationInfo == null) {
                    add(new ApplicationInfo(info, mIconCache), info);
                } else {
                    Intent launchIntent = ApplicationInfo.makeLaunchIntent(info);

                    mIconCache.geTitleAndIcon(applicationInfo, info);
                    applicationInfo.intent = launchIntent;
                    mDataChanged = true;
                }
            }
        } else {
            // Remove all data for this package.

            for (int i = data.size() - 1; i >= 0; i--) {
                final ApplicationInfo applicationInfo = data.get(i);
                if (packageName.equals(applicationInfo.componentName.getPackageName())) {
                    mIconCache.remove(applicationInfo.componentName);
                    removeApp(i);
                }
            }
        }

        return matches;
    }


    private static boolean findActivity(List<ResolveInfo> apps,
            ComponentName component) {
        for (ResolveInfo info : apps) {
            if (component.getPackageName().equals(info.activityInfo.packageName)
                    && component.getClassName().equals(info.activityInfo.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find an AppInfo object for the given componentName
     *
     * @return the corresponding AppInfo or null
     */
    public @Nullable ApplicationInfo findAppInfo(@NonNull ComponentName componentName) {
        for (ApplicationInfo info: data) {
            if (componentName.equals(info.componentName)) {
                return info;
            }
        }
        return null;
    }

}
