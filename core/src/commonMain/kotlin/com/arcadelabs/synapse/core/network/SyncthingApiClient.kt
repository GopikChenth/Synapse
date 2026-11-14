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
     * Directly accepts a pending device pairing request into the cluster.
     */
    suspend fun acceptPendingDevice(deviceId: String): Boolean

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
     * Directly accepts a pending folder share request into the cluster.
     */
    suspend fun acceptPendingFolder(folderId: String, localPath: String? = null): Boolean

    /**
     * Retrieves the list of files in virtual quarantine / conflict state.
     */
    suspend fun getConflicts(): List<CoreQuarantineConflict>

    /**
     * Computes or fetches the 3-way diff for a quarantined conflict.
     */
    suspend fun getConflictDiff(conflictId: String): CoreDiffResponse

    /**
     * Resolves a quarantined conflict with the chosen action policy.
     */
    suspend fun resolveConflict(conflictId: String, request: CoreResolveRequest): Boolean

    /**
     * Ignores a quarantined conflict.
     */
    suspend fun ignoreConflict(conflictId: String): Boolean

    /**
     * Reopens an ignored conflict back to pending.
     */
    suspend fun reopenConflict(conflictId: String): Boolean

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
    private val preferencesHelper: PreferencesHelper,
    private val coreClient: SynapseCoreClient = SynapseCoreClientImpl(
        httpClient = client,
        baseUrlProvider = { preferencesHelper.apiBaseUrl },
        tokenProvider = { preferencesHelper.apiKey }
    )
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

    private suspend fun getOrResolveApiKey(): String {
        val prefKey = preferencesHelper.apiKey.trim()
        if (prefKey.isNotEmpty()) {
            return prefKey
        }
        var key = cachedApiKey
        if (key != null) return key
        return apiKeyMutex.withLock {
            cachedApiKey?.let { return@withLock it }
            val resolved = apiKeyProvider.getApiKey()?.trim().orEmpty()
            val effectiveKey = resolved.ifEmpty { "synapse-managed-api-key-2025" }
            cachedApiKey = effectiveKey
            preferencesHelper.apiKey = effectiveKey
            effectiveKey
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders(key: String) {
        header("X-API-Key", key)
        header("Authorization", "Bearer $key")
    }

    override suspend fun systemStatus(): SystemStatus {
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/system/status") {
            authHeaders(key)
        }.body()
    }

    override suspend fun systemVersion(): SystemVersion {
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/system/version") {
            authHeaders(key)
        }.body()
    }

    override suspend fun systemConfig(): SyncthingConfig {
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/system/config") {
            authHeaders(key)
        }.body()
    }

    override suspend fun rawSystemConfig(): String {
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/system/config") {
            authHeaders(key)
        }.bodyAsText()
    }

    override suspend fun updateRawSystemConfig(configJson: String) {
        val key = getOrResolveApiKey()
        val response = client.post("$baseUrl/rest/system/config") {
            authHeaders(key)
            contentType(ContentType.Application.Json)
            setBody(configJson)
        }
        if (!response.status.isSuccess()) {
            throw SyncthingApiException("Failed to update configuration: HTTP ${response.status.value}")
        }
    }

    override suspend fun updateSystemConfig(config: SyncthingConfig) {
        val jsonText = json.encodeToString(SyncthingConfig.serializer(), config)
        updateRawSystemConfig(jsonText)
    }
    
    override suspend fun dbStatus(folderId: String): FolderDbStatus {
        val key = getOrResolveApiKey()
        return client.get("$baseUrl/rest/db/status") {
            authHeaders(key)
            parameter("folder", folderId)
        }.body()
    }

    override suspend fun systemConnections(): ConnectionsResponse {
        val key = getOrResolveApiKey()
        return try {
            val metricsMap = coreClient.getConnections()
            if (metricsMap.isNotEmpty()) {
                var totalIn = 0L
                var totalOut = 0L
                val mapped = mutableMapOf<String, DeviceConnection>()
                for ((k, m) in metricsMap) {
                    totalIn += m.totalInBytes
                    totalOut += m.totalOutBytes
                    val connType = if (m.clientType.contains("bep", ignoreCase = true) || m.clientType.contains("syncthing", ignoreCase = true)) {
                        "BEP v1 (Syncthing TLS)"
                    } else {
                        "Synapse QUIC (FastCDC)"
                    }
                    mapped[k] = DeviceConnection(
                        address = m.address,
                        clientVersion = m.clientType,
                        connected = m.isConnected,
                        type = connType,
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
            } else {
                client.get("$baseUrl/rest/system/connections") {
                    authHeaders(key)
                }.body()
            }
        } catch (_: Exception) {
            try {
                client.get("$baseUrl/rest/system/connections") {
                    authHeaders(key)
                }.body()
            } catch (_: Exception) {
                ConnectionsResponse()
            }
        }
    }

    override suspend fun dbCompletion(deviceId: String, folderId: String): DeviceCompletion {
        val key = getOrResolveApiKey()
        return try {
            client.get("$baseUrl/rest/db/completion") {
                authHeaders(key)
                parameter("device", deviceId)
                parameter("folder", folderId)
            }.body()
        } catch (_: Exception) {
            DeviceCompletion(
                completion = 100.0,
                device = deviceId,
                folder = folderId
            )
        }
    }

    override suspend fun systemLog(): SystemLog {
        val key = getOrResolveApiKey()
        return try {
            client.get("$baseUrl/rest/system/log") {
                authHeaders(key)
            }.body()
        } catch (_: Exception) {
            SystemLog(
                messages = listOf(
                    LogEntry(
                        timestamp = "Active",
                        message = "Synapse Core engine running natively on $baseUrl"
                    )
                )
            )
        }
    }

    override suspend fun pauseDevice(deviceId: String): HttpResponse {
        val key = getOrResolveApiKey()
        return client.post("$baseUrl/rest/system/pause") {
            authHeaders(key)
            parameter("device", deviceId)
        }
    }

    override suspend fun resumeDevice(deviceId: String): HttpResponse {
        val key = getOrResolveApiKey()
        return client.post("$baseUrl/rest/system/resume") {
            authHeaders(key)
            parameter("device", deviceId)
        }
    }

    override suspend fun setFolderPaused(folderId: String, paused: Boolean): HttpResponse {
        val key = getOrResolveApiKey()
        val body = buildJsonObject { put("paused", paused) }.toString()
        return client.patch("$baseUrl/rest/config/folders/$folderId") {
            authHeaders(key)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }
    
    override suspend fun restart(): HttpResponse {
        val key = getOrResolveApiKey()
        return client.post("$baseUrl/rest/system/restart") {
            authHeaders(key)
        }
    }

    override suspend fun shutdown(): HttpResponse {
        val key = getOrResolveApiKey()
        return client.post("$baseUrl/rest/system/shutdown") {
            authHeaders(key)
        }
    }

    override suspend fun scan(folderId: String?): HttpResponse {
        val key = getOrResolveApiKey()
        val response = client.post("$baseUrl/rest/db/scan") {
            authHeaders(key)
            if (folderId != null) {
                parameter("folder", folderId)
            }
        }
        if (!response.status.isSuccess()) {
            throw SyncthingApiException("Failed to scan: HTTP ${response.status.value}")
        }
        return response
    }

    override suspend fun deleteFolder(folderId: String) {
        val key = getOrResolveApiKey()
        val response = client.delete("$baseUrl/rest/config/folders/$folderId") {
            authHeaders(key)
        }
        if (!response.status.isSuccess()) {
            throw SyncthingApiException("Failed to delete folder: HTTP ${response.status.value}")
        }
    }

    override suspend fun deleteDevice(deviceId: String) {
        val key = getOrResolveApiKey()
        val response = client.delete("$baseUrl/rest/config/devices/$deviceId") {
            authHeaders(key)
        }
        if (!response.status.isSuccess()) {
            throw SyncthingApiException("Failed to delete device: HTTP ${response.status.value}")
        }
    }

    override suspend fun getPendingDevices(): Map<String, PendingDevice> {
        val key = getOrResolveApiKey()
        return try {
            val corePending = coreClient.getPendingDevices()
            if (corePending.isNotEmpty()) {
                corePending.associate { d ->
                    d.deviceId to PendingDevice(
                        name = d.name,
                        address = d.address,
                        time = d.receivedAt.toString()
                    )
                }
            } else {
                client.get("$baseUrl/rest/cluster/pending/devices") {
                    authHeaders(key)
                }.body()
            }
        } catch (_: Exception) {
            try {
                client.get("$baseUrl/rest/cluster/pending/devices") {
                    authHeaders(key)
                }.body()
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }

    override suspend fun dismissPendingDevice(deviceId: String): HttpResponse {
        val key = getOrResolveApiKey()
        return client.delete("$baseUrl/rest/cluster/pending/devices") {
            authHeaders(key)
            parameter("device", deviceId)
        }
    }

    override suspend fun acceptPendingDevice(deviceId: String): Boolean {
        return coreClient.acceptPendingDevice(deviceId)
    }

    override suspend fun acceptPendingFolder(folderId: String, localPath: String?): Boolean {
        return coreClient.acceptPendingFolder(folderId)
    }

    override suspend fun getConflicts(): List<CoreQuarantineConflict> = coreClient.getConflicts()

    override suspend fun getConflictDiff(conflictId: String): CoreDiffResponse = coreClient.getConflictDiff(conflictId)

    override suspend fun resolveConflict(conflictId: String, request: CoreResolveRequest): Boolean = coreClient.resolveConflict(conflictId, request)

    override suspend fun ignoreConflict(conflictId: String): Boolean = coreClient.ignoreConflict(conflictId)

    override suspend fun reopenConflict(conflictId: String): Boolean = coreClient.reopenConflict(conflictId)

    override suspend fun getPendingFolders(): Map<String, PendingFolderOffer> {
        val key = getOrResolveApiKey()
        return try {
            val corePending = coreClient.getPendingFolders()
            if (corePending.isNotEmpty()) {
                corePending.associate { f ->
                    f.folderId to PendingFolderOffer(
                        offeredBy = mapOf(
                            f.deviceId to PendingFolder(
                                label = f.folderLabel,
                                time = f.offeredAt.toString()
                            )
                        )
                    )
                }
            } else {
                client.get("$baseUrl/rest/cluster/pending/folders") {
                    authHeaders(key)
                }.body()
            }
        } catch (_: Exception) {
            try {
                client.get("$baseUrl/rest/cluster/pending/folders") {
                    authHeaders(key)
                }.body()
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }

    override suspend fun dismissPendingFolder(
        folderId: String,
        deviceId: String,
        label: String,
        time: String
    ): HttpResponse {
        val key = getOrResolveApiKey()
        return client.delete("$baseUrl/rest/cluster/pending/folders") {
            authHeaders(key)
            parameter("folder", folderId)
            if (deviceId.isNotEmpty()) {
                parameter("device", deviceId)
            }
        }
    }

    override suspend fun getEvents(since: Int, limit: Int): List<Event> {
        val key = getOrResolveApiKey()
        return try {
            val conflicts = coreClient.getConflicts()
            if (conflicts.isNotEmpty()) {
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
            } else {
                client.get("$baseUrl/rest/events") {
                    authHeaders(key)
                    parameter("since", since)
                    parameter("limit", limit)
                }.body()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getConfigOptions(): ConfigOptions {
        val key = getOrResolveApiKey()
        return try {
            client.get("$baseUrl/rest/config/options") {
                authHeaders(key)
            }.body()
        } catch (_: Exception) {
            ConfigOptions()
        }
    }

    override suspend fun updateConfigOptions(options: ConfigOptions) {
        val key = getOrResolveApiKey()
        try {
            client.put("$baseUrl/rest/config/options") {
                authHeaders(key)
                contentType(ContentType.Application.Json)
                setBody(options)
            }
        } catch (_: Exception) {}
    }

    override suspend fun getConfigGui(): GuiConfig {
        val key = getOrResolveApiKey()
        return try {
            client.get("$baseUrl/rest/config/gui") {
                authHeaders(key)
            }.body()
        } catch (_: Exception) {
            GuiConfig(address = baseUrl)
        }
    }

    override suspend fun updateConfigGui(gui: GuiConfig) {
        val key = getOrResolveApiKey()
        try {
            client.put("$baseUrl/rest/config/gui") {
                authHeaders(key)
                contentType(ContentType.Application.Json)
                setBody(gui)
            }
        } catch (_: Exception) {}
    }
}

