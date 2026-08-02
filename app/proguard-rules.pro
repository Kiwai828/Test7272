# ── Gson ──
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleAnnotations
-keep class com.recapmaker.app.data.model.** { *; }
-keep class com.recapmaker.app.data.api.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }

# ── OkHttp ──
-dontwarn okhttp3.**
-dontwarn okio.**

# ── FFmpeg-Kit ──
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }
-keep class com.moizhassan.ffmpeg.** { *; }
-dontwarn com.arthenica.**
-dontwarn com.moizhassan.**

# ── youtubedl-android (yt-dlp) ──
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.aria2c.** { *; }
-dontwarn com.yausername.**

# ── Room ──
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── Kotlin ──
-dontwarn kotlinx.coroutines.**
-dontwarn dagger.hilt.**

# ── Kotlinx Serialization ──
-keepclassmembers class kotlin.Metadata { *; }
-keep class kotlinx.serialization.** { *; }
-keepattributes InnerClasses,EnclosedMethod

# ── ExoPlayer / Media3 ──
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── Coil ──
-keep class coil.** { *; }
-dontwarn coil.**

# ── Hilt / Generated ──
-keep class dagger.hilt.internal.** { *; }
-keep class * extends dagger.hilt.compiler.** { *; }
-keep class * extends androidx.hilt.** { *; }
-keepclasseswithmembernames class * { @dagger.hilt.android.AndroidEntryPoint <methods>; }

# ── Room (generated code) ──
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * { @androidx.room.PrimaryKey <fields>; }
