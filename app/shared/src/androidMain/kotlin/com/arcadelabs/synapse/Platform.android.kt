package com.arcadelabs.synapse

import android.os.Build

import androidx.compose.runtime.Composable

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

@Composable
actual fun SynapseBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled, onBack)
}