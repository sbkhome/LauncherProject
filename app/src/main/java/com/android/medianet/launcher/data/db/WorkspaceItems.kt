package com.android.medianet.launcher.data.db

import com.android.medianet.launcher.data.model.FolderInfo
import com.android.medianet.launcher.data.model.ItemInfo
import com.android.medianet.launcher.middlelayer.ShortcutInfo

data class WorkspaceItems(
    var workspaceItems: ArrayList<ItemInfo>,
    var folderMaps : MutableMap<Long, FolderInfo>,
    var shortcutInfoItems : ArrayList<ShortcutInfo>
)
