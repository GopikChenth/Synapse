# ─── Synapse ProGuard Rules ───────────────────────────────────────────────────

# Kotlin
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# kotlinx.serialization — keep generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class * implements kotlinx.serialization.KSerializer { *; }

# Koin — keep module declarations
-keep class org.koin.** { *; }
-keepnames class * extends org.koin.core.module.Module

# Ktor — keep client internals
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# OkHttp (Ktor Android engine)
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Application entry point
-keep class com.arcadelabs.synapse.SynapseApp
-keep class com.arcadelabs.synapse.MainActivity
-keep class com.arcadelabs.synapse.service.SyncthingService
-keep class com.arcadelabs.synapse.service.SyncthingRunnable
