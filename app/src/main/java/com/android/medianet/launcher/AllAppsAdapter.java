package com.android.medianet.launcher;

import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AllAppsAdapter extends RecyclerView.Adapter<AllAppsAdapter.VH> {

    private List<ApplicationInfo> apps;

    public AllAppsAdapter(List<ApplicationInfo> apps) {
        this.apps = apps;
    }

    public void setApps(List<ApplicationInfo> list) {
        this.apps = list;
        notifyDataSetChanged();
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        BubbleTextView v = new BubbleTextView(parent.getContext());
        v.setClipToOutline(true); // safe drawing
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        ApplicationInfo ai = apps.get(position);
        holder.bubble.applyFromApplicationInfo(ai, false);

        // Disable long press
        holder.bubble.setOnLongClickListener(null);

        // Reduce memory usage by clearing background if not needed
        holder.bubble.setBackground(null);
    }

    @Override
    public int getItemCount() {
        return apps != null ? apps.size() : 0;
    }

    static class VH extends RecyclerView.ViewHolder {
        BubbleTextView bubble;

        VH(View v) {
            super(v);
            bubble = (BubbleTextView) v;
        }
    }
}
