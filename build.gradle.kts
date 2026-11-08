plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

tasks.register<Exec>("buildSynapseCore") {
    group = "native"
    description = "Compiles native Synapse Core using Cargo and syncs binaries to bin/ and app/desktopApp/bin/"
    workingDir = file("synapse-core")
    commandLine("cargo", "build", "--release")
    doLast {
        val exeName = if (System.getProperty("os.name").lowercase().contains("win")) "synapse-core.exe" else "synapse-core"
        val built = file("synapse-core/target/release/$exeName")
        if (built.exists()) {
            copy {
                from(built)
                into(file("bin"))
            }
            copy {
                from(built)
                into(file("app/desktopApp/bin"))
            }
        }
        val androidSo = file("synapse-core/bin/android/x86_64/libsynapse.so")
        if (androidSo.exists()) {
            copy {
                from(androidSo)
                into(file("bin/android/x86_64"))
            }
            copy {
                from(androidSo)
                into(file("app/androidApp/src/main/jniLibs/x86_64"))
            }
        }
    }
}