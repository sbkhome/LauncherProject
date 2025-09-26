package com.android.bks.launcher;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;

import java.util.List;

public class Util {

    public static Intent getContactIntent() {

        PackageManager pkgmgr = LauncherApplication.getAppContext().getPackageManager();
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(ContactsContract.Contacts.CONTENT_URI);
        intent.addCategory(Intent.CATEGORY_DEFAULT);

        List<ResolveInfo> resolveInfoList = pkgmgr.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

        if(!resolveInfoList.isEmpty()){
            ActivityInfo activityInfo = resolveInfoList.get(0).activityInfo;
            ComponentName cn = new ComponentName(activityInfo.applicationInfo.packageName,  activityInfo.name);
            intent.setComponent(cn);

            return intent;
        }
        return null;
    }


    public static Intent getMessageIntent() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:")); // No number, just opens the app
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return intent;
    }

    public static Intent getPhoneAppIntent(){
        PackageManager pkgmgr = LauncherApplication.getAppContext().getPackageManager();
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.addCategory(Intent.CATEGORY_DEFAULT);

        List<ResolveInfo> resolveInfoList = pkgmgr.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

        if(!resolveInfoList.isEmpty()){
            ActivityInfo activityInfo = resolveInfoList.get(0).activityInfo;
            ComponentName cn = new ComponentName(activityInfo.applicationInfo.packageName,  activityInfo.name);

            Intent phoneIntent = new Intent();
            phoneIntent.setAction(Intent.ACTION_MAIN);
            phoneIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            phoneIntent.setComponent(cn);

            return phoneIntent;
        }

        return null;
    }

    public static Intent getCameraAppIntent(){
        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return intent;

    }

    public static Intent getSettingAppIntent(){
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return intent;
    }

}
