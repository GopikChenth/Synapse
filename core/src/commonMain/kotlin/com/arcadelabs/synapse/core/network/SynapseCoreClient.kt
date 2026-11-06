package com.arcadelabs.synapse.core.network

import com.arcadelabs.synapse.core.domain.models.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

interface SynapseCoreClient {
    suspend fun getStatus(): CoreSystemStatus
    suspend fun getFolders(): List<CoreFolder>
    suspend fun registerFolder(folderId: String, localPath: String, conflictPolicy: String = "THREE_WAY_AUTO"): Boolean
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

    override suspend fun getFolders(): List<CoreFolder> {
        return httpClient.get("${baseUrl()}/api/v1/folders") {
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
        }.body()
    }

    override suspend fun registerFolder(folderId: String, localPath: String, conflictPolicy: String): Boolean {
        val resp = httpClient.post("${baseUrl()}/api/v1/folders") {
            contentType(ContentType.Application.Json)
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
            setBody(CoreRegisterFolderRequest(folderId, localPath, conflictPolicy))
        }
        return resp.status.isSuccess()
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
