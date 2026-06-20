package com.arcadelabs.synapse.core.network

import com.arcadelabs.synapse.core.domain.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class SyncthingApiClient(
    private val client: HttpClient,
    private val contextProvider: Any? // Context passed from the platform app
) {
    private val baseUrl = "http://127.0.0.1:8384"
    
    private var cachedApiKey: String = ""
    private val apiKey: String
        get() {
            if (cachedApiKey.isEmpty()) {
                cachedApiKey = getApiKey(contextProvider)
            }
            return cachedApiKey
        }

    suspend fun getSystemStatus(): SystemStatus {
        return client.get("$baseUrl/rest/system/status") {
            header("X-API-Key", apiKey)
        }.body()
    }

    suspend fun getSystemVersion(): SystemVersion {
        return client.get("$baseUrl/rest/system/version") {
            header("X-API-Key", apiKey)
        }.body()
    }

    suspend fun getConfig(): SyncthingConfig {
        return client.get("$baseUrl/rest/system/config") {
            header("X-API-Key", apiKey)
        }.body()
    }

    suspend fun updateConfig(config: SyncthingConfig): HttpStatusCode {
        return client.post("$baseUrl/rest/system/config") {
            header("X-API-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(config)
        }.status
    }
    
    suspend fun getDbStatus(folderId: String): FolderDbStatus {
        return client.get("$baseUrl/rest/db/status") {
            header("X-API-Key", apiKey)
            parameter("folder", folderId)
        }.body()
    }

    suspend fun getConnections(): ConnectionsResponse {
        return client.get("$baseUrl/rest/system/connections") {
            header("X-API-Key", apiKey)
        }.body()
    }

    suspend fun getDbCompletion(deviceId: String, folderId: String): DeviceCompletion {
        return client.get("$baseUrl/rest/db/completion") {
            header("X-API-Key", apiKey)
            parameter("device", deviceId)
            parameter("folder", folderId)
        }.body()
    }

    suspend fun getSystemLog(): SystemLog {
        return client.get("$baseUrl/rest/system/log") {
            header("X-API-Key", apiKey)
        }.body()
    }

    suspend fun pauseDevice(deviceId: String): HttpStatusCode {
        return client.post("$baseUrl/rest/system/pause") {
            header("X-API-Key", apiKey)
            parameter("device", deviceId)
        }.status
    }

    suspend fun resumeDevice(deviceId: String): HttpStatusCode {
        return client.post("$baseUrl/rest/system/resume") {
            header("X-API-Key", apiKey)
            parameter("device", deviceId)
        }.status
    }
    
    suspend fun restart(): HttpStatusCode {
        return client.post("$baseUrl/rest/system/restart") {
            header("X-API-Key", apiKey)
        }.status
    }

    suspend fun shutdown(): HttpStatusCode {
        return client.post("$baseUrl/rest/system/shutdown") {
            header("X-API-Key", apiKey)
        }.status
    }
}

