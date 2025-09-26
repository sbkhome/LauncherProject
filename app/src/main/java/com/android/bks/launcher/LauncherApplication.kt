package com.android.bks.launcher

import android.app.Application
import android.content.Context

class LauncherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        context = this
    }

    companion object{
        lateinit var context : Context

        @JvmStatic
        fun getAppContext() : Context{
            return context
        }
    }
}