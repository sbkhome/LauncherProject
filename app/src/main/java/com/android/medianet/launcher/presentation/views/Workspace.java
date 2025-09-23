/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.medianet.launcher.presentation.views;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;


import com.android.medianet.launcher.LauncherApplication;
import com.android.medianet.launcher.data.db.DeviceProfile;
import com.android.medianet.launcher.data.model.ItemInfo;
import com.android.medianet.launcher.presentation.views.dragndrop.DragController;
import com.android.medianet.launcher.presentation.views.dragndrop.DragSource;
import com.android.medianet.launcher.presentation.views.dragndrop.DragView;
import com.android.medianet.launcher.presentation.views.layouts.CellInfo;
import com.android.medianet.launcher.presentation.views.layouts.CellLayout;
import com.android.medianet.launcher.presentation.views.layouts.ShortcutAndWidgetContainer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The workspace is a wide area with a wallpaper and a finite number of pages.
 * Each page contains a number of icons, folders or widgets the user can
 * interact with. A workspace is meant to be used with a fixed width only.
 *
 * @param <T> Class that extends View and PageIndicator
 */
public class Workspace extends PagedView {

    /**
     * The value that {@link #mTransitionProgress} must be greater than for
     * {@link #transitionStateShouldAllowDrop()} to return true.
     */
    private static final float ALLOW_DROP_TRANSITION_PROGRESS = 0.25f;

    /**
     * The value that {@link #mTransitionProgress} must be greater than for
     * {@link #isFinishedSwitchingState()} ()} to return true.
     */
    private static final float FINISHED_SWITCHING_STATE_TRANSITION_PROGRESS = 0.5f;

    private static final float SIGNIFICANT_MOVE_SCREEN_WIDTH_PERCENTAGE = 0.15f;

    private static final boolean ENFORCE_DRAG_EVENT_ORDER = false;

    private static final int ADJACENT_SCREEN_DROP_DURATION = 300;

    public static final int DEFAULT_PAGE = 0;

    private final int mAllAppsIconSize;

    private LayoutTransition mLayoutTransition;
    final WallpaperManager mWallpaperManager;

    protected ShortcutAndWidgetContainer mDragSourceInternal;

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public final List<CellLayout> mWorkspaceScreens = new ArrayList<>();

    final IntArray mScreenOrder = new IntArray();
    boolean mDeferRemoveExtraEmptyScreen = false;

    /**
     * CellInfo for the cell that is currently being dragged
     */
    protected CellInfo mDragInfo;

    /**
     * Target drop area calculated during last acceptDrop call.
     */
    @Thunk
    int[] mTargetCell = new int[2];
    private int mDragOverX = -1;
    private int mDragOverY = -1;

    /**
     * The CellLayout that is currently being dragged over
     */
    @Thunk
    CellLayout mDragTargetLayout = null;
    /**
     * The CellLayout that we will show as highlighted
     */
    private CellLayout mDragOverlappingLayout = null;

    /**
     * The CellLayout which will be dropped to
     */
    private CellLayout mDropToLayout = null;

    @Thunk
    final Launcher mLauncher;
    @Thunk
    DragController mDragController;

    protected final int[] mTempXY = new int[2];
    private final float[] mTempFXY = new float[2];
    private final Rect mTempRect = new Rect();
    @Thunk
    float[] mDragViewVisualCenter = new float[2];

    private SpringLoadedDragController mSpringLoadedDragController;

    private boolean mIsSwitchingState = false;

    boolean mChildrenLayersEnabled = true;

    private boolean mStripScreensOnPageStopMoving = false;
    public boolean mHasOnLayoutBeenCalled = false;

    private boolean mWorkspaceFadeInAdjacentScreens;

    final WallpaperOffsetInterpolator mWallpaperOffset;
    private boolean mUnlockWallpaperFromDefaultPageOnLayout;

    public static final int REORDER_TIMEOUT = 650;
    protected final Alarm mReorderAlarm = new Alarm();
    private PreviewBackground mFolderCreateBg;
    /** The underlying view that we are dragging something over. */
    private View mDragOverView = null;
    private FolderIcon mDragOverFolderIcon = null;
    private boolean mCreateUserFolderOnDrop = false;
    private boolean mAddToExistingFolderOnDrop = false;

    // Variables relating to touch disambiguation (scrolling workspace vs. scrolling a widget)
    private float mXDown;
    private float mYDown;
    private View mFirstPagePinnedItem;
    private boolean mIsEventOverFirstPagePinnedItem;

    final static float START_DAMPING_TOUCH_SLOP_ANGLE = (float) Math.PI / 6;
    final static float MAX_SWIPE_ANGLE = (float) Math.PI / 3;
    final static float TOUCH_SLOP_DAMPING_FACTOR = 4;

    // Relating to the animation of items being dropped externally
    public static final int ANIMATE_INTO_POSITION_AND_DISAPPEAR = 0;
    public static final int ANIMATE_INTO_POSITION_AND_REMAIN = 1;
    public static final int ANIMATE_INTO_POSITION_AND_RESIZE = 2;
    public static final int COMPLETE_TWO_STAGE_WIDGET_DROP_ANIMATION = 3;
    public static final int CANCEL_TWO_STAGE_WIDGET_DROP_ANIMATION = 4;

    // Related to dragging, folder creation and reordering
    private static final int DRAG_MODE_NONE = 0;
    private static final int DRAG_MODE_CREATE_FOLDER = 1;
    private static final int DRAG_MODE_ADD_TO_FOLDER = 2;
    private static final int DRAG_MODE_REORDER = 3;
    protected int mDragMode = DRAG_MODE_NONE;
    @Thunk
    int mLastReorderX = -1;
    @Thunk
    int mLastReorderY = -1;

    private SparseArray<Parcelable> mSavedStates;
    private final IntArray mRestoredPages = new IntArray();

    private float mCurrentScale;
    private float mTransitionProgress;

    // State related to Launcher Overlay
    private OverlayEdgeEffect mOverlayEdgeEffect;
    private boolean mOverlayShown = false;
    private float mOverlayProgress; // 1 -> overlay completely visible, 0 -> home visible
    private final List<LauncherOverlayCallbacks> mOverlayCallbacks = new ArrayList<>();

    private boolean mForceDrawAdjacentPages = false;

    // Handles workspace state transitions
    private final WorkspaceStateTransitionAnimation mStateTransitionAnimation;

    private final StatsLogManager mStatsLogManager;

    private final MSDLPlayerWrapper mMSDLPlayerWrapper;

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs   The attributes set containing the Workspace's customization values.
     */
    public Workspace(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }


    public Workspace(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(context);
        mWallpaperManager = WallpaperManager.getInstance(context);
        mAllAppsIconSize = 100;//mLauncher.getDeviceProfile().allAppsIconSizePx;
        //mWallpaperOffset = new WallpaperOffsetInterpolator(this);

        initWorkspace();

        //setOnTouchListener(new WorkspaceTouchListener(mLauncher, this));
    }



    private void setPageIndicatorInset() {
    }

    private void updateCellLayoutMeasures() {
        Rect padding = mLauncher.getDeviceProfile().cellLayoutPaddingPx;
        mWorkspaceScreens.forEach(cellLayout -> {
            cellLayout.setPadding(padding.left, padding.top, padding.right, padding.bottom);
            cellLayout.setSpaceBetweenCellLayoutsPx(getPageSpacing() / 4);
        });
    }


    /**
     * Estimates the size of an item using spans: hSpan, vSpan.
     *
     * @return MAX_VALUE for each dimension if unsuccessful.
     */
    public int[] estimateItemSize(ItemInfo itemInfo) {
        int[] size = new int[2];
        if (getChildCount() > 0) {
            // Use the first page to estimate the child position
            CellLayout cl = (CellLayout) getChildAt(0);
            boolean isWidget = itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;

            Rect r = estimateItemPosition(cl, 0, 0, itemInfo.spanX, itemInfo.spanY);

            float scale = 1;

            size[0] = r.width();
            size[1] = r.height();

            if (isWidget) {
                size[0] /= scale;
                size[1] /= scale;
            }
            return size;
        } else {
            size[0] = Integer.MAX_VALUE;
            size[1] = Integer.MAX_VALUE;
            return size;
        }
    }

    public float getWallpaperOffsetForCenterPage() {
        return getWallpaperOffsetForPage(getPageNearestToCenterOfScreen());
    }

    private float getWallpaperOffsetForPage(int page) {
        int pageScroll = getScrollForPage(page);
        return mWallpaperOffset.wallpaperOffsetForScroll(pageScroll);
    }

    /**
     * Returns the number of pages used for the wallpaper parallax.
     */
    public int getNumPagesForWallpaperParallax() {
        return mWallpaperOffset.getNumPagesForWallpaperParallax();
    }

    public Rect estimateItemPosition(CellLayout cl, int hCell, int vCell, int hSpan, int vSpan) {
        Rect r = new Rect();
        cl.cellToRect(hCell, vCell, hSpan, vSpan, r);
        return r;
    }

    @Override
    public void onDragStart(DragObject dragObject, DragOptions options) {
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragStart", 0, 0);
        }

        if (mDragInfo != null && mDragInfo.cell != null) {
            CellLayout layout = (CellLayout) (mDragInfo.cell instanceof LauncherAppWidgetHostView
                    ? dragObject.dragView.getContentViewParent().getParent()
                    : mDragInfo.cell.getParent().getParent());
            layout.markCellsAsUnoccupiedForView(mDragInfo.cell);
        }

        updateChildrenLayersEnabled();

        // Do not add a new page if it is a accessible drag which was not started by the workspace.
        // We do not support accessibility drag from other sources and instead provide a direct
        // action for move/add to homescreen.
        // When a accessible drag is started by the folder, we only allow rearranging withing the
        // folder.
        boolean addNewPage = !(options.isAccessibleDrag && dragObject.dragSource != this);
        if (addNewPage) {
            mDeferRemoveExtraEmptyScreen = false;
            addExtraEmptyScreenOnDrag(dragObject);

            if (dragObject.dragInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                    && dragObject.dragSource != this) {
                // When dragging a widget from different source, move to a page which has
                // enough space to place this widget (after rearranging/resizing). We special case
                // widgets as they cannot be placed inside a folder.
                // Start at the current page and search right (on LTR) until finding a page with
                // enough space. Since an empty screen is the furthest right, a page must be found.
                int currentPage = getDestinationPage();
                for (int pageIndex = currentPage; pageIndex < getPageCount(); pageIndex++) {
                    CellLayout page = (CellLayout) getPageAt(pageIndex);
                    if (page.hasReorderSolution(dragObject.dragInfo)) {
                        setCurrentPage(pageIndex);
                        break;
                    }
                }
            }
        }

