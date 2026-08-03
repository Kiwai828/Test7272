package com.recapmaker.app.ui.subtitle

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recapmaker.app.data.local.VideoHistoryDao
import com.recapmaker.app.data.local.VideoHistoryEntity
import com.recapmaker.app.data.model.PricingTier
import com.recapmaker.app.data.model.SttSegment
import com.recapmaker.app.data.repository.MainRepository
import com.recapmaker.app.data.repository.Result
import com.recapmaker.app.media.FFmpegProcessor
import com.recapmaker.app.media.VideoDownloader
import com.recapmaker.app.util.copyToFile
import com.recapmaker.app.util.getCostForDuration
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

data class SubtitleState(
    val gold: Int = 0, val silver: Int = 0,
    val pricingTiers: List<PricingTier> = emptyList(),
    val videoUri: Uri? = null, val videoLocalPath: String? = null,
    val videoFilename: String? = null, val videoDuration: Int = 0,
    val urlInput: String = "",
    val isCheckingUrl: Boolean = false,
    val videoInfo: VideoDownloader.VideoInfo? = null,
    val showResolutionPopup: Boolean = false,
    val isDownloading: Boolean = false, val downloadProgress: Float = 0f,
    val fontColor: String = "#FFFFFF", val fontSize: Float = 16f,
    val boxEnabled: Boolean = true, val position: String = "bottom_center",
    val isProcessing: Boolean = false, val error: String? = null, val success: String? = null,
    val processStatus: String = "",
    val sttLanguage: String = "my",
    val generatedSrt: String = "",
    val subtitleSegments: List<SttSegment> = emptyList(),
    val audioReplaceUri: Uri? = null,
)

