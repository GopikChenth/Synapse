package com.arcadelabs.synapse

import android.content.Context
import android.content.Intent
import android.os.Build
import com.arcadelabs.synapse.service.SyncthingService
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class ShortcutHandler(private val context: Context) {
    companion object {
        const val ACTION_SCAN_QR = "com.arcadelabs.synapse.ACTION_SCAN_QR"
        const val ACTION_FORCE_START = "com.arcadelabs.synapse.ACTION_FORCE_START"
        const val ACTION_PAUSE_SYNC = "com.arcadelabs.synapse.ACTION_PAUSE_SYNC"
    }

    fun handleIntent(intent: Intent?, onQrScanned: (String) -> Unit) {
        if (intent == null) return
        when (intent.action) {
            ACTION_FORCE_START -> {
                val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
                prefs.edit().putString("run_behavior", "FORCE_START").apply()
                startSyncthingService()
            }
            ACTION_PAUSE_SYNC -> {
                val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
                prefs.edit().putString("run_behavior", "FORCE_STOP").apply()
                stopSyncthingService()
            }
            ACTION_SCAN_QR -> {
                triggerQrScanner(onQrScanned)
            }
        }
    }

    private fun startSyncthingService() {
        val serviceIntent = Intent(context, SyncthingService::class.java).apply {
            action = SyncthingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun stopSyncthingService() {
        val serviceIntent = Intent(context, SyncthingService::class.java).apply {
            action = SyncthingService.ACTION_STOP
        }
        context.startService(serviceIntent)
    }

    private fun triggerQrScanner(onQrScanned: (String) -> Unit) {
        try {
            val scanner = GmsBarcodeScanning.getClient(context)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    barcode.rawValue?.let { onQrScanned(it) }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
