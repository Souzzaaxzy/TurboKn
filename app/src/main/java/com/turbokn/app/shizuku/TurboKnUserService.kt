package com.turbokn.app.shizuku

import android.content.Context
import android.system.Os
import android.util.Log
import androidx.annotation.Keep
import com.turbokn.app.ITurboKnService
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Runs inside the process spawned by the Shizuku server, which means this code
 * executes with the `shell` UID (2000) instead of the app's own sandbox UID.
 */
class TurboKnUserService : ITurboKnService.Stub {

    @Keep
    constructor(context: Context) {
        Log.i(TAG, "created with context=$context")
    }

    constructor() {
        Log.i(TAG, "created")
    }

    override fun destroy() {
        System.exit(0)
    }

    override fun whoami(): String = "uid=${Os.getuid()} pid=${Os.getpid()}"

    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    output.append(line).append('\n')
                    line = reader.readLine()
                }
            }

            if (!process.waitFor(EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                output.append("timed out after ${EXEC_TIMEOUT_SECONDS}s")
            }
            output.toString().trim()
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    private companion object {
        const val TAG = "TurboKnUserService"
        const val EXEC_TIMEOUT_SECONDS = 15L
    }
}
