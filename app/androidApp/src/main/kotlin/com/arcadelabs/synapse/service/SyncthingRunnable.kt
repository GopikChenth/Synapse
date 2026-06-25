package com.arcadelabs.synapse.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicReference

class SyncthingRunnable(
    private val context: Context,
    private val commandType: CommandType
) {
    enum class CommandType {
        SERVE,
        GENERATE,
        DEVICE_ID
    }

    private val processRef = AtomicReference<Process?>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(onExit: (exitCode: Int) -> Unit) {
        scope.launch {
            val binary = File(context.applicationInfo.nativeLibraryDir, "libsyncthingnative.so")
            if (!binary.exists()) {
                Log.e(TAG, "Syncthing core binary missing at ${binary.absolutePath}")
                onExit(-1)
                return@launch
            }

            val command = when (commandType) {
                CommandType.SERVE -> listOf(binary.absolutePath, "serve", "--no-browser", "--no-restart")
                CommandType.GENERATE -> listOf(binary.absolutePath, "generate")
                CommandType.DEVICE_ID -> listOf(binary.absolutePath, "device-id")
            }

            val pb = ProcessBuilder(command)
            val env = pb.environment()
            env["HOME"] = context.filesDir.absolutePath
            env["STHOMEDIR"] = context.filesDir.absolutePath
            env["STMONITORED"] = "1"
            env["STNOUPGRADE"] = "1"
            env["SQLITE_TMPDIR"] = context.cacheDir.absolutePath

            try {
                Log.i(TAG, "Launching Syncthing daemon...")
                val process = pb.start()
                processRef.set(process)

                // Read output streams asynchronously using Coroutines
                launch {
                    try {
                        val reader = BufferedReader(InputStreamReader(process.inputStream))
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            Log.d(TAG_NATIVE, line ?: "")
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error reading stdout", e)
                    }
                }

                launch {
                    try {
                        val reader = BufferedReader(InputStreamReader(process.errorStream))
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            Log.e(TAG_NATIVE, line ?: "")
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error reading stderr", e)
                    }
                }

                val exitCode = process.waitFor()
                Log.i(TAG, "Syncthing process exited with code $exitCode")
                processRef.set(null)
                onExit(exitCode)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute Syncthing", e)
                onExit(-2)
            }
        }
    }

    fun stop() {
        processRef.get()?.destroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "SyncthingRunnable"
        private const val TAG_NATIVE = "SyncthingDaemon"
    }
}
