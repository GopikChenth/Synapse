package com.arcadelabs.synapse.core.network

import com.arcadelabs.synapse.core.domain.models.*
import com.arcadelabs.synapse.core.prefs.PreferencesHelper
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

open class SyncthingException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ApiKeyNotConfiguredException(message: String) : SyncthingException(message)
class SyncthingUnauthorizedException(message: String, cause: Throwable? = null) : SyncthingException(message, cause)
class SyncthingNotFoundException(message: String, cause: Throwable? = null) : SyncthingException(message, cause)
class SyncthingApiException(message: String, cause: Throwable? = null) : SyncthingException(message, cause)
class SyncthingTimeoutException(message: String, cause: Throwable? = null) : SyncthingException(message, cause)

/**
 * Thread-safe API client interface for Syncthing.
 * All functions are non-blocking and safe to be called from any coroutine context (including the Main dispatcher).
 */
interface SyncthingApiClient {
    /**
     * Retrieves the current system status and resource usage.
     */
    suspend fun systemStatus(): SystemStatus

    /**
     * Retrieves the running Syncthing version information.
     */
    suspend fun systemVersion(): SystemVersion

    /**
     * Retrieves the entire current Syncthing configuration.
     */
    suspend fun systemConfig(): SyncthingConfig

    /**
     * Retrieves the raw JSON configuration as a string.
     */
    suspend fun rawSystemConfig(): String

    /**
     * Overwrites the entire JSON configuration with the provided string.
     */
    suspend fun updateRawSystemConfig(configJson: String)

    /**
     * Updates the Syncthing configuration with the fields modeled in [SyncthingConfig].
     * Preserves unmodeled fields from the current server configuration.
     */
    suspend fun updateSystemConfig(config: SyncthingConfig)

    /**
     * Retrieves database status metrics for the specified folder.
     */
    suspend fun dbStatus(folderId: String): FolderDbStatus

    /**
     * Retrieves connection status information for all configured devices.
     */
    suspend fun systemConnections(): ConnectionsResponse

    /**
     * Retrieves the synchronization completion status for a specific device and folder.
     */
    suspend fun dbCompletion(deviceId: String, folderId: String): DeviceCompletion

    /**
     * Retrieves the running log messages from the system.
     */
    suspend fun systemLog(): SystemLog

    /**
     * Pauses communication with the specified device.
     * @return the [HttpResponse] from the server.
     */
    suspend fun pauseDevice(deviceId: String): HttpResponse

    /**
     * Resumes communication with the specified device.
     * @return the [HttpResponse] from the server.
     */
    suspend fun resumeDevice(deviceId: String): HttpResponse

    /**
     * Pauses or resumes synchronization for the specified folder.
     * Uses PATCH /rest/config/folders/{id} — the ONLY correct folder endpoint.
     * NOTE: /rest/system/pause and /rest/system/resume are DEVICE-ONLY endpoints;
     * passing a 'folder' param to them is silently ignored by Syncthing.
     * @param paused true to pause, false to resume.
     */
    suspend fun setFolderPaused(folderId: String, paused: Boolean): HttpResponse

    /**
     * Restarts the Syncthing daemon.
     * @return the [HttpResponse] from the server.
     */
    suspend fun restart(): HttpResponse

    /**
     * Shuts down the Syncthing daemon.
     * @return the [HttpResponse] from the server.
     */
    suspend fun shutdown(): HttpResponse

    /**
     * Rescans the specified folder, or all folders if null.
     */
    suspend fun scan(folderId: String? = null): HttpResponse

    /**
     * Retrieves recent events from the Syncthing daemon.
     */
    suspend fun getEvents(since: Int = 0, limit: Int = 100): List<Event>

    /**
     * Removes a single folder from the Syncthing configuration by ID.
     * Uses DELETE /rest/config/folders/{id} — no restart required.
     */
    suspend fun deleteFolder(folderId: String)

    /**
     * Removes a single device from the Syncthing configuration by ID.
     * Uses DELETE /rest/config/devices/{id} — no restart required.
     */
    suspend fun deleteDevice(deviceId: String)

    /**
     * Retrieves the map of pending remote devices that have requested a connection.
     */
    suspend fun getPendingDevices(): Map<String, PendingDevice>

    /**
     * Dismisses the pending connection request from the specified device.
     */
    suspend fun dismissPendingDevice(deviceId: String): HttpResponse

