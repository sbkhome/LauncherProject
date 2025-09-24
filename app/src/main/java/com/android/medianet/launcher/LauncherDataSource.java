package com.android.medianet.launcher;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;


import java.util.ArrayList;
import java.util.List;

public class LauncherDataSource {
    private final ContentResolver resolver;

    public LauncherDataSource(Context context) {
        this.resolver = context.getContentResolver();
    }

    public List<ApplicationInfo> getAllApps() {
        List<ApplicationInfo> apps = new ArrayList<>();
        Cursor c = resolver.query(LauncherProvider.CONTENT_URI, null, null, null,
                "title COLLATE NOCASE ASC");
        if (c != null) {
            while (c.moveToNext()) {
                ApplicationInfo ai = cursorToApp(c);
                apps.add(ai);
            }
            c.close();
        }
        return apps;
    }

    public List<ApplicationInfo> getWorkspaceApps() {
        List<ApplicationInfo> apps = new ArrayList<>();
        Cursor c = resolver.query(LauncherProvider.CONTENT_URI, null, "container=?", new String[]{"workspace"},
                "screen ASC, cellY ASC, cellX ASC");
        if (c != null) {
            while (c.moveToNext()) apps.add(cursorToApp(c));
            c.close();
        }
        return apps;
    }

    public List<ApplicationInfo> getHotseatApps() {
        List<ApplicationInfo> apps = new ArrayList<>();
        Cursor c = resolver.query(LauncherProvider.CONTENT_URI, null, "container=?", new String[]{"hotseat"},
                "cellX ASC");
        if (c != null) {
            while (c.moveToNext()) apps.add(cursorToApp(c));
            c.close();
        }
        return apps;
    }

    public void insertApp(ApplicationInfo app) {
        ContentValues v = appToContentValues(app);
        resolver.insert(LauncherProvider.CONTENT_URI, v);
    }

    public void updateApp(ApplicationInfo app) {
        ContentValues v = new ContentValues();
        v.put("screen", app.screen);
        v.put("cellX", app.cellX);
        v.put("cellY", app.cellY);
        v.put("container", app.container);
        String sel = "_id=?";
        String[] args = { String.valueOf(app.id) };
        resolver.update(LauncherProvider.CONTENT_URI, v, sel, args);
    }

    public void deleteApp(ApplicationInfo app) {
        String sel = "_id=?";
        String[] args = { String.valueOf(app.id) };
        resolver.delete(LauncherProvider.CONTENT_URI, sel, args);
    }

    public boolean isFavoritesEmpty() {
        Cursor c = resolver.query(LauncherProvider.CONTENT_URI, new String[]{"_id"}, null, null, null);
        boolean empty = true;
        if (c != null) {
            empty = !c.moveToFirst();
            c.close();
        }
        return empty;
    }

    private ApplicationInfo cursorToApp(Cursor c) {
        ApplicationInfo ai = new ApplicationInfo();
        ai.id = c.getLong(c.getColumnIndex("_id"));
        ai.title = c.getString(c.getColumnIndex("title"));
        ai.screen = c.getInt(c.getColumnIndex("screen"));
        ai.cellX = c.getInt(c.getColumnIndex("cellX"));
        ai.cellY = c.getInt(c.getColumnIndex("cellY"));
        ai.container = c.getString(c.getColumnIndex("container"));
        // intent leaving null for now (we can parse if stored)
        return ai;
    }

    private ContentValues appToContentValues(ApplicationInfo app) {
        ContentValues v = new ContentValues();
        v.put("title", app.title);
        v.put("container", app.container);
        v.put("screen", app.screen);
        v.put("cellX", app.cellX);
        v.put("cellY", app.cellY);
        v.put("intent", app.intent != null ? app.intent.toUri(0) : "");
        return v;
    }
}
