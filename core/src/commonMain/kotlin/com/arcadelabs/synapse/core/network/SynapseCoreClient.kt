package com.arcadelabs.synapse.core.network

import com.arcadelabs.synapse.core.domain.models.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

interface SynapseCoreClient {
    suspend fun getStatus(): CoreSystemStatus
    suspend fun getVersion(): CoreSystemVersion
    suspend fun getFolders(): List<CoreFolder>
    suspend fun registerFolder(
        folderId: String,
        localPath: String,
        conflictPolicy: String = "THREE_WAY_AUTO",
        syncMode: String? = null,
        pullOrder: String? = null,
        versioningType: String? = null,
        versioningKeepDays: Long? = null,
        markerName: String? = null
    ): Boolean
    suspend fun deleteFolder(folderId: String): Boolean
    suspend fun pauseFolder(folderId: String): Boolean
    suspend fun resumeFolder(folderId: String): Boolean
    suspend fun rescanFolder(folderId: String): Boolean
    suspend fun getDevices(): List<CoreDevice>
    suspend fun addDevice(deviceId: String, name: String, addresses: List<String> = emptyList()): Boolean
    suspend fun deleteDevice(deviceId: String): Boolean
    suspend fun pauseDevice(deviceId: String): Boolean
    suspend fun resumeDevice(deviceId: String): Boolean
    suspend fun getPendingDevices(): List<CorePendingDevice>
    suspend fun acceptPendingDevice(deviceId: String): Boolean
    suspend fun rejectPendingDevice(deviceId: String): Boolean
    suspend fun getPendingFolders(): List<CorePendingFolder>
    suspend fun acceptPendingFolder(folderId: String): Boolean
    suspend fun rejectPendingFolder(folderId: String): Boolean
    suspend fun getConnections(): Map<String, CorePeerConnectionMetrics>
    suspend fun getConflicts(): List<CoreQuarantineConflict>
    suspend fun getConflictDiff(conflictId: String): CoreDiffResponse
    suspend fun resolveConflict(conflictId: String, request: CoreResolveRequest): Boolean
}

