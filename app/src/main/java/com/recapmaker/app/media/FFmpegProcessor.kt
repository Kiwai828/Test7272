package com.recapmaker.app.media

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.recapmaker.app.data.model.BlurArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FFmpegProcessor {

    data class ProcessOptions(
        val flip: Boolean = false,
        val speed: Boolean = false,
        val pitch: Boolean = false,
        val noise: Boolean = false,
        val blurAreas: List<BlurArea> = emptyList(),
        val logoPath: String? = null,
        val logoX: Int = 0, val logoY: Int = 0, val logoW: Int = 100, val logoH: Int = 100,
        val watermarkText: String = "",
        val watermarkPosition: String = "bottom_center",
        val watermarkSize: Int = 24,
        val watermarkColor: String = "#FFFFFF",
        val watermarkScroll: Boolean = false,
        val watermarkBox: Boolean = false,
        val watermarkBoxOpacity: Float = 0.5f,
        val ttsAudioPath: String? = null,
        val fontPath: String? = null,
    )

    data class ProcessResult(
        val success: Boolean,
        val outputPath: String? = null,
        val error: String? = null,
        val durationMs: Long = 0,
    )

    /**
     * Extract audio from video as MP3 for Groq STT.
     * Returns the path to the extracted audio file.
     */
    suspend fun extractAudio(
        videoPath: String,
        context: Context,
    ): String? = withContext(Dispatchers.IO) {
        val outputFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.mp3")
        val cmd = "-i \"$videoPath\" -vn -acodec libmp3lame -ar 16000 -ac 1 -b:a 64k -y \"${outputFile.absolutePath}\""
        val session = FFmpegKit.execute(cmd)
        if (ReturnCode.isSuccess(session.returnCode) && outputFile.exists() && outputFile.length() > 0) {
            outputFile.absolutePath
        } else {
            outputFile.delete()
            null
        }
    }

    /**
     * Process video with effects. TTS audio REPLACES original audio (no mix).
     */
    suspend fun process(
        inputPath: String,
        context: Context,
        options: ProcessOptions,
    ): ProcessResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val outputFile = File(context.cacheDir, "processed_${System.currentTimeMillis()}.mp4")
        try {
            val cmd = buildCommand(inputPath, outputFile.absolutePath, options)
            val session = FFmpegKit.execute(cmd)
            if (ReturnCode.isSuccess(session.returnCode)) {
                ProcessResult(true, outputFile.absolutePath, durationMs = System.currentTimeMillis() - startTime)
            } else {
                val logs = session.allLogsAsString ?: "Unknown error"
                val lastLine = logs.lines().lastOrNull { it.isNotBlank() } ?: logs.take(200)
                outputFile.delete()
                ProcessResult(false, error = "FFmpeg: $lastLine")
            }
        } catch (e: Exception) {
            outputFile.delete()
            ProcessResult(false, error = "Process error: ${e.message}")
        }
    }

    private fun buildCommand(input: String, output: String, opts: ProcessOptions): String {
        val videoFilters = mutableListOf<String>()
        val sb = StringBuilder()
        var hasTtsAudio = opts.ttsAudioPath != null && File(opts.ttsAudioPath).exists()

        // ── Inputs ──
        sb.append("-i \"$input\" ")
        var inputIdx = 1

        // Logo input
        var logoInputIdx = -1
        if (opts.logoPath != null && File(opts.logoPath).exists()) {
            sb.append("-i \"${opts.logoPath}\" ")
            logoInputIdx = inputIdx
            inputIdx++
        }

        // TTS audio input (will REPLACE original audio)
        var ttsInputIdx = -1
        if (hasTtsAudio) {
            sb.append("-i \"${opts.ttsAudioPath}\" ")
            ttsInputIdx = inputIdx
            inputIdx++
        }

        // ── Video filters ──
        if (opts.flip) videoFilters.add("hflip")
        if (opts.noise) videoFilters.add("noise=alls=10:allf=t+u")

        for (area in opts.blurAreas) {
            if (area.w > 0 && area.h > 0) {
                videoFilters.add("delogo=x=${area.x}:y=${area.y}:w=${area.w}:h=${area.h}")
            }
        }

        // Speed (video)
        if (opts.speed) videoFilters.add("setpts=PTS/1.05")

        // Text watermark
        if (opts.watermarkText.isNotBlank()) {
            val clean = opts.watermarkText.replace(":", "\\:").replace("'", "\\'")
            val color = "0x${opts.watermarkColor.removePrefix("#")}"
            val (x, y) = positionToXY(opts.watermarkPosition)
            val scrollX = if (opts.watermarkScroll) "mod(t*60\\,w+tw)-tw" else x
            val boxOpt = if (opts.watermarkBox) ":box=1:boxcolor=black@${opts.watermarkBoxOpacity}:boxborderw=5" else ""
            val fontOpt = if (opts.fontPath != null) ":fontfile='${opts.fontPath}'" else ""
            videoFilters.add("drawtext=text='$clean':fontsize=${opts.watermarkSize}:fontcolor=$color:x=$scrollX:y=$y$boxOpt$fontOpt")
        }

        // ── Build filter complex for logo overlay ──
        if (logoInputIdx >= 0) {
            // Scale logo and overlay
            val lw = opts.logoW.coerceAtLeast(10)
            val lh = opts.logoH.coerceAtLeast(10)
            // If we have other video filters, apply them first then overlay
            if (videoFilters.isNotEmpty()) {
                val vfChain = videoFilters.joinToString(",")
                sb.append("-filter_complex \"[0:v]${vfChain}[vf];[$logoInputIdx:v]scale=$lw:$lh[logo];[vf][logo]overlay=${opts.logoX}:${opts.logoY}[vout]\" ")
                sb.append("-map \"[vout]\" ")
            } else {
                sb.append("-filter_complex \"[$logoInputIdx:v]scale=$lw:$lh[logo];[0:v][logo]overlay=${opts.logoX}:${opts.logoY}[vout]\" ")
                sb.append("-map \"[vout]\" ")
            }
        } else if (videoFilters.isNotEmpty()) {
            sb.append("-vf \"${videoFilters.joinToString(",")}\" ")
        }

        // ── Audio mapping ──
        if (hasTtsAudio) {
            // TTS REPLACES original audio completely
            sb.append("-map $ttsInputIdx:a ")
            // Speed adjust TTS audio if speed enabled
            if (opts.speed) {
                sb.append("-af \"atempo=1.05\" ")
            }
        } else {
            // Use original audio
            sb.append("-map 0:a? ")
            val audioFilters = mutableListOf<String>()
            if (opts.speed) audioFilters.add("atempo=1.05")
            if (opts.pitch) audioFilters.add("rubberband=pitch=0.94")
            if (audioFilters.isNotEmpty()) sb.append("-af \"${audioFilters.joinToString(",")}\" ")
        }

        // ── Output settings ──
        sb.append("-c:v libx264 -preset medium -crf 18 ")
        sb.append("-c:a aac -b:a 128k ")
        sb.append("-movflags +faststart -shortest ")
        sb.append("-y \"$output\"")

        return sb.toString()
    }

    private fun positionToXY(position: String): Pair<String, String> = when (position) {
        "top_left" -> "20" to "20"
        "top_center" -> "(w-text_w)/2" to "20"
        "top_right" -> "w-tw-20" to "20"
        "bottom_left" -> "20" to "h-th-20"
        "bottom_center" -> "(w-text_w)/2" to "h-th-20"
        "bottom_right" -> "w-tw-20" to "h-th-20"
        "center" -> "(w-text_w)/2" to "(h-text_h)/2"
        else -> "(w-text_w)/2" to "h-th-20"
    }

    suspend fun saveToGallery(
        context: Context, inputFile: File,
        displayName: String = "RecapMaker_${System.currentTimeMillis()}",
    ): String? = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RecapMaker")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
            context.contentResolver.openOutputStream(uri)?.use { out -> inputFile.inputStream().use { it.copyTo(out) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            uri.toString()
        } catch (_: Exception) { null }
    }
}
