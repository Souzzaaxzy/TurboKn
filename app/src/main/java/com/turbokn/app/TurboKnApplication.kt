package com.turbokn.app

import android.app.Application
import rikka.shizuku.Shizuku

class TurboKnApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // The user service is bound lazily by the ViewModel once permission is granted.
        Shizuku.addBinderDeadListener {
            // Binder died: the UI observes ShizukuShell state and reacts.
        }
    }
}
