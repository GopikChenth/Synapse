package com.arcadelabs.synapse

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform