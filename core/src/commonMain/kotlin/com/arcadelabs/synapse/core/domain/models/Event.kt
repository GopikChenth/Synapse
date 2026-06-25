package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
data class Event(
    @SerialName("id") val id: Int,
    @SerialName("globalID") val globalID: Int,
    @SerialName("type") val type: String,
    @SerialName("time") val time: String,
    @SerialName("data") val data: JsonObject? = null
)

@Serializable
data class ItemFinishedEventData(
    @SerialName("item") val item: String,
    @SerialName("folder") val folder: String,
    @SerialName("type") val type: String,
    @SerialName("action") val action: String,
    @SerialName("error") val error: String? = null
)

fun Event.toItemFinishedData(json: Json): ItemFinishedEventData? {
    if (type != "ItemFinished" || data == null) return null
    return try {
        json.decodeFromJsonElement(ItemFinishedEventData.serializer(), data)
    } catch (_: Exception) {
        null
    }
}
