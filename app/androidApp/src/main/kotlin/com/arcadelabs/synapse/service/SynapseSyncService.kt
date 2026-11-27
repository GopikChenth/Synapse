package com.arcadelabs.synapse.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arcadelabs.synapse.MainActivity
import com.arcadelabs.synapse.R
import kotlinx.coroutines.*

class SynapseSyncService : Service() {

    private val binder = SynapseSyncBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var runnable: EngineRunnable? = null
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiMulticastLock: WifiManager.MulticastLock? = null
    private var runConditionMonitor: RunConditionMonitor? = null
    private var widgetPollingJob: Job? = null

    inner class SynapseSyncBinder : Binder() {
        fun getService(): SynapseSyncService = this@SynapseSyncService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Call startForeground immediately to prevent ForegroundServiceDidNotStartInTimeException
        startForeground(NOTIFICATION_ID, createNotification("Synapse is starting..."))
        acquireLocks()
        isInstanceRunning = true

        runConditionMonitor = RunConditionMonitor(this) { isMet ->
            val prefs = getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
            val behaviorStr = prefs.getString("run_behavior", "FOLLOW")
            if (behaviorStr == "FOLLOW") {
                if (isMet) {
                    startEngineProcessOnly()
                } else {
                    stopEngineProcessOnly()
                }
            }
        }
        runConditionMonitor?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START) {
            val prefs = getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
            val behaviorStr = prefs.getString("run_behavior", "FOLLOW")
            when (behaviorStr) {
                "FORCE_START" -> {
                    updateNotification("Synapse is running")
                    startEngineProcessOnly()
                }
                "FORCE_STOP" -> {
                    stopEngine()
                }
                else -> { // FOLLOW
                    val isWifiConnected = runConditionMonitor?.let { monitor ->
                        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                        val activeNetwork = connectivityManager.activeNetwork
                        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
                        capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                    } ?: false
                    
                    if (isWifiConnected) {
                        updateNotification("Synapse is running")
                        startEngineProcessOnly()
                    } else {
                        updateNotification("Synapse is waiting for run conditions")
                        stopEngineProcessOnly()
                    }
                }
            }
        } else if (action == ACTION_STOP) {
            stopEngine()
        }
        return START_NOT_STICKY
    }

    private fun updateNotification(content: String, progressPercent: Int? = null) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(content, progressPercent))
    }

    private fun startEngineProcessOnly() {
        if (runnable != null) return

        _serviceState.value = ServiceLifecycleState.Starting
        updateNotification("Synapse is running")

        runnable = EngineRunnable(this, EngineRunnable.CommandType.SERVE).apply {
            start { exitCode ->
                Log.i(TAG, "Runnable exited with code $exitCode")
                runnable?.stop()
                runnable = null
                stopWidgetPolling()
                stopDynamicIsland()
                if (exitCode == 3) {
                    startEngineProcessOnly()
                } else if (exitCode != 0) {
                    _serviceState.value = ServiceLifecycleState.Error("Process exited with code $exitCode", exitCode)
                } else {
                    _serviceState.value = ServiceLifecycleState.Stopped
                }
            }
        }
        _serviceState.value = ServiceLifecycleState.Running()
        startWidgetPolling()
        startDynamicIsland()
    }

    private fun stopEngineProcessOnly() {
        runnable?.stop()
        runnable = null
        stopWidgetPolling()
        stopDynamicIsland()
        _serviceState.value = ServiceLifecycleState.WaitingForConditions
        updateNotification("Synapse is waiting for run conditions")
    }

    private fun stopEngine() {
        runnable?.stop()
        runnable = null
        stopWidgetPolling()
        stopDynamicIsland()
        _serviceState.value = ServiceLifecycleState.Stopped
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startWidgetPolling() {
        if (widgetPollingJob != null) return
        widgetPollingJob = serviceScope.launch {
            val koin = org.koin.core.context.GlobalContext.get()
            val apiClient = koin.get<com.arcadelabs.synapse.core.network.SynapseApiClient>()
            // Only push widget/notification updates when the shown state actually changes.
            var lastText: String? = null

            while (isActive) {
                // The engine answering /rest/system/status is the only thing we can state for certain.
                // (No sync percentage: the engine does not report real completion.)
                val engineUp = try {
                    apiClient.systemStatus()
                    true
                } catch (_: Exception) {
                    false
                }
                val text = if (engineUp) "Synapse is running" else "Starting..."

                if (text != lastText) {
                    lastText = text
                    try {
                        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this@SynapseSyncService)
                        val thisWidget = android.content.ComponentName(this@SynapseSyncService, com.arcadelabs.synapse.widget.SynapseWidgetProvider::class.java)
                        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                        if (appWidgetIds.isNotEmpty()) {
                            com.arcadelabs.synapse.widget.SynapseWidgetProvider.updateWidgetState(
                                context = this@SynapseSyncService,
                                appWidgetManager = appWidgetManager,
                                appWidgetIds = appWidgetIds,
                                isRunning = true,
                                progressText = if (engineUp) "Running" else "Starting..."
                            )
                        }
                    } catch (_: Exception) {}

                    // Ongoing notification: indeterminate progress only while starting.
                    if (engineUp) updateNotification(text) else updateNotification(text, 0)
                }

                delay(5000)
            }
        }
    }

    private fun stopWidgetPolling() {
        widgetPollingJob?.cancel()
        widgetPollingJob = null

        try {
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this)
            val thisWidget = android.content.ComponentName(this, com.arcadelabs.synapse.widget.SynapseWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (appWidgetIds.isNotEmpty()) {
                com.arcadelabs.synapse.widget.SynapseWidgetProvider.updateWidgetState(
                    context = this,
                    appWidgetManager = appWidgetManager,
                    appWidgetIds = appWidgetIds,
                    isRunning = false,
                    progressText = "Stopped"
                )
            }

            // Custom Dynamic Island updates removed

            // Update ongoing notification
            updateNotification("Stopped")
        } catch (_: Exception) {}

    }

    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        // Use indefinite acquire() — Synapse is a long-running daemon and the
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
        isInstanceRunning = false
        super.onDestroy()
        runConditionMonitor?.stop()
        stopEngine()
        releaseLocks()
        serviceScope.cancel()
    }

    private fun createNotification(content: String, progressPercent: Int? = null): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val canPostPromoted = if (Build.VERSION.SDK_INT >= 35) {
            try {
                manager.canPostPromotedNotifications()
            } catch (_: Throwable) {
                false
            }
        } else {
            false
        }

        val smallIconRes = R.drawable.synapse_logo_silhouette
        val statusColor = if (runnable != null) {
            android.graphics.Color.parseColor("#4CAF50") // Green
        } else {
            android.graphics.Color.parseColor("#F44336") // Red
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)

        if (canPostPromoted && progressPercent != null) {
            val progressStyle = Notification.ProgressStyle()
                .setProgressPoints(listOf(
                    Notification.ProgressStyle.Point(0),
                    Notification.ProgressStyle.Point(100)
                ))
                .setProgress(progressPercent)
                .setProgressIndeterminate(progressPercent == 0 || progressPercent == 100)

            return Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Synapse")
                .setContentText(content)
                .setSmallIcon(smallIconRes)
                .setColor(statusColor)
                .setContentIntent(pendingIntent)
                .setStyle(progressStyle)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .addExtras(Bundle().apply {
                    putBoolean("android.requestPromotedOngoing", true)
                })
                .build()
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Synapse")
            .setContentText(content)
            .setSmallIcon(smallIconRes)
            .setColor(statusColor)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progressPercent != null) {
            builder.setProgress(100, progressPercent, progressPercent == 0 || progressPercent == 100)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        }

        return builder.build()
    }


    private fun startDynamicIsland() {
        // Disabled
    }

    private fun stopDynamicIsland() {
        // Disabled
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
        private const val TAG = "SynapseSyncService"
        private const val CHANNEL_ID = "synapse_service_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.arcadelabs.synapse.ACTION_START"
        const val ACTION_STOP = "com.arcadelabs.synapse.ACTION_STOP"

        private val _serviceState = kotlinx.coroutines.flow.MutableStateFlow<ServiceLifecycleState>(ServiceLifecycleState.Stopped)
        val serviceState: kotlinx.coroutines.flow.StateFlow<ServiceLifecycleState> = _serviceState

        var isInstanceRunning: Boolean
            get() = _serviceState.value is ServiceLifecycleState.Running || _serviceState.value is ServiceLifecycleState.Starting
            set(value) {
                if (!value) _serviceState.value = ServiceLifecycleState.Stopped
            }
    }
}
