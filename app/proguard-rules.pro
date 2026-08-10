# ── Gson ──
-keepattributes Signature
-keepattributes *Annotation*
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

# ── ONNX Runtime (on-device RVC) ──
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ── Kotlin ──
-dontwarn kotlinx.coroutines.**
-dontwarn dagger.hilt.**
