package com.android.medianet.launcher;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;



import java.util.List;

public class LauncherViewModel extends AndroidViewModel {
    private final LauncherRepository repo;
    private final LiveData<List<ApplicationInfo>> appsLive;

    public LauncherViewModel(@NonNull Application application) {
        super(application);
        repo = new LauncherRepository(application.getApplicationContext());
        appsLive = repo.getAppsLive();
    }

    public LiveData<List<ApplicationInfo>> getAppsLive() { return appsLive; }

    public void insertApp(ApplicationInfo app) { repo.insertApp(app); }
    public void updateApp(ApplicationInfo app) { repo.updateApp(app); }
    public void deleteApp(ApplicationInfo app) { repo.deleteApp(app); }

    public boolean isDbEmpty() { return repo.isFavoritesEmpty(); }

    // For synchronous loading (used during UI init)
    public List<ApplicationInfo> getWorkspaceAppsSync() { return repo.getWorkspaceAppsSync(); }
    public List<ApplicationInfo> getHotseatAppsSync() { return repo.getHotseatAppsSync(); }

    public void refresh() { repo.loadAll(); }
}
