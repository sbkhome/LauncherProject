package com.android.bks.launcher;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Main Launcher activity (corrected)
 */
public class Launcher extends AppCompatActivity {

    private static final String TAG = "HOMETEST_Launcher";

    private DragLayer dragLayer;
    private Workspace workspace;
    private Hotseat hotseat;
    private TextView searchText;
    private AllAppsContainerView allApps;
    private LauncherViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "onCreate() called");

        setupSystemUi();
        setContentView(R.layout.launcher);

        dragLayer = findViewById(R.id.drag_layer);
        workspace = findViewById(R.id.workspace);
        hotseat = findViewById(R.id.hotseat);
        searchText = findViewById(R.id.search_text);
        allApps = findViewById(R.id.all_apps_container);

        viewModel = new ViewModelProvider(this).get(LauncherViewModel.class);

        // create two fixed pages
        for (int i = 0; i < 2; i++) {
            CellLayout page = new CellLayout(this);
            workspace.addPage(page);
        }

        // first-run defaults if DB empty
        if (viewModel.isDbEmpty()) {
            Log.i(TAG, "onCreate(): DB empty -> inserting defaults");
            populateDefaultsAndPersist();
        }

        // observe apps and bind
        viewModel.getAppsLive().observe(this, apps -> {
            Log.i(TAG, "LiveData callback: apps=" + apps.size());
            bindWorkspace(apps);
            bindHotseat(apps);
            bindAllApps(apps);
        });

        // swipe-up opens all apps (DragLayer listener)
        dragLayer.setOnSwipeUpListener(() -> {
            if (!allApps.isShownPanel()) {
                allApps.show();
            }
        });

        // search click launches web search
        searchText.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_WEB_SEARCH);
                i.putExtra("query", "");
                startActivity(i);
            } catch (Exception e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")));
            }
        });
    }

    private static IconCache sIconCache;

    private void setupSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window w = getWindow();
            w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            w.getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
        }
    }

    private void bindWorkspace(List<ApplicationInfo> apps) {
        Log.i(TAG, "bindWorkspace() : total apps=" + apps.size());

        // Clear pages
        for (int p = 0; p < workspace.getPageCount(); p++) {
            CellLayout page = workspace.getPageAt(p);
            page.removeAllViews();
        }

        // Collect folder_google contents from DB first
        List<ApplicationInfo> folderGoogleApps = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if ("folder_google".equals(app.container)) {
                folderGoogleApps.add(app);
            }
        }

        // Now place workspace apps; if a "Google Folder" placeholder is present, create FolderIcon using folderGoogleApps
        for (ApplicationInfo app : apps) {
            if ("workspace".equals(app.container)) {
                int screen = Math.max(0, Math.min(app.screen, workspace.getPageCount() - 1));
                CellLayout page = workspace.getPageAt(screen);

                if ("Google Folder".equals(app.title)) {
                    // Create folder icon and populate from DB-collected google apps
                    FolderIcon folderIcon = new FolderIcon(this);
                    folderIcon.setTitle("Google");
                    folderIcon.setContents(folderGoogleApps);
                    page.addViewToCell(folderIcon, app.cellX, app.cellY);
                } else {
                    BubbleTextView btv = new BubbleTextView(this);
                    btv.applyFromApplicationInfo(app, false);

                    btv.setOnClickListener(v -> {
                        if (app.intent != null) {
                            try {
                                startActivity(app.intent);
                            } catch (Exception e) {
                                Toast.makeText(this, "Cannot launch app", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

                    // long-press popup
                    btv.setOnLongClickListener(v -> {
                        showHomescreenPopup(app);
                        return true;
                    });

                    Log.i(TAG, "bindWorkspace(): app title="+app.title+", cellx="+app.cellX+", celly="+app.cellY);
                    page.addViewToCell(btv, app.cellX, app.cellY);
                }
            }
        }
    }

    private void bindHotseat(List<ApplicationInfo> apps) {
        Log.i(TAG, "bindHotseat() : total apps=" + apps.size());
        List<ApplicationInfo> hs = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if ("hotseat".equals(app.container)) hs.add(app);
        }
        hotseat.bindApps(hs);
    }

    private void bindAllApps(List<ApplicationInfo> apps) {
        Log.i(TAG, "bindAllApps() : total apps=" + apps.size());
        allApps.setApps(apps);
    }

    private void showHomescreenPopup(ApplicationInfo app) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, findViewById(android.R.id.content));
        popup.getMenu().add("Remove");
        popup.getMenu().add("App Info");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Remove")) {
                viewModel.deleteApp(app);
                Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show();
            } else {
                try {
                    if (app.intent != null && app.intent.getComponent() != null) {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        i.setData(Uri.parse("package:" + app.intent.getComponent().getPackageName()));
                        startActivity(i);
                    } else {
                        Toast.makeText(this, "No app info available", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception ex) {
                    Toast.makeText(this, "Cannot open app info", Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        });
        popup.show();
    }

    /**
     * Insert defaults into DB only when the corresponding launch Intent is actually resolvable.
     */
    private void populateDefaultsAndPersist() {
        PackageManager pm = getPackageManager();

        List<ApplicationInfo> applicationInfoList = new ArrayList<>();

        // Workspace last row (screen 0) - only insert if intent resolves
        Intent contactsIntent = Util.getContactIntent();
        if (contactsIntent != null) {
            applicationInfoList.add(makeAppFromIntent("Contacts", contactsIntent, "workspace", 0, 0, 3));
            Log.i(TAG, "Inserted Contacts into workspace");
        }

        Intent phoneIntent = Util.getPhoneAppIntent();
        if (phoneIntent != null) {
            applicationInfoList.add(makeAppFromIntent("Phone", phoneIntent, "workspace", 0, 1, 3));
            Log.i(TAG, "Inserted Phone into workspace");
        }

        Intent messagesIntent = Util.getMessageIntent();
        if (messagesIntent != null) {
            //viewModel.insertApp(makeAppFromIntent("Messages", messagesIntent, "workspace", 0, 2, 3));
            //Log.i(TAG, "Inserted Messages into workspace");
        }

        /*String[] googlePkgs = new String[]{
                "com.google.android.gm",           // Gmail
                "com.google.android.apps.maps",   // Maps
                "com.google.android.youtube",     // YouTube
                "com.google.android.apps.docs"    // Drive
        };
        boolean anyGoogle = false;
        for (String pkg : googlePkgs) {
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch != null) {
                ApplicationInfo ai = new ApplicationInfo();
                ai.title = pkg; // optionally change to human-friendly names
                ai.intent = launch;
                ai.container = "folder_google";
                ai.screen = 0;
                ai.cellX = 0;
                ai.cellY = 0;
                viewModel.insertApp(ai);
                Log.i(TAG, "Inserted Google app into folder: " + pkg);
                anyGoogle = true;
            }
        }
        if (anyGoogle) {
            ApplicationInfo folderPlaceholder = new ApplicationInfo();
            folderPlaceholder.title = "Google Folder";
            folderPlaceholder.container = "workspace";
            folderPlaceholder.screen = 0;
            folderPlaceholder.cellX = 3;
            folderPlaceholder.cellY = 3;
            viewModel.insertApp(folderPlaceholder);
            Log.i(TAG, "Inserted Google Folder placeholder on workspace");
        }*/

        Intent cameraIntent = Util.getCameraAppIntent(); // uses MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        Intent settingsIntent = Util.getSettingAppIntent();

        //Intent[] hotIntents = new Intent[]{contactsIntent, phoneIntent, cameraIntent, settingsIntent};
        //String[] hotTitles = new String[]{"Contacts", "Phone", "Camera", "Settings"};

        applicationInfoList.add(makeAppFromIntent("Messages", messagesIntent, "hotseat", 0, 0, 0));
        applicationInfoList.add(makeAppFromIntent("Camera", cameraIntent, "hotseat", 0, 1, 0));
        applicationInfoList.add(makeAppFromIntent("Settings", settingsIntent, "hotseat", 0, 2, 0));

        /*for (int i = 0; i < hotIntents.length; i++) {
            Intent intent = hotIntents[i];
            if (intent != null) {
                // verify it resolves (Settings normally will)
                ResolveInfo ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
                if (ri != null) {
                    viewModel.insertApp(makeAppFromIntent(hotTitles[i], intent, "hotseat", 0, i, 0));
                    Log.i(TAG, "Inserted hotseat item: " + hotTitles[i]);
                } else {
                    Log.i(TAG, "Hotseat intent not resolved for: " + hotTitles[i]);
                }
            } else {
                Log.i(TAG, "Hotseat intent null for: " + hotTitles[i]);
            }
        }*/
    }

    private ApplicationInfo makeAppFromIntent(String title, Intent intent, String container, int screen, int cellX, int cellY) {
        ApplicationInfo ai = new ApplicationInfo();
        ai.title = title;
        ai.intent = intent;
        ai.container = container;
        ai.screen = screen;
        ai.cellX = cellX;
        ai.cellY = cellY;
        return ai;
    }



    @Override
    public void onBackPressed() {
        if (allApps.isShownPanel()) {
            allApps.hide();
            return;
        }
        super.onBackPressed();
    }
}
