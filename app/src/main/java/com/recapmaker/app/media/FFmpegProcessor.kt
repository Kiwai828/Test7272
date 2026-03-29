package com.recapmaker.app.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.recapmaker.app.data.model.BlurArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FFmpegProcessor {

    private const val TAG = "FFmpegProc"

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
        val videoDurationSec: Int = 0, // for TTS speed matching
    )

    data class ProcessResult(
        val success: Boolean,
        val outputPath: String? = null,
        val error: String? = null,
        val durationMs: Long = 0,
    )

    /** Extract audio for AI transcription */
    suspend fun extractAudio(videoPath: String, context: Context): String? = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
        val cmd = "-i $videoPath -vn -c:a aac -ar 16000 -ac 1 -b:a 64k -y ${out.absolutePath}"
        Log.d(TAG, "extractAudio: $cmd")
        val session = FFmpegKit.execute(cmd)
        if (ReturnCode.isSuccess(session.returnCode) && out.exists() && out.length() > 0) out.absolutePath
        else { out.delete(); null }
    }

    /** Convert PCM (Gemini TTS: s16le 24kHz mono) → AAC */
    suspend fun convertPcmToAac(pcmPath: String, context: Context): String? = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "tts_aac_${System.currentTimeMillis()}.m4a")
        val cmd = "-f s16le -ar 24000 -ac 1 -i $pcmPath -c:a aac -b:a 128k -y ${out.absolutePath}"
        Log.d(TAG, "pcmToAac: $cmd")
        val session = FFmpegKit.execute(cmd)
        if (ReturnCode.isSuccess(session.returnCode) && out.exists() && out.length() > 0) out.absolutePath
        else { out.delete(); null }
    }

    /** Get audio duration in seconds */
    fun getAudioDuration(path: String): Double {
        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(path)
            val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            mmr.release()
            ms / 1000.0
        } catch (_: Exception) { 0.0 }
    }

    /**
     * Speed up/slow down TTS audio to match video duration.
     * If TTS=60s, video=45s → atempo=60/45=1.33x (speed up)
     * atempo range: 0.5 to 100.0
     */
    suspend fun matchAudioToVideoDuration(audioPath: String, videoDurationSec: Int, context: Context): String? = withContext(Dispatchers.IO) {
        if (videoDurationSec <= 0) return@withContext audioPath
        val audioDur = getAudioDuration(audioPath)
        if (audioDur <= 0) return@withContext audioPath

        val ratio = audioDur / videoDurationSec
        // Only adjust if difference > 5%
        if (ratio < 1.05 && ratio > 0.95) return@withContext audioPath

        val tempo = ratio.coerceIn(0.5, 3.0)
        val out = File(context.cacheDir, "tts_matched_${System.currentTimeMillis()}.m4a")
        val cmd = "-i $audioPath -af atempo=${"%.2f".format(tempo)} -c:a aac -b:a 128k -y ${out.absolutePath}"
        Log.d(TAG, "matchAudio: ratio=${"%.2f".format(ratio)} tempo=${"%.2f".format(tempo)} cmd=$cmd")
        val session = FFmpegKit.execute(cmd)
        if (ReturnCode.isSuccess(session.returnCode) && out.exists() && out.length() > 0) out.absolutePath
        else { Log.w(TAG, "matchAudio failed, using original"); audioPath }
    }

    /** Main video processing */
    suspend fun process(inputPath: String, context: Context, options: ProcessOptions): ProcessResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val outputFile = File(context.cacheDir, "processed_${System.currentTimeMillis()}.mp4")
        try {
            val cmd = buildCommand(inputPath, outputFile.absolutePath, options)
            Log.d(TAG, "FULL CMD: $cmd")
            val session = FFmpegKit.execute(cmd)
            if (ReturnCode.isSuccess(session.returnCode) && outputFile.exists() && outputFile.length() > 0) {
                ProcessResult(true, outputFile.absolutePath, durationMs = System.currentTimeMillis() - startTime)
            } else {
                val logs = session.allLogsAsString ?: "Unknown"
                val last5 = logs.lines().takeLast(5).joinToString("\n")
                Log.e(TAG, "FFmpeg FAILED:\n$last5")
                outputFile.delete()
                ProcessResult(false, error = last5)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Process exception", e)
            outputFile.delete()
            ProcessResult(false, error = "Error: ${e.message}")
        }
    }

    /**
     * Build FFmpeg command. Strategy:
     * - Use -filter_complex ONLY when logo overlay is needed
     * - Otherwise use simple -vf + -af
     * - Never mix -filter_complex with -vf or -af
     */
    private fun buildCommand(input: String, output: String, opts: ProcessOptions): String {
        val hasLogo = opts.logoPath != null && File(opts.logoPath).exists()
        val hasTts = opts.ttsAudioPath != null && File(opts.ttsAudioPath).exists()
        val vf = mutableListOf<String>()  // video filters
        val af = mutableListOf<String>()  // audio filters

        // Video filters
        if (opts.flip) vf.add("hflip")
        if (opts.noise) vf.add("noise=alls=10:allf=t+u")
        if (opts.speed) vf.add("setpts=PTS/1.05")
        for (a in opts.blurAreas) {
            if (a.w > 0 && a.h > 0) vf.add("delogo=x=${a.x}:y=${a.y}:w=${a.w}:h=${a.h}")
        }
        if (opts.watermarkText.isNotBlank()) {
            val t = opts.watermarkText.replace("'", "").replace(":", " ")
            val c = "0x${opts.watermarkColor.removePrefix("#")}"
            val (x, y) = posXY(opts.watermarkPosition)
            val sx = if (opts.watermarkScroll) "mod(t*60\\,w+tw)-tw" else x
            val bx = if (opts.watermarkBox) ":box=1:boxcolor=black@${opts.watermarkBoxOpacity}:boxborderw=5" else ""
            vf.add("drawtext=text='$t':fontsize=${opts.watermarkSize}:fontcolor=$c:x=$sx:y=$y$bx")
        }

        // Audio filters (only for original audio, not TTS)
        if (!hasTts) {
            if (opts.speed) af.add("atempo=1.05")
            if (opts.pitch) { af.add("asetrate=44100*0.94"); af.add("aresample=44100") }
        }

        // ── No processing at all → simple copy ──
        if (vf.isEmpty() && af.isEmpty() && !hasLogo && !hasTts) {
            return "-i $input -c copy -y $output"
        }

        val sb = StringBuilder()
        // Inputs
        sb.append("-i $input ")
        var idx = 1
        val logoIdx = if (hasLogo) { sb.append("-i ${opts.logoPath} "); idx++ ; idx - 1 } else -1
        val ttsIdx = if (hasTts) { sb.append("-i ${opts.ttsAudioPath} "); idx++ ; idx - 1 } else -1

        // ── Strategy A: Logo overlay → MUST use filter_complex for everything ──
        if (hasLogo) {
            val lw = opts.logoW.coerceAtLeast(10)
            val lh = opts.logoH.coerceAtLeast(10)
            val fc = StringBuilder()
            // Video chain
            if (vf.isNotEmpty()) {
                fc.append("[0:v]${vf.joinToString(",")}[vf];")
                fc.append("[$logoIdx:v]scale=$lw:$lh[logo];")
                fc.append("[vf][logo]overlay=${opts.logoX}:${opts.logoY}[vout]")
            } else {
                fc.append("[$logoIdx:v]scale=$lw:$lh[logo];")
                fc.append("[0:v][logo]overlay=${opts.logoX}:${opts.logoY}[vout]")
            }
            // Audio chain in filter_complex (if needed)
            val audioSrc = if (hasTts) "$ttsIdx:a" else "0:a"
            if (af.isNotEmpty()) {
                fc.append(";[$audioSrc]${af.joinToString(",")}[aout]")
                sb.append("-filter_complex $fc ")
                sb.append("-map [vout] -map [aout] ")
            } else {
                sb.append("-filter_complex $fc ")
                sb.append("-map [vout] ")
                if (hasTts) sb.append("-map $ttsIdx:a ") else sb.append("-map 0:a? ")
            }
        }
        // ── Strategy B: No logo → simple -vf + -af ──
        else {
            if (vf.isNotEmpty()) sb.append("-vf ${vf.joinToString(",")} ")
            sb.append("-map 0:v ")
            if (hasTts) sb.append("-map $ttsIdx:a ") else sb.append("-map 0:a? ")
            if (af.isNotEmpty()) sb.append("-af ${af.joinToString(",")} ")
        }

        // Output
        sb.append("-c:v mpeg4 -q:v 3 -c:a aac -b:a 128k -movflags +faststart -shortest -y $output")
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

    /** Save to Gallery → Movies/RecapMaker/ */
    suspend fun saveToGallery(context: Context, inputFile: File): String? = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "RecapMaker_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/RecapMaker")
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
        } catch (e: Exception) { Log.e(TAG, "Gallery save: ${e.message}"); null }
    }
}
