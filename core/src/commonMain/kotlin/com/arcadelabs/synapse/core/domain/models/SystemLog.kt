package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LogEntry(
    /**
     * ISO-8601 formatted timestamp representing when the log entry was generated.
     */
    @SerialName("when") val timestamp: String = "",
    @SerialName("message") val message: String = ""
)

@Serializable
data class SystemLog(
    @SerialName("messages") val messages: List<LogEntry> = emptyList()
)
