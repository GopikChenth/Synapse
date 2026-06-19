package com.arcadelabs.synapse.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.util.Log

class RunConditionMonitor(
    private val context: Context,
    private val onConditionsChanged: (isMet: Boolean) -> Unit
) {
    private var isCharging = false
    private var isWifiConnected = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val nowCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            if (nowCharging != isCharging) {
                isCharging = nowCharging
                Log.d(TAG, "Power condition changed: charging=$isCharging")
                checkConditions()
            }
        }
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            if (isWifi != isWifiConnected) {
                isWifiConnected = isWifi
                Log.d(TAG, "Network condition changed: wifiConnected=$isWifiConnected")
                checkConditions()
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            isWifiConnected = false
            Log.d(TAG, "Network lost: wifiConnected=false")
            checkConditions()
        }
    }

    fun start() {
        // Register battery monitor
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)

        // Register network monitor
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Initial check
        val statusIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = statusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isWifiConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        checkConditions()
    }

    fun stop() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private fun checkConditions() {
        // Initial setup: require Wi-Fi to sync. (Can be made user-configurable in the Settings UI later)
        val met = isWifiConnected
        onConditionsChanged(met)
    }

    companion object {
        private const val TAG = "RunConditionMonitor"
    }
}
