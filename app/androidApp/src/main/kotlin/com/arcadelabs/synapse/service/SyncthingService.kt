package com.arcadelabs.synapse.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class SyncthingService : Service() {

    private val binder = SyncthingBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var runnable: SyncthingRunnable? = null
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiMulticastLock: WifiManager.MulticastLock? = null
    private var runConditionMonitor: RunConditionMonitor? = null

    inner class SyncthingBinder : Binder() {
        fun getService(): SyncthingService = this@SyncthingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Call startForeground immediately to prevent ForegroundServiceDidNotStartInTimeException
        startForeground(NOTIFICATION_ID, createNotification("Synapse is starting..."))
        acquireLocks()

        runConditionMonitor = RunConditionMonitor(this) { isMet ->
            val prefs = getSharedPreferences("synapse_prefs", Context.MODE_PRIVATE)
            val behaviorStr = prefs.getString("run_behavior", "FOLLOW")
            if (behaviorStr == "FOLLOW") {
                if (isMet) {
                    startSyncthingProcessOnly()
                } else {
                    stopSyncthingProcessOnly()
                }
            }
        }
        runConditionMonitor?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START) {
            val prefs = getSharedPreferences("synapse_prefs", Context.MODE_PRIVATE)
            val behaviorStr = prefs.getString("run_behavior", "FOLLOW")
            when (behaviorStr) {
                "FORCE_START" -> {
                    updateNotification("Syncthing is running")
                    startSyncthingProcessOnly()
                }
                "FORCE_STOP" -> {
                    stopSyncthing()
                }
                else -> { // FOLLOW
                    val isWifiConnected = runConditionMonitor?.let { monitor ->
                        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                        val activeNetwork = connectivityManager.activeNetwork
                        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
                        capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                    } ?: false
                    
                    if (isWifiConnected) {
                        updateNotification("Syncthing is running")
                        startSyncthingProcessOnly()
                    } else {
                        updateNotification("Syncthing is waiting for run conditions")
                        stopSyncthingProcessOnly()
                    }
                }
            }
        } else if (action == ACTION_STOP) {
            stopSyncthing()
        }
        return START_NOT_STICKY
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun startSyncthingProcessOnly() {
        if (runnable != null) return

        updateNotification("Syncthing is running")

        runnable = SyncthingRunnable(this, SyncthingRunnable.CommandType.SERVE).apply {
            start { exitCode ->
                Log.i(TAG, "Runnable exited with code $exitCode")
                runnable?.stop()
                runnable = null
                if (exitCode == 3) {
                    startSyncthingProcessOnly()
                }
            }
        }
    }

    private fun stopSyncthingProcessOnly() {
        runnable?.stop()
        runnable = null

        updateNotification("Syncthing is waiting for run conditions")
    }

    private fun stopSyncthing() {
        runnable?.stop()
        runnable = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        // Use indefinite acquire() — Syncthing is a long-running daemon and the
        // 10-minute cap would let the device sleep mid-sync. The lock is released
        // explicitly in releaseLocks() called from onDestroy().
        @Suppress("WakelockTimeout")
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Synapse::WakeLock").apply {
            acquire()
        }

        val wm = getApplicationContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiMulticastLock = wm.createMulticastLock("Synapse::MulticastLock").apply {
            setReferenceCounted(true)
            acquire()
        }
    }

    private fun releaseLocks() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        if (wifiMulticastLock?.isHeld == true) {
            wifiMulticastLock?.release()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runConditionMonitor?.stop()
        stopSyncthing()
        releaseLocks()
        serviceScope.cancel()
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Synapse Sync Engine")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Synapse Sync Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "SyncthingService"
        private const val CHANNEL_ID = "synapse_service_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.arcadelabs.synapse.ACTION_START"
        const val ACTION_STOP = "com.arcadelabs.synapse.ACTION_STOP"
    }
}
