# Ignore warnings and notes for all third-party dependencies & Kotlin module metadata versions
-ignorewarnings
-dontwarn **
-dontnote **

# Keep Kotlin metadata & serialization
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class * implements kotlinx.serialization.KSerializer { *; }

# Ktor & OkHttp
-keep class io.ktor.** { *; }
-keep class okhttp3.** { *; }

# JNA & Native Access
-keep class com.sun.jna.** { *; }

# Application entry point & Koin
-keep class com.arcadelabs.synapse.** { *; }
-keep class org.koin.** { *; }
