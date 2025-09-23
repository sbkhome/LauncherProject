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

import static android.view.WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentCallbacks2;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.os.Trace;
import android.os.UserHandle;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LifecycleOwner;

import com.android.medianet.launcher.LauncherApplication;
import com.android.medianet.launcher.R;
import com.android.medianet.launcher.middlelayer.IconCache;
import com.android.medianet.launcher.presentation.viewmodel.LauncherModel;
import com.android.medianet.launcher.presentation.viewmodel.LauncherViewModel;
import com.android.medianet.launcher.presentation.views.dragndrop.DragController;
import com.android.medianet.launcher.presentation.views.dragndrop.DragLayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Default launcher application.
 */
public class Launcher extends AppCompatActivity {
    public static final String TAG = "Launcher";



    private static final float BOUNCE_ANIMATION_TENSION = 1.3f;

    /**
     * IntentStarter uses request codes starting with this. This must be greater than all activity
     * request codes used internally.
     */
    protected static final int REQUEST_LAST = 100;

    public static final String INTENT_ACTION_ALL_APPS_TOGGLE =
            "launcher.intent_action_all_apps_toggle";

    private static final int ON_ACTIVITY_RESULT_ANIMATION_DELAY = 500;

    // How long to wait before the new-shortcut animation automatically pans the workspace
    @VisibleForTesting public static final int NEW_APPS_PAGE_MOVE_DELAY = 500;
    private static final int NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS = 5;
    @VisibleForTesting public static final int NEW_APPS_ANIMATION_DELAY = 500;

    private final ModelCallbacks mModelCallbacks = createModelCallbacks();



    Workspace mWorkspace;
    DragLayer mDragLayer;

    Hotseat mHotseat;

    private DropTargetBar mDropTargetBar;


    ActivityAllAppsContainerView<Launcher> mAppsView;
    AllAppsTransitionController mAllAppsController;

    private LauncherModel mModel;

    private IconCache mIconCache;

    private LauncherViewModel launcherViewModel;

    private SharedPreferences mSharedPrefs;
    private ActivityResultInfo mPendingActivityResult;

    private PendingRequestArgs mPendingRequestArgs;
    // Request id for any pending activity result
    protected int mPendingActivityRequestCode = -1;


    protected DragController mDragController;
    // If true, overlay callbacks are deferred
    private boolean mDeferOverlayCallbacks;
    private final Runnable mDeferredOverlayCallbacks = this::checkIfOverlayStillDeferred;

    protected long mLastTouchUpTime = -1;

    private boolean mIsColdStartupAfterReboot;
    private boolean mForceConfigUpdate;

    private boolean mIsNaturalScrollingEnabled;


    @Override
    @TargetApi(Build.VERSION_CODES.S)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LauncherApplication launcherApplication = ((LauncherApplication)getApplicationContext());
        mModel = launcherApplication.mModel;
        mIconCache = launcherApplication.mIconCache;

        //mSharedPrefs = LauncherPrefs.getPrefs(this);


        initDragController();
        //mAllAppsController = new AllAppsTransitionController(this);

        setupViews();
        if (!mModel.addCallbacksAndLoad(this)) {

        }
        setContentView(getRootView());

        //getRootView().dispatchInsets();

        getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        setTitle(R.string.home_screen);


    }


    public void configureViewModel(){
        launcherViewModel.getLoadWorkspaceItemLiveData().observe((LifecycleOwner) this, data -> {

        });
    }

    protected ModelCallbacks createModelCallbacks() {
        return new ModelCallbacks(this);
    }



    /**
     * Provide {@link OnBackAnimationCallback} in below order:
     * <ol>
     *  <li> auto cancel action mode handler
     *  <li> drag handler
     *  <li> view handler
     *  <li> registered {@link BackPressHandler}
     *  <li> state handler
     * </ol>
     *
     * A back gesture (a single click on back button, or a swipe back gesture that contains a series
     * of swipe events) should be handled by the same handler from above list. For a new back
     * gesture, a new handler should be regenerated.
     *
     * Note that state handler will always be handling the back press event if the previous 3 don't.
     */
    @NonNull
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    protected OnBackAnimationCallback getOnBackAnimationCallback() {
        // #1 auto cancel action mode handler
        if (isInAutoCancelActionMode()) {
            return this::finishAutoCancelActionMode;
        }

        // #2 drag handler
        if (mDragController.isDragging()) {
            return mDragController::cancelDrag;
        }

        // #3 view handler
        AbstractFloatingView topView =
                AbstractFloatingView.getTopOpenView(Launcher.this);
        if (topView != null && topView.canHandleBack()) {
            return topView;
        }

        // #4 Custom back handlers
        for (BackPressHandler handler : mBackPressedHandlers) {
            if (handler.canHandleBack()) {
                return handler;
            }
        }

        // #5 state handler
        return new OnBackAnimationCallback() {
            @Override
            public void onBackStarted(BackEvent backEvent) {
                Launcher.this.onBackStarted();
            }

            @Override
            public void onBackInvoked() {
                onStateBack();
            }

            @Override
            public void onBackProgressed(@NonNull BackEvent backEvent) {
                mStateManager.getState().onBackProgressed(
                        Launcher.this, backEvent.getProgress());
            }

            @Override
            public void onBackCancelled() {
                Launcher.this.onBackCancelled();
            }
        };
    }


    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        mRotationHelper.setCurrentTransitionRequest(REQUEST_NONE);
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        // Always update device profile when multi window mode changed.
        initDeviceProfile(mDeviceProfile.inv);
        dispatchDeviceProfileChanged();
    }

    /**
     * Initializes the drag controller.
     */
    protected void initDragController() {
        mDragController = new LauncherDragController(this);
    }





    /**
     * Process any pending activity result if it was put on hold for any reason like item binding.
     */
    public void processActivityResult() {
        if (mPendingActivityResult != null) {
            handleActivityResult(mPendingActivityResult.requestCode,
                    mPendingActivityResult.resultCode, mPendingActivityResult.data);
            mPendingActivityResult = null;
        }
    }

    private void handleActivityResult(
            final int requestCode, final int resultCode, final Intent data) {
        if (isWorkspaceLoading()) {
            // process the result once the workspace has loaded.
            mPendingActivityResult = new ActivityResultInfo(requestCode, resultCode, data);
            return;
        }
        mPendingActivityResult = null;

        if (requestCode == REQUEST_HOME_ROLE) {
            if (resultCode != RESULT_OK) {
                Toast.makeText(
                        this,
                        this.getString(R.string.set_default_home_app,
                                this.getString(R.string.derived_app_name)),
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        // Reset the startActivity waiting flag
        final PendingRequestArgs requestArgs = mPendingRequestArgs;
        setWaitingForResult(null);
        if (requestArgs == null) {
            return;
        }

        final int pendingAddWidgetId = requestArgs.getWidgetId();

        Runnable exitSpringLoaded = MULTI_SELECT_EDIT_MODE.get() ? null
                : () -> mStateManager.goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);

        if (requestCode == REQUEST_BIND_APPWIDGET) {
            // This is called only if the user did not previously have permissions to bind widgets
            final int appWidgetId = data != null ?
                    data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
            if (resultCode == RESULT_CANCELED) {
                completeTwoStageWidgetDrop(RESULT_CANCELED, appWidgetId, requestArgs);
                mWorkspace.removeExtraEmptyScreenDelayed(
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false, exitSpringLoaded);
            } else if (resultCode == RESULT_OK) {
                addAppWidgetImpl(
                        appWidgetId, requestArgs, null,
                        requestArgs.getWidgetHandler(),
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY);
            }
            return;
        }

        boolean isWidgetDrop = (requestCode == REQUEST_PICK_APPWIDGET ||
                requestCode == REQUEST_CREATE_APPWIDGET);

        // We have special handling for widgets
        if (isWidgetDrop) {
            final int appWidgetId;
            int widgetId = data != null ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    : -1;
            if (widgetId < 0) {
                appWidgetId = pendingAddWidgetId;
            } else {
                appWidgetId = widgetId;
            }

            final int result;
            if (appWidgetId < 0 || resultCode == RESULT_CANCELED) {
                Log.e(TAG, "Error: appWidgetId (EXTRA_APPWIDGET_ID) was not " +
                        "returned from the widget configuration activity.");
                result = RESULT_CANCELED;
                completeTwoStageWidgetDrop(result, appWidgetId, requestArgs);
                mWorkspace.removeExtraEmptyScreenDelayed(
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false,
                        () -> getStateManager().goToState(NORMAL));
            } else {
                CellPos presenterPos = getCellPosMapper().mapModelToPresenter(requestArgs);
                if (requestArgs.container == CONTAINER_DESKTOP) {
                    // When the screen id represents an actual screen (as opposed to a rank)
                    // we make sure that the drop page actually exists.
                    int newScreenId = ensurePendingDropLayoutExists(presenterPos.screenId);
                    requestArgs.screenId = getCellPosMapper().mapPresenterToModel(
                            presenterPos.cellX, presenterPos.cellY, newScreenId, CONTAINER_DESKTOP)
                                    .screenId;
                }
                final CellLayout dropLayout =
                        mWorkspace.getScreenWithId(presenterPos.screenId);

                dropLayout.setDropPending(true);
                final Runnable onComplete = new Runnable() {
                    @Override
                    public void run() {
                        completeTwoStageWidgetDrop(resultCode, appWidgetId, requestArgs);
                        dropLayout.setDropPending(false);
                    }
                };
                mWorkspace.removeExtraEmptyScreenDelayed(
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false, onComplete);
            }
            return;
        }

        if (requestCode == REQUEST_RECONFIGURE_APPWIDGET
                || requestCode == REQUEST_BIND_PENDING_APPWIDGET) {
            if (resultCode == RESULT_OK) {
                // Update the widget view.
                completeAdd(requestCode, data, pendingAddWidgetId, requestArgs);
            }
            // Leave the widget in the pending state if the user canceled the configure.
            return;
        }

        if (requestCode == REQUEST_CREATE_SHORTCUT) {
            // Handle custom shortcuts created using ACTION_CREATE_SHORTCUT.
            if (resultCode == RESULT_OK && requestArgs.container != ItemInfo.NO_ID) {
                completeAdd(requestCode, data, -1, requestArgs);
                mWorkspace.removeExtraEmptyScreenDelayed(
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false, exitSpringLoaded);

            } else if (resultCode == RESULT_CANCELED) {
                mWorkspace.removeExtraEmptyScreenDelayed(
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false, exitSpringLoaded);
            }
        }

        mDragLayer.clearAnimatedView();
    }

    @Override
    public void onActivityResult(
            final int requestCode, final int resultCode, final Intent data) {
        mPendingActivityRequestCode = -1;
        handleActivityResult(requestCode, resultCode, data);
    }

    /**
     * Check to see if a given screen id exists. If not, create it at the end, return the new id.
     *
     * @param screenId the screen id to check
     * @return the new screen, or screenId if it exists
     */
    private int ensurePendingDropLayoutExists(int screenId) {
        CellLayout dropLayout = mWorkspace.getScreenWithId(screenId);
        if (dropLayout == null) {
            // it's possible that the add screen was removed because it was
            // empty and a re-bind occurred
            mWorkspace.addExtraEmptyScreens();
            IntSet emptyPagesAdded = mWorkspace.commitExtraEmptyScreens();
            return emptyPagesAdded.isEmpty() ? -1 : emptyPagesAdded.getArray().get(0);
        }
        return screenId;
    }



    @Override
    protected void onStop() {
        super.onStop();
        if (mDeferOverlayCallbacks) {
            checkIfOverlayStillDeferred();
        } else {
            mOverlayManager.onActivityStopped();
        }
        hideKeyboard();
        logStopAndResume(false /* isResume */);
        mAppWidgetHolder.setActivityStarted(false);
        NotificationListener.removeNotificationsChangedListener(getPopupDataProvider());
        FloatingIconView.resetIconLoadResult();
        AccessibilityManagerCompat.sendTestProtocolEventToTest(
                this, LAUNCHER_ACTIVITY_STOPPED_MESSAGE);
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    @CallSuper
    protected void onDeferredResumed() {
        logStopAndResume(true /* isResume */);

        // Process any items that were added while Launcher was away.
        ItemInstallQueue.INSTANCE.get(this)
                .resumeModelPush(FLAG_ACTIVITY_PAUSED);

        // Refresh shortcuts if the permission changed.
        mModel.validateModelDataOnResume();

        // Set the notification listener and fetch updated notifications when we resume
        NotificationListener.addNotificationsChangedListener(mPopupDataProvider);

        DiscoveryBounce.showForHomeIfNeeded(this);
        mAppWidgetHolder.setActivityResumed(true);

        // Listen for IME changes to keep state up to date.
        getRootView().setWindowInsetsAnimationCallback(
                new WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    @Override
                    public WindowInsets onProgress(WindowInsets windowInsets,
                            List<WindowInsetsAnimation> windowInsetsAnimations) {
                        return windowInsets;
                    }

                    @Override
                    public void onEnd(WindowInsetsAnimation animation) {
                        WindowInsets insets = getRootView().getRootWindowInsets();
                        boolean isImeVisible =
                                insets != null && insets.isVisible(WindowInsets.Type.ime());
                        getStatsLogManager().keyboardStateManager().setKeyboardState(
                                isImeVisible ? SHOW : HIDE);
                    }
                });
    }

    private void logStopAndResume(boolean isResume) {
        if (mModelCallbacks.getPendingExecutor() != null) return;
        int pageIndex = mWorkspace.isOverlayShown() ? -1 : mWorkspace.getCurrentPage();
        int statsLogOrdinal = mStateManager.getState().statsLogOrdinal;

        StatsLogManager.EventEnum event;
        StatsLogManager.StatsLogger logger = getStatsLogManager().logger();
        if (isResume) {
            logger.withSrcState(LAUNCHER_STATE_BACKGROUND)
                .withDstState(mStateManager.getState().statsLogOrdinal);
            event = LAUNCHER_ONRESUME;
        } else { /* command == Action.Command.STOP */
            logger.withSrcState(mStateManager.getState().statsLogOrdinal)
                    .withDstState(LAUNCHER_STATE_BACKGROUND);
            event = LAUNCHER_ONSTOP;
        }

        if (statsLogOrdinal == LAUNCHER_STATE_HOME && mWorkspace != null) {
            logger.withContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                    .setWorkspace(
                            LauncherAtom.WorkspaceContainer.newBuilder()
                                    .setPageIndex(pageIndex)).build());
        }
        logger.log(event);
    }

    private void scheduleDeferredCheck() {
        mHandler.removeCallbacks(mDeferredOverlayCallbacks);
        postAsyncCallback(mHandler, mDeferredOverlayCallbacks);
    }


    @Override
    public void onStateSetStart(LauncherState state) {
        super.onStateSetStart(state);
        if (mDeferOverlayCallbacks) {
            scheduleDeferredCheck();
        }
        addActivityFlags(ACTIVITY_STATE_TRANSITION_ACTIVE);

        if (state == SPRING_LOADED || state == EDIT_MODE) {
            // Prevent any Un/InstallShortcutReceivers from updating the db while we are
            // not on homescreen
            ItemInstallQueue.INSTANCE.get(this).pauseModelPush(FLAG_DRAG_AND_DROP);
            getRotationHelper().setCurrentStateRequest(REQUEST_LOCK);

            mWorkspace.showPageIndicatorAtCurrentScroll();
            mWorkspace.setClipChildren(false);
        }
        // When multiple pages are visible, show persistent page indicator
        mWorkspace.getPageIndicator().setShouldAutoHide(!state.hasFlag(FLAG_MULTI_PAGE));

        mPrevLauncherState = mStateManager.getCurrentStableState();
        if (mPrevLauncherState != state && ALL_APPS.equals(state)
                // Making sure mAllAppsSessionLogId is null to avoid double logging.
                && mAllAppsSessionLogId == null) {
            // creates new instance ID since new all apps session is started.
            mAllAppsSessionLogId = new InstanceIdSequence().newInstanceId();
            if (getAllAppsEntryEvent().isPresent()) {
                getStatsLogManager().logger()
                        .withContainerInfo(ContainerInfo.newBuilder()
                                .setWorkspace(WorkspaceContainer.newBuilder()
                                        .setPageIndex(getWorkspace().getCurrentPage())).build())
                        .log(getAllAppsEntryEvent().get());
            }
        }
        updateDisallowBack();
    }


    @Override
    public void onStateSetEnd(LauncherState state) {
        super.onStateSetEnd(state);
        getAppWidgetHolder().setStateIsNormal(state == LauncherState.NORMAL);
        getWorkspace().setClipChildren(!state.hasFlag(FLAG_MULTI_PAGE));

        finishAutoCancelActionMode();
        removeActivityFlags(ACTIVITY_STATE_TRANSITION_ACTIVE);

        // dispatch window state changed
        getWindow().getDecorView().sendAccessibilityEvent(TYPE_WINDOW_STATE_CHANGED);
        AccessibilityManagerCompat.sendStateEventToTest(this, state.ordinal);

        if (state == NORMAL) {
            // Re-enable any Un/InstallShortcutReceiver and now process any queued items
            ItemInstallQueue.INSTANCE.get(this)
                    .resumeModelPush(FLAG_DRAG_AND_DROP);

            // Clear any rotation locks when going to normal state
            getRotationHelper().setCurrentStateRequest(REQUEST_NONE);
        }

        if (ALL_APPS.equals(mPrevLauncherState) && !ALL_APPS.equals(state)
                // Making sure mAllAppsSessionLogId is not null to avoid double logging.
                && mAllAppsSessionLogId != null) {
            getAppsView().reset(false);
            getAllAppsExitEvent().ifPresent(getStatsLogManager().logger()::log);
            mAllAppsSessionLogId = null;
        }

        // Set screen title for Talkback
        setTitle(state.getTitle());
    }


    @Override
    protected void onResume() {
        super.onResume();

        DragView.removeAllViews(this);

    }

    @Override
    protected void onPause() {
        super.onPause();
        mDragController.cancelDrag();
        mLastTouchUpTime = -1;
        mDropTargetBar.animateToVisibility(false);

    }


    /**
     * Finds all the views we need and configure them properly.
     */
    protected void setupViews() {
        setContentView(R.layout.launcher);

        mDragLayer = findViewById(R.id.drag_layer);
        mWorkspace = mDragLayer.findViewById(R.id.workspace);
        mWorkspace.initParentViews(mDragLayer);

        mHotseat = findViewById(R.id.hotseat);
        mDragLayer.setDragController(mDragController);

        mWorkspace.setup(mDragController);
        // Until the workspace is bound, ensure that we keep the wallpaper offset locked to the
        // default state, otherwise we will update to the wrong offsets in RTL
        mWorkspace.lockWallpaperToDefaultPage();
        if (!enableSmartspaceRemovalToggle()) {
            mWorkspace.bindAndInitFirstWorkspaceScreen();
        }
        mDragController.addDragListener(mWorkspace);

        // Get the search/delete/uninstall bar
        mDropTargetBar = mDragLayer.findViewById(R.id.drop_target_bar);

        // Setup Apps
        mAppsView = findViewById(R.id.apps_view);
        mAppsView.setAllAppsTransitionController(mAllAppsController);

        // Setup Scrim
        mScrimView = findViewById(R.id.scrim_view);

        // Setup the drag controller (drop targets have to be added in reverse order in priority)
        mDropTargetBar.setup(mDragController);
        mAllAppsController.setupViews(mScrimView, mAppsView);

        mWorkspace.getPageIndicator().setShouldAutoHide(true);
        mWorkspace.getPageIndicator().setPaintColor(Themes.getAttrBoolean(
                this, R.attr.isWorkspaceDarkText) ? Color.BLACK : Color.WHITE);
    }

    /**
     * Add a shortcut to the workspace or to a Folder.
     *
     * @param data The intent describing the shortcut.
     */
    protected void completeAddShortcut(Intent data, int container, int screenId, int cellX,
            int cellY, PendingRequestArgs args) {
        if (args.getRequestCode() != REQUEST_CREATE_SHORTCUT) {
            return;
        }

        int[] cellXY = mTmpAddItemCellCoordinates;
        CellLayout layout = getCellLayout(container, screenId);

        WorkspaceItemInfo info = PinRequestHelper.createWorkspaceItemFromPinItemRequest(
                    this, PinRequestHelper.getPinItemRequest(data), 0);
        if (info == null) {
            Log.e(TAG, "Unable to parse a valid shortcut result");
            return;
        }

        if (container < 0) {
            // Adding a shortcut to the Workspace.
            final View view = mItemInflater.inflateItem(info, getModelWriter());
            boolean foundCellSpan = false;
            // First we check if we already know the exact location where we want to add this item.
            if (cellX >= 0 && cellY >= 0) {
                cellXY[0] = cellX;
                cellXY[1] = cellY;
                foundCellSpan = true;

                DragObject dragObject = new DragObject(getApplicationContext());
                dragObject.dragInfo = info;
                // If appropriate, either create a folder or add to an existing folder
                if (mWorkspace.createUserFolderIfNecessary(view, container, layout, cellXY, 0,
                        true, dragObject)) {
                    return;
                }
                if (mWorkspace.addToExistingFolderIfNecessary(view, layout, cellXY, 0, dragObject,
                        true)) {
                    return;
                }
            } else {
                foundCellSpan = layout.findCellForSpan(cellXY, 1, 1);
            }

            if (!foundCellSpan) {
                mWorkspace.onNoCellFound(layout, info, /* logInstanceId= */ null);
                return;
            }

            getModelWriter().addItemToDatabase(info, container, screenId, cellXY[0], cellXY[1]);
            mWorkspace.addInScreen(view, info);
        } else {
            // Adding a shortcut to a Folder.
            FolderIcon folderIcon = findFolderIcon(container);
            if (folderIcon != null) {
                FolderInfo folderInfo = (FolderInfo) folderIcon.getTag();
                folderInfo.add(info, args.rank, false);
            } else {
                Log.e(TAG, "Could not find folder with id " + container + " to add shortcut.");
            }
        }
    }

    @Override
    public @Nullable FolderIcon findFolderIcon(final int folderIconId) {
        return (FolderIcon) mWorkspace.getHomescreenIconByItemId(folderIconId);
    }



    private final ScreenOnListener mScreenOnListener = this::onScreenOnChanged;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mOverlayManager.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mOverlayManager.onDetachedFromWindow();
        closeContextMenu();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        NonConfigInstance instance = new NonConfigInstance();
        instance.config = new Configuration(mOldConfig);
        return instance;
    }


    @Override
    protected void onNewIntent(Intent intent) {
        if (Utilities.isRunningInTestHarness()) {
            Log.d(TestProtocol.PERMANENT_DIAG_TAG, "Launcher.onNewIntent: " + intent);
        }
        TraceHelper.INSTANCE.beginSection(ON_NEW_INTENT_EVT);
        super.onNewIntent(intent);

        boolean alreadyOnHome = hasWindowFocus() && ((intent.getFlags() &
                Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

        // Check this condition before handling isActionMain, as this will get reset.
        boolean shouldMoveToDefaultScreen = alreadyOnHome && isInState(NORMAL)
                && AbstractFloatingView.getTopOpenView(this) == null;
        boolean isActionMain = Intent.ACTION_MAIN.equals(intent.getAction());
        boolean internalStateHandled = ACTIVITY_TRACKER.handleNewIntent(this);

        if (isActionMain) {
            if (!internalStateHandled) {
                // In all these cases, only animate if we're already on home
                AbstractFloatingView.closeAllOpenViewsExcept(
                        this, isStarted(), AbstractFloatingView.TYPE_LISTENER);

                if (!isInState(NORMAL)) {
                    // Only change state, if not already the same. This prevents cancelling any
                    // animations running as part of resume
                    mStateManager.goToState(NORMAL, mStateManager.shouldAnimateStateChange());
                }

                // Reset the apps view
                if (!alreadyOnHome) {
                    mAppsView.reset(isStarted() /* animate */);
                }

                if (shouldMoveToDefaultScreen && !mWorkspace.isHandlingTouch()) {
                    mWorkspace.post(mWorkspace::moveToDefaultScreen);
                }
            }

            if (FeatureFlags.enableSplitContextually()) {
                handleSplitAnimationGoingToHome(LAUNCHER_SPLIT_SELECTION_EXIT_HOME);
            }
            mOverlayManager.hideOverlay(isStarted());
            handleGestureContract(intent);
        } else if (Intent.ACTION_ALL_APPS.equals(intent.getAction())) {
            showAllAppsFromIntent(alreadyOnHome);
        } else if (INTENT_ACTION_ALL_APPS_TOGGLE.equals(intent.getAction())) {
            toggleAllAppsSearch(alreadyOnHome);
        } else if (Intent.ACTION_SHOW_WORK_APPS.equals(intent.getAction())) {
            showAllAppsWithSelectedTabFromIntent(alreadyOnHome,
                    ActivityAllAppsContainerView.AdapterHolder.WORK);
        }

        TraceHelper.INSTANCE.endSection();
    }

    /** Handle animating away split placeholder view when user taps on home button */
    protected void handleSplitAnimationGoingToHome(EventEnum splitDismissReason) {
        // Overridden
    }

    /** Toggles Launcher All Apps with keyboard ready for search. */
    public void toggleAllAppsSearch() {
        toggleAllAppsSearch(/* alreadyOnHome= */ true);
    }

    protected void toggleAllAppsSearch(boolean alreadyOnHome) {
        if (getStateManager().isInStableState(ALL_APPS)) {
            getStateManager().goToState(NORMAL, alreadyOnHome);
        } else {
            if (mWorkspace.isOverlayShown()) {
                mOverlayManager.hideOverlay(/* animate */true);
            }
            AbstractFloatingView.closeAllOpenViews(this);
            getStateManager().goToState(ALL_APPS, true /* animated */,
                    new AnimationSuccessListener() {
                        @Override
                        public void onAnimationSuccess(Animator animator) {
                            if (mAppsView.getSearchUiManager().getEditText() != null) {
                                mAppsView.getSearchUiManager().getEditText().requestFocus();
                            }
                        }
                    });
        }
    }

    protected void showAllAppsFromIntent(boolean alreadyOnHome) {
        showAllAppsWithSelectedTabFromIntent(alreadyOnHome,
                ActivityAllAppsContainerView.AdapterHolder.MAIN);
    }

    private void showAllAppsWithSelectedTabFromIntent(boolean alreadyOnHome, int tab) {
        AbstractFloatingView.closeAllOpenViews(this);
        getStateManager().goToState(ALL_APPS, alreadyOnHome);
        if (mAppsView.isSearching()) {
            mAppsView.getSearchUiManager().resetSearch();
        }
        if (mAppsView.getCurrentPage() != tab) {
            mAppsView.switchToTab(tab);
        }
    }

    /**
     * Handles gesture nav contract
     */
    protected void handleGestureContract(Intent intent) {
        GestureNavContract gnc = GestureNavContract.fromIntent(intent);
        if (gnc != null) {
            AbstractFloatingView.closeOpenViews(this, false, TYPE_ICON_SURFACE);
            FloatingSurfaceView.show(this, gnc);
        }
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        IntSet synchronouslyBoundPages = mModelCallbacks.getSynchronouslyBoundPages();
        if (synchronouslyBoundPages != null) {
            synchronouslyBoundPages.forEach(screenId -> {
                int pageIndex = mWorkspace.getPageIndexForScreenId(screenId);
                if (pageIndex != PagedView.INVALID_PAGE) {
                    mWorkspace.restoreInstanceStateForChild(pageIndex);
                }
            });
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putIntArray(RUNTIME_STATE_CURRENT_SCREEN_IDS,
                mWorkspace.getCurrentPageScreenIds().getArray().toArray());
        outState.putInt(RUNTIME_STATE, mStateManager.getState().ordinal);

        AbstractFloatingView widgets = AbstractFloatingView
                .getOpenView(this, AbstractFloatingView.TYPE_WIDGETS_FULL_SHEET);
        if (widgets != null) {
            SparseArray<Parcelable> widgetsState = new SparseArray<>();
            widgets.saveHierarchyState(widgetsState);
            outState.putSparseParcelableArray(RUNTIME_STATE_WIDGET_PANEL, widgetsState);
        } else {
            outState.remove(RUNTIME_STATE_WIDGET_PANEL);
        }

        // We close any open folders and shortcut containers that are not safe for rebind,
        // and we need to make sure this state is reflected.
        AbstractFloatingView.closeAllOpenViewsExcept(
                this, isStarted() && !isForceInvisible(), TYPE_REBIND_SAFE);
        finishAutoCancelActionMode();

        if (mPendingRequestArgs != null) {
            outState.putParcelable(RUNTIME_STATE_PENDING_REQUEST_ARGS, mPendingRequestArgs);
        }
        outState.putInt(RUNTIME_STATE_PENDING_REQUEST_CODE, mPendingActivityRequestCode);

        if (mPendingActivityResult != null) {
            outState.putParcelable(RUNTIME_STATE_PENDING_ACTIVITY_RESULT, mPendingActivityResult);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ACTIVITY_TRACKER.onContextDestroyed(this);

        SettingsCache.INSTANCE.get(this).unregister(TOUCHPAD_NATURAL_SCROLLING,
                mNaturalScrollingChangedListener);
        ScreenOnTracker.INSTANCE.get(this).removeListener(mScreenOnListener);
        mWorkspace.removeFolderListeners();
        PluginManagerWrapper.INSTANCE.get(this).removePluginListener(this);

        mModel.removeCallbacks(this);
        mRotationHelper.destroy();

        mAppWidgetHolder.stopListening();
        mAppWidgetHolder.destroy();

        TextKeyListener.getInstance().release();
        mModelCallbacks.clearPendingBinds();
        LauncherAppState.getIDP(this).removeOnChangeListener(this);
        // if Launcher activity is recreated, {@link Window} including {@link ViewTreeObserver}
        // could be preserved in {@link ActivityThread#scheduleRelaunchActivity(IBinder)} if the
        // previous activity has not stopped, which could happen when wallpaper detects a color
        // changes while launcher is still loading.
        getRootView().getViewTreeObserver().removeOnPreDrawListener(mOnInitialBindListener);
        mOverlayManager.onActivityDestroyed();
        PillColorProvider.getInstance(mWorkspace.getContext()).unregisterObserver();
    }

    public LauncherAccessibilityDelegate getAccessibilityDelegate() {
        return mAccessibilityDelegate;
    }

    public DragController getDragController() {
        return mDragController;
    }

    @Override
    public DropTargetHandler getDropTargetHandler() {
        return new DropTargetHandler(this);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (requestCode != -1) {
            mPendingActivityRequestCode = requestCode;
        }
        super.startActivityForResult(intent, requestCode, options);
    }

    @Override
    public void startIntentSenderForResult(IntentSender intent, int requestCode,
            Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options) {
        if (requestCode != -1) {
            mPendingActivityRequestCode = requestCode;
        }
        try {
            super.startIntentSenderForResult(intent, requestCode,
                    fillInIntent, flagsMask, flagsValues, extraFlags, options);
        } catch (Exception e) {
            throw new ActivityNotFoundException();
        }
    }




    public void addPendingItem(PendingAddItemInfo info, int container, int screenId,
            int[] cell, int spanX, int spanY) {
        if (cell == null) {
            CellPos modelPos = getCellPosMapper().mapPresenterToModel(0, 0, screenId, container);
            info.screenId = modelPos.screenId;
        } else {
            CellPos modelPos = getCellPosMapper().mapPresenterToModel(
                    cell[0],  cell[1], screenId, container);
            info.screenId = modelPos.screenId;
            info.cellX = modelPos.cellX;
            info.cellY = modelPos.cellY;
        }
        info.container = container;
        info.spanX = spanX;
        info.spanY = spanY;

        if (info instanceof PendingAddWidgetInfo) {
            addAppWidgetFromDrop((PendingAddWidgetInfo) info);
        } else { // info can only be PendingAddShortcutInfo
            processShortcutFromDrop((PendingAddShortcutInfo) info);
        }
    }

    /**
     * Process a shortcut drop.
     */
    private void processShortcutFromDrop(PendingAddShortcutInfo info) {
        Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(info.componentName);
        setWaitingForResult(PendingRequestArgs.forIntent(REQUEST_CREATE_SHORTCUT, intent, info));
        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "start: processShortcutFromDrop");
        if (!info.getActivityInfo(this).startConfigActivity(this, REQUEST_CREATE_SHORTCUT)) {
            handleActivityResult(REQUEST_CREATE_SHORTCUT, RESULT_CANCELED, null);
        }
    }


    /**
     * Creates and adds new folder to CellLayout
     */
    public FolderIcon addFolder(CellLayout layout, int container, final int screenId, int cellX,
            int cellY) {
        final FolderInfo folderInfo = new FolderInfo();

        // Update the model
        getModelWriter().addItemToDatabase(folderInfo, container, screenId, cellX, cellY);

        // Create the view
        FolderIcon newFolder = FolderIcon.inflateFolderAndIcon(R.layout.folder_icon, this, layout,
                folderInfo);
        mWorkspace.addInScreen(newFolder, folderInfo);
        // Force measure the new folder icon
        CellLayout parent = mWorkspace.getParentCellLayoutForView(newFolder);
        parent.getShortcutsAndWidgets().measureChild(newFolder);
        return newFolder;
    }

    @Override
    public Rect getFolderBoundingBox() {
        // We need to bound the folder to the currently visible workspace area
        return getWorkspace().getPageAreaRelativeToDragLayer();
    }

    @Override
    public void updateOpenFolderPosition(int[] inOutPosition, Rect bounds, int width, int height) {
        int left = inOutPosition[0];
        int top = inOutPosition[1];
        DeviceProfile grid = getDeviceProfile();
        int distFromEdgeOfScreen = getWorkspace().getPaddingLeft();
        if (grid.isPhone && (grid.availableWidthPx - width) < 4 * distFromEdgeOfScreen) {
            // Center the folder if it is very close to being centered anyway, by virtue of
            // filling the majority of the viewport. ie. remove it from the uncanny valley
            // of centeredness.
            left = (grid.availableWidthPx - width) / 2;
        } else if (width >= bounds.width()) {
            // If the folder doesn't fit within the bounds, center it about the desired bounds
            left = bounds.left + (bounds.width() - width) / 2;
        }
        if (height >= bounds.height()) {
            // Folder height is greater than page height, center on page
            top = bounds.top + (bounds.height() - height) / 2;
        } else {
            // Folder height is less than page height, so bound it to the absolute open folder
            // bounds if necessary
            Rect folderBounds = grid.getAbsoluteOpenFolderBounds();
            left = Math.max(folderBounds.left, Math.min(left, folderBounds.right - width));
            top = Math.max(folderBounds.top, Math.min(top, folderBounds.bottom - height));
        }
        inOutPosition[0] = left;
        inOutPosition[1] = top;
    }

    /**
     * Unbinds the view for the specified item, and removes the item and all its children.
     *
     * @param v the view being removed.
     * @param itemInfo the {@link ItemInfo} for this view.
     * @param deleteFromDb whether or not to delete this item from the db.
     */
    public boolean removeItem(View v, final ItemInfo itemInfo, boolean deleteFromDb) {
        return removeItem(v, itemInfo, deleteFromDb, null);
    }

    /**
     * Unbinds the view for the specified item, and removes the item and all its children.
     *
     * @param v the view being removed.
     * @param itemInfo the {@link ItemInfo} for this view.
     * @param deleteFromDb whether or not to delete this item from the db.
     * @param reason the resaon for removal.
     */
    public boolean removeItem(View v, final ItemInfo itemInfo, boolean deleteFromDb,
            @Nullable final String reason) {
        if (itemInfo instanceof WorkspaceItemInfo) {
            View collectionIcon = mWorkspace.getHomescreenIconByItemId(itemInfo.container);
            if (collectionIcon instanceof FolderIcon) {
                // Remove the shortcut from the folder before removing it from launcher
                ((FolderInfo) collectionIcon.getTag()).remove((WorkspaceItemInfo) itemInfo, true);
            } else if (collectionIcon instanceof AppPairIcon appPairIcon) {
                removeItem(appPairIcon, appPairIcon.getInfo(), deleteFromDb,
                        "removing app pair because one of its member apps was removed");
            } else {
                mWorkspace.removeWorkspaceItem(v);
            }
            if (deleteFromDb) {
                getModelWriter().deleteItemFromDatabase(itemInfo, reason);
            }
        } else if (itemInfo instanceof CollectionInfo ci) {
            if (v instanceof FolderIcon) {
                ((FolderIcon) v).removeListeners();
            }
            mWorkspace.removeWorkspaceItem(v);
            if (deleteFromDb) {
                getModelWriter().deleteCollectionAndContentsFromDatabase(ci);
            }
        } else if (itemInfo instanceof LauncherAppWidgetInfo) {
            final LauncherAppWidgetInfo widgetInfo = (LauncherAppWidgetInfo) itemInfo;
            mWorkspace.removeWorkspaceItem(v);
            if (deleteFromDb) {
                getModelWriter().deleteWidgetInfo(widgetInfo, getAppWidgetHolder(), reason);
            }
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        TestLogging.recordKeyEvent(TestProtocol.SEQUENCE_MAIN, "Key event", event);
        return (event.getKeyCode() == KeyEvent.KEYCODE_HOME) || super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTouchInProgress = true;
                break;
            case MotionEvent.ACTION_UP:
                mLastTouchUpTime = SystemClock.uptimeMillis();
                // Follow through
            case MotionEvent.ACTION_CANCEL:
                mTouchInProgress = false;
                break;
        }
        TestLogging.recordMotionEvent(TestProtocol.SEQUENCE_MAIN, "Touch event", ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void onBackPressed() {
        getOnBackAnimationCallback().onBackInvoked();
    }

    protected void onBackStarted() {
        mStateManager.getState().onBackStarted(this);
    }

    protected void onStateBack() {
        mStateManager.getState().onBackInvoked(this);
    }

    protected void onBackCancelled() {
        mStateManager.getState().onBackCancelled(this);
    }

    protected void onScreenOnChanged(boolean isOn) {
        // Reset AllApps to its initial state only if we are not in the middle of
        // processing a multi-step drop
        if (!isOn && mPendingRequestArgs == null) {
            if (!isInState(NORMAL)) {
                onUiChangedWhileSleeping();
            }
            mStateManager.goToState(NORMAL);
        }
    }

    @Override
    public RunnableList startActivitySafely(View v, Intent intent, ItemInfo item) {
        if (!hasBeenResumed()) {
            RunnableList result = new RunnableList();
            // Workaround an issue where the WM launch animation is clobbered when finishing the
            // recents animation into launcher. Defer launching the activity until Launcher is
            // next resumed.
            addEventCallback(EVENT_RESUMED, () -> {
                RunnableList actualResult = startActivitySafely(v, intent, item);
                if (actualResult != null) {
                    actualResult.add(result::executeAllAndDestroy);
                } else {
                    result.executeAllAndDestroy();
                }
            });
            if (mOnDeferredActivityLaunchCallback != null) {
                mOnDeferredActivityLaunchCallback.run();
                mOnDeferredActivityLaunchCallback = null;
            }
            return result;
        }

        RunnableList result = super.startActivitySafely(v, intent, item);
        if (result != null && v instanceof BubbleTextView) {
            // This is set to the view that launched the activity that navigated the user away
            // from launcher. Since there is no callback for when the activity has finished
            // launching, enable the press state and keep this reference to reset the press
            // state when we return to launcher.
            BubbleTextView btv = (BubbleTextView) v;
            btv.setStayPressed(true);
            result.add(() -> btv.setStayPressed(false));
        }
        return result;
    }

    boolean isHotseatLayout(View layout) {
        // TODO: Remove this method
        return mHotseat != null && (layout == mHotseat);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // The widget preview db can result in holding onto over
            // 3MB of memory for caching which isn't necessary.
            SQLiteDatabase.releaseMemory();

            // This clears all widget bitmaps from the widget tray
            // TODO(hyunyoungs)
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        final boolean result = super.dispatchPopulateAccessibilityEvent(event);
        final List<CharSequence> text = event.getText();
        text.clear();
        // Populate event with a fake title based on the current state.
        // TODO: When can workspace be null?
        text.add(mWorkspace == null
                ? getString(R.string.home_screen)
                : mStateManager.getState().getDescription(this));
        return result;
    }

    @Override
    public IntSet getPagesToBindSynchronously(IntArray orderedScreenIds) {
        return mModelCallbacks.getPagesToBindSynchronously(orderedScreenIds);
    }

    @Override
    public void startBinding() {
        mModelCallbacks.startBinding();
    }

    @Override
    public void setIsFirstPagePinnedItemEnabled(boolean isFirstPagePinnedItemEnabled) {
        mModelCallbacks.setIsFirstPagePinnedItemEnabled(isFirstPagePinnedItemEnabled);
    }

    @Override
    public void bindScreens(IntArray orderedScreenIds) {
        mModelCallbacks.bindScreens(orderedScreenIds);
    }

    /**
     * Remove odd number because they are already included when isTwoPanels and add the pair screen
     * if not present.
     */
    private IntArray filterTwoPanelScreenIds(IntArray orderedScreenIds) {
        IntSet screenIds = IntSet.wrap(orderedScreenIds);
        orderedScreenIds.forEach(screenId -> {
            if (screenId % 2 == 1) {
                screenIds.remove(screenId);
                // In case the pair is not added, add it
                if (!mWorkspace.containsScreenId(screenId - 1)) {
                    screenIds.add(screenId - 1);
                }
            }
        });
        return screenIds.getArray();
    }

    @Override
    public void preAddApps() {
        mModelCallbacks.preAddApps();
    }

    @Override
    public void bindAppsAdded(IntArray newScreens, ArrayList<ItemInfo> addNotAnimated,
            ArrayList<ItemInfo> addAnimated) {
        mModelCallbacks.bindAppsAdded(newScreens, addNotAnimated, addAnimated);
    }

    /**
     * Bind the items start-end from the list.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void bindItems(final List<ItemInfo> items, final boolean forceAnimateIcons) {
        bindInflatedItems(items.stream()
                .map(i -> Pair.create(i, getItemInflater().inflateItem(i, getModelWriter())))
                .collect(Collectors.toList()),
                forceAnimateIcons ? new AnimatorSet() : null);
    }

    @Override
    public void bindInflatedItems(List<Pair<ItemInfo, View>> items) {
        bindInflatedItems(items, null);
    }

    /**
     * Bind all the items in the map, ignoring any null views
     *
     * @param boundAnim if non-null, uses it to create and play the bounce animation for added views
     */
    public void bindInflatedItems(
            List<Pair<ItemInfo, View>> shortcuts, @Nullable AnimatorSet boundAnim) {
        // Get the list of added items and intersect them with the set of items here
        Workspace<?> workspace = mWorkspace;
        int newItemsScreenId = -1;
        int index = 0;
        for (Pair<ItemInfo, View> e : shortcuts) {
            final ItemInfo item = e.first;

            // Remove colliding items.
            CellPos presenterPos = getCellPosMapper().mapModelToPresenter(item);
            if (item.container == CONTAINER_DESKTOP) {
                CellLayout cl = mWorkspace.getScreenWithId(presenterPos.screenId);
                if (cl != null && cl.isOccupied(presenterPos.cellX, presenterPos.cellY)) {
                    Object tag = cl.getChildAt(presenterPos.cellX, presenterPos.cellY).getTag();
                    String desc = "Collision while binding workspace item: " + item
                            + ". Collides with " + tag;
                    if (FeatureFlags.IS_STUDIO_BUILD) {
                        throw (new RuntimeException(desc));
                    } else {
                        getModelWriter().deleteItemFromDatabase(item, desc);
                        continue;
                    }
                }
            }

            View view = e.second;
            if (view == null) {
                continue;
            }
            if (enableWorkspaceInflation() && view instanceof LauncherAppWidgetHostView lv) {
                view = getAppWidgetHolder().attachViewToHostAndGetAttachedView(lv);
            }
            workspace.addInScreenFromBind(view, item);
            if (boundAnim != null) {
                // Animate all the applications up now
                view.setAlpha(0f);
                view.setScaleX(0f);
                view.setScaleY(0f);
                boundAnim.play(createNewAppBounceAnimation(view, index++));
                newItemsScreenId = presenterPos.screenId;
            }
        }

        // Animate to the correct page
        if (boundAnim != null && newItemsScreenId > -1) {
            int currentScreenId = mWorkspace.getScreenIdForPageIndex(mWorkspace.getNextPage());
            final int newScreenIndex = mWorkspace.getPageIndexForScreenId(newItemsScreenId);
            final Runnable startBounceAnimRunnable = boundAnim::start;

            if (canAnimatePageChange() && newItemsScreenId != currentScreenId) {
                // We post the animation slightly delayed to prevent slowdowns
                // when we are loading right after we return to launcher.
                mWorkspace.postDelayed(() -> {
                    closeOpenViews(false);
                    mWorkspace.snapToPage(newScreenIndex);
                    mWorkspace.postDelayed(startBounceAnimRunnable, NEW_APPS_ANIMATION_DELAY);
                }, NEW_APPS_PAGE_MOVE_DELAY);
            } else {
                mWorkspace.postDelayed(startBounceAnimRunnable, NEW_APPS_ANIMATION_DELAY);
            }
        }
        workspace.requestLayout();
    }

    /**
     * Add the views for a widget to the workspace.
     */
    public void bindAppWidget(LauncherAppWidgetInfo item) {
        View view = mItemInflater.inflateItem(item, getModelWriter());
        if (view != null) {
            mWorkspace.addInScreen(view, item);
            mWorkspace.requestLayout();
        }
    }

    /**
     * Restores a pending widget.
     *
     * @param appWidgetId The app widget id
     */
    private LauncherAppWidgetInfo completeRestoreAppWidget(int appWidgetId, int finalRestoreFlag) {
        LauncherAppWidgetHostView view = mWorkspace.getWidgetForAppWidgetId(appWidgetId);
        if (!(view instanceof PendingAppWidgetHostView)) {
            Log.e(TAG, "Widget update called, when the widget no longer exists.");
            return null;
        }

        LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) view.getTag();
        info.restoreStatus = finalRestoreFlag;
        if (info.restoreStatus == LauncherAppWidgetInfo.RESTORE_COMPLETED) {
            info.pendingItemInfo = null;
        }

        PendingAppWidgetHostView pv = (PendingAppWidgetHostView) view;
        if (pv.isReinflateIfNeeded()) {
            pv.reInflate();
        }

        getModelWriter().updateItemInDatabase(info);
        return info;
    }

    /**
     * Call back when ModelCallbacks finish binding the Launcher data.
     */
    @TargetApi(Build.VERSION_CODES.S)
    public void bindComplete(int workspaceItemCount, boolean isBindSync) {
        if (mOnInitialBindListener != null) {
            getRootView().getViewTreeObserver().removeOnPreDrawListener(mOnInitialBindListener);
            mOnInitialBindListener = null;
        }
        if (!isBindSync) {
            mStartupLatencyLogger
                    .logCardinality(workspaceItemCount)
                    .logEnd(LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC);
        }
        MAIN_EXECUTOR.getHandler().postAtFrontOfQueue(() -> {
            mStartupLatencyLogger
                    .logEnd(LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION)
                    .log()
                    .reset();
        });
    }

    @Override
    public void onInitialBindComplete(IntSet boundPages, RunnableList pendingTasks,
            RunnableList onCompleteSignal, int workspaceItemCount, boolean isBindSync) {
        mModelCallbacks.onInitialBindComplete(boundPages, pendingTasks, onCompleteSignal,
                workspaceItemCount, isBindSync);
        if (mIsColdStartupAfterReboot) {
            Trace.endAsyncSection(COLD_STARTUP_TRACE_METHOD_NAME,
                    COLD_STARTUP_TRACE_COOKIE);
        }
    }

    /**
     * Callback saying that there aren't any more items to bind.
     * <p>
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void finishBindingItems(IntSet pagesBoundFirst) {
        mModelCallbacks.finishBindingItems(pagesBoundFirst);
    }

    private boolean canAnimatePageChange() {
        if (mDragController.isDragging()) {
            return false;
        } else {
            return (SystemClock.uptimeMillis() - mLastTouchUpTime)
                    > (NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS * 1000);
        }
    }

    /**
     * Similar to {@link #getFirstMatch} but optimized to finding a suitable view for the app close
     * animation.
     *
     * @param svi The StableViewInfo of the preferred item to match to if it exists or null
     * @param packageName The package name of the app to match.
     * @param user The user of the app to match.
     * @param supportsAllAppsState If true and we are in All Apps state, looks for view in All Apps.
     *                             Else we only looks on the workspace.
     */
    public @Nullable View getFirstMatchForAppClose(
            @Nullable StableViewInfo svi, String packageName,
            UserHandle user, boolean supportsAllAppsState) {
        final Predicate<ItemInfo> preferredItem = svi == null ? i -> false : svi::matches;
        final Predicate<ItemInfo> packageAndUserAndApp = info ->
                info != null
                        && info.itemType == ITEM_TYPE_APPLICATION
                        && info.user.equals(user)
                        && info.getTargetComponent() != null
                        && TextUtils.equals(info.getTargetComponent().getPackageName(),
                        packageName);

        if (supportsAllAppsState && isInState(LauncherState.ALL_APPS)) {
            AllAppsRecyclerView activeRecyclerView = mAppsView.getActiveRecyclerView();
            View v = getFirstMatch(Collections.singletonList(activeRecyclerView),
                    preferredItem, packageAndUserAndApp);

            if (v != null && activeRecyclerView.computeVerticalScrollOffset() > 0) {
                RectF locationBounds = new RectF();
                FloatingIconView.getLocationBoundsForView(this, v, false, locationBounds,
                        new Rect());
                if (locationBounds.top < mAppsView.getHeaderBottom()) {
                    // Icon is covered by scrim, return null to play fallback animation.
                    return null;
                }
            }

            return v;
        }

        // Look for the item inside the folder at the current page
        Folder folder = Folder.getOpen(this);
        if (folder != null) {
            View v = getFirstMatch(Collections.singletonList(
                    folder.getContent().getCurrentCellLayout().getShortcutsAndWidgets()),
                    preferredItem,
                    packageAndUserAndApp);
            if (v == null) {
                folder.close(isStarted() && !isForceInvisible());
            } else {
                return v;
            }
        }

        List<ViewGroup> containers = new ArrayList<>(mWorkspace.getPanelCount() + 1);
        containers.add(mWorkspace.getHotseat().getShortcutsAndWidgets());
        mWorkspace.forEachVisiblePage(page
                -> containers.add(((CellLayout) page).getShortcutsAndWidgets()));

        // Order: Preferred item by itself or in folder, then by matching package/user
        return getFirstMatch(containers, preferredItem, forFolderMatch(preferredItem),
                packageAndUserAndApp, forFolderMatch(packageAndUserAndApp));
    }

    /**
     * Finds the first view matching the ordered operators across the given viewgroups in order.
     * @param containers List of ViewGroups to scan, in order of preference.
     * @param operators List of operators, in order starting from best matching operator.
     */
    @Nullable
    private static View getFirstMatch(Iterable<ViewGroup> containers,
            final Predicate<ItemInfo>... operators) {
        for (Predicate<ItemInfo> operator : operators) {
            for (ViewGroup container : containers) {
                View match = mapOverViewGroup(container, operator);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    /** Convert a {@link View} to {@link Bitmap}. */
    private static Bitmap getBitmapFromView(@Nullable View view) {
        if (view == null) {
            return null;
        }
        Bitmap returnedBitmap =
                Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(returnedBitmap);
        view.draw(canvas);
        return returnedBitmap;
    }

    /**
     * Returns the first view matching the operator in the given ViewGroups, or null if none.
     * Forward iteration matters.
     */
    @Nullable
    private static View mapOverViewGroup(ViewGroup container, Predicate<ItemInfo> op) {
        final int itemCount = container.getChildCount();
        for (int itemIdx = 0; itemIdx < itemCount; itemIdx++) {
            View item = container.getChildAt(itemIdx);
            if (item.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (item instanceof ViewGroup viewGroup) {
                View view = mapOverViewGroup(viewGroup, op);
                if (view != null) {
                    return view;
                }
            }
            if (item.getTag() instanceof ItemInfo itemInfo && op.test(itemInfo)) {
                return item;
            }
        }
        return null;
    }



    @Override
    @TargetApi(Build.VERSION_CODES.S)
    @UiThread
    public void bindAllApplications(AppInfo[] apps, int flags,
            Map<PackageUserKey, Integer> packageUserKeytoUidMap) {
        mModelCallbacks.bindAllApplications(apps, flags, packageUserKeytoUidMap);

    }

    /**
     * See {@code LauncherBindingDelegate}
     */
    @Override
    public void bindDeepShortcutMap(HashMap<ComponentKey, Integer> deepShortcutMapCopy) {
        mModelCallbacks.bindDeepShortcutMap(deepShortcutMapCopy);
    }

    @Override
    public void bindIncrementalDownloadProgressUpdated(AppInfo app) {
        mModelCallbacks.bindIncrementalDownloadProgressUpdated(app);
    }

    @Override
    public void bindWidgetsRestored(ArrayList<LauncherAppWidgetInfo> widgets) {
        mModelCallbacks.bindWidgetsRestored(widgets);
    }

    /**
     * See {@code LauncherBindingDelegate}
     */
    @Override
    public void bindWorkspaceItemsChanged(List<WorkspaceItemInfo> updated) {
        mModelCallbacks.bindWorkspaceItemsChanged(updated);
    }

    /**
     * See {@code LauncherBindingDelegate}
     */
    @Override
    public void bindRestoreItemsChange(HashSet<ItemInfo> updates) {
        mModelCallbacks.bindRestoreItemsChange(updates);
    }

    /**
     * See {@code LauncherBindingDelegate}
     */
    @Override
    public void bindWorkspaceComponentsRemoved(Predicate<ItemInfo> matcher) {
        mModelCallbacks.bindWorkspaceComponentsRemoved(matcher);
    }




    /**
     * Populates the list of shortcuts. Logic delegated to {@Link KeyboardShortcutsDelegate}.
     *
     * @param data The data list to populate with shortcuts.
     * @param menu The current menu, which may be null.
     * @param deviceId The id for the connected device the shortcuts should be provided for.
     */
    @Override
    public void onProvideKeyboardShortcuts(
            List<KeyboardShortcutGroup> data, Menu menu, int deviceId) {
        mKeyboardShortcutsDelegate.onProvideKeyboardShortcuts(data, menu, deviceId);
        super.onProvideKeyboardShortcuts(data, menu, deviceId);
    }

    /**
     * Logic delegated to {@Link KeyboardShortcutsDelegate}.
     * @param keyCode The value in event.getKeyCode().
     * @param event Description of the key event.
     */
    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        Boolean result = mKeyboardShortcutsDelegate.onKeyShortcut(keyCode, event);
        return result != null ? result : super.onKeyShortcut(keyCode, event);
    }

    /**
     * Logic delegated to {@Link KeyboardShortcutsDelegate}.
     * @param keyCode The value in event.getKeyCode().
     * @param event Description of the key event.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Boolean result = mKeyboardShortcutsDelegate.onKeyDown(keyCode, event);
        return result != null ? result : super.onKeyDown(keyCode, event);
    }

    /**
     * Logic delegated to {@Link KeyboardShortcutsDelegate}.
     * @param keyCode The value in event.getKeyCode().
     * @param event Description of the key event.
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Boolean result = mKeyboardShortcutsDelegate.onKeyUp(keyCode, event);
        return result != null ? result : super.onKeyUp(keyCode, event);
    }



    public void onDragLayerHierarchyChanged() {
        updateDisallowBack();
    }




    /** To be overridden by subclasses */
    public boolean isSplitSelectionActive() {
        // Overridden
        return false;
    }


    @Override
    public void returnToHomescreen() {
        super.returnToHomescreen();
        getStateManager().goToState(LauncherState.NORMAL);
    }

    public void closeOpenViews() {
        closeOpenViews(true);
    }

    protected void closeOpenViews(boolean animate) {
        AbstractFloatingView.closeAllOpenViews(this, animate);
    }

    protected LauncherAccessibilityDelegate createAccessibilityDelegate() {
        return new LauncherAccessibilityDelegate(this);
    }

    /** Enables/disabled the hotseat prediction icon long press edu for testing. */
    @VisibleForTesting
    public void enableHotseatEdu(boolean enable) {}


    /**
     * Just a wrapper around the type cast to allow easier tracking of calls.
     */
    public static <T extends Launcher> T cast(ActivityContext activityContext) {
        return (T) activityContext;
    }

    public boolean supportsAdaptiveIconAnimation(View clickedView) {
        return false;
    }

    /**
     * Animates Launcher elements during a transition to the All Apps page.
     *
     * @param progress Transition progress from 0 to 1; where 0 => home and 1 => all apps.
     */
    public void onAllAppsTransition(float progress) {
        // No-Op
    }


    }



    public boolean isWorkspaceLocked() {
        return isWorkspaceLoading() || mPendingRequestArgs != null;
    }

    public boolean isWorkspaceLoading() {
        return mModelCallbacks.getWorkspaceLoading();
    }

    @Override
    public boolean isBindingItems() {
        return isWorkspaceLoading();
    }

    /**
     * Returns true if a touch interaction is in progress
     */
    public boolean isTouchInProgress() {
        return mTouchInProgress;
    }

    public boolean isDraggingEnabled() {
        // We prevent dragging when we are loading the workspace as it is possible to pick up a view
        // that is subsequently removed from the workspace in startBinding().
        return !isWorkspaceLoading();
    }

    public boolean isNaturalScrollingEnabled() {
        return mIsNaturalScrollingEnabled;
    }

    public void setWaitingForResult(PendingRequestArgs args) {
        mPendingRequestArgs = args;
    }

    /**
     * Call this after onCreate to set or clear overlay.
     */
    public void setLauncherOverlay(LauncherOverlayTouchProxy overlay) {
        mWorkspace.setLauncherOverlay(overlay);
    }

    public Workspace<?> getWorkspace() {
        return mWorkspace;
    }

    public Hotseat getHotseat() {
        return mHotseat;
    }


    public LauncherModel getModel() {
        return mModel;
    }

    /**
     * Returns the ModelWriter writer, make sure to call the function every time you want to use it.
     */
    public ModelWriter getModelWriter() {
        return mModelWriter;
    }

    public SharedPreferences getSharedPrefs() {
        return mSharedPrefs;
    }

    public int getOrientation() {
        return mOldConfig.orientation;
    }


}
