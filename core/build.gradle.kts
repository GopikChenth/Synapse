import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // --- iOS targets share an intermediate iosMain source set ---
    val iosArm64 = iosArm64()
    val iosSimulatorArm64 = iosSimulatorArm64()
    
    jvm()
    
    js {
        browser()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    
    androidLibrary {
       namespace = "com.arcadelabs.synapse.core"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        // --- iOS intermediate source set: shared by both iOS targets ---
        val iosMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        getByName("iosArm64Main").dependsOn(iosMain)
        getByName("iosSimulatorArm64Main").dependsOn(iosMain)

        commonMain.dependencies {
            // Koin DI
            api(libs.koin.core)

            // Ktor HttpClient
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // Serialization
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            // Ktor engine for Android/JVM
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            // Ktor engine for Desktop
            implementation(libs.ktor.client.okhttp)
        }
        jsMain.dependencies {
            // Ktor engine for Kotlin/JS
            implementation(libs.ktor.client.js)
        }
        wasmJsMain.dependencies {
            // Ktor engine for Kotlin/WasmJS
            implementation(libs.ktor.client.js)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}