package com.arcadelabs.synapse

import androidx.compose.runtime.Composable

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): Platform = WasmPlatform()

@Composable
actual fun SynapseBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op on WasmJs
}