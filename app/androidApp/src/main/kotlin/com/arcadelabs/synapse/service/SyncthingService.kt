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

class SyncthingService : Service() {

    private val binder = SyncthingBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var runnable: SyncthingRunnable? = null
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiMulticastLock: WifiManager.MulticastLock? = null
    private var runConditionMonitor: RunConditionMonitor? = null
    private var widgetPollingJob: Job? = null

    private val prefChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "enable_dynamic_island") {
            val enabled = prefs.getBoolean("enable_dynamic_island", false)
            if (enabled) {
                startDynamicIsland()
            } else {
                stopDynamicIsland()
            }
        }
    }

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
        isInstanceRunning = true

        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)

        runConditionMonitor = RunConditionMonitor(this) { isMet ->
            val prefs = getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
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
            val prefs = getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
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

    private fun updateNotification(content: String, progressPercent: Int? = null) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(content, progressPercent))
    }

    private fun startSyncthingProcessOnly() {
        if (runnable != null) return

        updateNotification("Syncthing is running")

        runnable = SyncthingRunnable(this, SyncthingRunnable.CommandType.SERVE).apply {
            start { exitCode ->
                Log.i(TAG, "Runnable exited with code $exitCode")
                runnable?.stop()
                runnable = null
                stopWidgetPolling()
                stopDynamicIsland()
                if (exitCode == 3) {
                    startSyncthingProcessOnly()
                }
            }
        }
        startWidgetPolling()
        startDynamicIsland()
    }

    private fun stopSyncthingProcessOnly() {
        runnable?.stop()
        runnable = null
        stopWidgetPolling()
        stopDynamicIsland()

        updateNotification("Syncthing is waiting for run conditions")
    }

    private fun stopSyncthing() {
        runnable?.stop()
        runnable = null
        stopWidgetPolling()
        stopDynamicIsland()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startWidgetPolling() {
        if (widgetPollingJob != null) return
        widgetPollingJob = serviceScope.launch {
            var lastInBytes = 0L
            var lastOutBytes = 0L
            var lastTime = System.currentTimeMillis()
            
            val downloadHistory = mutableListOf<Long>()
            val uploadHistory = mutableListOf<Long>()

            while (isActive) {
                try {
                    val koin = org.koin.core.context.GlobalContext.get()
                    val apiClient = koin.get<com.arcadelabs.synapse.core.network.SyncthingApiClient>()

                    val status = apiClient.systemStatus()
                    val connections = apiClient.systemConnections()
                    val config = apiClient.systemConfig()

                    var totalBytes = 0L
                    var totalInSyncBytes = 0L
                    var totalGlobalBytes = 0L

                    config.folders.forEach { folder ->
                        try {
                            val dbStatus = apiClient.dbStatus(folder.id)
                            totalBytes += dbStatus.localBytes
                            totalInSyncBytes += dbStatus.inSyncBytes
                            totalGlobalBytes += dbStatus.globalBytes
                        } catch (_: Exception) {}
                    }

                    val progressPercent = if (totalGlobalBytes > 0L) {
                        ((totalInSyncBytes.toDouble() / totalGlobalBytes.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                    } else {
                        100
                    }

                    val now = System.currentTimeMillis()
                    val elapsedSec = (now - lastTime).toDouble() / 1000.0
                    lastTime = now

                    val currentIn = connections.total.inBytesTotal
                    val currentOut = connections.total.outBytesTotal

                    val dlSpeed = if (lastInBytes > 0 && elapsedSec > 0) {
                        ((currentIn - lastInBytes) / elapsedSec).toLong().coerceAtLeast(0)
                    } else 0L
                    val ulSpeed = if (lastOutBytes > 0 && elapsedSec > 0) {
                        ((currentOut - lastOutBytes) / elapsedSec).toLong().coerceAtLeast(0)
                    } else 0L

                    lastInBytes = currentIn
                    lastOutBytes = currentOut

                    // Add to history
                    downloadHistory.add(dlSpeed)
                    uploadHistory.add(ulSpeed)
                    if (downloadHistory.size > 15) downloadHistory.removeAt(0)
                    if (uploadHistory.size > 15) uploadHistory.removeAt(0)

                    val peersCount = connections.connections.values.count { it.connected }

                    val chartBitmap = com.arcadelabs.synapse.widget.SynapseWidgetProvider.drawSpeedChart(
                        context = this@SyncthingService,
                        downloadHistory = downloadHistory,
                        uploadHistory = uploadHistory
                    )

                    val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this@SyncthingService)
                    val thisWidget = android.content.ComponentName(this@SyncthingService, com.arcadelabs.synapse.widget.SynapseWidgetProvider::class.java)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

                    if (appWidgetIds.isNotEmpty()) {
                        com.arcadelabs.synapse.widget.SynapseWidgetProvider.updateWidgetState(
                            context = this@SyncthingService,
                            appWidgetManager = appWidgetManager,
                            appWidgetIds = appWidgetIds,
                            isRunning = true,
                            peersCount = peersCount,
                            progressText = "$progressPercent% synced",
                            progressPercent = progressPercent,
                            chartBitmap = chartBitmap
                        )
                    }

                    // Custom Dynamic Island updates removed

                    // Update ongoing/status bar notification with active sync progress
                    updateNotification("$progressPercent% synced", progressPercent)
                } catch (e: Exception) {
                    // REST API not ready yet or offline, update widget with starting state
                    try {
                        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this@SyncthingService)
                        val thisWidget = android.content.ComponentName(this@SyncthingService, com.arcadelabs.synapse.widget.SynapseWidgetProvider::class.java)
                        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                        if (appWidgetIds.isNotEmpty()) {
                            com.arcadelabs.synapse.widget.SynapseWidgetProvider.updateWidgetState(
                                context = this@SyncthingService,
                                appWidgetManager = appWidgetManager,
                                appWidgetIds = appWidgetIds,
                                isRunning = true,
                                peersCount = 0,
                                progressText = "Starting...",
                                progressPercent = 0,
                                chartBitmap = null
                            )
                        }
                    } catch (_: Exception) {}

                    // Custom Dynamic Island updates removed

                    // Update ongoing notification
                    updateNotification("Starting...", 0)
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
                    peersCount = 0,
                    progressText = "Stopped",
                    progressPercent = 0,
                    chartBitmap = null
                )
            }

            // Custom Dynamic Island updates removed

            // Update ongoing notification
            updateNotification("Stopped")
        } catch (_: Exception) {}

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
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)

        isInstanceRunning = false
        super.onDestroy()
        runConditionMonitor?.stop()
        stopSyncthing()
        releaseLocks()
        stopDynamicIsland()
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
                .setContentTitle("Syncing")
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
            .setContentTitle("Syncing")
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
        private const val TAG = "SyncthingService"
        private const val CHANNEL_ID = "synapse_service_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.arcadelabs.synapse.ACTION_START"
        const val ACTION_STOP = "com.arcadelabs.synapse.ACTION_STOP"

        var isInstanceRunning = false
    }
}
