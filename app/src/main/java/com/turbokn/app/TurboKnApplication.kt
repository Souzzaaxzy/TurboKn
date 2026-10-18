package com.turbokn.app

import android.app.Application
import android.util.Log
import rikka.shizuku.Shizuku

class TurboKnApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // The user service is bound lazily by the ViewModel once permission is
        // granted. Here we only keep the binder state observable so the UI can
        // react to Shizuku being killed or restarted.
        Shizuku.addBinderDeadListener {
            Log.i(TAG, "Shizuku binder died")
        }
        Shizuku.addBinderReceivedListenerSticky {
            Log.i(TAG, "Shizuku binder received")
        }
    }

    private companion object {
        const val TAG = "TurboKnApplication"
    }
}
