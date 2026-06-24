package com.arcadelabs.synapse

import platform.UIKit.UIDevice

import androidx.compose.runtime.Composable

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

@Composable
actual fun SynapseBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op on iOS
}