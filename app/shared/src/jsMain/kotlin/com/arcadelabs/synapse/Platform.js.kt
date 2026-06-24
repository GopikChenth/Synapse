package com.arcadelabs.synapse

import web.navigator.navigator

import androidx.compose.runtime.Composable

class JsPlatform: Platform {
    private val userAgent = navigator.userAgent
    private val browserList = listOf("Chrome", "Firefox", "Safari", "Edge")

    override val name: String = userAgent.findAnyOf(browserList, ignoreCase = true)
            ?.let { (startIndex) -> userAgent.substring(startIndex).substringBefore(" ") }
            ?: "Unknown"
}

actual fun getPlatform(): Platform = JsPlatform()

@Composable
actual fun SynapseBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op on JS
}