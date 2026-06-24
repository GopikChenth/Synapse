package com.arcadelabs.synapse

import androidx.compose.runtime.Composable

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

@Composable
actual fun SynapseBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op on JVM
}