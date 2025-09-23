package com.android.medianet.launcher.presentation.views.dragndrop;

import android.view.View;

public interface DragSource {

    void setDragController(DragController controller);

    void onDropCompleted(View target, boolean success);
}
