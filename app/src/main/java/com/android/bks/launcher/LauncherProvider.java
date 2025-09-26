package com.android.bks.launcher;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LauncherProvider extends ContentProvider {

    public static final String AUTHORITY = "com.android.bks.launcher.settings";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/favorites");

    private static final int FAVORITES = 1;
    private static final int FAVORITE_ID = 2;

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sUriMatcher.addURI(AUTHORITY, "favorites", FAVORITES);
        sUriMatcher.addURI(AUTHORITY, "favorites/#", FAVORITE_ID);
    }

    private LauncherDbHelper mDbHelper;

    @Override
    public boolean onCreate() {
        mDbHelper = new LauncherDbHelper(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        Cursor c;
        switch (sUriMatcher.match(uri)) {
            case FAVORITES:
                c = db.query("favorites", projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case FAVORITE_ID:
                long id = ContentUris.parseId(uri);
                c = db.query("favorites", projection, "_id=?", new String[]{String.valueOf(id)}, null, null, sortOrder);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long rowId;
        switch (sUriMatcher.match(uri)) {
            case FAVORITES:
                rowId = db.insert("favorites", null, values);
                if (rowId > 0) {
                    Uri newUri = ContentUris.withAppendedId(CONTENT_URI, rowId);
                    getContext().getContentResolver().notifyChange(newUri, null);
                    return newUri;
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        return null;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
            case FAVORITES:
                count = db.update("favorites", values, selection, selectionArgs);
                break;
            case FAVORITE_ID:
                long id = ContentUris.parseId(uri);
                count = db.update("favorites", values, "_id=?", new String[]{String.valueOf(id)});
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        if (count > 0) getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
            case FAVORITES:
                count = db.delete("favorites", selection, selectionArgs);
                break;
            case FAVORITE_ID:
                long id = ContentUris.parseId(uri);
                count = db.delete("favorites", "_id=?", new String[]{String.valueOf(id)});
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        if (count > 0) getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case FAVORITES:
                return "vnd.android.cursor.dir/vnd.com.example.launcher.favorites";
            case FAVORITE_ID:
                return "vnd.android.cursor.item/vnd.com.example.launcher.favorites";
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    // Internal DB helper
    private static class LauncherDbHelper extends android.database.sqlite.SQLiteOpenHelper {
        private static final String DB_NAME = "launcher.db";
        private static final int DB_VERSION = 1;

        LauncherDbHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS favorites (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "title TEXT," +
                    "intent TEXT," +
                    "container TEXT," +    // "workspace","hotseat","folder","all_apps"
                    "screen INTEGER DEFAULT 0," +
                    "cellX INTEGER DEFAULT 0," +
                    "cellY INTEGER DEFAULT 0," +
                    "spanX INTEGER DEFAULT 1," +
                    "spanY INTEGER DEFAULT 1," +
                    "itemType TEXT DEFAULT 'APPLICATION'" + // could be FOLDER, APPLICATION
                    ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // No-op for now
        }
    }
}
