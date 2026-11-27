package com.arcadelabs.synapse.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.arcadelabs.synapse.core.prefs.PreferencesHelper
import com.arcadelabs.synapse.service.SynapseSyncService
import org.koin.core.context.GlobalContext

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Honour the "Start on boot" switch in Settings. Koin is started in SynapseApp,
            // which runs before any receiver in this process.
            val autoStart = runCatching {
                GlobalContext.get().get<PreferencesHelper>().autoStart
            }.getOrDefault(false)
            if (!autoStart) {
                Log.i(TAG, "Device boot completed; Start on boot is off, not starting sync service")
                return
            }
            Log.i(TAG, "Device boot completed, starting Synapse sync service...")
            val serviceIntent = Intent(context, SynapseSyncService::class.java).apply {
                action = SynapseSyncService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
