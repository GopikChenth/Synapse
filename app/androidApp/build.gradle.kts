import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(projects.app.shared)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)       // Required by NotificationCompat in SyncthingService
    implementation(libs.koin.android)             // Koin Android support (androidContext, etc.)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.arcadelabs.synapse"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.arcadelabs.synapse"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 6
        versionName = "1.0.6"
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }
    }
    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
        // Release key: from CI secrets (env vars) or local ~/.gradle/gradle.properties.
        // See docs/RELEASE_SIGNING.md. Never commit the keystore or its passwords.
        val releaseKeystore = System.getenv("SYNAPSE_KEYSTORE_FILE")
            ?: (project.findProperty("synapse.keystore.file") as String?)
        if (releaseKeystore != null && file(releaseKeystore).exists()) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("SYNAPSE_KEYSTORE_PASSWORD")
                    ?: (project.findProperty("synapse.keystore.password") as String?)
                keyAlias = System.getenv("SYNAPSE_KEY_ALIAS")
                    ?: (project.findProperty("synapse.key.alias") as String?)
                keyPassword = System.getenv("SYNAPSE_KEY_PASSWORD")
                    ?: (project.findProperty("synapse.key.password") as String?)
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            // Real release key when configured; otherwise debug (local builds keep working,
            // but debug-signed releases cannot update each other or go on Google Play).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug").also {
                logger.warn("Synapse: no release signing key configured — release APK will be DEBUG-signed. See docs/RELEASE_SIGNING.md")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

afterEvaluate {
    tasks.configureEach {
        if (name.contains("test", ignoreCase = true)) {
            enabled = false
            group = null
        }
    }
}