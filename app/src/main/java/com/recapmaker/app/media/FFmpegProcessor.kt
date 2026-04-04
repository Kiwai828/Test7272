package com.recapmaker.app.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
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
        val noise: Boolean = false,          // kept in model, silently ignored (not in LGPL build)
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
        val videoDurationSec: Int = 0,
        val videoWidth: Int = 0, val videoHeight: Int = 0,
        val previewWidth: Int = 0, val previewHeight: Int = 0,
    )

    data class ProcessResult(
        val success: Boolean,
        val outputPath: String? = null,
        val error: String? = null,
        val durationMs: Long = 0,
    )

    // ─────────────────────────────────────────
    // Font setup — MUST call before any drawtext
    // ─────────────────────────────────────────
    fun setupFonts(context: Context) {
        try {
            // Register system fonts so drawtext can find "Sans" / default font
            FFmpegKitConfig.setFontDirectory(context, "/system/fonts", mapOf())
            // Also register app fonts dir if it exists
            val appFonts = File(context.filesDir, "fonts")
            if (!appFonts.exists()) appFonts.mkdirs()
            FFmpegKitConfig.setFontDirectory(context, appFonts.absolutePath, mapOf())
            Log.d(TAG, "Fonts registered: /system/fonts + ${appFonts.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Font setup warning: ${e.message}")
        }
    }

    // ─────────────────────────────────────────
    // Audio extraction for AI analyze
    // ─────────────────────────────────────────
    suspend fun extractAudio(videoPath: String, context: Context): String? = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
        val session = FFmpegKit.execute("-i $videoPath -vn -c:a aac -ar 16000 -ac 1 -b:a 64k -y ${out.absolutePath}")
        if (ReturnCode.isSuccess(session.returnCode) && out.exists() && out.length() > 0) out.absolutePath
        else { out.delete(); null }
    }

    suspend fun convertPcmToAac(pcmPath: String, context: Context): String? = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "tts_aac_${System.currentTimeMillis()}.m4a")
        val session = FFmpegKit.execute("-f s16le -ar 24000 -ac 1 -i $pcmPath -c:a aac -b:a 128k -y ${out.absolutePath}")
        if (ReturnCode.isSuccess(session.returnCode) && out.exists() && out.length() > 0) out.absolutePath
        else { out.delete(); null }
    }

    fun getAudioDuration(path: String): Double = try {
        val m = MediaMetadataRetriever()
        m.setDataSource(path)
        val ms = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        m.release()
        ms / 1000.0
    } catch (_: Exception) { 0.0 }

    suspend fun matchAudioToVideoDuration(audioPath: String, videoDurSec: Int, context: Context): String? =
        withContext(Dispatchers.IO) {
            if (videoDurSec <= 0) return@withContext audioPath
            val audioDur = getAudioDuration(audioPath)
            if (audioDur <= 0) return@withContext audioPath
            val ratio = audioDur / videoDurSec
            if (ratio in 0.95..1.05) return@withContext audioPath
            val tempo = ratio.coerceIn(0.5, 3.0)
            val out = File(context.cacheDir, "tts_matched_${System.currentTimeMillis()}.m4a")
            val session = FFmpegKit.execute(
                "-i $audioPath -af atempo=${"%.2f".format(tempo)} -c:a aac -b:a 128k -y ${out.absolutePath}"
            )
            if (ReturnCode.isSuccess(session.returnCode) && out.exists() && out.length() > 0) out.absolutePath
            else audioPath
        }

    // ─────────────────────────────────────────
    // Main process — always mpeg4 (stable LGPL)
    // h264_mediacodec breaks with filter_complex
    // ─────────────────────────────────────────
    suspend fun process(inputPath: String, context: Context, options: ProcessOptions): ProcessResult =
        withContext(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()

            if (!File(inputPath).exists() || File(inputPath).length() == 0L) {
                return@withContext ProcessResult(false, error = "Input video file not found")
            }

            // Register fonts before any drawtext command
            if (options.watermarkText.isNotBlank()) {
                setupFonts(context)
            }

            val outFile = File(context.cacheDir, "processed_${System.currentTimeMillis()}.mp4")
            try {
                val cmd = buildCommand(inputPath, outFile.absolutePath, options, context)
                Log.d(TAG, "CMD: $cmd")
                val session = FFmpegKit.execute(cmd)

                if (ReturnCode.isSuccess(session.returnCode) && outFile.exists() && outFile.length() > 0) {
                    ProcessResult(true, outFile.absolutePath, durationMs = System.currentTimeMillis() - t0)
                } else {
                    val logs = session.allLogsAsString ?: "Unknown error"
                    Log.e(TAG, "FAIL logs:\n${logs.takeLast(2000)}")
                    outFile.delete()
                    // Extract meaningful error message
                    val errLine = logs.lines().lastOrNull {
                        it.contains("Error") || it.contains("Invalid") || it.contains("Cannot") ||
                        it.contains("No such") || it.contains("Conversion failed")
                    } ?: logs.lines().lastOrNull { it.isNotBlank() } ?: "Processing failed"
                    ProcessResult(false, error = errLine.trim())
                }
            } catch (e: Exception) {
                outFile.delete()
                ProcessResult(false, error = e.message ?: "Exception")
            }
        }

    // ─────────────────────────────────────────
    // Command builder
    // Rules:
    //  • mpeg4 only — h264_mediacodec incompatible with filter_complex
    //  • drawtext needs fontfile= pointing to a real .ttf on the device
    //  • filter_complex used when: logo OR (vfParts > 1 or mixed with audio filter)
    //  • Simple -vf used when: only vfParts, no logo, no hasTts
    //  • -map 0:a? always optional so audio-less videos don't crash
    // ─────────────────────────────────────────
    private fun buildCommand(input: String, output: String, opts: ProcessOptions, context: Context): String {
        val hasLogo = opts.logoPath != null && File(opts.logoPath).exists()
        val hasTts  = opts.ttsAudioPath != null && File(opts.ttsAudioPath).exists()

        val scX = if (opts.previewWidth  > 0 && opts.videoWidth  > 0) opts.videoWidth.toFloat()  / opts.previewWidth  else 1f
        val scY = if (opts.previewHeight > 0 && opts.videoHeight > 0) opts.videoHeight.toFloat() / opts.previewHeight else 1f
        fun scaleX(v: Int) = (v * scX).toInt()
        fun scaleY(v: Int) = (v * scY).toInt()

        // ── Video filter parts ──
        val vfParts = mutableListOf<String>()
        if (opts.flip)  vfParts.add("hflip")
        if (opts.speed) vfParts.add("setpts=PTS/1.05")

        for (a in opts.blurAreas) {
            val bx = scaleX(a.x).coerceAtLeast(0)
            val by = scaleY(a.y).coerceAtLeast(0)
            val bw = scaleX(a.w).coerceIn(4, 3840)
            val bh = scaleY(a.h).coerceIn(4, 2160)
            vfParts.add("delogo=x=$bx:y=$by:w=$bw:h=$bh")
        }

        if (opts.watermarkText.isNotBlank()) {
            // Sanitize text — remove all chars that break drawtext filter syntax
            val safe = opts.watermarkText
                .replace("\\", "").replace("'", "").replace("\"", "")
                .replace(":", " ").replace("%", "").replace(";", "")
                .replace(",", " ").replace("[", "").replace("]", "")
                .trim()

            if (safe.isNotBlank()) {
                // Find a real font file for drawtext — required on Android
                val fontFile = findFontFile(context)
                val fontParam = if (fontFile != null) "fontfile=$fontFile:" else ""

                val wmColor = "0x${opts.watermarkColor.removePrefix("#")}"
                val (px, py) = posXY(opts.watermarkPosition)
                // Scroll: mod uses \, which in FFmpegKit direct-arg needs single backslash
                val wmX = if (opts.watermarkScroll) "mod(t*60\\,w+text_w)-text_w" else px
                val wmBoxStr = if (opts.watermarkBox)
                    ":box=1:boxcolor=black@${opts.watermarkBoxOpacity}:boxborderw=5" else ""
                // drawtext in filter_complex: text value must NOT use shell quotes
                // Use fontfile= so Android can find the font without fontconfig
                vfParts.add("drawtext=${fontParam}text=$safe:fontsize=${opts.watermarkSize}:fontcolor=$wmColor:x=$wmX:y=$py$wmBoxStr")
            }
        }

        // ── Audio filter parts ──
        val afParts = mutableListOf<String>()
        if (!hasTts) {
            if (opts.speed) afParts.add("atempo=1.05")
            if (opts.pitch) { afParts.add("asetrate=44100*0.94"); afParts.add("aresample=44100") }
        }

        // ── Nothing to do → stream copy ──
        if (vfParts.isEmpty() && afParts.isEmpty() && !hasLogo && !hasTts) {
            return "-i $input -c copy -y $output"
        }

        val sb = StringBuilder()
        sb.append("-i $input ")
        var inputIdx = 1
        val logoIdx = if (hasLogo) { sb.append("-i ${opts.logoPath} "); val i = inputIdx; inputIdx++; i } else -1
        val ttsIdx  = if (hasTts)  { sb.append("-i ${opts.ttsAudioPath} "); val i = inputIdx; inputIdx++; i } else -1

        // ── Decide strategy ──
        // Strategy A (filter_complex): logo present OR audio filters needed
        // Strategy B (simple -vf / -af): video-only filters, no logo
        val needFilterComplex = hasLogo || afParts.isNotEmpty()

        if (needFilterComplex) {
            val fc = StringBuilder()

            // Video chain
            if (hasLogo) {
                val lw = scaleX(opts.logoW).coerceAtLeast(10)
                val lh = scaleY(opts.logoH).coerceAtLeast(10)
                val lx = scaleX(opts.logoX)
                val ly = scaleY(opts.logoY)
                if (vfParts.isNotEmpty()) {
                    fc.append("[0:v]${vfParts.joinToString(",")}[vftmp];")
                    fc.append("[$logoIdx:v]scale=$lw:$lh[logo];")
                    fc.append("[vftmp][logo]overlay=$lx:$ly[vout]")
                } else {
                    fc.append("[$logoIdx:v]scale=$lw:$lh[logo];")
                    fc.append("[0:v][logo]overlay=$lx:$ly[vout]")
                }
            } else {
                if (vfParts.isNotEmpty()) {
                    fc.append("[0:v]${vfParts.joinToString(",")}[vout]")
                } else {
                    // No video filter needed — still need filter_complex for audio
                    fc.append("[0:v]null[vout]")
                }
            }

            // Audio chain in filter_complex
            val audioSrc = if (hasTts) "$ttsIdx:a" else "0:a"
            if (afParts.isNotEmpty()) {
                fc.append(";[$audioSrc]${afParts.joinToString(",")}[aout]")
                sb.append("-filter_complex $fc ")
                sb.append("-map [vout] -map [aout] ")
            } else {
                sb.append("-filter_complex $fc ")
                sb.append("-map [vout] ")
                if (hasTts) sb.append("-map $ttsIdx:a ") else sb.append("-map 0:a? ")
            }
        } else {
            // Strategy B: simple -vf (no filter_complex), no logo
            if (vfParts.isNotEmpty()) sb.append("-vf ${vfParts.joinToString(",")} ")
            // -map not needed with -vf alone; add audio map
            if (hasTts) sb.append("-map 0:v -map $ttsIdx:a ") else sb.append("-map 0:v -map 0:a? ")
        }

        val shortestFlag = if (hasTts) "-shortest " else ""
        sb.append("-c:v mpeg4 -q:v 3 -c:a aac -b:a 128k -movflags +faststart $shortestFlag-y $output")
        return sb.toString()
    }

    // Find a real TTF font file on the device for drawtext fontfile= param
    private fun findFontFile(context: Context): String? {
        // Check app assets first (most reliable)
        val appFont = File(context.filesDir, "fonts/NotoSans-Regular.ttf")
        if (appFont.exists()) return appFont.absolutePath

        // Common Android system font paths
        val candidates = listOf(
            "/system/fonts/NotoSansMyanmarUI-Regular.ttf",
            "/system/fonts/NotoSansMyanmar-Regular.ttf",
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/DroidSans.ttf",
            "/system/fonts/Arial.ttf",
        )
        return candidates.firstOrNull { File(it).exists() }
    }

    private fun posXY(pos: String): Pair<String, String> = when (pos) {
        "top_left"      -> "10"            to "10"
        "top_center"    -> "(w-text_w)/2"  to "10"
        "top_right"     -> "w-text_w-10"   to "10"
        "bottom_left"   -> "10"            to "h-text_h-10"
        "bottom_center" -> "(w-text_w)/2"  to "h-text_h-10"
        "bottom_right"  -> "w-text_w-10"   to "h-text_h-10"
        "center"        -> "(w-text_w)/2"  to "(h-text_h)/2"
        else            -> "(w-text_w)/2"  to "h-text_h-10"
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
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null
            context.contentResolver.openOutputStream(uri)?.use { o ->
                inputFile.inputStream().use { it.copyTo(o) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            uri.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Gallery save error: ${e.message}")
            null
        }
    }
}