@HiltViewModel
class SubtitleViewModel @Inject constructor(
    private val repo: MainRepository,
    private val historyDao: VideoHistoryDao,
) : ViewModel() {
    var state by mutableStateOf(SubtitleState()); private set
    init { loadCoins() }

    private fun loadCoins() {
        viewModelScope.launch {
            when (val r = repo.getUserInfo()) {
                is Result.Success -> state = state.copy(gold = r.data.gold, silver = r.data.silver, pricingTiers = r.data.pricing_tiers ?: emptyList())
                is Result.Error -> {}
            }
        }
    }

    fun onVideoSelected(uri: Uri, context: Context) {
        viewModelScope.launch {
            state = state.copy(videoUri = uri, error = null)
            try {
                val temp = File(context.cacheDir, "sub_in_${System.currentTimeMillis()}.mp4")
                uri.copyToFile(context, temp)
                if (!temp.exists() || temp.length() == 0L) { state = state.copy(error = "File ဖတ်မရ"); return@launch }
                val mmr = MediaMetadataRetriever(); mmr.setDataSource(temp.absolutePath)
                val dur = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000
                mmr.release()
                state = state.copy(videoLocalPath = temp.absolutePath, videoDuration = dur.toInt(), videoFilename = uri.lastPathSegment ?: "video.mp4")
            } catch (e: Exception) { state = state.copy(error = "Video: ${e.message}") }
        }
    }

    fun updateUrl(v: String) { state = state.copy(urlInput = v) }

    fun checkUrlInfo(context: Context) {
        val url = state.urlInput.trim()
        if (url.isBlank()) return
        viewModelScope.launch {
            state = state.copy(isCheckingUrl = true, error = null)
            val info = VideoDownloader.getVideoInfo(url, context)
            if (info.valid) state = state.copy(isCheckingUrl = false, videoInfo = info, showResolutionPopup = true)
            else state = state.copy(isCheckingUrl = false, error = info.error ?: "URL စစ်မရ")
        }
    }

    fun dismissResolutionPopup() { state = state.copy(showResolutionPopup = false) }

    fun downloadWithFormat(context: Context, format: VideoDownloader.VideoFormat) {
        val info = state.videoInfo ?: return
        state = state.copy(showResolutionPopup = false, isDownloading = true, downloadProgress = 0f)
        viewModelScope.launch {
            val result = VideoDownloader.download(info.url, context, format.formatId, info.isDirectUrl) { p -> state = state.copy(downloadProgress = p) }
            if (result.success && result.file != null) {
                var dur = 0
                try { val mmr = MediaMetadataRetriever(); mmr.setDataSource(result.file.absolutePath); dur = ((mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000).toInt(); mmr.release() } catch (_: Exception) {}
                state = state.copy(isDownloading = false, videoUri = Uri.fromFile(result.file), videoLocalPath = result.file.absolutePath,
                    videoFilename = info.title.take(50).ifBlank { result.file.name }, videoDuration = if (dur > 0) dur else info.duration, urlInput = "", videoInfo = null)
            } else state = state.copy(isDownloading = false, error = result.error ?: "Download fail")
        }
    }

    fun setFontSize(v: Float) { state = state.copy(fontSize = v) }
    fun setFontColor(c: String) { state = state.copy(fontColor = c) }
    fun toggleBox(v: Boolean) { state = state.copy(boxEnabled = v) }
    fun setPosition(p: String) { state = state.copy(position = p) }
    fun setSttLanguage(lang: String) { state = state.copy(sttLanguage = lang) }
    fun setAudioReplace(uri: Uri?) { state = state.copy(audioReplaceUri = uri) }

    fun startProcessing(context: Context) {
        val inputPath = state.videoLocalPath ?: run { state = state.copy(error = "Video ရွေးပါ"); return }
        viewModelScope.launch {
            state = state.copy(isProcessing = true, error = null, processStatus = "Coins check...", generatedSrt = "", subtitleSegments = emptyList())

            // ── 1. Deduct coins ──
            val cost = getCostForDuration(state.videoDuration, state.pricingTiers)
            if (cost > 0) {
                when (val r = repo.deductCoins(cost, "Subtitle ${state.videoDuration}s")) {
                    is Result.Success -> state = state.copy(gold = r.data.gold, silver = r.data.silver)
                    is Result.Error -> { state = state.copy(isProcessing = false, processStatus = "", error = "Coins: ${r.message}"); return@launch }
                }
            }

            // ── 2. Extract audio ──
            state = state.copy(processStatus = "Extracting audio...")
            var audioPath: String? = null
            try {
                audioPath = FFmpegProcessor.extractAudio(inputPath, context)
            } catch (e: Exception) {
                state = state.copy(isProcessing = false, processStatus = "", error = "Audio extract: ${e.message}")
                if (cost > 0) { repo.refundCoins(cost, "Failed", "silver"); loadCoins() }
                return@launch
            }
            if (audioPath == null) {
                state = state.copy(isProcessing = false, processStatus = "", error = "Audio extract failed")
                if (cost > 0) { repo.refundCoins(cost, "Failed", "silver"); loadCoins() }
                return@launch
            }

            // ── 3. Speech-to-Text via API ──
            state = state.copy(processStatus = "Transcribing (AI)...")
            val audioFile = File(audioPath)
            val sttResult = repo.groqStt(audioFile, state.sttLanguage)
            if (sttResult is Result.Error) {
                state = state.copy(isProcessing = false, processStatus = "", error = "STT: ${sttResult.message}")
                audioFile.delete()
                if (cost > 0) { repo.refundCoins(cost, "Failed", "silver"); loadCoins() }
                return@launch
            }
            val response = (sttResult as Result.Success).data
            audioFile.delete()
            val segments = response.result?.segments ?: emptyList()
            if (segments.isEmpty()) {
                state = state.copy(isProcessing = false, processStatus = "", error = "No speech detected")
                if (cost > 0) { repo.refundCoins(cost, "Failed", "silver"); loadCoins() }
                return@launch
            }
            state = state.copy(subtitleSegments = segments)

            // ── 4. Generate SRT ──
            state = state.copy(processStatus = "Generating subtitles...")
            val srtContent = generateSrt(segments)
            state = state.copy(generatedSrt = srtContent)
            val srtFile = File(context.cacheDir, "subs_${System.currentTimeMillis()}.srt")
            try {
                FileWriter(srtFile).use { it.write(srtContent) }
            } catch (e: Exception) {
                state = state.copy(isProcessing = false, processStatus = "", error = "SRT write: ${e.message}")
                if (cost > 0) { repo.refundCoins(cost, "Failed", "silver"); loadCoins() }
                return@launch
            }

            // ── 5. Burn subtitles into video ──
            state = state.copy(processStatus = "Burning subtitles...")
            val outDir = File(context.cacheDir, "subtitle_out")
            outDir.mkdirs()
            val outputFile = File(outDir, "subbed_${System.currentTimeMillis()}.mp4")
            FFmpegKitConfig.setFontDirectory(context, "/system/fonts", mapOf())
            val fontFile = findFontFile(context) ?: "/system/fonts/Roboto-Regular.ttf"
            val fsStyle = "FontSize=${state.fontSize.toInt()},PrimaryColor=&H${colorToSrtHex(state.fontColor)}" +
                (if (state.boxEnabled) ",BackColour=&H80000000" else "")
            val fontParam = "fontfile=$fontFile:force_style='$fsStyle'"
            val vf = "subtitles=${srtFile.absolutePath}:$fontParam"
            val cmd = "-i $inputPath -vf \"$vf\" -c:a copy -c:v mpeg4 -q:v 2 -y ${outputFile.absolutePath}"
            val session = FFmpegKit.execute(cmd)
            srtFile.delete()
            if (!ReturnCode.isSuccess(session.returnCode) || !outputFile.exists() || outputFile.length() == 0L) {
                outputFile.delete()
                val logs = session.allLogsAsString ?: "Unknown FFmpeg error"
                state = state.copy(isProcessing = false, processStatus = "", error = "FFmpeg burn failed: ${logs.lines().lastOrNull { it.contains("Error", ignoreCase = true) } ?: "Unknown"}")
                if (cost > 0) { repo.refundCoins(cost, "Failed", "silver"); loadCoins() }
                return@launch
            }

            // ── 6. Save to gallery + history ──
            state = state.copy(processStatus = "Saving to gallery...")
            val galleryUri = FFmpegProcessor.saveToGallery(context, outputFile)
            historyDao.insert(VideoHistoryEntity(
                fileName = state.videoFilename ?: "video.mp4",
                filePath = galleryUri ?: outputFile.absolutePath,
                status = "completed", duration = state.videoDuration,
            ))
            outputFile.delete()
            state = state.copy(isProcessing = false, processStatus = "", success = "✅ Subtitle တင်ပြီး!")
            loadCoins()
        }
    }

    private fun generateSrt(segments: List<SttSegment>): String {
        val sb = StringBuilder()
        var idx = 1
        for (seg in segments) {
            val start = formatSrtTime(seg.start)
            val end = formatSrtTime(seg.end)
            val text = seg.text.trim()
            if (text.isNotEmpty()) {
                sb.append("$idx\n$start --> $end\n$text\n\n")
                idx++
            }
        }
        return sb.toString()
    }

    private fun formatSrtTime(seconds: Double): String {
        val totalMs = (seconds * 1000).toLong()
        val h = totalMs / 3_600_000
        val m = (totalMs % 3_600_000) / 60_000
        val s = (totalMs % 60_000) / 1_000
        val ms = totalMs % 1_000
        return "%02d:%02d:%02d,%03d".format(h, m, s, ms)
    }

    private fun colorToSrtHex(color: String): String {
        val c = color.removePrefix("#")
        return when (c.length) {
            6 -> "00${c.substring(4, 6)}${c.substring(2, 4)}${c.substring(0, 2)}" // BBGGRR
            else -> "00FFFFFF"
        }
    }

    private fun findFontFile(context: Context): String? {
        val candidates = listOf(
            "${context.filesDir}/fonts/NotoSansMyanmarUI-Regular.ttf",
            "${context.filesDir}/fonts/NotoSansMyanmar-Regular.ttf",
            "/system/fonts/NotoSansMyanmarUI-Regular.ttf",
            "/system/fonts/NotoSansMyanmar-Regular.ttf",
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
        )
        return candidates.firstOrNull { File(it).exists() }
    }

    fun clearError() { state = state.copy(error = null) }
    fun clearSuccess() { state = state.copy(success = null) }
}
