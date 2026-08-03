package com.recapmaker.app.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
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
        val videoDurationSec: Int = 0,
        val videoWidth: Int = 0, val videoHeight: Int = 0,
        val previewWidth: Int = 0, val previewHeight: Int = 0,
        val videoEffects: VideoEffectsState = VideoEffectsState(),
        val bgMusicPath: String? = null, val bgMusicVolume: Float = 0.3f, val autoDuck: Boolean = true,
        val audioEffects: AudioEffectsState = AudioEffectsState(),
        val extraClips: List<String> = emptyList(),
        val subtitlePath: String? = null,
    )

    data class VideoEffectsState(
        val grayscale: Boolean = false, val sepia: Boolean = false, val vignette: Boolean = false,
        val brightness: Float = 1.0f, val contrast: Float = 1.0f,
    )

    data class AudioEffectsState(
        val echo: Boolean = false, val reverb: Boolean = false, val bassBoost: Boolean = false,
        val echoDelay: Float = 60f, val echoDecay: Float = 0.4f, val reverbAmount: Float = 0.3f, val bassAmount: Float = 3f,
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

    // Read original video bitrate so we can match it during re-encode (preserves quality)
    private fun getVideoBitrateKbps(path: String): Int = try {
        val m = MediaMetadataRetriever()
        m.setDataSource(path)
        val totalBps = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
        val audioBps = getAudioBitrate(path)
        m.release()
        val videoBps = if (audioBps > 0) totalBps - audioBps else totalBps
        (videoBps / 1000).coerceAtLeast(500)
    } catch (_: Exception) { 0 }

    private fun getAudioBitrate(path: String): Int {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(path)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    return format.getInteger(MediaFormat.KEY_BIT_RATE)
                }
            }
            0
        } catch (_: Exception) { 0 }
    }

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

    suspend fun autoDuckAudio(bgMusicPath: String, mainAudioPath: String, context: Context): String? =
        withContext(Dispatchers.IO) {
            val out = File(context.cacheDir, "bg_ducked_${System.currentTimeMillis()}.mp3")
            val cmd = "-i $bgMusicPath -i $mainAudioPath -filter_complex [1:a]asplit=2[main][sidechain];[sidechain]sidechaincompress=threshold=0.1:ratio=20:attack=1000:release=2000[compressed];[0:a][compressed]amix=inputs=2:duration=first:dropout_transition=3 -c:a aac -b:a 192k -y ${out.absolutePath}"
            val session = FFmpegKit.execute(cmd)
            if (ReturnCode.isSuccess(session.returnCode) && out.exists() && out.length() > 0) out.absolutePath else null
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

            if (options.watermarkText.isNotBlank()) {
                setupFonts(context)
            }

            val outFile = File(context.cacheDir, "processed_${System.currentTimeMillis()}.mp4")
            try {
                val cmd = if (options.extraClips.isNotEmpty()) buildMultiClipCommand(inputPath, outFile.absolutePath, options, context)
                else buildCommand(inputPath, outFile.absolutePath, options, context, inputPath)
                Log.d(TAG, "CMD: $cmd")
                val session = FFmpegKit.execute(cmd)

                if (ReturnCode.isSuccess(session.returnCode) && outFile.exists() && outFile.length() > 0) {
                    ProcessResult(true, outFile.absolutePath, durationMs = System.currentTimeMillis() - t0)
                } else {
                    val logs = session.allLogsAsString ?: "Unknown error"
                    Log.e(TAG, "FAIL logs:\n${logs.takeLast(2000)}")
                    outFile.delete()
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
    // Multi-clip joiner with fade transitions
    // ─────────────────────────────────────────
    private fun buildMultiClipCommand(input: String, output: String, opts: ProcessOptions, context: Context, originalPath: String = input): String {
        val allClips = listOf(input) + opts.extraClips.filter { File(it).exists() }
        if (allClips.size < 2) return buildCommand(input, output, opts, context, originalPath)

        val fadeDur = 0.5
        val sb = StringBuilder()
        allClips.forEachIndexed { i, clip ->
            sb.append("-i $clip ")
        }

        val filterParts = mutableListOf<String>()
        val labelMap = mutableMapOf<Int, String>()
        allClips.forEachIndexed { i, _ ->
            labelMap[i] = "[$i:v]"
        }

        if (allClips.size == 2) {
            filterParts.add("${labelMap[0]}xfade=transition=fade:duration=$fadeDur:offset=3[vout]")
        } else {
            var prev = "[0:v]"
            for (i in 1 until allClips.size) {
                val offset = (i * 3.0)
                filterParts.add("${prev}[${labelMap[i]!!.substringAfter('[').substringBefore(']')}:v]xfade=transition=fade:duration=$fadeDur:offset=$offset[xf$i]")
                prev = "[xf$i]"
            }
            filterParts.add("$prev[vout]")
        }

        val audioChain = StringBuilder()
        val audioParts = mutableListOf<String>()
        if (opts.ttsAudioPath != null && File(opts.ttsAudioPath).exists()) {
            audioParts.add("[${allClips.size}:a]")
        }
        allClips.forEachIndexed { i, _ ->
            if (i > 0) audioParts.add("[$i:a]")
        }
        if (audioParts.isNotEmpty()) {
            audioChain.append("${audioParts.joinToString("")}amix=inputs=${audioParts.size}:duration=first[aout]")
            filterParts.add(audioChain.toString())
        }

        val origKbps = getVideoBitrateKbps(originalPath)
        val targetKbps = if (origKbps > 0) origKbps.coerceIn(800, 20_000) else 4_000
        val filterStr = filterParts.joinToString(";")
        return "${sb}-filter_complex $filterStr -map [vout] -map [aout] -c:v mpeg4 -q:v 1 -b:v ${targetKbps}k -c:a aac -b:a 192k -movflags +faststart -y $output"
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
    private fun buildCommand(input: String, output: String, opts: ProcessOptions, context: Context, originalPath: String = input): String {
        val hasLogo = opts.logoPath != null && File(opts.logoPath).exists()
        val hasTts  = opts.ttsAudioPath != null && File(opts.ttsAudioPath).exists()
        val hasBgMusic = opts.bgMusicPath != null && File(opts.bgMusicPath).exists()
        val hasSubtitle = opts.subtitlePath != null && File(opts.subtitlePath).exists()

        val scX = if (opts.previewWidth  > 0 && opts.videoWidth  > 0) opts.videoWidth.toFloat()  / opts.previewWidth  else 1f
        val scY = if (opts.previewHeight > 0 && opts.videoHeight > 0) opts.videoHeight.toFloat() / opts.previewHeight else 1f
        fun scaleX(v: Int) = (v * scX).toInt()
        fun scaleY(v: Int) = (v * scY).toInt()

        // ── Video filter parts ──
        val vfParts = mutableListOf<String>()
        if (opts.flip)  vfParts.add("hflip")

        val ve = opts.videoEffects
        if (ve.grayscale) vfParts.add("colorchannelmixer=aa=0:aa=0:aa=0")
        if (ve.sepia) vfParts.add("colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131")
        if (ve.vignette) vfParts.add("vignette=PI/4")
        if (ve.brightness != 1.0f) vfParts.add("eq=brightness=${"%.2f".format((ve.brightness - 1).coerceIn(-0.5f, 0.5f))}")
        if (ve.contrast != 1.0f) vfParts.add("eq=contrast=${"%.2f".format(ve.contrast.coerceIn(0.5f, 2.0f))}")
        if (opts.speed) vfParts.add("setpts=PTS/1.05")

        for (a in opts.blurAreas) {
            val bx = scaleX(a.x).coerceAtLeast(0)
            val by = scaleY(a.y).coerceAtLeast(0)
            val bw = scaleX(a.w).coerceIn(4, 3840)
            val bh = scaleY(a.h).coerceIn(4, 2160)
            vfParts.add("delogo=x=$bx:y=$by:w=$bw:h=$bh")
        }

        if (opts.watermarkText.isNotBlank()) {
            val safe = opts.watermarkText
                .replace("\\", "").replace("'", "").replace("\"", "")
                .replace(":", " ").replace("%", "").replace(";", "")
                .replace(",", " ").replace("[", "").replace("]", "")
                .trim()

            if (safe.isNotBlank()) {
                val fontFile = findFontFile(context)
                val fontParam = if (fontFile != null) "fontfile=$fontFile:" else ""

                val wmColor = "0x${opts.watermarkColor.removePrefix("#")}"
                val (px, py) = posXY(opts.watermarkPosition)
                val wmX = if (opts.watermarkScroll) "mod(t*60\\,w+text_w)-text_w" else px
                val wmBoxStr = if (opts.watermarkBox)
                    ":box=1:boxcolor=black@${opts.watermarkBoxOpacity}:boxborderw=5" else ""
                vfParts.add("drawtext=${fontParam}text=$safe:fontsize=${opts.watermarkSize}:fontcolor=$wmColor:x=$wmX:y=$py$wmBoxStr")
            }
        }

        if (hasSubtitle) {
            val escapedSubPath = opts.subtitlePath!!.replace("\\", "/").replace(":", "\\:")
            vfParts.add("subtitles='$escapedSubPath':force_style='FontSize=${opts.watermarkSize.coerceIn(10, 32)},PrimaryColour=&H${opts.watermarkColor.removePrefix("#")},Alignment=2'")
        }

        // ── Audio filter parts ──
        val afParts = mutableListOf<String>()
        val ae = opts.audioEffects
        if (ae.echo) afParts.add("aecho=0.8:0.9:${ae.echoDelay.toInt()}:${"%.1f".format(ae.echoDecay)}")
        if (ae.reverb) afParts.add("aecho=1.0:0.8:40:${"%.1f".format(ae.reverbAmount)}|0.8:0.9:60:0.25")
        if (ae.bassBoost) afParts.add("bass=g=${ae.bassAmount.toInt()}")
        if (!hasTts) {
            if (opts.speed) afParts.add("atempo=1.05")
            if (opts.pitch) { afParts.add("asetrate=44100*0.94"); afParts.add("aresample=44100") }
        }

        val hasAnyAudioFilter = afParts.isNotEmpty()
        val hasAnyVideoFilter = vfParts.isNotEmpty()
        val hasAnyFilter = hasAnyVideoFilter || hasAnyAudioFilter || hasLogo || hasTts || hasBgMusic || hasSubtitle

        if (!hasAnyFilter) {
            return "-i $input -c copy -y $output"
        }

        val sb = StringBuilder()
        sb.append("-i $input ")
        var inputIdx = 1
        val logoIdx = if (hasLogo) { sb.append("-i ${opts.logoPath} "); val i = inputIdx; inputIdx++; i } else -1
        val ttsIdx  = if (hasTts)  { sb.append("-i ${opts.ttsAudioPath} "); val i = inputIdx; inputIdx++; i } else -1
        val bgIdx = if (hasBgMusic) { sb.append("-i ${opts.bgMusicPath} "); val i = inputIdx; inputIdx++; i } else -1

        val needFilterComplex = hasLogo || hasAnyAudioFilter || hasBgMusic || hasSubtitle || (hasTts && hasAnyVideoFilter)

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
                    fc.append("[0:v]null[vout]")
                }
            }

            // Audio chain
            if (hasTts || hasBgMusic || hasAnyAudioFilter) {
                val audioSrcs = mutableListOf<String>()
                if (hasTts) audioSrcs.add("$ttsIdx:a")
                else if (hasBgMusic) audioSrcs.add("0:a?")
                else audioSrcs.add("0:a?")

                if (hasBgMusic) {
                    val bgVol = "%.2f".format(opts.bgMusicVolume)
                    if (opts.autoDuck && hasTts) {
                        audioSrcs.add("[$bgIdx:a]volume=$bgVol[bg]")
                        fc.append(";[bg][$ttsIdx:a]sidechaincompress=threshold=0.1:ratio=20:attack=1000:release=2000[mixed]")
                    } else {
                        audioSrcs.add("[$bgIdx:a]volume=$bgVol[bg]")
                    }
                }

                val finalAudioSrc = if (opts.autoDuck && hasTts && hasBgMusic) "[mixed]" else if (hasBgMusic) "[bg]" else if (hasTts) "$ttsIdx:a" else "0:a"
                if (afParts.isNotEmpty()) {
                    fc.append(";$finalAudioSrc${afParts.joinToString(",")}[aout]")
                    sb.append("-filter_complex $fc ")
                    sb.append("-map [vout] -map [aout] ")
                } else {
                    sb.append("-filter_complex $fc ")
                    sb.append("-map [vout] ")
                    sb.append("-map $finalAudioSrc ")
                }
            } else {
                sb.append("-filter_complex $fc ")
                sb.append("-map [vout] ")
                sb.append("-map 0:a? ")
            }
        } else {
            // Strategy B: simple -vf (no filter_complex), no logo
            if (vfParts.isNotEmpty()) sb.append("-vf ${vfParts.joinToString(",")} ")
            if (hasTts) sb.append("-map 0:v -map $ttsIdx:a ") else sb.append("-map 0:v -map 0:a? ")
        }

        val shortestFlag = if (hasTts) "-shortest " else ""
        val origKbps = getVideoBitrateKbps(originalPath)
        val targetKbps = if (origKbps > 0) origKbps.coerceIn(800, 20_000) else 4_000
        sb.append("-c:v mpeg4 -q:v 1 -b:v ${targetKbps}k -c:a aac -b:a 192k -movflags +faststart $shortestFlag-y $output")
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
