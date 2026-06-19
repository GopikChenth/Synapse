package com.arcadelabs.synapse.core.network

import com.arcadelabs.synapse.core.domain.models.SystemStatus
import com.arcadelabs.synapse.core.domain.models.SystemVersion
import com.arcadelabs.synapse.core.domain.models.SyncthingConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class SyncthingApiClient(
    private val client: HttpClient,
    private val contextProvider: Any? // Context passed from the platform app
) {
    private val baseUrl = "http://127.0.0.1:8384"
    
    private val apiKey: String by lazy {
        getApiKey(contextProvider)
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
