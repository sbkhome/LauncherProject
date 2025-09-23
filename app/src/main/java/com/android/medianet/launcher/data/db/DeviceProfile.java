package com.android.medianet.launcher.data.db;

import com.android.medianet.launcher.LauncherApplication;
import com.android.medianet.launcher.presentation.views.layouts.CellLayout;

public class DeviceProfile {

    public static float iconScale = 0f;
    public static int iconSizePx = 0;
    public static int iconTextSizePx = 0;
    public static int iconDrawablePaddingPx = 0;
    private static final int mIconDrawablePaddingOriginalPx = 0;
    public static boolean iconCenterVertically = false;

    public static float cellScaleToFit = 0f;
    public static int cellWidthPx = 0;
    public static int cellHeightPx = 0;
    public static int workspaceCellPaddingXPx = 0;

    public static int cellYPaddingPx = -1;

    // Folder
    public static final int numFolderRows = 0;
    public static final int numFolderColumns = 0;
    public static final float folderLabelTextScale = 0f;


    public int folderCellHeightPx = 100;

    public static int calculateCellWidth(int width, int gap, int countCell) {
        if (countCell <= 0) {
            return width;
        } else {
            return ((width + gap) / countCell) - gap;
        }
    }

    public static int calculateCellHeight(int width, int gap, int countCell) {
        if (countCell <= 0) {
            return width;
        } else {
            return ((width + gap) / countCell) - gap;
        }
    }

    public int getCellContentHeight(int containerType) {
        switch (containerType) {
            case CellLayout.WORKSPACE:
                return cellHeightPx;
            case CellLayout.FOLDER:
                return folderCellHeightPx;
            case CellLayout.HOTSEAT:
                // The hotseat is the only container where the cell height is going to be
                // different from the content within that cell.
                return iconSizePx;
            default:
                // ??
                return 0;
        }
    }
    public static DeviceProfile getDeviceProfile(){
        return LauncherApplication.Companion.getDeviceProfile();
    }

}
//////////


