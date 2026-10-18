package com.turbokn.app.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.turbokn.app.BuildConfig
import com.turbokn.app.ITurboKnService
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs

/**
 * Owns the lifecycle of the Shizuku user service and exposes a single entry
 * point for running shell commands with elevated privileges.
 */
object ShizukuShell {

    private const val TAG = "ShizukuShell"
    private const val PERMISSION_REQUEST_CODE = 1001

    @Volatile
    private var service: ITurboKnService? = null

    private val userServiceArgs = UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, TurboKnUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(false)
        .version(1)

    val isBinderAlive: Boolean
        get() = Shizuku.pingBinder()

    val isPermissionGranted: Boolean
        get() = if (!isBinderAlive) false else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    val isReady: Boolean
        get() = isBinderAlive && isPermissionGranted && service != null

    /**
     * Waits until the user service is connected. Binding is asynchronous, so a
     * caller that needs the service immediately must await it instead of
     * assuming failure.
     */
    suspend fun awaitReady(timeoutMs: Long = 3000): Boolean {
        if (isReady) return true
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isReady) return true
            kotlinx.coroutines.delay(100)
        }
        return isReady
    }

    fun requestPermission() {
        if (!isBinderAlive) return
        if (Shizuku.isPreV11()) return
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = ITurboKnService.Stub.asInterface(binder)
            Log.i(TAG, "user service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            Log.i(TAG, "user service disconnected")
        }
    }

    fun bind() {
        if (!isPermissionGranted) return
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (e: Exception) {
            Log.e(TAG, "bind failed", e)
        }
    }

    fun unbind() {
        try {
            Shizuku.unbindUserService(userServiceArgs, connection, true)
        } catch (e: Exception) {
            Log.e(TAG, "unbind failed", e)
        }
        service = null
    }

    /**
     * Runs a command through the user service. Returns the combined stdout/stderr,
     * or a prefixed error string when the service is unavailable.
     */
    fun exec(command: String): String {
        val svc = service ?: return "error: serviço Shizuku não conectado"
        return try {
            svc.exec(command)
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }
}
