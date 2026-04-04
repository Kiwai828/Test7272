package com.recapmaker.app

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class RecapApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Init yt-dlp + FFmpeg
        try {
            YoutubeDL.getInstance().init(this)
            com.yausername.ffmpeg.FFmpeg.getInstance().init(this)
            Log.d("RecapApp", "yt-dlp + FFmpeg init OK")
        } catch (e: Exception) {
            Log.e("RecapApp", "init error: ${e.message}")
        }

        // Update yt-dlp binary in background — fixes "version older than 90 days" warning
        // This runs once per app launch, silently, without blocking UI
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("RecapApp", "Checking yt-dlp update...")
                val status = YoutubeDL.getInstance().updateYoutubeDL(
                    this@RecapApp,
                    YoutubeDL.UpdateChannel.STABLE
                )
                Log.d("RecapApp", "yt-dlp update status: $status")
            } catch (e: Exception) {
                // Update failed (e.g. no internet) — old version still works for most sites
                Log.w("RecapApp", "yt-dlp update skipped: ${e.message}")
            }
        }
    }
}
