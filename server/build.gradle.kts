plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
}

group = "com.arcadelabs.synapse"
version = "1.0.0"
application {
    mainClass = "com.arcadelabs.synapse.ApplicationKt"
}

dependencies {
    api(projects.core)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}

afterEvaluate {
    tasks.configureEach {
        if (name.contains("test", ignoreCase = true)) {
            enabled = false
            group = null
        }
    }
}