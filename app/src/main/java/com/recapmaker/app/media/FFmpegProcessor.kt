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
        val flip: Boolean = false, val speed: Boolean = false,
        val pitch: Boolean = false, val noise: Boolean = false,
        val blurAreas: List<BlurArea> = emptyList(),
        val logoPath: String? = null,
        val logoX: Int = 0, val logoY: Int = 0, val logoW: Int = 100, val logoH: Int = 100,
        val watermarkText: String = "", val watermarkPosition: String = "bottom_center",
        val watermarkSize: Int = 24, val watermarkColor: String = "#FFFFFF",
        val watermarkScroll: Boolean = false, val watermarkBox: Boolean = false,
        val watermarkBoxOpacity: Float = 0.5f,
        val ttsAudioPath: String? = null, val videoDurationSec: Int = 0,
        // For scaling preview coords → actual video coords
        val videoWidth: Int = 0, val videoHeight: Int = 0,
        val previewWidth: Int = 0, val previewHeight: Int = 0,
    )

    data class ProcessResult(val success: Boolean, val outputPath: String? = null, val error: String? = null, val durationMs: Long = 0)

    suspend fun extractAudio(videoPath: String, context: Context): String? = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
        val cmd = "-i $videoPath -vn -c:a aac -ar 16000 -ac 1 -b:a 64k -y ${out.absolutePath}"
        val s = FFmpegKit.execute(cmd)
        if (ReturnCode.isSuccess(s.returnCode) && out.exists() && out.length() > 0) out.absolutePath else { out.delete(); null }
    }

    suspend fun convertPcmToAac(pcmPath: String, context: Context): String? = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "tts_aac_${System.currentTimeMillis()}.m4a")
        val cmd = "-f s16le -ar 24000 -ac 1 -i $pcmPath -c:a aac -b:a 128k -y ${out.absolutePath}"
        val s = FFmpegKit.execute(cmd)
        if (ReturnCode.isSuccess(s.returnCode) && out.exists() && out.length() > 0) out.absolutePath else { out.delete(); null }
    }

    fun getAudioDuration(path: String): Double = try {
        val m = MediaMetadataRetriever(); m.setDataSource(path)
        val ms = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        m.release(); ms / 1000.0
    } catch (_: Exception) { 0.0 }

    suspend fun matchAudioToVideoDuration(audioPath: String, videoDurSec: Int, context: Context): String? = withContext(Dispatchers.IO) {
        if (videoDurSec <= 0) return@withContext audioPath
        val audioDur = getAudioDuration(audioPath)
        if (audioDur <= 0) return@withContext audioPath
        val ratio = audioDur / videoDurSec
        if (ratio in 0.95..1.05) return@withContext audioPath
        val tempo = ratio.coerceIn(0.5, 3.0)
        val out = File(context.cacheDir, "tts_matched_${System.currentTimeMillis()}.m4a")
        val cmd = "-i $audioPath -af atempo=${"%.2f".format(tempo)} -c:a aac -b:a 128k -y ${out.absolutePath}"
        val s = FFmpegKit.execute(cmd)
        if (ReturnCode.isSuccess(s.returnCode) && out.exists() && out.length() > 0) out.absolutePath else audioPath
    }

    suspend fun process(inputPath: String, context: Context, options: ProcessOptions): ProcessResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val outFile = File(context.cacheDir, "processed_${System.currentTimeMillis()}.mp4")
        try {
            val cmd = buildCommand(inputPath, outFile.absolutePath, options)
            Log.d(TAG, "CMD: $cmd")
            val session = FFmpegKit.execute(cmd)
            if (ReturnCode.isSuccess(session.returnCode) && outFile.exists() && outFile.length() > 0) {
                ProcessResult(true, outFile.absolutePath, durationMs = System.currentTimeMillis() - t0)
            } else {
                val logs = session.allLogsAsString ?: "Unknown"
                Log.e(TAG, "FAIL:\n${logs.takeLast(500)}")
                outFile.delete()
                ProcessResult(false, error = logs.lines().takeLast(3).joinToString("\n"))
            }
        } catch (e: Exception) { outFile.delete(); ProcessResult(false, error = "${e.message}") }
    }

    private fun buildCommand(input: String, output: String, opts: ProcessOptions): String {
        val hasLogo = opts.logoPath != null && File(opts.logoPath).exists()
        val hasTts = opts.ttsAudioPath != null && File(opts.ttsAudioPath).exists()

        // ── Scale factor: preview container → actual video resolution ──
        val sx = if (opts.previewWidth > 0 && opts.videoWidth > 0) opts.videoWidth.toFloat() / opts.previewWidth else 1f
        val sy = if (opts.previewHeight > 0 && opts.videoHeight > 0) opts.videoHeight.toFloat() / opts.previewHeight else 1f
        fun scaleX(v: Int) = (v * sx).toInt()
        fun scaleY(v: Int) = (v * sy).toInt()

        // ── Collect video filters ──
        val vf = mutableListOf<String>()
        if (opts.flip) vf.add("hflip")
        if (opts.noise) vf.add("noise=alls=10:allf=t+u")
        if (opts.speed) vf.add("setpts=PTS/1.05")

        // Blur areas — scale preview coords to video coords + clamp
        for (a in opts.blurAreas) {
            val bx = scaleX(a.x).coerceAtLeast(0)
            val by = scaleY(a.y).coerceAtLeast(0)
            val bw = scaleX(a.w).coerceIn(2, 3840)
            val bh = scaleY(a.h).coerceIn(2, 2160)
            vf.add("delogo=x=$bx:y=$by:w=$bw:h=$bh")
        }

        // Drawtext watermark — sanitize text for FFmpegKit (NO shell, NO double escape)
        if (opts.watermarkText.isNotBlank()) {
            val wmText = opts.watermarkText
                .replace("\\", "")
                .replace("'", "")
                .replace(":", " ")
                .replace("%", "")
                .replace(";", " ")
            val wmColor = "0x${opts.watermarkColor.removePrefix("#")}"
            val (px, py) = posXY(opts.watermarkPosition)
            // FFmpegKit: single backslash escape only (not shell)
            // NOTE: must NOT reuse variable name `sx` — that's the scale factor above
            val wmX = if (opts.watermarkScroll) "mod(t*60\\,w+text_w)-text_w" else px
            val wmBox = if (opts.watermarkBox) ":box=1:boxcolor=black@${opts.watermarkBoxOpacity}:boxborderw=5" else ""
            vf.add("drawtext=text='$wmText':fontsize=${opts.watermarkSize}:fontcolor=$wmColor:x=$wmX:y=$py$wmBox")
        }

        // ── Audio filters ──
        val af = mutableListOf<String>()
        if (!hasTts) {
            if (opts.speed) af.add("atempo=1.05")
            if (opts.pitch) { af.add("asetrate=44100*0.94"); af.add("aresample=44100") }
        }

        // ── No processing → copy ──
        if (vf.isEmpty() && af.isEmpty() && !hasLogo && !hasTts) {
            return "-i $input -c copy -y $output"
        }

        val sb = StringBuilder()
        sb.append("-i $input ")
        var idx = 1
        val logoIdx = if (hasLogo) { sb.append("-i ${opts.logoPath} "); val i = idx; idx++; i } else -1
        val ttsIdx = if (hasTts) { sb.append("-i ${opts.ttsAudioPath} "); val i = idx; idx++; i } else -1

        // ═══ Strategy A: Logo → use filter_complex for ALL filters ═══
        if (hasLogo) {
            val lw = scaleX(opts.logoW).coerceAtLeast(10)
            val lh = scaleY(opts.logoH).coerceAtLeast(10)
            val lx = scaleX(opts.logoX)
            val ly = scaleY(opts.logoY)
            val fc = StringBuilder()

            // Video: [filters] → [overlay logo] → [vout]
            if (vf.isNotEmpty()) {
                fc.append("[0:v]${vf.joinToString(",")}[vf];")
                fc.append("[$logoIdx:v]scale=$lw:$lh[logo];")
                fc.append("[vf][logo]overlay=${lx}:${ly}[vout]")
            } else {
                fc.append("[$logoIdx:v]scale=$lw:$lh[logo];")
                fc.append("[0:v][logo]overlay=${lx}:${ly}[vout]")
            }

            // Audio in filter_complex (if filters needed)
            val audioSrc = if (hasTts) "$ttsIdx:a" else "0:a"
            if (af.isNotEmpty()) {
                fc.append(";[$audioSrc]${af.joinToString(",")}[aout]")
                sb.append("-filter_complex ").append(fc).append(" ")
                sb.append("-map [vout] -map [aout] ")
            } else {
                sb.append("-filter_complex ").append(fc).append(" ")
                sb.append("-map [vout] ")
                if (hasTts) sb.append("-map $ttsIdx:a ") else sb.append("-map 0:a? ")
            }
        }
        // ═══ Strategy B: No logo → simple -vf + -af (never mixed with filter_complex) ═══
        else {
            if (vf.isNotEmpty()) sb.append("-vf ").append(vf.joinToString(",")).append(" ")
            sb.append("-map 0:v ")
            if (hasTts) sb.append("-map $ttsIdx:a ") else sb.append("-map 0:a? ")
            if (af.isNotEmpty()) sb.append("-af ").append(af.joinToString(",")).append(" ")
        }

        sb.append("-c:v mpeg4 -q:v 3 -c:a aac -b:a 128k -movflags +faststart -shortest -y $output")
        return sb.toString()
    }

    private fun posXY(pos: String): Pair<String, String> = when (pos) {
        "top_left"      -> "20"                    to "20"
        "top_center"    -> "(w-text_w)/2"          to "20"
        "top_right"     -> "w-text_w-20"           to "20"
        "bottom_left"   -> "20"                    to "h-text_h-20"
        "bottom_center" -> "(w-text_w)/2"          to "h-text_h-20"
        "bottom_right"  -> "w-text_w-20"           to "h-text_h-20"
        "center"        -> "(w-text_w)/2"          to "(h-text_h)/2"
        else            -> "(w-text_w)/2"          to "h-text_h-20"
    }

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
            context.contentResolver.openOutputStream(uri)?.use { o -> inputFile.inputStream().use { it.copyTo(o) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0); context.contentResolver.update(uri, values, null, null) }
            uri.toString()
        } catch (e: Exception) { Log.e(TAG, "Gallery: ${e.message}"); null }
    }
}
