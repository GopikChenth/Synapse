package com.arcadelabs.synapse.core.network

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object HttpClientProvider {
    fun create(): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = false   // Never add whitespace on the wire
                    isLenient = false     // Reject malformed JSON — fail fast
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000   // 10 s total request timeout
                connectTimeoutMillis = 5_000    // 5 s connection timeout
                socketTimeoutMillis  = 10_000   // 10 s socket idle timeout
            }
        }
    }
}
