package com.android.medianet.launcher;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;



import java.util.ArrayList;
import java.util.List;

/**
 * Main Launcher activity
 */
public class Launcher extends AppCompatActivity {

    private static final String TAG = "BINOD_Launcher";

    private DragLayer dragLayer;
    private Workspace workspace;
    private Hotseat hotseat;
    private TextView searchText;
    private AllAppsContainerView allApps;
    private LauncherViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG , "onCreate() called");

        temp();
        setContentView(R.layout.launcher);

        dragLayer = findViewById(R.id.drag_layer);
        workspace = findViewById(R.id.workspace);
        hotseat = findViewById(R.id.hotseat);
        searchText = findViewById(R.id.search_text);
        allApps = findViewById(R.id.all_apps_container);

        viewModel = new ViewModelProvider(this).get(LauncherViewModel.class);

        // create two pages
        for (int i = 0; i < 2; i++) {
            CellLayout page = new CellLayout(this);
            workspace.addPage(page);
        }

        // first-run defaults if DB empty
        if (viewModel.isDbEmpty()) {
            Log.i(TAG , "onCreate() : db empty");
            populateDefaultsAndPersist();
        }

        // observe apps and bind
        viewModel.getAppsLive().observe(this, apps -> {
            Log.i(TAG , "onCreate() : callback received from viewmodel");
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

    public void temp(){
        // inside onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window w = getWindow();
            w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
        }

    }

    private void bindWorkspace(List<ApplicationInfo> apps) {

        Log.i(TAG , "bindWorkspace() : total apps="+apps.size());

        // clear pages
        for (int p = 0; p < workspace.getPageCount(); p++) {
            CellLayout page = workspace.getPageAt(p);
            page.removeAllViews();
        }

        // place workspace apps (container == workspace)
        for (ApplicationInfo app : apps) {
            if ("workspace".equals(app.container)) {
                int screen = Math.max(0, Math.min(app.screen, workspace.getPageCount()-1));
                CellLayout page = workspace.getPageAt(screen);
                BubbleTextView btv = new BubbleTextView(this);
                btv.applyFromApplicationInfo(app, false);

                // long-press popup only for homescreen
                btv.setOnLongClickListener(v -> {
                    showHomescreenPopup(app);
                    return true;
                });

                page.addViewToCell(btv, app.cellX, app.cellY);
            } else if ("folder".equals(app.container)) {
                // For minimal implementation, folder items are inside FolderIcon; we don't create separate DB items for folder placeholder
            }
        }

        // ensure Google folder exists at 0, last row if not present (visual; persisted initially)
        // persistence handled on first-run.
    }

    private void bindHotseat(List<ApplicationInfo> apps) {
        Log.i(TAG , "bindHotseat() : total apps="+apps.size());
        List<ApplicationInfo> hs = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if ("hotseat".equals(app.container)) hs.add(app);
        }
        hotseat.bindApps(hs);
    }

    private void bindAllApps(List<ApplicationInfo> apps) {
        Log.i(TAG , "bindAllApps() : total apps="+apps.size());
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
                // Try to open App Info if intent exists; otherwise open Play/app settings placeholder
                try {
                    if (app.intent != null && app.intent.getComponent() != null) {
                        Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
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

    private void populateDefaultsAndPersist() {
        // Place Contacts, Phone, Messages, Google Folder on last row (cellY=3) of screen 0
        String[] topTitles = new String[]{"Contacts", "Phone", "Messages", "Google Folder"};
        for (int i = 0; i < 4; i++) {
            ApplicationInfo ai = new ApplicationInfo();
            ai.title = topTitles[i];
            ai.container = "workspace";
            ai.screen = 0;
            ai.cellX = i;
            ai.cellY = 3; // last row
            viewModel.insertApp(ai);
        }

        // Hotseat defaults: Contacts, Phone, Settings, Camera
        String[] hot = new String[]{"Contacts", "Phone", "Settings", "Camera"};
        for (int i = 0; i < 4; i++) {
            ApplicationInfo ai = new ApplicationInfo();
            ai.title = hot[i];
            ai.container = "hotseat";
            ai.cellX = i;
            viewModel.insertApp(ai);
        }

        // Note: Google folder content is currently only visual (we created placeholder row entry above "Google Folder")
    }

    @Override
    public void onBackPressed() {
        if (allApps.isShownPanel()) {
            allApps.hide();
            return;
        }
        // otherwise behave normally (stay on homescreen)
        super.onBackPressed();
    }
}