class SynapseCoreClientImpl(
    private val httpClient: HttpClient,
    private val baseUrlProvider: () -> String = { "http://127.0.0.1:8384" },
    private val tokenProvider: () -> String? = { "synapse-managed-api-key-2025" }
) : SynapseCoreClient {

    private fun baseUrl(): String {
        val base = baseUrlProvider().trimEnd('/')
        return if (base.startsWith("http://") || base.startsWith("https://")) base else "http://$base"
    }

    override suspend fun getStatus(): CoreSystemStatus {
        return httpClient.get("${baseUrl()}/api/v1/system/status") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }.body()
    }

    override suspend fun getVersion(): CoreSystemVersion {
        return httpClient.get("${baseUrl()}/api/v1/system/version") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }.body()
    }

    override suspend fun getFolders(): List<CoreFolder> {
        return httpClient.get("${baseUrl()}/api/v1/folders") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }.body()
    }

    override suspend fun registerFolder(
        folderId: String,
        localPath: String,
        conflictPolicy: String,
        syncMode: String?,
        pullOrder: String?,
        versioningType: String?,
        versioningKeepDays: Long?,
        markerName: String?
    ): Boolean {
        val resp = httpClient.post("${baseUrl()}/api/v1/folders") {
            contentType(ContentType.Application.Json)
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
            setBody(
                CoreRegisterFolderRequest(
                    folderId = folderId,
                    localPath = localPath,
                    conflictPolicy = conflictPolicy,
                    syncMode = syncMode,
                    pullOrder = pullOrder,
                    versioningType = versioningType,
                    versioningKeepDays = versioningKeepDays,
                    markerName = markerName
                )
            )
        }
        return resp.status.isSuccess()
    }

    override suspend fun deleteFolder(folderId: String): Boolean {
        val resp = httpClient.delete("${baseUrl()}/api/v1/folders/$folderId") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }
        return resp.status.isSuccess()
    }

    override suspend fun pauseFolder(folderId: String): Boolean {
        val resp = httpClient.post("${baseUrl()}/api/v1/folders/$folderId/pause") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }
        return resp.status.isSuccess()
    }

    override suspend fun resumeFolder(folderId: String): Boolean {
        val resp = httpClient.post("${baseUrl()}/api/v1/folders/$folderId/resume") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }
        return resp.status.isSuccess()
    }

    override suspend fun rescanFolder(folderId: String): Boolean {
        val resp = httpClient.post("${baseUrl()}/api/v1/folders/$folderId/rescan") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }
        return resp.status.isSuccess()
    }

    override suspend fun getDevices(): List<CoreDevice> {
        return httpClient.get("${baseUrl()}/api/v1/devices") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }.body()
    }

    override suspend fun addDevice(deviceId: String, name: String, addresses: List<String>): Boolean {
        val resp = httpClient.post("${baseUrl()}/api/v1/devices") {
            contentType(ContentType.Application.Json)
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
            setBody(CoreDevice(deviceId = deviceId, name = name, addresses = addresses))
        }
        return resp.status.isSuccess()
    }

    override suspend fun deleteDevice(deviceId: String): Boolean {
        val resp = httpClient.delete("${baseUrl()}/api/v1/devices/$deviceId") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }
        return resp.status.isSuccess()
    }

    override suspend fun pauseDevice(deviceId: String): Boolean {
        val resp = httpClient.post("${baseUrl()}/api/v1/devices/$deviceId/pause") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }
        return resp.status.isSuccess()
    }

    override suspend fun resumeDevice(deviceId: String): Boolean {
        val resp = httpClient.post("${baseUrl()}/api/v1/devices/$deviceId/resume") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }
        return resp.status.isSuccess()
    }

    override suspend fun getPendingDevices(): List<CorePendingDevice> {
        return httpClient.get("${baseUrl()}/api/v1/cluster/pending/devices") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }.body()
    }

    override suspend fun acceptPendingDevice(deviceId: String): Boolean {
        val resp = httpClient.post("${baseUrl()}/api/v1/cluster/pending/devices/$deviceId/accept") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }
        return resp.status.isSuccess()
    }

    override suspend fun rejectPendingDevice(deviceId: String): Boolean {
        val resp = httpClient.post("${baseUrl()}/api/v1/cluster/pending/devices/$deviceId/reject") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }
        return resp.status.isSuccess()
    }

    override suspend fun getPendingFolders(): List<CorePendingFolder> {
        return httpClient.get("${baseUrl()}/api/v1/cluster/pending/folders") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }.body()
    }

    override suspend fun acceptPendingFolder(folderId: String): Boolean {
        val resp = httpClient.post("${baseUrl()}/api/v1/cluster/pending/folders/$folderId/accept") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }
        return resp.status.isSuccess()
    }

    override suspend fun rejectPendingFolder(folderId: String): Boolean {
        val resp = httpClient.post("${baseUrl()}/api/v1/cluster/pending/folders/$folderId/reject") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }
        return resp.status.isSuccess()
    }

    override suspend fun getConnections(): Map<String, CorePeerConnectionMetrics> {
        return httpClient.get("${baseUrl()}/api/v1/connections") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }.body()
    }

    override suspend fun getConflicts(): List<CoreQuarantineConflict> {
        return httpClient.get("${baseUrl()}/api/v1/conflicts") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }.body()
    }

    override suspend fun getConflictDiff(conflictId: String): CoreDiffResponse {
        return httpClient.get("${baseUrl()}/api/v1/conflicts/$conflictId/diff") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }.body()
    }

    override suspend fun resolveConflict(conflictId: String, request: CoreResolveRequest): Boolean {
        val resp = httpClient.post("${baseUrl()}/api/v1/conflicts/$conflictId/resolve") {
            contentType(ContentType.Application.Json)
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
            setBody(request)
        }
        return resp.status.isSuccess()
    }
}
