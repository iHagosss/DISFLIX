package com.tvplayer.app

import android.app.Application
import android.util.Log
import androidx.multidex.MultiDex
import com.tvplayer.app.stremio.StremioManager

class StremioPlayerApp : Application() {

    lateinit var stremioManager: StremioManager
        private set

    override fun onCreate() {
        super.onCreate()
        MultiDex.install(this)
        instance = this

        stremioManager = StremioManager(this)
        stremioManager.initialize()

        Log.i(TAG, "StremioPlayerApp initialized")
    }

    override fun onTerminate() {
        super.onTerminate()
        stremioManager.shutdown()
    }

    companion object {
        private const val TAG = "StremioPlayerApp"

        lateinit var instance: StremioPlayerApp
            private set
    }
}
