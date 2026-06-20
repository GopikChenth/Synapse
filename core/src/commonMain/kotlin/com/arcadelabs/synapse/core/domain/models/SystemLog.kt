package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class LogEntry(
    val `when`: String = "",
    val message: String = ""
)

@Serializable
data class SystemLog(
    val messages: List<LogEntry> = emptyList()
)
