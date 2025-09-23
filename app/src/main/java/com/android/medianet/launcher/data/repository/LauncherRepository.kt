package com.android.medianet.launcher.data.repository

import com.android.launcher.data.model.CardData
import com.android.medianet.launcher.data.db.LauncherDataSource
import com.android.medianet.launcher.data.db.WorkspaceItems
import kotlinx.coroutines.flow.Flow

class LauncherRepository(private val launcherDataSource: LauncherDataSource){


    suspend fun loadWorkspace() : Flow<WorkspaceItems>{
        return launcherDataSource.loadWorkspace()
    }

}