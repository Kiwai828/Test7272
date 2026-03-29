package com.recapmaker.app.media

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
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
    )

    data class ProcessResult(
        val success: Boolean,
        val outputPath: String? = null,
        val error: String? = null,
        val durationMs: Long = 0,
    )

    /** Extract audio → MP3 for AI transcription */
    suspend fun extractAudio(videoPath: String, context: Context): String? = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "audio_${System.currentTimeMillis()}.mp3")
        // NO quotes around paths — FFmpegKit passes args directly
        val cmd = arrayOf("-i", videoPath, "-vn", "-acodec", "libmp3lame", "-ar", "16000", "-ac", "1", "-b:a", "64k", "-y", out.absolutePath)
        val session = FFmpegKit.execute(cmd.joinToString(" "))
        if (ReturnCode.isSuccess(session.returnCode) && out.exists() && out.length() > 0) out.absolutePath
        else { out.delete(); null }
    }

    /** Convert PCM raw audio (from Gemini TTS) to MP3 */
    suspend fun convertPcmToMp3(pcmPath: String, context: Context): String? = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "tts_mp3_${System.currentTimeMillis()}.mp3")
        val cmd = "-f s16le -ar 24000 -ac 1 -i $pcmPath -acodec libmp3lame -ab 128k -y ${out.absolutePath}"
        val session = FFmpegKit.execute(cmd)
        if (ReturnCode.isSuccess(session.returnCode) && out.exists() && out.length() > 0) out.absolutePath
        else { out.delete(); null }
    }

    /** Main video processing — all effects on-device */
    suspend fun process(inputPath: String, context: Context, options: ProcessOptions): ProcessResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val outputFile = File(context.cacheDir, "processed_${System.currentTimeMillis()}.mp4")
        try {
            val cmd = buildCommand(inputPath, outputFile.absolutePath, options)
            android.util.Log.d("FFmpeg", "CMD: $cmd")
            val session = FFmpegKit.execute(cmd)
            if (ReturnCode.isSuccess(session.returnCode) && outputFile.exists() && outputFile.length() > 0) {
                ProcessResult(true, outputFile.absolutePath, durationMs = System.currentTimeMillis() - startTime)
            } else {
                val logs = session.allLogsAsString ?: "Unknown error"
                val lastLines = logs.lines().takeLast(5).joinToString("\n")
                outputFile.delete()
                ProcessResult(false, error = "FFmpeg failed:\n$lastLines")
            }
        } catch (e: Exception) {
            outputFile.delete()
            ProcessResult(false, error = "Process error: ${e.message}")
        }
    }

    private fun buildCommand(input: String, output: String, opts: ProcessOptions): String {
        val hasLogo = opts.logoPath != null && File(opts.logoPath).exists()
        val hasTts = opts.ttsAudioPath != null && File(opts.ttsAudioPath).exists()
        val videoFilters = mutableListOf<String>()
        val audioFilters = mutableListOf<String>()

        // ── Collect video filters ──
        if (opts.flip) videoFilters.add("hflip")
        if (opts.noise) videoFilters.add("noise=alls=10:allf=t+u")
        if (opts.speed) videoFilters.add("setpts=PTS/1.05")
        for (a in opts.blurAreas) {
            if (a.w > 0 && a.h > 0) videoFilters.add("delogo=x=${a.x}:y=${a.y}:w=${a.w}:h=${a.h}")
        }
        if (opts.watermarkText.isNotBlank()) {
            val clean = opts.watermarkText.replace(":", "\\:").replace("'", "\\'")
            val color = "0x${opts.watermarkColor.removePrefix("#")}"
            val (x, y) = posXY(opts.watermarkPosition)
            val sx = if (opts.watermarkScroll) "mod(t*60\\,w+tw)-tw" else x
            val box = if (opts.watermarkBox) ":box=1:boxcolor=black@${opts.watermarkBoxOpacity}:boxborderw=5" else ""
            videoFilters.add("drawtext=text='$clean':fontsize=${opts.watermarkSize}:fontcolor=$color:x=$sx:y=$y$box")
        }

        // ── Collect audio filters (only when NOT replacing with TTS) ──
        if (!hasTts) {
            if (opts.speed) audioFilters.add("atempo=1.05")
            // pitch shift without rubberband (LGPL safe)
            if (opts.pitch) { audioFilters.add("asetrate=44100*0.94"); audioFilters.add("aresample=44100") }
        } else {
            if (opts.speed) audioFilters.add("atempo=1.05")
        }

        // ── Check: any processing needed? ──
        val hasVideoFilters = videoFilters.isNotEmpty()
        val noProcessing = !hasVideoFilters && !hasLogo && !hasTts && audioFilters.isEmpty()

        if (noProcessing) {
            // Simple copy — no re-encoding needed
            return "-i $input -c copy -y $output"
        }

        // ── Build command ──
        val sb = StringBuilder()
        sb.append("-i $input ")
        var nextInput = 1

        // Logo input
        val logoIdx: Int
        if (hasLogo) {
            sb.append("-i ${opts.logoPath} ")
            logoIdx = nextInput; nextInput++
        } else logoIdx = -1

        // TTS input
        val ttsIdx: Int
        if (hasTts) {
            sb.append("-i ${opts.ttsAudioPath} ")
            ttsIdx = nextInput; nextInput++
        } else ttsIdx = -1

        // ── Video chain ──
        if (hasLogo) {
            val lw = opts.logoW.coerceAtLeast(10)
            val lh = opts.logoH.coerceAtLeast(10)
            val vfChain = if (hasVideoFilters) "[0:v]${videoFilters.joinToString(",")}[vf];[vf]" else "[0:v]"
            sb.append("-filter_complex ")
            sb.append("[$logoIdx:v]scale=$lw:$lh[logo];${vfChain}[logo]overlay=${opts.logoX}:${opts.logoY}[vout] ")
            sb.append("-map [vout] ")
        } else if (hasVideoFilters) {
            sb.append("-vf ${videoFilters.joinToString(",")} ")
            sb.append("-map 0:v ")
        } else {
            sb.append("-map 0:v ")
        }

        // ── Audio chain ──
        if (hasTts) {
            sb.append("-map $ttsIdx:a ")
        } else {
            sb.append("-map 0:a? ")
        }
        if (audioFilters.isNotEmpty()) {
            sb.append("-af ${audioFilters.joinToString(",")} ")
        }

        // ── Encoding (LGPL safe — no libx264) ──
        sb.append("-c:v mpeg4 -q:v 3 ")  // mpeg4 with quality 3 (good quality)
        sb.append("-c:a aac -b:a 128k ")
        sb.append("-movflags +faststart -shortest ")
        sb.append("-y $output")

        return sb.toString()
    }

    private fun posXY(pos: String): Pair<String, String> = when (pos) {
        "top_left" -> "20" to "20"
        "top_center" -> "(w-text_w)/2" to "20"
        "top_right" -> "w-tw-20" to "20"
        "bottom_left" -> "20" to "h-th-20"
        "bottom_center" -> "(w-text_w)/2" to "h-th-20"
        "bottom_right" -> "w-tw-20" to "h-th-20"
        "center" -> "(w-text_w)/2" to "(h-text_h)/2"
        else -> "(w-text_w)/2" to "h-th-20"
    }

    /**
     * Save processed video to Gallery → Movies/RecapMaker/
     * Returns content URI string or null
     */
    suspend fun saveToGallery(context: Context, inputFile: File): String? = withContext(Dispatchers.IO) {
        try {
            val displayName = "RecapMaker_${System.currentTimeMillis()}"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/RecapMaker")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null

            context.contentResolver.openOutputStream(uri)?.use { out ->
                inputFile.inputStream().use { inp -> inp.copyTo(out) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            uri.toString()
        } catch (e: Exception) {
            android.util.Log.e("FFmpeg", "Gallery save failed: ${e.message}")
            null
        }
    }
}