        if (!mLauncher.isInState(EDIT_MODE)) {
            mLauncher.getStateManager().goToState(SPRING_LOADED);
        }
        mStatsLogManager.logger().withItemInfo(dragObject.dragInfo)
                .withInstanceId(dragObject.logInstanceId)
                .log(LauncherEvent.LAUNCHER_ITEM_DRAG_STARTED);
    }

    private boolean isTwoPanelEnabled() {
        return !FOLDABLE_SINGLE_PAGE.get() && mLauncher.mDeviceProfile.isTwoPanels;
    }

    public void deferRemoveExtraEmptyScreen() {
        mDeferRemoveExtraEmptyScreen = true;
    }

    @Override
    public void onDragEnd() {
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragEnd", 0, 0);
        }

        updateChildrenLayersEnabled();
        StateManager<LauncherState, Launcher> stateManager = mLauncher.getStateManager();
        stateManager.addStateListener(new StateManager.StateListener<LauncherState>() {
            @Override
            public void onStateTransitionComplete(LauncherState finalState) {
                if (finalState == NORMAL) {
                    if (!mDeferRemoveExtraEmptyScreen) {
                        removeExtraEmptyScreen(true /* stripEmptyScreens */);
                    }
                    stateManager.removeStateListener(this);
                }
            }
        });

        mDragInfo = null;
        mDragSourceInternal = null;
    }

    /**
     * Initializes various states for this workspace.
     */
    protected void initWorkspace() {
        mCurrentPage = DEFAULT_PAGE;
        setClipToPadding(false);

        setupLayoutTransition();

        // Set the wallpaper dimensions when Launcher starts up
        setWallpaperDimension();
    }

    private void setupLayoutTransition() {
        // We want to show layout transitions when pages are deleted, to close the gap.
        mLayoutTransition = new LayoutTransition();

        mLayoutTransition.enableTransitionType(LayoutTransition.DISAPPEARING);
        mLayoutTransition.enableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        // Change the interpolators such that the fade animation plays before the move animation.
        // This prevents empty adjacent pages to overlay during animation
        mLayoutTransition.setInterpolator(LayoutTransition.DISAPPEARING,
                Interpolators.clampToProgress(Interpolators.ACCELERATE_DECELERATE, 0, 0.5f));
        mLayoutTransition.setInterpolator(LayoutTransition.CHANGE_DISAPPEARING,
                Interpolators.clampToProgress(Interpolators.ACCELERATE_DECELERATE, 0.5f, 1));

        mLayoutTransition.disableTransitionType(LayoutTransition.APPEARING);
        mLayoutTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        setLayoutTransition(mLayoutTransition);
    }

    void enableLayoutTransitions() {
        setLayoutTransition(mLayoutTransition);
    }

    void disableLayoutTransitions() {
        setLayoutTransition(null);
    }

    @Override
    public void onViewAdded(View child) {
        if (!(child instanceof CellLayout)) {
            throw new IllegalArgumentException("A Workspace can only have CellLayout children.");
        }
        CellLayout cl = ((CellLayout) child);
        cl.setOnInterceptTouchListener(this);
        cl.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        super.onViewAdded(child);
    }

    /**
     * Initializes and binds the first page
     */
    public void bindAndInitFirstWorkspaceScreen() {
        if ((!FeatureFlags.QSB_ON_FIRST_SCREEN
                || !mLauncher.getIsFirstPagePinnedItemEnabled())
                || SHOULD_SHOW_FIRST_PAGE_WIDGET) {
            mFirstPagePinnedItem = null;
            return;
        }

        // Add the first page
        CellLayout firstPage = insertNewWorkspaceScreen(Workspace.FIRST_SCREEN_ID, getChildCount());
        if (mFirstPagePinnedItem == null) {
            // In transposed layout, we add the first page pinned widget in the Grid.
            // As workspace does not touch the edges, we do not need a full
            // width first page pinned item.
            mFirstPagePinnedItem = LayoutInflater.from(getContext())
                    .inflate(R.layout.search_container_workspace, firstPage, false);
        }

        int cellHSpan = mLauncher.getDeviceProfile().inv.numSearchContainerColumns;
        CellLayoutLayoutParams lp = new CellLayoutLayoutParams(0, 0, cellHSpan, 1);
        lp.canReorder = false;
        if (!firstPage.addViewToCellLayout(
                mFirstPagePinnedItem, 0, R.id.search_container_workspace, lp, true)) {
            Log.e(TAG, "Failed to add to item at (0, 0) to CellLayout");
            mFirstPagePinnedItem = null;
        }
    }

    public void removeAllWorkspaceScreens() {
        // Disable all layout transitions before removing all pages to ensure that we don't get the
        // transition animations competing with us changing the scroll when we add pages
        disableLayoutTransitions();

        // Recycle the first page pinned item
        if (mFirstPagePinnedItem != null) {
            ((ViewGroup) mFirstPagePinnedItem.getParent()).removeView(mFirstPagePinnedItem);
        }

        // Remove the pages and clear the screen models
        removeFolderListeners();
        removeAllViews();
        mScreenOrder.clear();
        mWorkspaceScreens.clear();

        // Ensure that the first page is always present
        if (!enableSmartspaceRemovalToggle()) {
            bindAndInitFirstWorkspaceScreen();
        }

        // Remove any deferred refresh callbacks
        mLauncher.mHandler.removeCallbacksAndMessages(DeferredWidgetRefresh.class);

        // Re-enable the layout transitions
        enableLayoutTransitions();
    }

    public void insertNewWorkspaceScreenBeforeEmptyScreen(int screenId) {
        // Find the index to insert this view into.  If the empty screen exists, then
        // insert it before that.
        int insertIndex = mScreenOrder.indexOf(EXTRA_EMPTY_SCREEN_ID);
        if (insertIndex < 0) {
            insertIndex = mScreenOrder.size();
        }
        insertNewWorkspaceScreen(screenId, insertIndex);
    }

    public void insertNewWorkspaceScreen(int screenId) {
        insertNewWorkspaceScreen(screenId, getChildCount());
    }

    public CellLayout insertNewWorkspaceScreen(int screenId, int insertIndex) {
        if (mWorkspaceScreens.containsKey(screenId)) {
            throw new RuntimeException("Screen id " + screenId + " already exists!");
        }

        CellLayout newScreen;

        newScreen = (CellLayout) LayoutInflater.from(getContext()).inflate(
                R.layout.workspace_screen, this, false /* attachToRoot */);

        //newScreen.setCellLayoutContainer(this);

        mWorkspaceScreens.put(screenId, newScreen);
        mScreenOrder.add(insertIndex, screenId);
        addView(newScreen, insertIndex);
        mStateTransitionAnimation.applyChildState(
                mLauncher.getStateManager().getState(), newScreen, insertIndex);

        updatePageScrollValues();
        updateCellLayoutMeasures();
        return newScreen;
    }

    private void addExtraEmptyScreenOnDrag(DragObject dragObject) {
        boolean lastChildOnScreen = false;
        boolean childOnFinalScreen = false;

        if (mDragSourceInternal != null) {
            int dragSourceChildCount = mDragSourceInternal.getChildCount();

            // If the icon was dragged from Hotseat, there is no page pair
            if (isTwoPanelEnabled() && !(mDragSourceInternal.getParent() instanceof Hotseat)) {
                int pagePairScreenId = getScreenPair(getCellPosMapper().mapModelToPresenter(
                        dragObject.dragInfo).screenId);
                CellLayout pagePair = mWorkspaceScreens.get(pagePairScreenId);
                dragSourceChildCount += pagePair.getShortcutsAndWidgets().getChildCount();
            }

            // When the drag view content is a LauncherAppWidgetHostView, we should increment the
            // drag source child count by 1 because the widget in drag has been detached from its
            // original parent, ShortcutAndWidgetContainer, and reattached to the DragView.
            if (dragObject.dragView.getContentView() instanceof LauncherAppWidgetHostView) {
                dragSourceChildCount++;
            }

            if (dragSourceChildCount == 1) {
                lastChildOnScreen = true;
            }
            CellLayout cl = (CellLayout) mDragSourceInternal.getParent();
            if (!FOLDABLE_SINGLE_PAGE.get() && getLeftmostVisiblePageForIndex(indexOfChild(cl))
                    == getLeftmostVisiblePageForIndex(getPageCount() - 1)) {
                childOnFinalScreen = true;
            }
        }

        // If this is the last item on the final screen
        if (lastChildOnScreen && childOnFinalScreen) {
            return;
        }

        forEachExtraEmptyPageId(extraEmptyPageId -> {
            if (!mWorkspaceScreens.containsKey(extraEmptyPageId)) {
                insertNewWorkspaceScreen(extraEmptyPageId);
            }
        });
    }


    /**
     * Returns if the given screenId is already in the Workspace
     */
    public boolean containsScreenId(int screenId) {
        return this.mWorkspaceScreens.containsKey(screenId);
    }

    /**
     * Inserts extra empty pages to the end of the existing workspaces.
     * Usually we add one extra empty screen, but when two panel home is enabled we add
     * two extra screens.
     **/
    public void addExtraEmptyScreens() {
        forEachExtraEmptyPageId(extraEmptyPageId -> {
            if (!mWorkspaceScreens.containsKey(extraEmptyPageId)) {
                insertNewWorkspaceScreen(extraEmptyPageId);
            }
        });
    }

    /**
     * Calls the consumer with all the necessary extra empty page IDs.
     * On a normal one panel Workspace that means only EXTRA_EMPTY_SCREEN_ID,
     * but in a two panel scenario this also includes EXTRA_EMPTY_SCREEN_SECOND_ID.
     */
    private void forEachExtraEmptyPageId(Consumer<Integer> callback) {
        callback.accept(EXTRA_EMPTY_SCREEN_ID);
        if (isTwoPanelEnabled()) {
            callback.accept(EXTRA_EMPTY_SCREEN_SECOND_ID);
        }
    }

    /**
     * If two panel home is enabled we convert the last two screens that are visible at the same
     * time. In other cases we only convert the last page.
     */
    private void convertFinalScreenToEmptyScreenIfNecessary() {
        if (mLauncher.isWorkspaceLoading()) {
            // Invalid and dangerous operation if workspace is loading
            return;
        }

        int panelCount = getPanelCount();
        if (hasExtraEmptyScreens() || mScreenOrder.size() < panelCount) {
            return;
        }

        SparseArray<CellLayout> finalScreens = new SparseArray<>();

        int pageCount = mScreenOrder.size();
        // First we add the last page(s) to the finalScreens collection. The number of final pages
        // depends on the panel count.
        for (int pageIndex = pageCount - panelCount; pageIndex < pageCount; pageIndex++) {
            int screenId = mScreenOrder.get(pageIndex);
            CellLayout screen = mWorkspaceScreens.get(screenId);
            if (screen == null || screen.getShortcutsAndWidgets().getChildCount() != 0
                    || screen.isDropPending()) {
                // Final screen doesn't exist or it isn't empty or there's a pending drop
                return;
            }
            finalScreens.append(screenId, screen);
        }

        // Then we remove the final screens from the collections (but not from the view hierarchy)
        // and we store them as extra empty screens.
        for (int i = 0; i < finalScreens.size(); i++) {
            int screenId = finalScreens.keyAt(i);

            // We don't want to remove the first screen even if it's empty because that's where
            // first page pinned item would go if it gets turned back on.
            if (enableSmartspaceRemovalToggle() && screenId == FIRST_SCREEN_ID) {
                continue;
            }

            CellLayout screen = finalScreens.get(screenId);

            mWorkspaceScreens.remove(screenId);
            mScreenOrder.removeValue(screenId);

            int newScreenId = mWorkspaceScreens.containsKey(EXTRA_EMPTY_SCREEN_ID)
                    ? EXTRA_EMPTY_SCREEN_SECOND_ID : EXTRA_EMPTY_SCREEN_ID;
            mWorkspaceScreens.put(newScreenId, screen);
            mScreenOrder.add(newScreenId);
        }
    }

    public void removeExtraEmptyScreen(boolean stripEmptyScreens) {
        removeExtraEmptyScreenDelayed(0, stripEmptyScreens, null);
    }

    /**
     * The purpose of this method is to remove empty pages from Workspace.
     * Empty page(s) from the end of mWorkspaceScreens will always be removed. The pages with
     * ID = Workspace.EXTRA_EMPTY_SCREEN_IDS will be removed if there are other non-empty pages.
     * If there are no more non-empty pages left, extra empty page(s) will either stay or get added.
     * <p>
     * If stripEmptyScreens is true, all empty pages (not just the ones on the end) will be removed
     * from the Workspace, and if there are no more pages left then extra empty page(s) will be
     * added.
     * <p>
     * The number of extra empty pages is equal to what getPanelCount() returns.
     * <p>
     * After the method returns the possible pages are:
     * stripEmptyScreens = true : [non-empty pages, extra empty page(s) alone]
     * stripEmptyScreens = false : [non-empty pages, empty pages (not in the end),
     * extra empty page(s) alone]
     */
    public void removeExtraEmptyScreenDelayed(
            int delay, boolean stripEmptyScreens, Runnable onComplete) {
        if (mLauncher.isWorkspaceLoading()) {
            // Don't strip empty screens if the workspace is still loading
            return;
        }

        if (delay > 0) {
            postDelayed(
                    () -> removeExtraEmptyScreenDelayed(0, stripEmptyScreens, onComplete), delay);
            return;
        }

        // First we convert the last page to an extra page if the last page is empty
        // and we don't already have an extra page.
        convertFinalScreenToEmptyScreenIfNecessary();
        // Then we remove the extra page(s) if they are not the only pages left in Workspace.
        if (hasExtraEmptyScreens()) {
            forEachExtraEmptyPageId(extraEmptyPageId -> {
                removeView(mWorkspaceScreens.get(extraEmptyPageId));
                mWorkspaceScreens.remove(extraEmptyPageId);
                mScreenOrder.removeValue(extraEmptyPageId);
            });

            setCurrentPage(getNextPage());

            // Update the page indicator to reflect the removed page.
            showPageIndicatorAtCurrentScroll();
        }

        if (stripEmptyScreens) {
            // This will remove all empty pages from the Workspace. If there are no more pages left,
            // it will add extra page(s) so that users can put items on at least one page.
            stripEmptyScreens();
        }

        if (onComplete != null) {
            onComplete.run();
        }
    }

    public boolean hasExtraEmptyScreens() {
        return mWorkspaceScreens.containsKey(EXTRA_EMPTY_SCREEN_ID)
                && getChildCount() > getPanelCount()
                && (!isTwoPanelEnabled()
                || mWorkspaceScreens.containsKey(EXTRA_EMPTY_SCREEN_SECOND_ID));
    }

    /**
     * Commits the extra empty pages then returns the screen ids of those new screens.
     * Usually there's only one extra empty screen, but when two panel home is enabled we commit
     * two extra screens.
     * <p>
     * Returns an empty IntSet in case we cannot commit any new screens.
     */
    public IntSet commitExtraEmptyScreens() {
        if (mLauncher.isWorkspaceLoading()) {
            // Invalid and dangerous operation if workspace is loading
            return new IntSet();
        }

        IntSet extraEmptyPageIds = new IntSet();
        forEachExtraEmptyPageId(extraEmptyPageId ->
                extraEmptyPageIds.add(commitExtraEmptyScreen(extraEmptyPageId)));

        return extraEmptyPageIds;
    }

    private int commitExtraEmptyScreen(int emptyScreenId) {
        CellLayout cl = mWorkspaceScreens.get(emptyScreenId);
        mWorkspaceScreens.remove(emptyScreenId);
        mScreenOrder.removeValue(emptyScreenId);

        int newScreenId = LauncherAppState.getInstance(getContext())
                .getModel().getModelDbController().getNewScreenId();
        // Launcher database isn't aware of empty pages that are already bound, so we need to
        // skip those IDs manually.
        while (mWorkspaceScreens.containsKey(newScreenId)) {
            newScreenId++;
        }

        mWorkspaceScreens.put(newScreenId, cl);
        mScreenOrder.add(newScreenId);

        return newScreenId;
    }

    @Override
    public Hotseat getHotseat() {
        return mLauncher.getHotseat();
    }

    @Override
    public void onAddDropTarget(DropTarget target) {
        mDragController.addDropTarget(target);
    }

    @Override
    public CellLayout getScreenWithId(int screenId) {
        return mWorkspaceScreens.get(screenId);
    }

    @Override
    public int getCellLayoutId(CellLayout layout) {
        int index = mWorkspaceScreens.indexOfValue(layout);
        if (index != -1) {
            return mWorkspaceScreens.keyAt(index);
        }
        return -1;
    }

    public int getPageIndexForScreenId(int screenId) {
        return indexOfChild(mWorkspaceScreens.get(screenId));
    }

    @Override
    public int getCellLayoutIndex(CellLayout cellLayout) {
        return indexOfChild(mWorkspaceScreens.get(getCellLayoutId(cellLayout)));
    }

    @Override
    public int getPanelCount() {
        return isTwoPanelEnabled() ? 2 : super.getPanelCount();
    }

    public IntSet getCurrentPageScreenIds() {
        return IntSet.wrap(getScreenIdForPageIndex(getCurrentPage()));
    }

    public int getScreenIdForPageIndex(int index) {
        if (0 <= index && index < mScreenOrder.size()) {
            return mScreenOrder.get(index);
        }
        return -1;
    }

    public IntArray getScreenOrder() {
        return mScreenOrder;
    }

    /**
     * Returns the screen ID of a page that is shown together with the given page screen ID when the
     * two panel UI is enabled.
     */
    public int getScreenPair(int screenId) {
        if (screenId == EXTRA_EMPTY_SCREEN_ID) {
            return EXTRA_EMPTY_SCREEN_SECOND_ID;
        } else if (screenId == EXTRA_EMPTY_SCREEN_SECOND_ID) {
            return EXTRA_EMPTY_SCREEN_ID;
        } else if (screenId % 2 == 0) {
            return screenId + 1;
        } else {
            return screenId - 1;
        }
    }

    /**
     * Returns {@link CellLayout} that is shown together with the given {@link CellLayout} when the
     * two panel UI is enabled.
     */
    @Nullable
    public CellLayout getScreenPair(CellLayout cellLayout) {
        if (!isTwoPanelEnabled()) {
            return null;
        }
        int screenId = getCellLayoutId(cellLayout);
        if (screenId == -1) {
            return null;
        }
        return getScreenWithId(getScreenPair(screenId));
    }

    public void stripEmptyScreens() {
        if (mLauncher.isWorkspaceLoading()) {
            // Don't strip empty screens if the workspace is still loading.
            // This is dangerous and can result in data loss.
            return;
        }

        if (isPageInTransition()) {
            mStripScreensOnPageStopMoving = true;
            return;
        }

        int currentPage = getNextPage();
        IntArray removeScreens = new IntArray();
        int total = mWorkspaceScreens.size();
        for (int i = 0; i < total; i++) {
            int id = mWorkspaceScreens.keyAt(i);
            CellLayout cl = mWorkspaceScreens.valueAt(i);
            // FIRST_SCREEN_ID can never be removed.
            if (((!FeatureFlags.QSB_ON_FIRST_SCREEN
                    || SHOULD_SHOW_FIRST_PAGE_WIDGET)
                    || id > FIRST_SCREEN_ID)
                    && cl.getShortcutsAndWidgets().getChildCount() == 0) {
                removeScreens.add(id);
            }
        }

        // When two panel home is enabled we only remove an empty page if both visible pages are
        // empty.
        if (isTwoPanelEnabled()) {
            // We go through all the pages that were marked as removable and check their page pair
            Iterator<Integer> removeScreensIterator = removeScreens.iterator();
            while (removeScreensIterator.hasNext()) {
                int pageToRemove = removeScreensIterator.next();
                int pagePair = getScreenPair(pageToRemove);
                if (!removeScreens.contains(pagePair)) {
                    // The page pair isn't empty so we want to remove the current page from the
                    // removable pages' collection
                    removeScreensIterator.remove();
                }
            }
        }

        // We enforce at least one page (two pages on two panel home) to add new items to.
        // In the case that we remove the last such screen(s), we convert the last screen(s)
        // to the empty screen(s)
        int minScreens = getPanelCount();

        int pageShift = 0;
        for (int i = 0; i < removeScreens.size(); i++) {
            int id = removeScreens.get(i);
            CellLayout cl = mWorkspaceScreens.get(id);
            mWorkspaceScreens.remove(id);
            mScreenOrder.removeValue(id);

            if (getChildCount() > minScreens) {
                // If this isn't the last page, just remove it
                if (indexOfChild(cl) < currentPage) {
                    pageShift++;
                }
                removeView(cl);
            } else {
                // The last page(s) should be converted into extra empty page(s)
                int extraScreenId = isTwoPanelEnabled() && id % 2 == 1
                        // This is the right panel in a two panel scenario
                        ? EXTRA_EMPTY_SCREEN_SECOND_ID
                        // This is either the last screen in a one panel scenario, or the left panel
                        // in a two panel scenario when there are only two empty pages left
                        : EXTRA_EMPTY_SCREEN_ID;
                mWorkspaceScreens.put(extraScreenId, cl);
                mScreenOrder.add(extraScreenId);
            }
        }

        if (pageShift >= 0) {
            setCurrentPage(currentPage - pageShift);
        }
    }

    /**
     * Needed here because launcher has a fullscreen exclusion rect and doesn't pilfer the pointers.
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isTrackpadMultiFingerSwipe(ev)) {
            return false;
        }
        return super.onInterceptTouchEvent(ev);
    }

    /**
     * Needed here because launcher has a fullscreen exclusion rect and doesn't pilfer the pointers.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isTrackpadMultiFingerSwipe(ev)) {
            return false;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected void onDisallowSwipeToMinusOnePage() {
        mLauncher.getOverlayManager().onDisallowSwipeToMinusOnePage();
    }

    /**
     * Called directly from a CellLayout (not by the framework), after we've been added as a
     * listener via setOnInterceptTouchEventListener(). This allows us to tell the CellLayout
     * that it should intercept touch events, which is not something that is normally supported.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return shouldConsumeTouch(v);
    }

    private boolean shouldConsumeTouch(View v) {
        return !workspaceIconsCanBeDragged()
                || (!workspaceInModalState() && !isVisible(v));
    }

    public boolean isSwitchingState() {
        return mIsSwitchingState;
    }

    /**
     * This differs from isSwitchingState in that we take into account how far the transition
     * has completed.
     */
    public boolean isFinishedSwitchingState() {
        return !mIsSwitchingState
                || (mTransitionProgress > FINISHED_SWITCHING_STATE_TRANSITION_PROGRESS);
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        if (workspaceInModalState() || !isFinishedSwitchingState()) {
            // when the home screens are shrunken, shouldn't allow side-scrolling
            return false;
        }
        return super.dispatchUnhandledMove(focused, direction);
    }

    @Override
    protected void updateIsBeingDraggedOnTouchDown(MotionEvent ev) {
        super.updateIsBeingDraggedOnTouchDown(ev);

        mXDown = ev.getX();
        mYDown = ev.getY();
        if (mFirstPagePinnedItem != null) {
            final float[] tempFXY = new float[2];
            tempFXY[0] = mXDown;
            tempFXY[1] = mYDown;
            Utilities.mapCoordInSelfToDescendant(mFirstPagePinnedItem, this, tempFXY);
            mIsEventOverFirstPagePinnedItem = mFirstPagePinnedItem.getLeft() <= tempFXY[0]
                    && mFirstPagePinnedItem.getRight() >= tempFXY[0]
                    && mFirstPagePinnedItem.getTop() <= tempFXY[1]
                    && mFirstPagePinnedItem.getBottom() >= tempFXY[1];
        } else {
            mIsEventOverFirstPagePinnedItem = false;
        }
    }

    @Override
    protected void determineScrollingStart(MotionEvent ev) {
        if (!isFinishedSwitchingState() || mIsEventOverFirstPagePinnedItem) return;

        float deltaX = ev.getX() - mXDown;
        float absDeltaX = Math.abs(deltaX);
        float absDeltaY = Math.abs(ev.getY() - mYDown);

        if (Float.compare(absDeltaX, 0f) == 0) return;

        float slope = absDeltaY / absDeltaX;
        float theta = (float) Math.atan(slope);

        if (absDeltaX > mTouchSlop || absDeltaY > mTouchSlop) {
            cancelCurrentPageLongPress();
        }

        if (theta > MAX_SWIPE_ANGLE) {
            // Above MAX_SWIPE_ANGLE, we don't want to ever start scrolling the workspace
            return;
        } else if (theta > START_DAMPING_TOUCH_SLOP_ANGLE) {
            // Above START_DAMPING_TOUCH_SLOP_ANGLE and below MAX_SWIPE_ANGLE, we want to
            // increase the touch slop to make it harder to begin scrolling the workspace. This
            // results in vertically scrolling widgets to more easily. The higher the angle, the
            // more we increase touch slop.
            theta -= START_DAMPING_TOUCH_SLOP_ANGLE;
            float extraRatio = (float)
                    Math.sqrt((theta / (MAX_SWIPE_ANGLE - START_DAMPING_TOUCH_SLOP_ANGLE)));
            super.determineScrollingStart(ev, 1 + TOUCH_SLOP_DAMPING_FACTOR * extraRatio);
        } else {
            // Below START_DAMPING_TOUCH_SLOP_ANGLE, we don't do anything special
            super.determineScrollingStart(ev);
        }
    }

    protected void onPageBeginTransition() {
        // Widget resize frame doesn't receive events to close when talkback is enabled. For that
        // case, close it here.
        AbstractFloatingView.closeOpenViews(mLauncher, false, TYPE_WIDGET_RESIZE_FRAME);

        super.onPageBeginTransition();
        updateChildrenLayersEnabled();
    }

    protected void onPageEndTransition() {
        super.onPageEndTransition();
        updateChildrenLayersEnabled();

        if (mDragController.isDragging()) {
            if (workspaceInModalState()) {
                // If we are in springloaded mode, then force an event to check if the current touch
                // is under a new page (to scroll to)
                mDragController.forceTouchMove();
            }
        }

        if (mStripScreensOnPageStopMoving) {
            stripEmptyScreens();
            mStripScreensOnPageStopMoving = false;
        }

        // Inform the Launcher activity that the page transition ended so that it can react to the
        // newly visible page if it wants to.
        mLauncher.onPageEndTransition();
    }

    public void setLauncherOverlay(LauncherOverlayTouchProxy overlay) {
        final EdgeEffectCompat newEffect;
        if (overlay == null) {
            newEffect = new EdgeEffectCompat(getContext());
            mOverlayEdgeEffect = null;
        } else {
            newEffect = mOverlayEdgeEffect = new OverlayEdgeEffect(getContext(), overlay);
            overlay.setOverlayCallbacks(this);
        }

        if (mIsRtl) {
            mEdgeGlowRight = newEffect;
        } else {
            mEdgeGlowLeft = newEffect;
        }
        onOverlayScrollChanged(0);
    }

    public boolean hasOverlay() {
        return mOverlayEdgeEffect != null;
    }

    @Override
    protected void snapToDestination() {
        if (mOverlayEdgeEffect != null && !mOverlayEdgeEffect.isFinished()) {
            snapToPageImmediately(0);
        } else {
            super.snapToDestination();
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        // Update the page indicator progress.
        // Unlike from other states, we show the page indicator when transitioning from HINT_STATE.
        boolean isSwitchingState = mIsSwitchingState
                && mLauncher.getStateManager().getCurrentStableState() != HINT_STATE;
        boolean isTransitioning = isSwitchingState
                || (getLayoutTransition() != null && getLayoutTransition().isRunning());
        if (!isTransitioning) {
            showPageIndicatorAtCurrentScroll();
        }

        updatePageAlphaValues();
        updatePageScrollValues();
        enableHwLayersOnVisiblePages();
    }

    public void showPageIndicatorAtCurrentScroll() {
        if (mPageIndicator != null) {
            mPageIndicator.setScroll(getScrollX(), computeMaxScroll());
        }
    }

    @Override
    protected boolean shouldFlingForVelocity(int velocityX) {
        // When the overlay is moving, the fling or settle transition is controlled by the overlay.
        return Float.compare(Math.abs(mOverlayProgress), 0) == 0
                && super.shouldFlingForVelocity(velocityX);
    }

    /**
     * The overlay scroll is being controlled locally, just update our overlay effect
     */
    @Override
    public void onOverlayScrollChanged(float scroll) {
        mOverlayProgress = Utilities.boundToRange(scroll, 0, 1);
        if (Float.compare(mOverlayProgress, 1f) == 0) {
            if (!mOverlayShown) {
                mOverlayShown = true;
                mLauncher.onOverlayVisibilityChanged(true);
            }
        } else if (Float.compare(mOverlayProgress, 0f) == 0) {
            if (mOverlayShown) {
                mOverlayShown = false;
                mLauncher.onOverlayVisibilityChanged(false);
            }
        }
        int count = mOverlayCallbacks.size();
        for (int i = 0; i < count; i++) {
            mOverlayCallbacks.get(i).onOverlayScrollChanged(mOverlayProgress);
        }
    }

    /**
     * Adds a callback for receiving overlay progress
     */
    public void addOverlayCallback(LauncherOverlayCallbacks callback) {
        mOverlayCallbacks.add(callback);
        callback.onOverlayScrollChanged(mOverlayProgress);
    }

    /**
     * Removes a previously added overlay progress callback
     */
    public void removeOverlayCallback(LauncherOverlayCallbacks callback) {
        mOverlayCallbacks.remove(callback);
    }

    @Override
    protected void notifyPageSwitchListener(int prevPage) {
        super.notifyPageSwitchListener(prevPage);
        if (prevPage != mCurrentPage) {
            StatsLogManager.EventEnum event = (prevPage < mCurrentPage)
                    ? LAUNCHER_SWIPERIGHT : LAUNCHER_SWIPELEFT;
            mLauncher.getStatsLogManager().logger()
                    .withSrcState(LAUNCHER_STATE_HOME)
                    .withDstState(LAUNCHER_STATE_HOME)
                    .withContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                            .setWorkspace(
                                    LauncherAtom.WorkspaceContainer.newBuilder()
                                            .setPageIndex(prevPage)).build())
                    .log(event);
        }
    }

    protected void setWallpaperDimension() {
        Executors.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final Point size = LauncherAppState.getIDP(getContext()).defaultWallpaperSize;
                if (size.x != mWallpaperManager.getDesiredMinimumWidth()
                        || size.y != mWallpaperManager.getDesiredMinimumHeight()) {
                    mWallpaperManager.suggestDesiredDimensions(size.x, size.y);
                }
            }
        });
    }

    public void lockWallpaperToDefaultPage() {
        mWallpaperOffset.setLockToDefaultPage(true);
    }

    public void unlockWallpaperFromDefaultPageOnNextLayout() {
        if (mWallpaperOffset.isLockedToDefaultPage()) {
            mUnlockWallpaperFromDefaultPageOnLayout = true;
            requestLayout();
        }
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        mWallpaperOffset.syncWithScroll();
    }

    @Override
    public void announceForAccessibility(CharSequence text) {
        // Don't announce if apps is on top of us.
        if (!mLauncher.isInState(ALL_APPS)) {
            super.announceForAccessibility(text);
        }
    }

    private void updatePageAlphaValues() {
        // We need to check the isDragging case because updatePageAlphaValues is called between
        // goToState(SPRING_LOADED) and onStartStateTransition.
        if (!workspaceInModalState() && !mIsSwitchingState && !mDragController.isDragging()) {
            int screenCenter = getScrollX() + getMeasuredWidth() / 2;
            for (int i = 0; i < getChildCount(); i++) {
                CellLayout child = (CellLayout) getChildAt(i);
                if (child != null) {
                    float scrollProgress = getScrollProgress(screenCenter, child, i);
                    float alpha = 1 - Math.abs(scrollProgress);
                    if (mWorkspaceFadeInAdjacentScreens) {
                        child.getShortcutsAndWidgets().setAlpha(alpha);
                    } else {
                        // Pages that are off-screen aren't important for accessibility.
                        child.getShortcutsAndWidgets().setImportantForAccessibility(
                                alpha > 0 ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                                        : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                    }
                }
            }
        }
    }

    private void updatePageScrollValues() {
        int screenCenter = getScrollX() + getMeasuredWidth() / 2;
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout child = (CellLayout) getChildAt(i);
            if (child != null) {
                float scrollProgress = getScrollProgress(screenCenter, child, i);
                child.setScrollProgress(scrollProgress);
            }
        }
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mWallpaperOffset.setWindowToken(getWindowToken());
        computeScroll();
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mWallpaperOffset.setWindowToken(null);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mHasOnLayoutBeenCalled = true; // b/349929393 - is the required call to onLayout not done?
        if (mUnlockWallpaperFromDefaultPageOnLayout) {
            mWallpaperOffset.setLockToDefaultPage(false);
            mUnlockWallpaperFromDefaultPageOnLayout = false;
        }
        if (mFirstLayout && mCurrentPage >= 0 && mCurrentPage < getChildCount()) {
            mWallpaperOffset.syncWithScroll();
            mWallpaperOffset.jumpToFinal();
        }
        super.onLayout(changed, left, top, right, bottom);
        updatePageAlphaValues();
    }

    @Override
    public int getDescendantFocusability() {
        if (workspaceInModalState()) {
            return ViewGroup.FOCUS_BLOCK_DESCENDANTS;
        }
        return super.getDescendantFocusability();
    }

    private boolean workspaceInModalState() {
        return !mLauncher.isInState(NORMAL);
    }

    private boolean workspaceInScrollableState() {
        return mLauncher.isInState(SPRING_LOADED) || mLauncher.isInState(EDIT_MODE)
                || !workspaceInModalState();
    }

    /**
     * Returns whether a drag should be allowed to be started from the current workspace state.
     */
    public boolean workspaceIconsCanBeDragged() {
        return mLauncher.getStateManager().getState().hasFlag(FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED);
    }

    private void updateChildrenLayersEnabled() {
        boolean enableChildrenLayers = mIsSwitchingState || isPageInTransition();

        if (enableChildrenLayers != mChildrenLayersEnabled) {
            mChildrenLayersEnabled = enableChildrenLayers;
            if (mChildrenLayersEnabled) {
                enableHwLayersOnVisiblePages();
            } else {
                for (int i = 0; i < getPageCount(); i++) {
                    final CellLayout cl = (CellLayout) getChildAt(i);
                    cl.enableHardwareLayer(false);
                }
            }
        }
    }

    private void enableHwLayersOnVisiblePages() {
        if (mChildrenLayersEnabled) {
            final int screenCount = getChildCount();

            final int[] visibleScreens = getVisibleChildrenRange();
            int leftScreen = visibleScreens[0];
            int rightScreen = visibleScreens[1];
            if (mForceDrawAdjacentPages) {
                // In overview mode, make sure that the two side pages are visible.
                leftScreen = Utilities.boundToRange(getCurrentPage() - 1, 0, rightScreen);
                rightScreen = Utilities.boundToRange(getCurrentPage() + 1,
                        leftScreen, getPageCount() - 1);
            }

            if (leftScreen == rightScreen) {
                // make sure we're caching at least two pages always
                if (rightScreen < screenCount - 1) {
                    rightScreen++;
                } else if (leftScreen > 0) {
                    leftScreen--;
                }
            }

            for (int i = 0; i < screenCount; i++) {
                final CellLayout layout = (CellLayout) getPageAt(i);
                // enable layers between left and right screen inclusive.
                boolean enableLayer = leftScreen <= i && i <= rightScreen;
                layout.enableHardwareLayer(enableLayer);
            }
        }
    }

    public void onWallpaperTap(MotionEvent ev) {
        final int[] position = mTempXY;
        getLocationOnScreen(position);

        int pointerIndex = ev.getActionIndex();
        position[0] += (int) ev.getX(pointerIndex);
        position[1] += (int) ev.getY(pointerIndex);

        mWallpaperManager.sendWallpaperCommand(getWindowToken(),
                ev.getAction() == MotionEvent.ACTION_UP
                        ? WallpaperManager.COMMAND_TAP : WallpaperManager.COMMAND_SECONDARY_TAP,
                position[0], position[1], 0, null);
    }

    private void onStartStateTransition() {
        mIsSwitchingState = true;
        mTransitionProgress = 0;

        updateChildrenLayersEnabled();
    }

    private void onEndStateTransition() {
        mIsSwitchingState = false;
        mForceDrawAdjacentPages = false;
        mTransitionProgress = 1;

        updateChildrenLayersEnabled();
        updateAccessibilityFlags();
    }

    /**
     * Sets the current workspace {@link LauncherState} and updates the UI without any animations
     */
    @Override
    public void setState(LauncherState toState) {
        onStartStateTransition();
        mLauncher.getStateManager().getState().onLeavingState(mLauncher, toState);
        mStateTransitionAnimation.setState(toState);
        onEndStateTransition();
    }

    /**
     * Sets the current workspace {@link LauncherState}, then animates the UI
     */
    @Override
    public void setStateWithAnimation(
            LauncherState toState, StateAnimationConfig config, PendingAnimation animation) {
        StateTransitionListener listener = new StateTransitionListener();
        mLauncher.getStateManager().getState().onLeavingState(mLauncher, toState);
        mStateTransitionAnimation.setStateWithAnimation(toState, config, animation);

        // Invalidate the pages now, so that we have the visible pages before the
        // animation is started
        if (toState.hasFlag(FLAG_MULTI_PAGE)) {
            mForceDrawAdjacentPages = true;
        }
        invalidate(); // This will call dispatchDraw(), which calls getVisiblePages().

        ValueAnimator stepAnimator = ValueAnimator.ofFloat(0, 1);
        stepAnimator.addUpdateListener(listener);
        stepAnimator.addListener(listener);
        animation.add(stepAnimator);
    }

    public WorkspaceStateTransitionAnimation getStateTransitionAnimation() {
        return mStateTransitionAnimation;
    }

    public void updateAccessibilityFlags() {
        // TODO: Update the accessibility flags appropriately when dragging.
        int accessibilityFlag =
                mLauncher.getStateManager().getState().hasFlag(FLAG_WORKSPACE_INACCESSIBLE)
                        ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        : IMPORTANT_FOR_ACCESSIBILITY_AUTO;
        if (!mLauncher.getAccessibilityDelegate().isInAccessibleDrag()) {
            int total = getPageCount();
            for (int i = 0; i < total; i++) {
                updateAccessibilityFlags(accessibilityFlag, (CellLayout) getPageAt(i));
            }
            setImportantForAccessibility(accessibilityFlag);
        }
    }

    @Override
    public AccessibilityNodeInfo createAccessibilityNodeInfo() {
        if (getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
            // TAPL tests verify that workspace is not present in Overview and AllApps states.
            // TAPL can work only if UIDevice is set up as setCompressedLayoutHeirarchy(false).
            // Hiding workspace from the tests when it's
            // IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS.
            return AccessibilityNodeInfo.obtain();
        }
        return super.createAccessibilityNodeInfo();
    }

    private void updateAccessibilityFlags(int accessibilityFlag, CellLayout page) {
        page.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        page.getShortcutsAndWidgets().setImportantForAccessibility(accessibilityFlag);
        page.setContentDescription(null);
        page.setAccessibilityDelegate(null);
    }

    public void startDrag(CellInfo cellInfo, DragOptions options) {
        View child = cellInfo.cell;

        mDragInfo = cellInfo;
        child.setVisibility(INVISIBLE);

        if (options.isAccessibleDrag) {
            mDragController.addDragListener(
                    new AccessibleDragListenerAdapter(this, WorkspaceAccessibilityHelper::new) {
                        @Override
                        protected void enableAccessibleDrag(boolean enable,
                                                            @Nullable DragObject dragObject) {
                            super.enableAccessibleDrag(enable, dragObject);
                            setEnableForLayout(mLauncher.getHotseat(), enable);
                            if (enable && dragObject != null
                                    && dragObject.dragInfo instanceof LauncherAppWidgetInfo) {
                                mLauncher.getHotseat().setImportantForAccessibility(
                                        IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                            }
                        }
                    });
        }

        beginDragShared(child, this, options);
    }

    public void beginDragShared(View child, DragSource source, DragOptions options) {
        Object dragObject = child.getTag();
        if (!(dragObject instanceof ItemInfo)) {
            String msg = "Drag started with a view that has no tag set. This "
                    + "will cause a crash (issue 11627249) down the line. "
                    + "View: " + child + "  tag: " + child.getTag();
            throw new IllegalStateException(msg);
        }
        beginDragShared(child, null, source, (ItemInfo) dragObject,
                new DragPreviewProvider(child), options);
    }

    /**
     * Core functionality for beginning a drag operation for an item that will be dropped within
     * the workspace
     */
    public DragView beginDragShared(View child, DraggableView draggableView, DragSource source,
                                    ItemInfo dragObject, DragPreviewProvider previewProvider, DragOptions dragOptions) {

        float iconScale = 1f;
        if (child instanceof BubbleTextView) {
            Drawable icon = ((BubbleTextView) child).getIcon();
            if (icon instanceof FastBitmapDrawable) {
                iconScale = ((FastBitmapDrawable) icon).getAnimatedScale();
            }
        }

        // Clear the pressed state if necessary
        child.clearFocus();
        child.setPressed(false);
        if (child instanceof BubbleTextView) {
            BubbleTextView icon = (BubbleTextView) child;
            icon.clearPressedBackground();
        }

        if (draggableView == null && child instanceof DraggableView) {
            draggableView = (DraggableView) child;
        }

        final View contentView = previewProvider.getContentView();
        final float scale;
        // The draggable drawable follows the touch point around on the screen
        final Drawable drawable;
        if (contentView == null) {
            drawable = previewProvider.createDrawable();
            scale = previewProvider.getScaleAndPosition(drawable, mTempXY);
        } else {
            drawable = null;
            scale = previewProvider.getScaleAndPosition(contentView, mTempXY);
        }

        int dragLayerX = mTempXY[0];
        int dragLayerY = mTempXY[1];

        Rect dragRect = new Rect();

        if (draggableView != null) {
            draggableView.getSourceVisualDragBounds(dragRect);
            dragLayerY += dragRect.top;
        }


        if (child.getParent() instanceof ShortcutAndWidgetContainer) {
            mDragSourceInternal = (ShortcutAndWidgetContainer) child.getParent();
        }

        if (child instanceof BubbleTextView) {
            BubbleTextView btv = (BubbleTextView) child;
            if (!dragOptions.isAccessibleDrag) {
                dragOptions.preDragCondition = btv.startLongPressAction();
            }
            if (btv.isDisplaySearchResult()) {
                dragOptions.preDragEndScale = (float) mAllAppsIconSize / btv.getIconSize();
            }
        }

        if (dragOptions.preDragCondition != null) {
            int xDragOffSet = dragOptions.preDragCondition.getDragOffset().x;
            int yDragOffSet = dragOptions.preDragCondition.getDragOffset().y;
            if (xDragOffSet != 0 || yDragOffSet != 0) {
                dragLayerX += xDragOffSet;
                dragLayerY += yDragOffSet;
            }
        }

        final DragView dv;
        if (contentView != null) {
            dv = mDragController.startDrag(
                    contentView,
                    draggableView,
                    dragLayerX,
                    dragLayerY,
                    source,
                    dragObject,
                    dragRect,
                    scale * iconScale,
                    scale,
                    dragOptions);
        } else {
            dv = mDragController.startDrag(
                    drawable,
                    draggableView,
                    dragLayerX,
                    dragLayerY,
                    source,
                    dragObject,
                    dragRect,
                    scale * iconScale,
                    scale,
                    dragOptions);
        }
        return dv;
    }

    private boolean transitionStateShouldAllowDrop() {
        return (!isSwitchingState() || mTransitionProgress > ALLOW_DROP_TRANSITION_PROGRESS) &&
                workspaceIconsCanBeDragged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acceptDrop(DragObject d) {
        // If it's an external drop (e.g. from All Apps), check if it should be accepted
        CellLayout dropTargetLayout = mDropToLayout;
        if (d.dragSource != this) {
            // Don't accept the drop if we're not over a valid drop target at time of drop
            if (dropTargetLayout == null) {
                return false;
            }
            if (!transitionStateShouldAllowDrop()) return false;

            mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);

            // We want the point to be mapped to the dragTarget.
            mapPointFromDropLayout(dropTargetLayout, mDragViewVisualCenter);

            int spanX;
            int spanY;
            if (mDragInfo != null) {
                final CellInfo dragCellInfo = mDragInfo;
                spanX = dragCellInfo.spanX;
                spanY = dragCellInfo.spanY;
            } else {
                spanX = d.dragInfo.spanX;
                spanY = d.dragInfo.spanY;
            }

            int minSpanX = spanX;
            int minSpanY = spanY;
            if (d.dragInfo instanceof PendingAddWidgetInfo) {
                minSpanX = ((PendingAddWidgetInfo) d.dragInfo).minSpanX;
                minSpanY = ((PendingAddWidgetInfo) d.dragInfo).minSpanY;
            }

            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, dropTargetLayout,
                    mTargetCell);
            float distance = dropTargetLayout.getDistanceFromWorkspaceCellVisualCenter(
                    mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);
            if (mCreateUserFolderOnDrop && willCreateUserFolder(d.dragInfo,
                    dropTargetLayout, mTargetCell, distance, true)) {
                return true;
            }

            if (mAddToExistingFolderOnDrop && willAddToExistingUserFolder(d.dragInfo,
                    dropTargetLayout, mTargetCell, distance)) {
                return true;
            }

            int[] resultSpan = new int[2];
            mTargetCell = dropTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY,
                    null, mTargetCell, resultSpan, CellLayout.MODE_ACCEPT_DROP);
            boolean foundCell = mTargetCell[0] >= 0 && mTargetCell[1] >= 0;

            // Don't accept the drop if there's no room for the item
            if (!foundCell) {
                onNoCellFound(dropTargetLayout, d.dragInfo, d.logInstanceId);
                return false;
            }
        }

        int screenId = getCellLayoutId(dropTargetLayout);
        if (Workspace.EXTRA_EMPTY_SCREEN_IDS.contains(screenId)) {
            commitExtraEmptyScreens();
        }

        return true;
    }

    boolean willCreateUserFolder(ItemInfo info, CellLayout target, int[] targetCell,
                                 float distance, boolean considerTimeout) {
        if (distance > target.getFolderCreationRadius(targetCell)) return false;
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        return willCreateUserFolder(info, dropOverView, considerTimeout);
    }

    boolean willCreateUserFolder(ItemInfo info, View dropOverView, boolean considerTimeout) {
        if (dropOverView != null) {
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) dropOverView.getLayoutParams();
            if (lp.useTmpCoords && (lp.getTmpCellX() != lp.getCellX()
                    || lp.getTmpCellY() != lp.getCellY())) {
                return false;
            }
        }

        boolean hasntMoved = false;
        if (mDragInfo != null) {
            hasntMoved = dropOverView == mDragInfo.cell;
        }

        if (dropOverView == null || hasntMoved || (considerTimeout && !mCreateUserFolderOnDrop)) {
            return false;
        }

        boolean aboveShortcut = Folder.willAccept(dropOverView.getTag())
                && ((ItemInfo) dropOverView.getTag()).container != CONTAINER_HOTSEAT_PREDICTION;
        boolean willBecomeShortcut = Folder.willAcceptItemType(info.itemType);

        return (aboveShortcut && willBecomeShortcut);
    }

    boolean willAddToExistingUserFolder(ItemInfo dragInfo, CellLayout target, int[] targetCell,
                                        float distance) {
        if (distance > target.getFolderCreationRadius(targetCell)) return false;
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        return willAddToExistingUserFolder(dragInfo, dropOverView);

    }

    boolean willAddToExistingUserFolder(ItemInfo dragInfo, View dropOverView) {
        if (dropOverView != null) {
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) dropOverView.getLayoutParams();
            if (lp.useTmpCoords && (lp.getTmpCellX() != lp.getCellX()
                    || lp.getTmpCellY() != lp.getCellY())) {
                return false;
            }
        }

        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(dragInfo)) {
                return true;
            }
        }
        return false;
    }

    boolean createUserFolderIfNecessary(View newView, int container, CellLayout target,
                                        int[] targetCell, float distance, boolean external, DragObject d) {
        if (distance > target.getFolderCreationRadius(targetCell)) return false;
        View v = target.getChildAt(targetCell[0], targetCell[1]);

        boolean hasntMoved = false;
        if (mDragInfo != null) {
            CellLayout cellParent = getParentCellLayoutForView(mDragInfo.cell);
            hasntMoved = (mDragInfo.cellX == targetCell[0] &&
                    mDragInfo.cellY == targetCell[1]) && (cellParent == target);
        }

        if (v == null || hasntMoved || !mCreateUserFolderOnDrop) return false;
        mCreateUserFolderOnDrop = false;
        final int screenId = getCellLayoutId(target);

        boolean aboveShortcut = Folder.willAccept(v.getTag());
        boolean willBecomeShortcut = Folder.willAccept(newView.getTag());

        if (aboveShortcut && willBecomeShortcut) {
            ItemInfo sourceInfo = (ItemInfo) newView.getTag();
            ItemInfo destInfo = (ItemInfo) v.getTag();
            // if the drag started here, we need to remove it from the workspace
            if (!external) {
                getParentCellLayoutForView(mDragInfo.cell).removeView(mDragInfo.cell);
            }

            Rect folderLocation = new Rect();
            float scale = mLauncher.getDragLayer().getDescendantRectRelativeToSelf(v, folderLocation);
            target.removeView(v);
            mStatsLogManager.logger().withItemInfo(destInfo).withInstanceId(d.logInstanceId)
                    .log(LauncherEvent.LAUNCHER_ITEM_DROP_FOLDER_CREATED);
            FolderIcon fi = mLauncher.addFolder(target, container, screenId, targetCell[0],
                    targetCell[1]);
            destInfo.cellX = -1;
            destInfo.cellY = -1;
            sourceInfo.cellX = -1;
            sourceInfo.cellY = -1;

            // If the dragView is null, we can't animate
            boolean animate = d != null;
            if (animate) {
                // In order to keep everything continuous, we hand off the currently rendered
                // folder background to the newly created icon. This preserves animation state.
                fi.setFolderBackground(mFolderCreateBg);
                mFolderCreateBg = new PreviewBackground(getContext());
                fi.performCreateAnimation(destInfo, v, sourceInfo, d, folderLocation, scale);
            } else {
                fi.prepareCreateAnimation(v);
                fi.addItem(destInfo);
                fi.addItem(sourceInfo);
            }
            return true;
        }
        return false;
    }

    boolean addToExistingFolderIfNecessary(View newView, CellLayout target, int[] targetCell,
                                           float distance, DragObject d, boolean external) {
        if (distance > target.getFolderCreationRadius(targetCell)) return false;

        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        if (!mAddToExistingFolderOnDrop) return false;
        mAddToExistingFolderOnDrop = false;

        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(d.dragInfo)) {
                mStatsLogManager.logger().withItemInfo(fi.mInfo).withInstanceId(d.logInstanceId)
                        .log(LauncherEvent.LAUNCHER_ITEM_DROP_COMPLETED_ON_FOLDER_ICON);
                fi.onDrop(d, false /* itemReturnedOnFailedDrop */);
                // if the drag started here, we need to remove it from the workspace
                if (!external) {
                    getParentCellLayoutForView(mDragInfo.cell).removeView(mDragInfo.cell);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void prepareAccessibilityDrop() {}

    @Override
    public void onDrop(final DragObject d, DragOptions options) {
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);
        CellLayout dropTargetLayout = mDropToLayout;

        // We want the point to be mapped to the dragTarget.
        if (dropTargetLayout != null) {
            mapPointFromDropLayout(dropTargetLayout, mDragViewVisualCenter);
        }

        boolean droppedOnOriginalCell = false;

        boolean snappedToNewPage = false;
        boolean resizeOnDrop = false;
        Runnable onCompleteRunnable = null;
        if (d.dragSource != this || mDragInfo == null) {
            final int[] touchXY = new int[]{(int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1]};
            onDropExternal(touchXY, dropTargetLayout, d);
        } else {
            final View cell = mDragInfo.cell;
            boolean droppedOnOriginalCellDuringTransition = false;

            if (dropTargetLayout != null && !d.cancelled) {
                // Move internally
                boolean hasMovedLayouts = (getParentCellLayoutForView(cell) != dropTargetLayout);
                boolean hasMovedIntoHotseat = mLauncher.isHotseatLayout(dropTargetLayout);
                int container = hasMovedIntoHotseat ?
                        LauncherSettings.Favorites.CONTAINER_HOTSEAT :
                        LauncherSettings.Favorites.CONTAINER_DESKTOP;
                int screenId = (mTargetCell[0] < 0) ?
                        mDragInfo.screenId : getCellLayoutId(dropTargetLayout);
                int spanX = mDragInfo != null ? mDragInfo.spanX : 1;
                int spanY = mDragInfo != null ? mDragInfo.spanY : 1;
                // First we find the cell nearest to point at which the item is
                // dropped, without any consideration to whether there is an item there.

                mTargetCell = findNearestArea((int) mDragViewVisualCenter[0], (int)
                        mDragViewVisualCenter[1], spanX, spanY, dropTargetLayout, mTargetCell);
                float distance = dropTargetLayout.getDistanceFromWorkspaceCellVisualCenter(
                        mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);

                // If the item being dropped is a shortcut and the nearest drop
                // cell also contains a shortcut, then create a folder with the two shortcuts.
                if (createUserFolderIfNecessary(cell, container, dropTargetLayout, mTargetCell,
                        distance, false, d)
                        || addToExistingFolderIfNecessary(cell, dropTargetLayout, mTargetCell,
                        distance, d, false)) {
                    if (!mLauncher.isInState(EDIT_MODE)) {
                        mLauncher.getStateManager().goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
                    }
                    return;
                }

                // Aside from the special case where we're dropping a shortcut onto a shortcut,
                // we need to find the nearest cell location that is vacant
                ItemInfo item = d.dragInfo;
                int minSpanX = item.spanX;
                int minSpanY = item.spanY;
                if (item.minSpanX > 0 && item.minSpanY > 0) {
                    minSpanX = item.minSpanX;
                    minSpanY = item.minSpanY;
                }

                CellPos originalPresenterPos = getCellPosMapper().mapModelToPresenter(item);
                droppedOnOriginalCell = originalPresenterPos.screenId == screenId
                        && item.container == container
                        && originalPresenterPos.cellX == mTargetCell[0]
                        && originalPresenterPos.cellY == mTargetCell[1];
                droppedOnOriginalCellDuringTransition = droppedOnOriginalCell && mIsSwitchingState;

                // When quickly moving an item, a user may accidentally rearrange their
                // workspace. So instead we move the icon back safely to its original position.
                boolean returnToOriginalCellToPreventShuffling = !isFinishedSwitchingState()
                        && !droppedOnOriginalCellDuringTransition && !dropTargetLayout
                        .isRegionVacant(mTargetCell[0], mTargetCell[1], spanX, spanY);
                int[] resultSpan = new int[2];
                if (returnToOriginalCellToPreventShuffling) {
                    mTargetCell[0] = mTargetCell[1] = -1;
                } else {
                    mTargetCell = dropTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                            (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY,
                            cell, mTargetCell, resultSpan, CellLayout.MODE_ON_DROP);
                }

                boolean foundCell = mTargetCell[0] >= 0 && mTargetCell[1] >= 0;

                // if the widget resizes on drop
                if (foundCell && (cell instanceof AppWidgetHostView) &&
                        (resultSpan[0] != item.spanX || resultSpan[1] != item.spanY)) {
                    resizeOnDrop = true;
                    item.spanX = resultSpan[0];
                    item.spanY = resultSpan[1];
                    AppWidgetHostView awhv = (AppWidgetHostView) cell;
                    WidgetSizes.updateWidgetSizeRanges(awhv, mLauncher, resultSpan[0],
                            resultSpan[1]);
                }

                if (foundCell) {
                    int targetScreenIndex = getPageIndexForScreenId(screenId);
                    int snapScreen = getLeftmostVisiblePageForIndex(targetScreenIndex);
                    // On large screen devices two pages can be shown at the same time, and snap
                    // isn't needed if the source and target screens appear at the same time
                    if (snapScreen != mCurrentPage && !hasMovedIntoHotseat) {
                        snapToPage(snapScreen);
                        snappedToNewPage = true;
                    }
                    final ItemInfo info = (ItemInfo) cell.getTag();
                    if (hasMovedLayouts) {
                        // Reparent the view
                        CellLayout parentCell = getParentCellLayoutForView(cell);
                        if (parentCell != null) {
                            parentCell.removeView(cell);
                        } else if (mDragInfo.cell instanceof LauncherAppWidgetHostView) {
                            d.dragView.detachContentView(/* reattachToPreviousParent= */ false);
                        } else if (FeatureFlags.IS_STUDIO_BUILD) {
                            throw new NullPointerException("mDragInfo.cell has null parent");
                        }
                        addInScreen(cell, container, screenId, mTargetCell[0], mTargetCell[1],
                                info.spanX, info.spanY);
                    }

                    // update the item's position after drop
                    CellLayoutLayoutParams lp = (CellLayoutLayoutParams) cell.getLayoutParams();
                    lp.setTmpCellX(mTargetCell[0]);
                    lp.setCellX(mTargetCell[0]);
                    lp.setTmpCellY(mTargetCell[1]);
                    lp.setCellY(mTargetCell[1]);
                    lp.cellHSpan = item.spanX;
                    lp.cellVSpan = item.spanY;
                    lp.isLockedToGrid = true;

                    if (container != LauncherSettings.Favorites.CONTAINER_HOTSEAT &&
                            cell instanceof LauncherAppWidgetHostView) {

                        // We post this call so that the widget has a chance to be placed
                        // in its final location
                        onCompleteRunnable = getWidgetResizeFrameRunnable(options,
                                (LauncherAppWidgetHostView) cell, dropTargetLayout);
                    }
                    mLauncher.getModelWriter().modifyItemInDatabase(info, container, screenId,
                            lp.getCellX(), lp.getCellY(), item.spanX, item.spanY);
                } else {
                    if (!returnToOriginalCellToPreventShuffling) {
                        onNoCellFound(dropTargetLayout, d.dragInfo, d.logInstanceId);
                    }
                    if (mDragInfo.cell instanceof LauncherAppWidgetHostView) {
                        d.dragView.detachContentView(/* reattachToPreviousParent= */ true);
                    }

                    // If we can't find a drop location, we return the item to its original position
                    CellLayoutLayoutParams lp = (CellLayoutLayoutParams) cell.getLayoutParams();
                    mTargetCell[0] = lp.getCellX();
                    mTargetCell[1] = lp.getCellY();
                    CellLayout layout = (CellLayout) cell.getParent().getParent();
                    layout.markCellsAsOccupiedForView(cell);
                }
            } else {
                // When drag is cancelled, reattach content view back to its original parent.
                if (cell instanceof LauncherAppWidgetHostView) {
                    d.dragView.detachContentView(/* reattachToPreviousParent= */ true);

                    final CellLayout cellLayout = getParentCellLayoutForView(cell);
                    boolean pageIsVisible = isVisible(cellLayout);

                    if (pageIsVisible) {
                        onCompleteRunnable = getWidgetResizeFrameRunnable(options,
                                (LauncherAppWidgetHostView) cell, cellLayout);
                    }
                }
            }

            final CellLayout parent = (CellLayout) cell.getParent().getParent();
            if (d.dragView.hasDrawn()) {
                if (droppedOnOriginalCellDuringTransition) {
                    // Animate the item to its original position, while simultaneously exiting
                    // spring-loaded mode so the page meets the icon where it was picked up.
                    final RunnableList callbackList = new RunnableList();
                    final Runnable onCompleteCallback = onCompleteRunnable;
                    LauncherState currentState = mLauncher.getStateManager().getState();
                    mLauncher.getDragController().animateDragViewToOriginalPosition(
                            /* onComplete= */ callbackList::executeAllAndDestroy, cell,
                            currentState.getTransitionDuration(mLauncher, true /* isToState */));
                    if (!mLauncher.isInState(EDIT_MODE)) {
                        mLauncher.getStateManager().goToState(NORMAL, /* delay= */ 0,
                                onCompleteCallback == null
                                        ? null
                                        : forSuccessCallback(
                                        () -> callbackList.add(onCompleteCallback)));
                    } else if (onCompleteCallback != null) {
                        forSuccessCallback(() -> callbackList.add(onCompleteCallback));
                    }
                    mLauncher.getDropTargetBar().onDragEnd();
                    parent.onDropChild(cell);
                    return;
                }
                final ItemInfo info = (ItemInfo) cell.getTag();
                boolean isWidget = info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                        || info.itemType == LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;
                if (isWidget && dropTargetLayout != null) {
                    // animate widget to a valid place
                    int animationType = resizeOnDrop ? ANIMATE_INTO_POSITION_AND_RESIZE :
                            ANIMATE_INTO_POSITION_AND_DISAPPEAR;
                    animateWidgetDrop(info, parent, d.dragView, null, animationType, cell, false);
                } else {
                    int duration = snappedToNewPage ? ADJACENT_SCREEN_DROP_DURATION : -1;
                    mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, cell, duration,
                            this);
                }
            } else {
                d.deferDragViewCleanupPostAnimation = false;
                cell.setVisibility(VISIBLE);
            }
            parent.onDropChild(cell);

            if (!mLauncher.isInState(EDIT_MODE)) {
                mLauncher.getStateManager().goToState(NORMAL, SPRING_LOADED_EXIT_DELAY,
                        onCompleteRunnable == null ? null : forSuccessCallback(onCompleteRunnable));
            } else if (onCompleteRunnable != null) {
                forSuccessCallback(onCompleteRunnable);
            }
            mStatsLogManager.logger().withItemInfo(d.dragInfo).withInstanceId(d.logInstanceId)
                    .log(LauncherEvent.LAUNCHER_ITEM_DROP_COMPLETED);
        }

        if (d.stateAnnouncer != null && !droppedOnOriginalCell) {
            d.stateAnnouncer.completeAction(R.string.item_moved);
        }
        TestEventEmitter.INSTANCE.get(getContext()).sendEvent(TestEvent.WORKSPACE_ON_DROP);
    }

    @Nullable
    private Runnable getWidgetResizeFrameRunnable(DragOptions options,
                                                  LauncherAppWidgetHostView hostView, CellLayout cellLayout) {
        AppWidgetProviderInfo pInfo = hostView.getAppWidgetInfo();
        if (pInfo != null && pInfo.resizeMode != AppWidgetProviderInfo.RESIZE_NONE) {
            return () -> {
                if (!isPageInTransition()) {
                    AppWidgetResizeFrame.showForWidget(hostView, cellLayout);
                }
            };
        }
        return null;
    }

    public void onNoCellFound(
            View dropTargetLayout, ItemInfo itemInfo, @Nullable InstanceId logInstanceId) {
        int strId = mLauncher.isHotseatLayout(dropTargetLayout)
                ? R.string.hotseat_out_of_space : R.string.out_of_space;
        Toast.makeText(mLauncher, mLauncher.getString(strId), Toast.LENGTH_SHORT).show();
        StatsLogManager.StatsLogger logger = mStatsLogManager.logger().withItemInfo(itemInfo);
        if (logInstanceId != null) {
            logger = logger.withInstanceId(logInstanceId);
        }
        logger.log(LauncherEvent.LAUNCHER_ITEM_DROP_FAILED_INSUFFICIENT_SPACE);
    }

    /**
     * Computes and returns the area relative to dragLayer which is used to display a page.
     * In case we have multiple pages displayed at the same time, we return the union of the areas.
     */
    public Rect getPageAreaRelativeToDragLayer() {
        Rect area = new Rect();
        int nextPage = getNextPage();
        int panelCount = getPanelCount();
        for (int page = nextPage; page < nextPage + panelCount; page++) {
            CellLayout child = (CellLayout) getChildAt(page);
            if (child == null) {
                break;
            }

            ShortcutAndWidgetContainer boundingLayout = child.getShortcutsAndWidgets();
            Rect tmpRect = new Rect();
            mLauncher.getDragLayer().getDescendantRectRelativeToSelf(boundingLayout, tmpRect);
            area.union(tmpRect);
        }

        return area;
    }

    @Override
    public void onDragEnter(DragObject d) {
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragEnter", 1, 1);
        }

        mCreateUserFolderOnDrop = false;
        mAddToExistingFolderOnDrop = false;

        mDropToLayout = null;
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);
        setDropLayoutForDragObject(d, mDragViewVisualCenter[0], mDragViewVisualCenter[1]);
    }

    @Override
    public void onDragExit(DragObject d) {
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragExit", -1, 0);
        }

        // Here we store the final page that will be dropped to, if the workspace in fact
        // receives the drop
        mDropToLayout = mDragTargetLayout;
        if (mDragMode == DRAG_MODE_CREATE_FOLDER) {
            mCreateUserFolderOnDrop = true;
        } else if (mDragMode == DRAG_MODE_ADD_TO_FOLDER) {
            mAddToExistingFolderOnDrop = true;
        }

        // Reset the previous drag target
        setCurrentDropLayout(null);
        setCurrentDragOverlappingLayout(null);

        mSpringLoadedDragController.cancel();
    }

    private void enforceDragParity(String event, int update, int expectedValue) {
        enforceDragParity(this, event, update, expectedValue);
        for (int i = 0; i < getChildCount(); i++) {
            enforceDragParity(getChildAt(i), event, update, expectedValue);
        }
    }

    private void enforceDragParity(View v, String event, int update, int expectedValue) {
        Object tag = v.getTag(R.id.drag_event_parity);
        int value = tag == null ? 0 : (Integer) tag;
        value += update;
        v.setTag(R.id.drag_event_parity, value);

        if (value != expectedValue) {
            Log.e(TAG, event + ": Drag contract violated: " + value);
        }
    }

    void setCurrentDropLayout(CellLayout layout) {
        if (mDragTargetLayout != null) {
            mDragTargetLayout.revertTempState();
            mDragTargetLayout.onDragExit();
        }
        mDragTargetLayout = layout;
        if (mDragTargetLayout != null) {
            mDragTargetLayout.onDragEnter();
        }
        cleanupReorder(true);
        cleanupFolderCreation();
        setCurrentDropOverCell(-1, -1);
    }

    void setCurrentDragOverlappingLayout(CellLayout layout) {
        if (mDragOverlappingLayout != null) {
            mDragOverlappingLayout.setIsDragOverlapping(false);
        }
        mDragOverlappingLayout = layout;
        if (mDragOverlappingLayout != null) {
            mDragOverlappingLayout.setIsDragOverlapping(true);
        }
    }

    void setCurrentDropOverCell(int x, int y) {
        if (x != mDragOverX || y != mDragOverY) {
            mDragOverX = x;
            mDragOverY = y;
            setDragMode(DRAG_MODE_NONE);
        }
    }

    void setDragMode(int dragMode) {
        if (dragMode != mDragMode) {
            if (dragMode == DRAG_MODE_NONE) {
                cleanupAddToFolder();
                // We don't want to cancel the re-order alarm every time the target cell changes
                // as this feels to slow / unresponsive.
                cleanupReorder(false);
                cleanupFolderCreation();
            } else if (dragMode == DRAG_MODE_ADD_TO_FOLDER) {
                cleanupReorder(true);
                cleanupFolderCreation();
            } else if (dragMode == DRAG_MODE_CREATE_FOLDER) {
                cleanupAddToFolder();
                cleanupReorder(true);
            } else if (dragMode == DRAG_MODE_REORDER) {
                cleanupAddToFolder();
                cleanupFolderCreation();
            }
            mDragMode = dragMode;
        }
    }

    protected void cleanupFolderCreation() {
        if (mFolderCreateBg != null) {
            mFolderCreateBg.animateToRest();
        }

        if (mDragOverView instanceof AppPairIcon api) {
            api.getIconDrawableArea().onTemporaryContainerChange(null);
            mDragOverView = null;
        }
    }

    private void cleanupAddToFolder() {
        if (mDragOverFolderIcon != null) {
            mDragOverFolderIcon.onDragExit();
            mDragOverFolderIcon = null;
        }
    }

    protected void cleanupReorder(boolean cancelAlarm) {
        // Any pending reorders are canceled
        if (cancelAlarm) {
            mReorderAlarm.cancelAlarm();
        }
        mLastReorderX = -1;
        mLastReorderY = -1;
    }

    /*
     *
     * Convert the 2D coordinate xy from the parent View's coordinate space to this CellLayout's
     * coordinate space. The argument xy is modified with the return result.
     */
    private void mapPointFromSelfToChild(View v, float[] xy) {
        xy[0] = xy[0] - v.getLeft();
        xy[1] = xy[1] - v.getTop();
    }

    /**
     * Updates the point in {@param xy} to point to the co-ordinate space of {@param layout}
     *
     * @param layout either hotseat of a page in workspace
     * @param xy     the point location in workspace co-ordinate space
     */
    private void mapPointFromDropLayout(CellLayout layout, float[] xy) {
        if (mLauncher.isHotseatLayout(layout)) {
            mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(this, xy, true);
            mLauncher.getDragLayer().mapCoordInSelfToDescendant(layout, xy);
        } else {
            mapPointFromSelfToChild(layout, xy);
        }
    }

    private boolean isDragWidget(DragObject d) {
        return (d.dragInfo instanceof LauncherAppWidgetInfo ||
                d.dragInfo instanceof PendingAddWidgetInfo);
    }

    public void onDragOver(DragObject d) {
        // Skip drag over events while we are dragging over side pages
        if (!transitionStateShouldAllowDrop()) return;

        ItemInfo item = d.dragInfo;
        if (item == null) {
            if (FeatureFlags.IS_STUDIO_BUILD) {
                throw new NullPointerException("DragObject has null info");
            }
            return;
        }

        // Ensure that we have proper spans for the item that we are dropping
        if (item.spanX < 0 || item.spanY < 0) throw new RuntimeException("Improper spans found");
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);

        final View child = (mDragInfo == null) ? null : mDragInfo.cell;
        if (setDropLayoutForDragObject(d, mDragViewVisualCenter[0], mDragViewVisualCenter[1])) {
            if (mDragTargetLayout == null || mLauncher.isHotseatLayout(mDragTargetLayout)) {
                mSpringLoadedDragController.cancel();
            } else {
                mSpringLoadedDragController.setAlarm(mDragTargetLayout);
            }
        }

        // Handle the drag over
        if (mDragTargetLayout != null) {
            // We want the point to be mapped to the dragTarget.
            mapPointFromDropLayout(mDragTargetLayout, mDragViewVisualCenter);

            int minSpanX = item.spanX;
            int minSpanY = item.spanY;
            if (item.minSpanX > 0 && item.minSpanY > 0) {
                minSpanX = item.minSpanX;
                minSpanY = item.minSpanY;
            }

            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], item.spanX, item.spanY,
                    mDragTargetLayout, mTargetCell);
            int reorderX = mTargetCell[0];
            int reorderY = mTargetCell[1];

            setCurrentDropOverCell(mTargetCell[0], mTargetCell[1]);

            float targetCellDistance = mDragTargetLayout.getDistanceFromWorkspaceCellVisualCenter(
                    mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);

            manageFolderFeedback(targetCellDistance, d);

            boolean nearestDropOccupied = mDragTargetLayout.isNearestDropLocationOccupied((int)
                            mDragViewVisualCenter[0], (int) mDragViewVisualCenter[1], item.spanX,
                    item.spanY, child, mTargetCell);

            manageReorderOnDragOver(d, targetCellDistance, nearestDropOccupied, minSpanX, minSpanY,
                    reorderX, reorderY);

            if (mDragMode == DRAG_MODE_CREATE_FOLDER || mDragMode == DRAG_MODE_ADD_TO_FOLDER ||
                    !nearestDropOccupied) {
                if (mDragTargetLayout != null) {
                    mDragTargetLayout.revertTempState();
                }
            }
        }
    }

    protected void manageReorderOnDragOver(DragObject d, float targetCellDistance,
                                           boolean nearestDropOccupied, int minSpanX, int minSpanY, int reorderX, int reorderY) {

        ItemInfo item = d.dragInfo;
        final View child = (mDragInfo == null) ? null : mDragInfo.cell;
        if (!nearestDropOccupied) {
            int[] span = new int[2];
            mDragTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, item.spanX, item.spanY,
                    child, mTargetCell, span, CellLayout.MODE_SHOW_REORDER_HINT);
            mDragTargetLayout.visualizeDropLocation(mTargetCell[0], mTargetCell[1], span[0],
                    span[1], d);
            nearestDropOccupied = mDragTargetLayout.isNearestDropLocationOccupied((int)
                            mDragViewVisualCenter[0], (int) mDragViewVisualCenter[1], item.spanX,
                    item.spanY, child, mTargetCell);
        } else if ((mDragMode == DRAG_MODE_NONE || mDragMode == DRAG_MODE_REORDER)
                && (mLastReorderX != reorderX || mLastReorderY != reorderY)
                && targetCellDistance < mDragTargetLayout.getReorderRadius(mTargetCell, item.spanX,
                item.spanY)) {
            mReorderAlarm.cancelAlarm();
            mLastReorderX = reorderX;
            mLastReorderY = reorderY;
            mDragTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, item.spanX, item.spanY,
                    child, mTargetCell, new int[2], CellLayout.MODE_SHOW_REORDER_HINT);
            // Otherwise, if we aren't adding to or creating a folder and there's no pending
            // reorder, then we schedule a reorder
            ReorderAlarmListener listener = new ReorderAlarmListener(mDragViewVisualCenter,
                    minSpanX, minSpanY, item.spanX, item.spanY, d, child);
            mReorderAlarm.setOnAlarmListener(listener);
            mReorderAlarm.setAlarm(REORDER_TIMEOUT);
        }
    }

    /**
     * Updates {@link #mDragTargetLayout} and {@link #mDragOverlappingLayout}
     * based on the DragObject's position.
     *
     * The layout will be:
     * - The Hotseat if the drag object is over it
     * - A side page if we are in spring-loaded mode and the drag object is over it
     * - The current page otherwise
     *
     * @return whether the layout is different from the current {@link #mDragTargetLayout}.
     */
    private boolean setDropLayoutForDragObject(DragObject d, float centerX, float centerY) {
        CellLayout layout = null;
        if (shouldUseHotseatAsDropLayout(d)) {
            layout = mLauncher.getHotseat();
        } else if (!isDragObjectOverSmartSpace(d)) {
            // If the object is over qsb/smartspace, we don't want to highlight anything.

            // Check neighbour pages
            layout = checkDragObjectIsOverNeighbourPages(d, centerX);

            if (layout == null) {
                // Check visible pages
                IntSet visiblePageIndices = getVisiblePageIndices();
                for (int visiblePageIndex : visiblePageIndices) {
                    layout = verifyInsidePage(visiblePageIndex, d.x, d.y);
                    if (layout != null) break;
                }
            }
        }

        // Update the current drop layout if the target changed
        if (layout != mDragTargetLayout) {
            setCurrentDropLayout(layout);
            setCurrentDragOverlappingLayout(layout);
            return true;
        }
        return false;
    }

    private boolean shouldUseHotseatAsDropLayout(DragObject dragObject) {
        if (mLauncher.getHotseat() == null
                || mLauncher.getHotseat().getShortcutsAndWidgets() == null
                || isDragWidget(dragObject)) {
            return false;
        }
        View hotseatShortcuts = mLauncher.getHotseat().getShortcutsAndWidgets();
        getViewBoundsRelativeToWorkspace(hotseatShortcuts, mTempRect);
        return mTempRect.contains(dragObject.x, dragObject.y);
    }

    private boolean isDragObjectOverSmartSpace(DragObject dragObject) {
        if (mFirstPagePinnedItem == null) {
            return false;
        }
        getViewBoundsRelativeToWorkspace(mFirstPagePinnedItem, mTempRect);
        return mTempRect.contains(dragObject.x, dragObject.y);
    }

    private CellLayout checkDragObjectIsOverNeighbourPages(DragObject d, float centerX) {
        if (isPageInTransition()) {
            return null;
        }

        // Check the workspace pages whether the object is over any of them

        // Note, centerX represents the center of the object that is being dragged, visually.
        // d.x represents the location of the finger within the dragged item.
        float touchX;
        float touchY = d.y;

        // Go through the pages and check if the dragged item is inside one of them. This block
        // is responsible for determining whether we need to snap to a different screen.
        int nextPage = getNextPage();
        IntSet pageIndexesToVerify = IntSet.wrap(nextPage - 1,
                nextPage + (isTwoPanelEnabled() ? 2 : 1));

        for (int pageIndex : pageIndexesToVerify) {
            // When deciding whether to perform a page switch, we need to consider the most
            // extreme X coordinate between the finger location and the center of the object
            // being dragged. This is either the max or the min of the two depending on whether
            // dragging to the left / right, respectively.
            touchX = (((pageIndex < nextPage) && !mIsRtl) || (pageIndex > nextPage && mIsRtl))
                    ? Math.min(d.x, centerX) : Math.max(d.x, centerX);
            CellLayout layout = verifyInsidePage(pageIndex, touchX, touchY);
            if (layout != null) {
                return layout;
            }
        }
        return null;
    }

    /**
     * Gets the given view's bounds relative to Workspace
     */
    private void getViewBoundsRelativeToWorkspace(View view, Rect outRect) {
        mLauncher.getDragLayer()
                .getDescendantRectRelativeToSelf(view, mTempRect);
        // map draglayer relative bounds to workspace
        mLauncher.getDragLayer().mapRectInSelfToDescendant(this, mTempRect);
        outRect.set(mTempRect);
    }

    /**
     * Returns the child CellLayout if the point is inside the page coordinates, null otherwise.
     */
    private CellLayout verifyInsidePage(int pageNo, float x, float y) {
        if (pageNo >= 0 && pageNo < getPageCount()) {
            CellLayout cl = (CellLayout) getChildAt(pageNo);
            if (x >= cl.getLeft() && x <= cl.getRight()
                    && y >= cl.getTop() && y <= cl.getBottom()) {
                // This point is inside the cell layout
                return cl;
            }
        }
        return null;
    }


    /**
     * Drop an item that didn't originate on one of the workspace screens.
     * It may have come from Launcher (e.g. from all apps or customize), or it may have
     * come from another app altogether.
     * <p>
     * NOTE: This can also be called when we are outside of a drag event, when we want
     * to add an item to one of the workspace screens.
     */
    private void onDropExternal(final int[] touchXY, final CellLayout cellLayout, DragSource d) {}


    private void getFinalPositionForDropAnimation(int[] loc, float[] scaleXY,
                                                  DragView dragView, CellLayout layout, ItemInfo info, int[] targetCell, boolean scale,
                                                  final View finalView) {
    }

    public void setFinalTransitionTransform() {
        if (isSwitchingState()) {
            mCurrentScale = getScaleX();
            setScaleX(mStateTransitionAnimation.getFinalScale());
            setScaleY(mStateTransitionAnimation.getFinalScale());
        }
    }

    public void resetTransitionTransform() {
        if (isSwitchingState()) {
            setScaleX(mCurrentScale);
            setScaleY(mCurrentScale);
        }
    }

    /**
     * Return the current CellInfo describing our current drag; this method exists
     * so that Launcher can sync this object with the correct info when the activity is created/
     * destroyed
     */
    public CellInfo getDragInfo() {
        return mDragInfo;
    }

    /**
     * Calculate the nearest cell where the given object would be dropped.
     * <p>
     * pixelX and pixelY should be in the coordinate system of layout
     */
    int[] findNearestArea(int pixelX, int pixelY,
                          int spanX, int spanY, CellLayout layout, int[] recycle) {
        return layout.findNearestAreaIgnoreOccupied(
                pixelX, pixelY, spanX, spanY, recycle);
    }

    void setup(DragController dragController) {
        //mSpringLoadedDragController = new SpringLoadedDragController(mLauncher);
        mDragController = dragController;

        // hardware layers on children are enabled on startup, but should be disabled until
        // needed
        updateChildrenLayersEnabled();
    }

    /**
     * Called at the end of a drag which originated on the workspace.
     */
    public void onDropCompleted(final View target, final DragSource d,
                                final boolean success) {
    }


    @Override
    public boolean scrollLeft() {
        boolean result = false;
        if (!mIsSwitchingState && workspaceInScrollableState()) {
            result = super.scrollLeft();
        }
        Folder openFolder = Folder.getOpen(mLauncher);
        if (openFolder != null) {
            openFolder.completeDragExit();
        }
        return result;
    }

    @Override
    public boolean scrollRight() {
        boolean result = false;
        if (!mIsSwitchingState && workspaceInScrollableState()) {
            result = super.scrollRight();
        }
        Folder openFolder = Folder.getOpen(mLauncher);
        if (openFolder != null) {
            openFolder.completeDragExit();
        }
        return result;
    }

    /**
     * Returns a specific CellLayout
     */
    CellLayout getParentCellLayoutForView(View v) {
        for (CellLayout layout : getWorkspaceAndHotseatCellLayouts()) {
            if (layout.getShortcutsAndWidgets().indexOfChild(v) > -1) {
                return layout;
            }
        }
        return null;
    }


    public View getHomescreenIconByItemId(final int id) {
        return getFirstMatch((info, v) -> info != null && info.id == id);
    }

    public LauncherAppWidgetHostView getWidgetForAppWidgetId(final int appWidgetId) {
        return (LauncherAppWidgetHostView) getFirstMatch((info, v) ->
                (info instanceof LauncherAppWidgetInfo) &&
                        ((LauncherAppWidgetInfo) info).appWidgetId == appWidgetId);
    }

    public View getFirstMatch(final ItemOperator operator) {
        final View[] value = new View[1];
        mapOverItems(new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View v) {
                if (operator.evaluate(info, v)) {
                    value[0] = v;
                    return true;
                }
                return false;
            }
        });
        return value[0];
    }

    void clearDropTargets() {
        mapOverItems(new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View v) {
                if (v instanceof DropTarget) {
                    mDragController.removeDropTarget((DropTarget) v);
                }
                // not done, process all the shortcuts
                return false;
            }
        });
    }

    public boolean isOverlayShown() {
        return mOverlayShown;
    }

    /**
     * Calls {@link #snapToPage(int)} on the {@link #DEFAULT_PAGE}, then requests focus on it.
     */
    public void moveToDefaultScreen() {
        int page = DEFAULT_PAGE;
        if (!workspaceInModalState() && getNextPage() != page) {
            snapToPage(page);
        }
        View child = getChildAt(page);
        if (child != null) {
            child.requestFocus();
        }
    }

    /**
     * Set the given view's pivot point to match the workspace's, so that it scales together. Since
     * both this view and workspace can move, transform the point manually instead of using
     * dragLayer.getDescendantCoordRelativeToSelf and related methods.
     */
    public void setPivotToScaleWithSelf(View sibling) {
        sibling.setPivotY(getPivotY() + getTop()
                - sibling.getTop() - sibling.getTranslationY());
        sibling.setPivotX(getPivotX() + getLeft()
                - sibling.getLeft() - sibling.getTranslationX());
    }

    @Override
    public int getExpectedHeight() {
        return getMeasuredHeight() <= 0 || !mIsLayoutValid
                ? DeviceProfile.getDeviceProfile().heightPx : getMeasuredHeight();
    }

    @Override
    public int getExpectedWidth() {
        return getMeasuredWidth() <= 0 || !mIsLayoutValid
                ? DeviceProfile.getDeviceProfile().widthPx : getMeasuredWidth();
    }

    @Override
    protected boolean isSignificantMove(float absoluteDelta, int pageOrientedSize) {
        DeviceProfile deviceProfile = LauncherApplication.Companion.getDeviceProfile();

        return absoluteDelta > deviceProfile.availableWidthPx * SIGNIFICANT_MOVE_SCREEN_WIDTH_PERCENTAGE;
    }


    private class StateTransitionListener extends AnimatorListenerAdapter
            implements AnimatorUpdateListener {

        @Override
        public void onAnimationUpdate(ValueAnimator anim) {
            mTransitionProgress = anim.getAnimatedFraction();
        }

        @Override
        public void onAnimationStart(Animator animation) {
            onStartStateTransition();
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            onEndStateTransition();
        }
    }
}
