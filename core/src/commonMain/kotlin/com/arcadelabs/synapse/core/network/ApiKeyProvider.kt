package com.arcadelabs.synapse.core.network

interface ApiKeyProvider {
    suspend fun getApiKey(): String?
}

class EmptyApiKeyProvider : ApiKeyProvider {
    override suspend fun getApiKey(): String? = null
}
