package com.arcadelabs.synapse.core.network

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.header
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

fun createHttpClient(): HttpClient {
    return HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = false   // Never add whitespace on the wire
                isLenient = false     // Reject malformed JSON — fail fast
                encodeDefaults = false // Don't write default values on wire (prevent overwriting server defaults)
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000   // 10 s total request timeout
            connectTimeoutMillis = 5_000    // 5 s connection timeout
            socketTimeoutMillis  = 10_000   // 10 s socket idle timeout
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            retryOnExceptionOrServerErrors()
            exponentialDelay()
        }
        defaultRequest {
            header("User-Agent", "Synapse/1.0.0")
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, request ->
                val exception = when (cause) {
                    is ClientRequestException -> {
                        val status = cause.response.status
                        when (status.value) {
                            401, 403 -> SynapseUnauthorizedException("Unauthorized: API key is invalid or rejected", cause)
                            404 -> SynapseNotFoundException("Endpoint not found: ${request.url}", cause)
                            else -> SynapseApiException("HTTP Error ${status.value}: ${status.description}", cause)
                        }
                    }
                    is ServerResponseException -> {
                        val status = cause.response.status
                        SynapseApiException("Server Error ${status.value}: ${status.description}", cause)
                    }
                    is HttpRequestTimeoutException,
                    is ConnectTimeoutException,
                    is SocketTimeoutException -> {
                        SynapseTimeoutException("Request timed out", cause)
                    }
                    else -> cause
                }
                throw exception
            }
        }
    }
}
