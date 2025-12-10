# Stremio Core Kotlin
-keep class com.stremio.core.** { *; }
-keepclassmembers class com.stremio.core.** { *; }

# pbandk protobuf
-keep class pbandk.** { *; }
-keepclassmembers class pbandk.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# App classes
-keep class com.tvplayer.app.stremio.** { *; }
-keep class com.tvplayer.app.models.** { *; }
