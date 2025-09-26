package com.android.bks.launcher;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;


import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * LauncherRepository - central data access
 */
public class LauncherRepository {
    private final String TAG = "HOMETEST_LauncherRepository";
    private final LauncherDataSource dataSource;
    private final MutableLiveData<List<ApplicationInfo>> appsLive = new MutableLiveData<>();
    private final Executor bg = Executors.newSingleThreadExecutor();

    public LauncherRepository(Context context) {
        dataSource = new LauncherDataSource(context);
        Log.i(TAG , "LauncherRepository(): created");
    }

    public LiveData<List<ApplicationInfo>> getAppsLive() { return appsLive; }

    public void loadAll() {
        bg.execute(() -> {
            List<ApplicationInfo> list = dataSource.getAllApps();
            Log.i(TAG, "loadAll(): total apps loaded=" + list.size());
            appsLive.postValue(list);
        });
    }

    public void insertApp(ApplicationInfo app) {
        bg.execute(() -> {
            dataSource.insertApp(app);
            loadAll();
        });
    }

    public void insertApps(List<ApplicationInfo> applicationInfoList) {
        bg.execute(() -> {
            synchronized (this){
                for(ApplicationInfo appInfo : applicationInfoList){
                    dataSource.insertApp(appInfo);
                }
                Log.i(TAG, "insertApps(): total apps inserted="+applicationInfoList.size());
                loadAll();
            }
        });
    }

    public void updateApp(ApplicationInfo app) {
        bg.execute(() -> {
            dataSource.updateApp(app);
            loadAll();
        });
    }

    public void deleteApp(ApplicationInfo app) {
        bg.execute(() -> {
            dataSource.deleteApp(app);
            loadAll();
        });
    }

    public boolean isFavoritesEmpty() {
        return dataSource.isFavoritesEmpty();
    }

    public List<ApplicationInfo> getWorkspaceAppsSync() {
        return dataSource.getWorkspaceApps();
    }

    public List<ApplicationInfo> getHotseatAppsSync() {
        return dataSource.getHotseatApps();
    }
}
