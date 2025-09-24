package com.android.medianet.launcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Launcher-style AllApps overlay view.
 * Hidden by default.
 */
public class AllAppsContainerView extends LinearLayout {

    private EditText searchBar;
    private RecyclerView recyclerView;
    private AllAppsAdapter adapter;

    public AllAppsContainerView(Context context) {
        super(context);
        init(context);
    }

    public AllAppsContainerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.all_apps_container, this, true);

        searchBar = findViewById(R.id.all_apps_search_text);
        recyclerView = findViewById(R.id.all_apps_recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(context, 4));

        adapter = new AllAppsAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        setVisibility(GONE);
    }

    public void setApps(List<ApplicationInfo> apps) {
        adapter.setApps(apps);
    }

    public void show() {
        setVisibility(VISIBLE);
        setTranslationY(0); // optional animation
    }

    public void hide() {
        setVisibility(GONE);
    }

    public boolean isShownPanel() {
        return getVisibility() == VISIBLE;
    }
}