    /**
     * Retrieves the map of pending folder shares offered by remote devices.
     */
    suspend fun getPendingFolders(): Map<String, PendingFolderOffer>

    /**
     * Dismisses/rejects the pending folder share request.
     */
    suspend fun dismissPendingFolder(
        folderId: String,
        deviceId: String = "",
        label: String = "",
        time: String = ""
    ): HttpResponse

    /**
     * Retrieves the Syncthing daemon options configuration.
     */
    suspend fun getConfigOptions(): ConfigOptions

    /**
     * Replaces the Syncthing daemon options configuration.
     */
    suspend fun updateConfigOptions(options: ConfigOptions)

    /**
     * Retrieves the Syncthing GUI configuration.
     */
    suspend fun getConfigGui(): GuiConfig

    /**
     * Replaces the Syncthing GUI configuration.
     */
    suspend fun updateConfigGui(gui: GuiConfig)
}

internal class SyncthingApiClientImpl(
    private val client: HttpClient,
    private val apiKeyProvider: ApiKeyProvider,
    private val preferencesHelper: PreferencesHelper
) : SyncthingApiClient {
    private val baseUrl: String
        get() {
            val url = preferencesHelper.apiBaseUrl.trim()
            return if (url.endsWith("/")) url.removeSuffix("/") else url
        }
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    
    private val apiKeyMutex = kotlinx.coroutines.sync.Mutex()
    private var cachedApiKey: String? = null
    @Volatile
    private var isSynapseCoreEngine: Boolean? = null

    private suspend fun checkIsSynapseCore(): Boolean {
        val cached = isSynapseCoreEngine
        if (cached != null) return cached
        val isCore = try {
            val resp = client.get("$baseUrl/api/v1/system/status")
            resp.status.value in 200..299
        } catch (_: Exception) {
            false
        }
        if (isCore) {
            isSynapseCoreEngine = true
            return true
        }
        return false
    }

    private suspend fun getOrResolveApiKey(): String {
        val prefKey = preferencesHelper.apiKey.trim()
        if (prefKey.isNotEmpty()) {
            return prefKey
        }
        if (checkIsSynapseCore()) {
            val defaultKey = "synapse-managed-api-key-2025"
            preferencesHelper.apiKey = defaultKey
            cachedApiKey = defaultKey
            return defaultKey
        }
        var key = cachedApiKey
        if (key == null) {
            apiKeyMutex.withLock {
                key = cachedApiKey
                if (key == null) {
                    val resolved = apiKeyProvider.getApiKey()
                    if (resolved.isNullOrEmpty()) {
                        if (checkIsSynapseCore()) {
                            val defaultKey = "synapse-managed-api-key-2025"
                            preferencesHelper.apiKey = defaultKey
                            cachedApiKey = defaultKey
                            return defaultKey
                        }
                        throw ApiKeyNotConfiguredException("Syncthing API key could not be resolved from configuration")
                    }
                    cachedApiKey = resolved
                    preferencesHelper.apiKey = resolved
                    key = resolved
                }
            }
        }
        return key!!
    }

    override suspend fun systemStatus(): SystemStatus {
        if (checkIsSynapseCore()) {
            return try {
                val core = client.get("$baseUrl/api/v1/system/status").body<CoreSystemStatus>()
                SystemStatus(
                    myID = core.deviceId,
                    uptime = core.uptimeSeconds,
                    alloc = core.memoryFootprintBytes,
                    sys = core.memoryFootprintBytes,
                    guiAddressUsed = baseUrl
                )
            } catch (_: Exception) {
                val key = getOrResolveApiKey()
                client.get("$baseUrl/rest/system/status") {
                    header("X-API-Key", key)
                }.body()
            }
        }
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/system/status") {
            header("X-API-Key", key)
        }.body()
    }

    override suspend fun systemVersion(): SystemVersion {
        if (checkIsSynapseCore()) {
            return try {
                val core = client.get("$baseUrl/api/v1/system/status").body<CoreSystemStatus>()
                SystemVersion(
                    currentVersion = core.version,
                    longVersion = "Synapse Core v${core.version} (Native Rust Engine)"
                )
            } catch (_: Exception) {
                val key = getOrResolveApiKey()
                client.get("$baseUrl/rest/system/version") {
                    header("X-API-Key", key)
                }.body()
            }
        }
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/system/version") {
            header("X-API-Key", key)
        }.body()
    }

    override suspend fun systemConfig(): SyncthingConfig {
        if (checkIsSynapseCore()) {
            return try {
                val coreFolders = client.get("$baseUrl/api/v1/folders").body<List<CoreFolder>>()
                val mappedFolders = coreFolders.map { f ->
                    val folderDevices = try {
                        val devs = client.get("$baseUrl/api/v1/folders/${f.folderId}/devices").body<List<CoreDevice>>()
                        devs.map { FolderDeviceReference(deviceID = it.deviceId) }
                    } catch (_: Exception) {
                        emptyList()
                    }
                    Folder(
                        id = f.folderId,
                        label = f.folderId,
                        path = f.localPath,
                        paused = f.isPaused,
                        type = f.syncMode,
                        devices = folderDevices
                    )
                }
                val coreDevices = try {
                    client.get("$baseUrl/api/v1/devices").body<List<CoreDevice>>()
                } catch (_: Exception) {
                    emptyList()
                }
                val mappedDevices = coreDevices.map { d ->
                    Device(
                        deviceID = d.deviceId,
                        name = d.name,
                        addresses = d.addresses,
                        paused = d.isPaused,
                        introducer = d.introducer
                    )
                }
                SyncthingConfig(
                    version = 1,
                    folders = mappedFolders,
                    devices = mappedDevices
                )
            } catch (_: Exception) {
                val key = getOrResolveApiKey()
                client.get("$baseUrl/rest/system/config") {
                    header("X-API-Key", key)
                }.body()
            }
        }
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/system/config") {
            header("X-API-Key", key)
        }.body()
    }

    override suspend fun rawSystemConfig(): String {
        if (checkIsSynapseCore()) {
            val cfg = systemConfig()
            return json.encodeToString(SyncthingConfig.serializer(), cfg)
        }
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/system/config") {
            header("X-API-Key", key)
        }.bodyAsText()
    }

    override suspend fun updateRawSystemConfig(configJson: String) {
        if (checkIsSynapseCore()) {
            try {
                val parsed = json.decodeFromString(SyncthingConfig.serializer(), configJson)
                parsed.folders.forEach { f ->
                    client.post("$baseUrl/api/v1/folders") {
                        contentType(ContentType.Application.Json)
                        setBody(CoreRegisterFolderRequest(
                            folderId = f.id,
                            localPath = f.path,
                            conflictPolicy = "THREE_WAY_AUTO",
                            syncMode = f.type,
                            markerName = ".synapse-folder"
                        ))
                    }
                    f.devices.forEach { d ->
                        try {
                            client.post("$baseUrl/api/v1/folders/${f.id}/devices/${d.deviceID}")
                        } catch (_: Exception) {}
                    }
                }
                parsed.devices.forEach { d ->
                    client.post("$baseUrl/api/v1/devices") {
                        contentType(ContentType.Application.Json)
                        setBody(CoreDevice(deviceId = d.deviceID, name = d.name, addresses = d.addresses))
                    }
                }
                return
            } catch (_: Exception) {}
        }
        val key = getOrResolveApiKey()
        val response = client.post("$baseUrl/rest/system/config") {
            header("X-API-Key", key)
            contentType(ContentType.Application.Json)
            setBody(configJson)
        }
        if (response.status.value !in 200..299) {
            throw Exception("Failed to update configuration: HTTP ${response.status.value}")
        }
    }

    override suspend fun updateSystemConfig(config: SyncthingConfig) {
        val rawConfigString = rawSystemConfig()
        val rawConfigJson = json.parseToJsonElement(rawConfigString).jsonObject

        val folderKnownKeys = setOf(
            "id", "label", "path", "type", "paused",
            "rescanIntervalS", "fsWatcherEnabled", "devices", "versioning"
        )
        val deviceKnownKeys = setOf(
            "deviceID", "name", "addresses", "paused",
            "introducer", "autoAcceptFolders", "untrusted"
        )

        val existingFoldersMap = rawConfigJson["folders"]?.jsonArray?.associateBy {
            it.jsonObject["id"]?.jsonPrimitive?.content ?: ""
        } ?: emptyMap()

        val existingDevicesMap = rawConfigJson["devices"]?.jsonArray?.associateBy {
            it.jsonObject["deviceID"]?.jsonPrimitive?.content ?: ""
        } ?: emptyMap()

        val patchedFolders = config.folders.map { folder ->
            val existing = existingFoldersMap[folder.id]
            if (existing != null) {
                val updatedFolderJson = json.encodeToJsonElement(Folder.serializer(), folder).jsonObject
                val newMap = existing.jsonObject.toMutableMap()
                for (key in folderKnownKeys) {
                    val newVal = updatedFolderJson[key]
                    if (newVal != null) {
                        newMap[key] = newVal
                    }
                }
                JsonObject(newMap)
            } else {
                json.encodeToJsonElement(Folder.serializer(), folder).jsonObject
            }
        }

        val patchedDevices = config.devices.map { device ->
            val existing = existingDevicesMap[device.deviceID]
            if (existing != null) {
                val updatedDeviceJson = json.encodeToJsonElement(Device.serializer(), device).jsonObject
                val newMap = existing.jsonObject.toMutableMap()
                for (key in deviceKnownKeys) {
                    val newVal = updatedDeviceJson[key]
                    if (newVal != null) {
                        newMap[key] = newVal
                    }
                }
                JsonObject(newMap)
            } else {
                json.encodeToJsonElement(Device.serializer(), device).jsonObject
            }
        }

        val newRootMap = rawConfigJson.toMutableMap()
        newRootMap["version"] = JsonPrimitive(config.version)
        newRootMap["folders"] = JsonArray(patchedFolders)
        newRootMap["devices"] = JsonArray(patchedDevices)

        val patchedConfigJson = JsonObject(newRootMap)
        updateRawSystemConfig(patchedConfigJson.toString())
        // No restart needed — Syncthing v1.12+ applies config changes hot via the REST API.
    }
    
    override suspend fun dbStatus(folderId: String): FolderDbStatus {
        if (checkIsSynapseCore()) {
            return try {
                client.get("$baseUrl/rest/db/status") {
                    parameter("folder", folderId)
                }.body<FolderDbStatus>()
            } catch (_: Exception) {
                try {
                    val coreFolders = client.get("$baseUrl/api/v1/folders").body<List<CoreFolder>>()
                    val target = coreFolders.firstOrNull { it.folderId == folderId }
                    val pullErrorsCount = try {
                        val conflicts = client.get("$baseUrl/api/v1/conflicts").body<List<CoreQuarantineConflict>>()
                        conflicts.count { it.folderId == folderId }.toLong()
                    } catch (_: Exception) {
                        0L
                    }
                    FolderDbStatus(
                        localFiles = target?.filesCount?.toLong() ?: 0L,
                        localDirectories = 0L,
                        localBytes = 0L,
                        globalFiles = target?.filesCount?.toLong() ?: 0L,
                        globalBytes = 0L,
                        inSyncBytes = 0L,
                        state = if (target?.isPaused == true) "paused" else "idle",
                        pullErrors = pullErrorsCount
                    )
                } catch (_: Exception) {
                    FolderDbStatus()
                }
            }
        }
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/db/status") {
            header("X-API-Key", key)
            parameter("folder", folderId)
        }.body()
    }

    override suspend fun systemConnections(): ConnectionsResponse {
        if (checkIsSynapseCore()) {
            return try {
                val metricsMap = client.get("$baseUrl/api/v1/connections").body<Map<String, CorePeerConnectionMetrics>>()
                var totalIn = 0L
                var totalOut = 0L
                val mapped = mutableMapOf<String, DeviceConnection>()
                for ((k, m) in metricsMap) {
                    totalIn += m.totalInBytes
                    totalOut += m.totalOutBytes
                    mapped[k] = DeviceConnection(
                        address = m.address,
                        clientVersion = m.clientType,
                        connected = m.isConnected,
                        type = "QUIC",
                        inBytesTotal = m.totalInBytes,
                        outBytesTotal = m.totalOutBytes
                    )
                }
                ConnectionsResponse(
                    connections = mapped,
                    total = TotalConnection(
                        inBytesTotal = totalIn,
                        outBytesTotal = totalOut
                    )
                )
            } catch (_: Exception) {
                ConnectionsResponse(
                    connections = emptyMap(),
                    total = TotalConnection(inBytesTotal = 0L, outBytesTotal = 0L)
                )
            }
        }
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/system/connections") {
            header("X-API-Key", key)
        }.body()
    }

    override suspend fun dbCompletion(deviceId: String, folderId: String): DeviceCompletion {
        if (checkIsSynapseCore()) {
            return try {
                client.get("$baseUrl/rest/db/completion") {
                    parameter("device", deviceId)
                    parameter("folder", folderId)
                }.body<DeviceCompletion>()
            } catch (_: Exception) {
                DeviceCompletion(
                    completion = 100.0,
                    globalBytes = 0L,
                    needBytes = 0L,
                    device = deviceId,
                    folder = folderId,
                    remoteState = ""
                )
            }
        }
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/db/completion") {
            header("X-API-Key", key)
            parameter("device", deviceId)
            parameter("folder", folderId)
        }.body()
    }

    override suspend fun systemLog(): SystemLog {
        if (checkIsSynapseCore()) {
            return SystemLog(
                messages = listOf(
                    LogEntry(
                        timestamp = "Active",
                        message = "Synapse Core engine running natively on $baseUrl"
                    )
                )
            )
        }
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/system/log") {
            header("X-API-Key", key)
        }.body()
    }

    override suspend fun pauseDevice(deviceId: String): HttpResponse {
        if (checkIsSynapseCore()) {
            return try {
                client.post("$baseUrl/api/v1/devices/$deviceId/pause")
            } catch (_: Exception) {
                client.get("$baseUrl/api/v1/system/status")
            }
        }
        val key = getOrResolveApiKey()
        return client.post("$baseUrl/rest/system/pause") {
            header("X-API-Key", key)
            parameter("device", deviceId)
        }
    }

    override suspend fun resumeDevice(deviceId: String): HttpResponse {
        if (checkIsSynapseCore()) {
            return try {
                client.post("$baseUrl/api/v1/devices/$deviceId/resume")
            } catch (_: Exception) {
                client.get("$baseUrl/api/v1/system/status")
            }
        }
        val key = getOrResolveApiKey()
        return client.post("$baseUrl/rest/system/resume") {
            header("X-API-Key", key)
            parameter("device", deviceId)
        }
    }

    override suspend fun setFolderPaused(folderId: String, paused: Boolean): HttpResponse {
        if (checkIsSynapseCore()) {
            val endpoint = if (paused) "$baseUrl/api/v1/folders/$folderId/pause" else "$baseUrl/api/v1/folders/$folderId/resume"
            return try {
                client.post(endpoint)
            } catch (_: Exception) {
                client.get("$baseUrl/api/v1/system/status")
            }
        }
        val key = getOrResolveApiKey()
        // PATCH /rest/config/folders/{id} is the correct endpoint for toggling folder pause.
        // /rest/system/pause and /rest/system/resume are DEVICE-ONLY — Syncthing silently ignores the folder param.
        val body = buildJsonObject { put("paused", paused) }.toString()
        return client.patch("$baseUrl/rest/config/folders/$folderId") {
            header("X-API-Key", key)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }
    
    override suspend fun restart(): HttpResponse {
        if (checkIsSynapseCore()) {
            return try {
                client.post("$baseUrl/rest/system/restart")
            } catch (_: Exception) {
                client.get("$baseUrl/api/v1/system/status")
            }
        }
        val key = getOrResolveApiKey()
        return client.post("$baseUrl/rest/system/restart") {
            header("X-API-Key", key)
        }
    }

    override suspend fun shutdown(): HttpResponse {
        if (checkIsSynapseCore()) {
            return try {
                client.post("$baseUrl/rest/system/shutdown")
            } catch (_: Exception) {
                client.get("$baseUrl/api/v1/system/status")
            }
        }
        val key = getOrResolveApiKey()
        return client.post("$baseUrl/rest/system/shutdown") {
            header("X-API-Key", key)
        }
    }

    override suspend fun scan(folderId: String?): HttpResponse {
        if (checkIsSynapseCore()) {
            return try {
                if (folderId != null) {
                    client.post("$baseUrl/api/v1/folders/$folderId/rescan")
                } else {
                    client.post("$baseUrl/rest/db/scan")
                }
            } catch (_: Exception) {
                client.get("$baseUrl/api/v1/system/status")
            }
        }
        val key = getOrResolveApiKey()
        val response = client.post("$baseUrl/rest/db/scan") {
            header("X-API-Key", key)
            if (folderId != null) {
                parameter("folder", folderId)
            }
        }
        if (response.status.value !in 200..299) {
            val errorText = try { response.bodyAsText() } catch (_: Exception) { "" }
            throw SyncthingApiException("Failed to scan folder: HTTP ${response.status.value} - ${errorText.trim()}")
        }
        return response
    }

    override suspend fun deleteFolder(folderId: String) {
        if (checkIsSynapseCore()) {
            try {
                client.delete("$baseUrl/api/v1/folders/$folderId")
            } catch (_: Exception) {}
            return
        }
        val key = getOrResolveApiKey()
        val response = client.delete("$baseUrl/rest/config/folders/$folderId") {
            header("X-API-Key", key)
        }
        if (response.status.value !in 200..299) {
            throw SyncthingApiException("Failed to delete folder: HTTP ${response.status.value}")
        }
    }

    override suspend fun deleteDevice(deviceId: String) {
        if (checkIsSynapseCore()) {
            try {
                client.delete("$baseUrl/api/v1/devices/$deviceId")
            } catch (_: Exception) {}
            return
        }
        val key = getOrResolveApiKey()
        val response = client.delete("$baseUrl/rest/config/devices/$deviceId") {
            header("X-API-Key", key)
        }
        if (response.status.value !in 200..299) {
            throw SyncthingApiException("Failed to delete device: HTTP ${response.status.value}")
        }
    }

    override suspend fun getPendingDevices(): Map<String, PendingDevice> {
        if (checkIsSynapseCore()) {
            return try {
                val list = client.get("$baseUrl/api/v1/cluster/pending/devices").body<List<CorePendingDevice>>()
                list.associate { d ->
                    d.deviceId to PendingDevice(
                        name = d.name,
                        address = d.address,
                        time = d.receivedAt.toString()
                    )
                }
            } catch (_: Exception) {
                emptyMap()
            }
        }
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/cluster/pending/devices") {
            header("X-API-Key", key)
        }.body()
    }

    override suspend fun dismissPendingDevice(deviceId: String): HttpResponse {
        if (checkIsSynapseCore()) {
            return try {
                client.post("$baseUrl/api/v1/cluster/pending/devices/$deviceId/reject")
            } catch (_: Exception) {
                client.get("$baseUrl/api/v1/system/status")
            }
        }
        val key = getOrResolveApiKey()
        val deleteResponse = client.delete("$baseUrl/rest/cluster/pending/devices") {
            header("X-API-Key", key)
            parameter("device", deviceId)
        }

        try {
            val rawConfigString = rawSystemConfig()
            val rawConfigJson = json.parseToJsonElement(rawConfigString).jsonObject
            val newRootMap = rawConfigJson.toMutableMap()

            val existingIgnored = (rawConfigJson["ignoredDevices"] as? JsonArray)?.toMutableList() ?: mutableListOf()
            val isAlreadyIgnored = existingIgnored.any {
                it is JsonPrimitive && it.content == deviceId
            }
            if (!isAlreadyIgnored) {
                existingIgnored.add(JsonPrimitive(deviceId))
                newRootMap["ignoredDevices"] = JsonArray(existingIgnored)
                val patchedConfigJson = JsonObject(newRootMap)
                updateRawSystemConfig(patchedConfigJson.toString())
            }
        } catch (e: Exception) {
            // Ignore/Log error
        }

        return deleteResponse
    }

    override suspend fun getPendingFolders(): Map<String, PendingFolderOffer> {
        if (checkIsSynapseCore()) {
            return try {
                val list = client.get("$baseUrl/api/v1/cluster/pending/folders").body<List<CorePendingFolder>>()
                list.associate { f ->
                    f.folderId to PendingFolderOffer(
                        offeredBy = mapOf(
                            f.deviceId to PendingFolder(
                                label = f.folderLabel,
                                time = f.offeredAt.toString()
                            )
                        )
                    )
                }
            } catch (_: Exception) {
                emptyMap()
            }
        }
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/cluster/pending/folders") {
            header("X-API-Key", key)
        }.body()
    }

    override suspend fun dismissPendingFolder(
        folderId: String,
        deviceId: String,
        label: String,
        time: String
    ): HttpResponse {
        if (checkIsSynapseCore()) {
            return try {
                client.post("$baseUrl/api/v1/cluster/pending/folders/$folderId/reject")
            } catch (_: Exception) {
                client.get("$baseUrl/api/v1/system/status")
            }
        }
        val key = getOrResolveApiKey()
        val deleteResponse = client.delete("$baseUrl/rest/cluster/pending/folders") {
            header("X-API-Key", key)
            parameter("folder", folderId)
            if (deviceId.isNotEmpty()) {
                parameter("device", deviceId)
            }
        }

        if (deviceId.isNotEmpty()) {
            try {
                val rawConfigString = rawSystemConfig()
                val rawConfigJson = json.parseToJsonElement(rawConfigString).jsonObject
                val newRootMap = rawConfigJson.toMutableMap()

                val devicesArray = rawConfigJson["devices"]?.jsonArray
                if (devicesArray != null) {
                    val updatedDevices = devicesArray.map { deviceElement ->
                        val deviceObj = deviceElement.jsonObject
                        val currentDeviceId = deviceObj["deviceID"]?.jsonPrimitive?.content ?: ""
                        if (currentDeviceId == deviceId) {
                            val newDeviceMap = deviceObj.toMutableMap()
                            val existingIgnored = (deviceObj["ignoredFolders"] as? JsonArray)?.toMutableList() ?: mutableListOf()

                            val isAlreadyIgnored = existingIgnored.any {
                                (it as? JsonObject)?.get("id")?.jsonPrimitive?.content == folderId
                            }
                            if (!isAlreadyIgnored) {
                                val newIgnoredFolder = buildJsonObject {
                                    put("id", folderId)
                                    put("label", label)
                                    put("time", time)
                                }
                                existingIgnored.add(newIgnoredFolder)
                                newDeviceMap["ignoredFolders"] = JsonArray(existingIgnored)
                            }
                            JsonObject(newDeviceMap)
                        } else {
                            deviceElement
                        }
                    }
                    newRootMap["devices"] = JsonArray(updatedDevices)
                    val patchedConfigJson = JsonObject(newRootMap)
                    updateRawSystemConfig(patchedConfigJson.toString())
                }
            } catch (e: Exception) {
                // Ignore/Log error
            }
        }

        return deleteResponse
    }

    override suspend fun getEvents(since: Int, limit: Int): List<Event> {
        if (checkIsSynapseCore()) {
            return try {
                val conflicts = client.get("$baseUrl/api/v1/conflicts").body<List<CoreQuarantineConflict>>()
                conflicts.mapIndexed { idx, c ->
                    Event(
                        id = idx + 1,
                        globalID = idx + 1,
                        type = "ItemFinished",
                        time = "",
                        data = buildJsonObject {
                            put("item", "sync-conflict-${c.conflictId}-${c.relativePath}")
                            put("folder", c.folderId)
                        }
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/events") {
            header("X-API-Key", key)
            parameter("since", since)
            parameter("limit", limit)
        }.body()
    }

    override suspend fun getConfigOptions(): ConfigOptions {
        if (checkIsSynapseCore()) return ConfigOptions()
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/config/options") {
            header("X-API-Key", key)
        }.body()
    }

    override suspend fun updateConfigOptions(options: ConfigOptions) {
        if (checkIsSynapseCore()) return
        val key = getOrResolveApiKey()
        val response = client.put("$baseUrl/rest/config/options") {
            header("X-API-Key", key)
            contentType(ContentType.Application.Json)
            setBody(options)
        }
        if (response.status.value !in 200..299) {
            throw SyncthingApiException("Failed to update config options: HTTP ${response.status.value}")
        }
    }

    override suspend fun getConfigGui(): GuiConfig {
        if (checkIsSynapseCore()) return GuiConfig(address = baseUrl)
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/config/gui") {
            header("X-API-Key", key)
        }.body()
    }

    override suspend fun updateConfigGui(gui: GuiConfig) {
        if (checkIsSynapseCore()) return
        val key = getOrResolveApiKey()
        val response = client.put("$baseUrl/rest/config/gui") {
            header("X-API-Key", key)
            contentType(ContentType.Application.Json)
            setBody(gui)
        }
        if (response.status.value !in 200..299) {
            throw SyncthingApiException("Failed to update GUI config: HTTP ${response.status.value}")
        }
    }
}

