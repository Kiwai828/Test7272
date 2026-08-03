package com.recapmaker.app.media

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File

class VideoProcessService : Service() {

    companion object {
        const val TAG = "VideoProcessSvc"
        const val CHANNEL_ID = "recap_process_channel"
        const val NOTIFICATION_ID = 101
        const val DONE_NOTIFICATION_ID = 102

        const val EXTRA_INPUT_PATH = "input_path"
        const val EXTRA_OPTIONS_JSON = "options_json"

        // Shared state — ViewModel polls this
        @Volatile var isRunning = false; private set
        @Volatile var currentStatus = ""; private set
        @Volatile var resultSuccess: Boolean? = null; private set
        @Volatile var resultMessage: String? = null; private set
        @Volatile var resultOutputPath: String? = null; private set

        // Process params — set BEFORE starting service
        var pendingInputPath: String? = null
        var pendingOptions: FFmpegProcessor.ProcessOptions? = null

        fun reset() {
            isRunning = false; currentStatus = ""; resultSuccess = null
            resultMessage = null; resultOutputPath = null
        }
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Start foreground IMMEDIATELY in onCreate to avoid ANR on Android 12+
        startForeground(NOTIFICATION_ID, buildNotification("Preparing..."))
        Log.d(TAG, "Service created, foreground started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val inputPath = pendingInputPath
        val options = pendingOptions

        if (inputPath == null || options == null) {
            Log.e(TAG, "No pending input/options — stopping")
            resultSuccess = false
            resultMessage = "Service error: no input data"
            isRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Mark running BEFORE launching coroutine (prevents race condition)
        isRunning = true
        resultSuccess = null
        Log.d(TAG, "Starting process: $inputPath")

        scope.launch {
            try {
                // Step 1: FFmpeg process
                currentStatus = "Video ပြုပြင်နေသည်..."
                updateNotification(currentStatus)
                Log.d(TAG, "FFmpeg processing...")

                val result = FFmpegProcessor.process(inputPath, this@VideoProcessService, options)

                if (result.success && result.outputPath != null) {
                    // Step 2: Save to gallery
                    currentStatus = "Gallery သို့ သိမ်းနေသည်..."
                    updateNotification(currentStatus)
                    Log.d(TAG, "Saving to gallery...")

                    val outputFile = File(result.outputPath)
                    val galleryUri = FFmpegProcessor.saveToGallery(this@VideoProcessService, outputFile)

                    resultSuccess = true
                    resultOutputPath = galleryUri ?: result.outputPath
                    resultMessage = "✅ ပြီးပါပြီ! Movies/RecapMaker/ (${result.durationMs / 1000}s)"

                    outputFile.delete()
                    showDoneNotification("✅ Video ပြီးပါပြီ!", "Movies/RecapMaker/ ထဲ သိမ်းပြီး", true)
                    Log.d(TAG, "Success! Gallery URI: $galleryUri")
                } else {
                    resultSuccess = false
                    resultMessage = result.error ?: "FFmpeg processing failed"
                    showDoneNotification("❌ Process Failed", resultMessage?.take(100) ?: "", false)
                    Log.e(TAG, "FFmpeg failed: ${result.error}")
                }
            } catch (e: Exception) {
                resultSuccess = false
                resultMessage = "Service error: ${e.message}"
                showDoneNotification("❌ Error", e.message?.take(100) ?: "Unknown error", false)
                Log.e(TAG, "Exception in service", e)
            } finally {
                currentStatus = ""
                pendingInputPath = null
                pendingOptions = null
                isRunning = false
                Log.d(TAG, "Service finishing, isRunning=false")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, "Video Processing", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Video processing progress"
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recap Maker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setSilent(true)
            .build()
    }

    private fun playCompletionSound(success: Boolean) {
        try {
            val uri: Uri = if (success) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val rm = RingtoneManager.getRingtone(this, uri)
            rm.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            rm.play()
        } catch (_: Exception) {}
    }

    private fun vibrate(pattern: LongArray) {
        try {
            val vib = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
            else @Suppress("DEPRECATION") vib.vibrate(pattern, -1)
        } catch (_: Exception) {}
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (e: Exception) {
            Log.w(TAG, "Notification update failed: ${e.message}")
        }
    }

    private fun showDoneNotification(title: String, text: String, success: Boolean) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(if (success) android.R.drawable.ic_dialog_info else android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .build()
            nm.notify(DONE_NOTIFICATION_ID, notification)
            playCompletionSound(success)
            if (success) vibrate(longArrayOf(0, 100, 50, 100)) else vibrate(longArrayOf(0, 200, 100, 200, 100, 200))
        } catch (e: Exception) {
            Log.w(TAG, "Done notification failed: ${e.message}")
        }
    }
}
