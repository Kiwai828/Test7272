package com.recapmaker.app.media

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.recapmaker.app.R
import kotlinx.coroutines.*
import java.io.File

/**
 * Foreground Service for video processing.
 * Runs FFmpeg in background — app ပိတ်ထားလည်း process ဆက်လုပ်.
 * Shows notification with progress status.
 */
class VideoProcessService : Service() {

    companion object {
        const val CHANNEL_ID = "recap_process_channel"
        const val NOTIFICATION_ID = 101
        const val DONE_NOTIFICATION_ID = 102

        // State shared via companion (simple approach, ViewModel observes)
        var isRunning = false; private set
        var currentStatus = ""; private set
        var resultSuccess: Boolean? = null; private set
        var resultMessage: String? = null; private set
        var resultOutputPath: String? = null; private set

        // Process params set before starting service
        var pendingInputPath: String? = null
        var pendingOptions: FFmpegProcessor.ProcessOptions? = null
        var pendingContext: Context? = null

        fun reset() {
            isRunning = false; currentStatus = ""; resultSuccess = null
            resultMessage = null; resultOutputPath = null
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val inputPath = pendingInputPath
        val options = pendingOptions

        if (inputPath == null || options == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning = true
        resultSuccess = null

        // Start foreground immediately
        val notification = buildNotification("Video ပြုပြင်နေသည်...")
        startForeground(NOTIFICATION_ID, notification)

        scope.launch {
            try {
                currentStatus = "Video ပြုပြင်နေသည်..."
                updateNotification(currentStatus)

                val result = FFmpegProcessor.process(inputPath, this@VideoProcessService, options)

                if (result.success && result.outputPath != null) {
                    currentStatus = "Gallery သို့ သိမ်းနေသည်..."
                    updateNotification(currentStatus)

                    val galleryUri = FFmpegProcessor.saveToGallery(this@VideoProcessService, File(result.outputPath))

                    resultSuccess = true
                    resultOutputPath = galleryUri ?: result.outputPath
                    resultMessage = "✅ ပြီးပါပြီ! Movies/RecapMaker/ (${result.durationMs / 1000}s)"

                    // Cleanup temp
                    File(result.outputPath).delete()

                    showDoneNotification("✅ Video ပြီးပါပြီ!", "Movies/RecapMaker/ ထဲ သိမ်းပြီး")
                } else {
                    resultSuccess = false
                    resultMessage = result.error ?: "Processing failed"
                    showDoneNotification("❌ Process Failed", resultMessage ?: "")
                }
            } catch (e: Exception) {
                resultSuccess = false
                resultMessage = "Error: ${e.message}"
                showDoneNotification("❌ Error", resultMessage ?: "")
            } finally {
                isRunning = false
                currentStatus = ""
                pendingInputPath = null
                pendingOptions = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Video Processing", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Video processing progress"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recap Maker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun showDoneNotification(title: String, text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setAutoCancel(true)
            .build()
        nm.notify(DONE_NOTIFICATION_ID, notification)
    }
}
