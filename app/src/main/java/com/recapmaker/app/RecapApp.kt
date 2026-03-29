package com.recapmaker.app

import android.app.Application
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RecapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
            com.yausername.ffmpeg.FFmpeg.getInstance().init(this)
        } catch (e: Exception) {
            android.util.Log.e("RecapApp", "yt-dlp init: ${e.message}")
        }
    }
}
