import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.app.shared)

    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
    implementation(libs.compose.material3)
    implementation(libs.koin.compose)

    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
}

compose.desktop {
    application {
        mainClass = "com.arcadelabs.synapse.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(false)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            packageName = "Synapse"
            packageVersion = "1.0.0"
            description = "Synapse - Continuous Peer-to-Peer File Synchronization"
            vendor = "ArcadeLabs"
            windows {
                dirChooser = true
                shortcut = true
                menu = true
                menuGroup = "Synapse"
                iconFile.set(project.file("icon.ico"))
            }
        }
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