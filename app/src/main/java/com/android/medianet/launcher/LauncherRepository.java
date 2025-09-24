package com.android.medianet.launcher;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;


import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * LauncherRepository - central data access
 */
public class LauncherRepository {
    private final LauncherDataSource dataSource;
    private final MutableLiveData<List<ApplicationInfo>> appsLive = new MutableLiveData<>();
    private final Executor bg = Executors.newSingleThreadExecutor();

    public LauncherRepository(Context context) {
        dataSource = new LauncherDataSource(context);
        loadAll();
    }

    public LiveData<List<ApplicationInfo>> getAppsLive() { return appsLive; }

    public void loadAll() {
        bg.execute(() -> {
            List<ApplicationInfo> list = dataSource.getAllApps();
            appsLive.postValue(list);
        });
    }

    public void insertApp(ApplicationInfo app) {
        bg.execute(() -> {
            dataSource.insertApp(app);
            loadAll();
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
