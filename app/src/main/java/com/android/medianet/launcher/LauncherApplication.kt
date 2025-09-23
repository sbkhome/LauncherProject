package com.android.medianet.launcher

import android.app.Application
import android.graphics.drawable.Icon
import com.android.medianet.launcher.data.db.DeviceProfile
import com.android.medianet.launcher.middlelayer.IconCache
import com.android.medianet.launcher.presentation.viewmodel.LauncherModel

class LauncherApplication : Application() {

    lateinit var mIconCache : IconCache
    lateinit var mModel : LauncherModel

    override fun onCreate() {
        super.onCreate()
        setContext(this)
        mIconCache = IconCache(this)
        mModel = LauncherModel(this, mIconCache)
    }


    companion object{
        private lateinit var context : LauncherApplication
        private val deviceProfile = DeviceProfile()
        fun setContext(context : LauncherApplication){
            this.context = context
        }
        fun getApplication() : LauncherApplication{
            return context
        }

        fun getDeviceProfile() : DeviceProfile{
            return deviceProfile
        }
    }


}