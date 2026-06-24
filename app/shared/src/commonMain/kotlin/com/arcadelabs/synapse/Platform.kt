package com.arcadelabs.synapse

import androidx.compose.runtime.Composable

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

@Composable
expect fun SynapseBackHandler(enabled: Boolean = true, onBack: () -> Unit)