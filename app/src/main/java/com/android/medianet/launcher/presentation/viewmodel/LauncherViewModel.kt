package com.android.medianet.launcher.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.medianet.launcher.data.db.WorkspaceItems
import com.android.medianet.launcher.data.repository.LauncherRepository
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

class LauncherViewModel(private val launcherRepository: LauncherRepository)  : ViewModel() {

    private val _loadWorkspaceItemLiveData = MutableLiveData<WorkspaceItems>()
    val loadWorkspaceItemLiveData  : LiveData<WorkspaceItems> = _loadWorkspaceItemLiveData


    fun loadWorkspaceItems() = viewModelScope.launch{

        launcherRepository.loadWorkspace().collect{
            _loadWorkspaceItemLiveData.postValue(it)
        }

    }

}