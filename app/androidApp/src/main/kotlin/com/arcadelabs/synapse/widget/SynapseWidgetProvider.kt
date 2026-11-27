package com.arcadelabs.synapse.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.arcadelabs.synapse.MainActivity
import com.arcadelabs.synapse.R
import com.arcadelabs.synapse.service.SynapseSyncService
import kotlinx.coroutines.launch

class SynapseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        val behaviorStr = prefs.getString("run_behavior", "FOLLOW")
        // Use the service's real lifecycle state (covers FOLLOW mode too), not just the
        // saved run behaviour. The service's own polling replaces this text once the engine answers.
        val serviceState = SynapseSyncService.serviceState.value
        val isRunning = SynapseSyncService.isInstanceRunning

        updateWidgetState(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetIds = appWidgetIds,
            isRunning = isRunning,
            progressText = when {
                isRunning -> "Starting..."
                behaviorStr == "FOLLOW" && serviceState is com.arcadelabs.synapse.service.ServiceLifecycleState.WaitingForConditions -> "Waiting..."
                else -> "Stopped"
            }
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_TOGGLE_SYNC) {
            val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            val behaviorStr = prefs.getString("run_behavior", "FOLLOW")
            
            // Cycle run behavior: FOLLOW -> FORCE_START -> FORCE_STOP -> FOLLOW
            val newBehavior = when (behaviorStr) {
                "FOLLOW" -> "FORCE_START"
                "FORCE_START" -> "FORCE_STOP"
                else -> "FOLLOW"
            }
            
            prefs.edit().putString("run_behavior", newBehavior).apply()

            // Trigger service state change
            val serviceIntent = Intent(context, SynapseSyncService::class.java)
            if (newBehavior == "FORCE_START" || newBehavior == "FOLLOW") {
                serviceIntent.action = SynapseSyncService.ACTION_START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                serviceIntent.action = SynapseSyncService.ACTION_STOP
                context.startService(serviceIntent)
            }

            // Immediately update UI of all widgets to show starting/stopping state
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, SynapseWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            val isRunning = newBehavior == "FORCE_START"
            
            updateWidgetState(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetIds = appWidgetIds,
                isRunning = isRunning,
                progressText = when (newBehavior) {
                    "FORCE_START" -> "Starting..."
                    "FORCE_STOP" -> "Stopping..."
                    else -> "Waiting..."
                }
            )
        } else if (intent.action == ACTION_WIDGET_SYNC_NOW) {
            val pendingResult = goAsync()
            val koin = org.koin.core.context.GlobalContext.get()
            val apiClient = koin.get<com.arcadelabs.synapse.core.network.SynapseApiClient>()

            // Launch standard coroutine on IO Dispatcher to trigger folder scan
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    apiClient.scan(null)
                } catch (_: Exception) {}
                finally {
                    pendingResult.finish()
                }
            }
        }
    }

    class ThemePalette(
        val bg: Int,
        val subCardBg: Int,
        val border: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val progressFill: Int,
        val iconTint: Int
    )

    companion object {
        const val ACTION_WIDGET_TOGGLE_SYNC = "com.arcadelabs.synapse.ACTION_WIDGET_TOGGLE_SYNC"
        const val ACTION_WIDGET_SYNC_NOW = "com.arcadelabs.synapse.ACTION_WIDGET_SYNC_NOW"

        fun getThemePalette(context: Context): ThemePalette {
            val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            val selectedTheme = prefs.getString("selected_theme", "Default")
            val themeMode = prefs.getString("theme_mode", "Dark")

            val darkTheme = when (themeMode) {
                "Light" -> false
                "Dark" -> true
                else -> {
                    val mode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            }

            return when (selectedTheme) {
                "MidnightGreen" -> {
                    if (darkTheme) {
                        ThemePalette(
                            bg = android.graphics.Color.parseColor("#171615"),
                            subCardBg = android.graphics.Color.parseColor("#232120"),
                            border = android.graphics.Color.parseColor("#353332"),
                            textPrimary = android.graphics.Color.parseColor("#FFFFFF"),
                            textSecondary = android.graphics.Color.parseColor("#B7B7B7"),
                            progressFill = android.graphics.Color.parseColor("#8CCF73"),
                            iconTint = android.graphics.Color.parseColor("#FFFFFF")
                        )
                    } else {
                        ThemePalette(
                            bg = android.graphics.Color.parseColor("#F7F8F6"),
                            subCardBg = android.graphics.Color.parseColor("#FFFFFF"),
                            border = android.graphics.Color.parseColor("#EAECE7"),
                            textPrimary = android.graphics.Color.parseColor("#171615"),
                            textSecondary = android.graphics.Color.parseColor("#5C5E5A"),
                            progressFill = android.graphics.Color.parseColor("#52A435"),
                            iconTint = android.graphics.Color.parseColor("#171615")
                        )
                    }
                }
                "DeepSpace" -> {
                    if (darkTheme) {
                        ThemePalette(
                            bg = android.graphics.Color.parseColor("#0A0D10"),
                            subCardBg = android.graphics.Color.parseColor("#12161A"),
                            border = android.graphics.Color.parseColor("#1E252B"),
                            textPrimary = android.graphics.Color.parseColor("#FFFFFF"),
                            textSecondary = android.graphics.Color.parseColor("#8E9CA8"),
                            progressFill = android.graphics.Color.parseColor("#45A29E"),
                            iconTint = android.graphics.Color.parseColor("#FFFFFF")
                        )
                    } else {
                        ThemePalette(
                            bg = android.graphics.Color.parseColor("#F4F6F9"),
                            subCardBg = android.graphics.Color.parseColor("#FFFFFF"),
                            border = android.graphics.Color.parseColor("#E9ECEF"),
                            textPrimary = android.graphics.Color.parseColor("#1F2833"),
                            textSecondary = android.graphics.Color.parseColor("#495057"),
                            progressFill = android.graphics.Color.parseColor("#45A29E"),
                            iconTint = android.graphics.Color.parseColor("#1F2833")
                        )
                    }
                }
                "TacticalHUD" -> {
                    ThemePalette(
                        bg = android.graphics.Color.parseColor("#000000"),
                        subCardBg = android.graphics.Color.parseColor("#0B0F13"),
                        border = android.graphics.Color.parseColor("#2C3B47"),
                        textPrimary = android.graphics.Color.parseColor("#FFFFFF"),
                        textSecondary = android.graphics.Color.parseColor("#9CB4C2"),
                        progressFill = android.graphics.Color.parseColor("#24FCDE"),
                        iconTint = android.graphics.Color.parseColor("#24FCDE")
                    )
                }
                else -> { // Default Standard Purple
                    if (darkTheme) {
                        ThemePalette(
                            bg = android.graphics.Color.parseColor("#120E16"),
                            subCardBg = android.graphics.Color.parseColor("#1D192B"),
                            border = android.graphics.Color.parseColor("#352D42"),
                            textPrimary = android.graphics.Color.parseColor("#FFFFFF"),
                            textSecondary = android.graphics.Color.parseColor("#CAC4D0"),
                            progressFill = android.graphics.Color.parseColor("#D0BCFF"),
                            iconTint = android.graphics.Color.parseColor("#FFFFFF")
                        )
                    } else {
                        ThemePalette(
                            bg = android.graphics.Color.parseColor("#FAF8FD"),
                            subCardBg = android.graphics.Color.parseColor("#FFFFFF"),
                            border = android.graphics.Color.parseColor("#E8E0EB"),
                            textPrimary = android.graphics.Color.parseColor("#1D1B20"),
                            textSecondary = android.graphics.Color.parseColor("#49454F"),
                            progressFill = android.graphics.Color.parseColor("#6750A4"),
                            iconTint = android.graphics.Color.parseColor("#6750A4")
                        )
                    }
                }
            }
        }

        private fun setViewBackgroundTint(views: RemoteViews, viewId: Int, color: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(viewId, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(color))
            } else {
                views.setInt(viewId, "setBackgroundColor", color)
            }
        }

        fun updateWidgetState(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            isRunning: Boolean,
            progressText: String
        ) {
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            val behaviorStr = prefs.getString("run_behavior", "FOLLOW")
            val toggleIconRes = when (behaviorStr) {
                "FORCE_START" -> R.drawable.ic_widget_start
                "FORCE_STOP" -> R.drawable.ic_widget_stop
                else -> R.drawable.ic_widget_follow
            }

            val palette = getThemePalette(context)

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_glance)

                // Apply dynamic theme colors to texts
                views.setTextColor(R.id.widget_progress_text, palette.textPrimary)

                // Update status text
                views.setTextViewText(R.id.widget_progress_text, progressText)
                
                // Swap the button state icon dynamically
                views.setImageViewResource(R.id.widget_btn_toggle_icon, toggleIconRes)

                // Update status dot
                views.setImageViewResource(R.id.widget_status_dot, if (isRunning) R.drawable.ic_widget_dot_green else R.drawable.ic_widget_dot_red)

                // Dynamically tint structural backgrounds
                setViewBackgroundTint(views, R.id.widget_root, palette.bg)
                setViewBackgroundTint(views, R.id.widget_top_card_container, palette.subCardBg)
                setViewBackgroundTint(views, R.id.widget_btn_toggle_container, palette.subCardBg)
                setViewBackgroundTint(views, R.id.widget_btn_scan_container, palette.subCardBg)

                // Apply icon tints programmatically
                views.setInt(R.id.widget_btn_toggle_icon, "setColorFilter", palette.iconTint)
                views.setInt(R.id.widget_btn_scan_icon, "setColorFilter", palette.iconTint)

                // PendingIntent 1: Toggle button container click
                val toggleIntent = Intent(context, SynapseWidgetProvider::class.java).apply {
                    action = ACTION_WIDGET_TOGGLE_SYNC
                }
                val togglePendingIntent = PendingIntent.getBroadcast(context, 0, toggleIntent, pendingIntentFlags)
                views.setOnClickPendingIntent(R.id.widget_btn_toggle_container, togglePendingIntent)

                // PendingIntent 2: Recheck folders sync click (Using Scan Icon)
                val syncIntent = Intent(context, SynapseWidgetProvider::class.java).apply {
                    action = ACTION_WIDGET_SYNC_NOW
                }
                val syncPendingIntent = PendingIntent.getBroadcast(context, 1, syncIntent, pendingIntentFlags)
                views.setOnClickPendingIntent(R.id.widget_btn_scan_container, syncPendingIntent)

                // PendingIntent 3: Widget body click to open app
                val appIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val appPendingIntent = PendingIntent.getActivity(context, 2, appIntent, pendingIntentFlags)
                views.setOnClickPendingIntent(R.id.widget_progress_text, appPendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
