package com.recapmaker.app.ui.editor

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recapmaker.app.data.local.VideoHistoryDao
import com.recapmaker.app.data.local.VideoHistoryEntity
import com.recapmaker.app.data.model.*
import com.recapmaker.app.data.repository.MainRepository
import com.recapmaker.app.data.repository.Result
import com.recapmaker.app.media.FFmpegProcessor
import com.recapmaker.app.media.VideoDownloader
import com.recapmaker.app.media.VideoProcessService
import com.recapmaker.app.util.copyToFile
import com.recapmaker.app.util.getCostForDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class EditorState(
    val gold: Int = 0, val silver: Int = 0,
    val pricingTiers: List<PricingTier> = emptyList(),
    val videoUri: Uri? = null, val videoLocalPath: String? = null,
    val videoFilename: String? = null, val videoDuration: Int = 0,
    // URL download
    val urlInput: String = "",
    val isCheckingUrl: Boolean = false,
    val urlInfo: VideoDownloader.VideoInfo? = null,  // HEAD result — show in popup
    val showDownloadPopup: Boolean = false,
    val isDownloading: Boolean = false, val downloadProgress: Float = 0f,
    // Effects
    val flipEnabled: Boolean = false, val speedEnabled: Boolean = false,
    val pitchEnabled: Boolean = false, val noiseEnabled: Boolean = false,
    val blurEnabled: Boolean = false, val blurAreas: List<BlurArea> = emptyList(),
    val logoUri: Uri? = null, val logoArea: BlurArea = BlurArea(),
    // Watermark
    val wmText: String = "", val wmPosition: String = "bottom_center",
    val wmSize: Int = 24, val wmColor: String = "#FFFFFF",
    val wmScroll: Boolean = false, val wmBox: Boolean = false, val wmBoxOpacity: Float = 0.5f,
    // AI
    val aiText: String = "", val selectedVoice: String = "ThihaNeural",
    val voiceTab: String = "microsoft", val voiceSearch: String = "",
    val isAnalyzing: Boolean = false,
    // Processing
    val isProcessing: Boolean = false, val processStatus: String = "",
    val error: String? = null, val success: String? = null,
    // History
    val history: List<VideoHistoryEntity> = emptyList(), val showHistory: Boolean = false,
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repo: MainRepository,
    private val historyDao: VideoHistoryDao,
) : ViewModel() {
    var state by mutableStateOf(EditorState()); private set

    init { loadCoins(); loadHistory() }

    private fun loadCoins() {
        viewModelScope.launch {
            when (val r = repo.getUserInfo()) {
                is Result.Success -> state = state.copy(gold = r.data.gold, silver = r.data.silver, pricingTiers = r.data.pricing_tiers ?: emptyList())
                is Result.Error -> {}
            }
        }
    }

    val costText: String get() {
        val dur = state.videoDuration
        if (dur == 0 && state.videoLocalPath == null) return ""
        val cost = getCostForDuration(dur, state.pricingTiers)
        if (cost == -1) return "(ရှည်လွန်းသည်)"
        if (cost == 0) return "(အခမဲ့)"
        val isGemini = state.aiText.isNotBlank() && VoiceData.isGeminiVoice(state.selectedVoice)
        return if (isGemini) "(🥇 $cost Gold)" else "($cost Coins)"
    }

    val filteredVoices: List<VoiceInfo> get() {
        val src = if (state.voiceTab == "google") VoiceData.googleVoices else VoiceData.microsoftVoices
        val q = state.voiceSearch.lowercase().trim()
        return if (q.isEmpty()) src else src.filter { it.label.lowercase().contains(q) || it.gender.name.lowercase().contains(q) }
    }

    // ═══ VIDEO SOURCE ═══

    fun onVideoSelected(uri: Uri, context: Context) {
        viewModelScope.launch {
            state = state.copy(videoUri = uri, error = null)
            try {
                val tempFile = File(context.cacheDir, "input_${System.currentTimeMillis()}.mp4")
                uri.copyToFile(context, tempFile)
                if (!tempFile.exists() || tempFile.length() == 0L) { state = state.copy(error = "Video file ဖတ်မရ"); return@launch }
                val mmr = MediaMetadataRetriever(); mmr.setDataSource(tempFile.absolutePath)
                val dur = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000
                mmr.release()
                state = state.copy(videoLocalPath = tempFile.absolutePath, videoDuration = dur.toInt(), videoFilename = uri.lastPathSegment ?: "video.mp4")
            } catch (e: Exception) { state = state.copy(error = "Video ဖတ်မရ: ${e.message}") }
        }
    }

    // ═══ URL DOWNLOAD (with info popup) ═══

    fun updateUrl(v: String) { state = state.copy(urlInput = v) }

    /** Step 1: User presses download → HEAD request → show info popup */
    fun checkUrlInfo() {
        val url = state.urlInput.trim()
        if (url.isBlank()) return
        viewModelScope.launch {
            state = state.copy(isCheckingUrl = true, error = null)
            val info = VideoDownloader.getVideoInfo(url)
            if (info.valid) {
                state = state.copy(isCheckingUrl = false, urlInfo = info, showDownloadPopup = true)
            } else {
                state = state.copy(isCheckingUrl = false, error = "URL စစ်မရ: ${info.error}")
            }
        }
    }

    fun dismissDownloadPopup() { state = state.copy(showDownloadPopup = false, urlInfo = null) }

    /** Step 2: User confirms download from popup */
    fun confirmDownload(context: Context) {
        val url = state.urlInfo?.url ?: state.urlInput.trim()
        state = state.copy(showDownloadPopup = false, isDownloading = true, downloadProgress = 0f, error = null)
        viewModelScope.launch {
            val result = VideoDownloader.download(url, context) { downloaded, total ->
                state = state.copy(downloadProgress = if (total > 0) downloaded.toFloat() / total else 0f)
            }
            if (result.success && result.file != null) {
                var dur = 0
                try {
                    val mmr = MediaMetadataRetriever(); mmr.setDataSource(result.file.absolutePath)
                    dur = ((mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000).toInt()
                    mmr.release()
                } catch (_: Exception) {}
                state = state.copy(
                    isDownloading = false, videoUri = Uri.fromFile(result.file),
                    videoLocalPath = result.file.absolutePath,
                    videoFilename = result.file.name, videoDuration = dur, urlInput = "", urlInfo = null,
                )
            } else {
                state = state.copy(isDownloading = false, error = "Download fail: ${result.error}")
            }
        }
    }

    // ═══ EFFECTS ═══
    fun toggleFlip(v: Boolean) { state = state.copy(flipEnabled = v) }
    fun toggleSpeed(v: Boolean) { state = state.copy(speedEnabled = v) }
    fun togglePitch(v: Boolean) { state = state.copy(pitchEnabled = v) }
    fun toggleNoise(v: Boolean) { state = state.copy(noiseEnabled = v) }
    fun toggleBlur(v: Boolean) { state = if (v && state.blurAreas.isEmpty()) state.copy(blurEnabled = true, blurAreas = listOf(BlurArea(50, 50, 120, 60))) else state.copy(blurEnabled = v) }
    fun addBlurBox() { state = state.copy(blurAreas = state.blurAreas + BlurArea(30 + state.blurAreas.size * 20, 30 + state.blurAreas.size * 20, 120, 60)) }
    fun removeBlurBox(i: Int) { state = state.copy(blurAreas = state.blurAreas.toMutableList().apply { if (i in indices) removeAt(i) }) }
    fun updateBlurBox(i: Int, a: BlurArea) { state = state.copy(blurAreas = state.blurAreas.toMutableList().apply { if (i in indices) set(i, a) }) }
    fun onLogoSelected(uri: Uri) { state = state.copy(logoUri = uri) }
    fun removeLogo() { state = state.copy(logoUri = null) }
    fun updateLogoArea(a: BlurArea) { state = state.copy(logoArea = a) }
    fun setWmText(v: String) { state = state.copy(wmText = v) }
    fun setWmPosition(v: String) { state = state.copy(wmPosition = v) }
    fun setWmSize(v: Int) { state = state.copy(wmSize = v) }
    fun setWmColor(v: String) { state = state.copy(wmColor = v) }
    fun setWmScroll(v: Boolean) { state = state.copy(wmScroll = v) }
    fun setWmBox(v: Boolean) { state = state.copy(wmBox = v) }
    fun setWmBoxOpacity(v: Float) { state = state.copy(wmBoxOpacity = v) }
    fun setAiText(v: String) { state = state.copy(aiText = v) }
    fun selectVoice(n: String) { val v = VoiceData.allVoices.find { it.name == n } ?: return; state = state.copy(selectedVoice = n, voiceTab = if (v.provider == VoiceProvider.Google) "google" else "microsoft") }
    fun switchVoiceTab(t: String) { state = state.copy(voiceTab = t, voiceSearch = "") }
    fun setVoiceSearch(q: String) { state = state.copy(voiceSearch = q) }

    // ═══ AI ANALYZE ═══
    fun analyzeScript(context: Context) {
        val videoPath = state.videoLocalPath ?: run { state = state.copy(error = "Video ရွေးပါ"); return }
        viewModelScope.launch {
            state = state.copy(isAnalyzing = true, error = null, processStatus = "Audio extract...")
            try {
                val audioPath = FFmpegProcessor.extractAudio(videoPath, context) ?: run { state = state.copy(isAnalyzing = false, processStatus = "", error = "Audio extract မရ"); return@launch }
                state = state.copy(processStatus = "AI Transcribe + Translate...")
                val audioFile = File(audioPath)
                val audioBase64 = android.util.Base64.encodeToString(audioFile.readBytes(), android.util.Base64.NO_WRAP)
                audioFile.delete()
                val instruction = "Listen to this audio. Transcribe all speech to English, then translate to natural spoken Burmese. Transliterate names phonetically. Output ONLY Burmese text."
                when (val r = repo.analyzeText(text = "", instruction = instruction, audioBase64 = audioBase64)) {
                    is Result.Success -> {
                        val text = r.data.text ?: ""
                        if (text.isBlank()) state = state.copy(isAnalyzing = false, processStatus = "", error = "စကားပြောသံ မတွေ့ပါ")
                        else state = state.copy(aiText = text, isAnalyzing = false, processStatus = "")
                    }
                    is Result.Error -> state = state.copy(isAnalyzing = false, processStatus = "", error = "AI: ${r.message}")
                }
            } catch (e: Exception) { state = state.copy(isAnalyzing = false, processStatus = "", error = "Analyze: ${e.message}") }
        }
    }

    fun translateScript() {
        if (state.aiText.isBlank()) return
        viewModelScope.launch {
            state = state.copy(isAnalyzing = true)
            when (val r = repo.analyzeText(text = state.aiText, instruction = "Translate to natural spoken Burmese. Output ONLY Burmese text.")) {
                is Result.Success -> state = state.copy(aiText = r.data.text ?: state.aiText, isAnalyzing = false)
                is Result.Error -> state = state.copy(isAnalyzing = false, error = "Translate: ${r.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════
    // PROCESS VIDEO — Foreground Service (background)
    //
    // Flow:
    //   1. Deduct coins (API)
    //   2. Generate TTS audio (API, NO timeout — wait until done or error)
    //   3. Prepare logo
    //   4. Start ForegroundService → FFmpeg process → Gallery save → Notification
    // ═══════════════════════════════════════════════

    fun startProcessing(context: Context) {
        val inputPath = state.videoLocalPath ?: run { state = state.copy(error = "Video ရွေးပါ"); return }

        viewModelScope.launch {
            state = state.copy(isProcessing = true, error = null, success = null)

            // ── 1. Coins ──
            state = state.copy(processStatus = "Coins စစ်ဆေးနေသည်...")
            val cost = getCostForDuration(state.videoDuration, state.pricingTiers)
            var coinTypeUsed = "auto"
            if (cost > 0) {
                val isGemini = state.aiText.isNotBlank() && VoiceData.isGeminiVoice(state.selectedVoice)
                coinTypeUsed = if (isGemini) "gold" else "auto"
                when (val r = repo.deductCoins(cost, "Video ${state.videoDuration}s", coinTypeUsed)) {
                    is Result.Success -> state = state.copy(gold = r.data.gold, silver = r.data.silver)
                    is Result.Error -> { state = state.copy(isProcessing = false, processStatus = "", error = "Coins: ${r.message}"); return@launch }
                }
            } else if (cost == -1) {
                state = state.copy(isProcessing = false, processStatus = "", error = "Video ရှည်လွန်းပါသည်"); return@launch
            }

            // ── 2. TTS (NO timeout — wait until done or error) ──
            var ttsAudioPath: String? = null
            if (state.aiText.isNotBlank()) {
                if (!VoiceData.isGeminiVoice(state.selectedVoice)) {
                    state = state.copy(processStatus = "⚠ Microsoft voice — TTS skip")
                    delay(1000)
                } else {
                    state = state.copy(processStatus = "AI Voice ဖန်တီးနေသည်... (စောင့်ပါ)")
                    when (val r = repo.geminiTts(state.aiText, state.selectedVoice)) {
                        is Result.Success -> {
                            if (r.data.audio_data != null) {
                                try {
                                    val pcmFile = File(context.cacheDir, "tts_pcm_${System.currentTimeMillis()}.raw")
                                    pcmFile.writeBytes(android.util.Base64.decode(r.data.audio_data, android.util.Base64.DEFAULT))
                                    state = state.copy(processStatus = "TTS audio converting...")
                                    val mp3Path = FFmpegProcessor.convertPcmToMp3(pcmFile.absolutePath, context)
                                    pcmFile.delete()
                                    ttsAudioPath = mp3Path
                                    if (mp3Path == null) state = state.copy(processStatus = "⚠ TTS convert fail")
                                } catch (e: Exception) {
                                    state = state.copy(processStatus = "⚠ TTS error: ${e.message}")
                                }
                            }
                        }
                        is Result.Error -> {
                            // TTS error — show but continue processing without TTS
                            state = state.copy(processStatus = "⚠ TTS: ${r.message}")
                            delay(2000)
                        }
                    }
                }
            }

            // ── 3. Logo ──
            var logoPath: String? = null
            state.logoUri?.let { uri ->
                val f = File(context.cacheDir, "logo_${System.currentTimeMillis()}.png")
                if (uri.copyToFile(context, f) && f.exists()) logoPath = f.absolutePath
            }

            // ── 4. Start Foreground Service ──
            state = state.copy(processStatus = "Background processing starting...")
            val opts = FFmpegProcessor.ProcessOptions(
                flip = state.flipEnabled, speed = state.speedEnabled,
                pitch = state.pitchEnabled, noise = state.noiseEnabled,
                blurAreas = if (state.blurEnabled) state.blurAreas else emptyList(),
                logoPath = logoPath,
                logoX = state.logoArea.x, logoY = state.logoArea.y,
                logoW = state.logoArea.w.coerceAtLeast(10), logoH = state.logoArea.h.coerceAtLeast(10),
                watermarkText = state.wmText, watermarkPosition = state.wmPosition,
                watermarkSize = state.wmSize, watermarkColor = state.wmColor,
                watermarkScroll = state.wmScroll, watermarkBox = state.wmBox,
                watermarkBoxOpacity = state.wmBoxOpacity,
                ttsAudioPath = ttsAudioPath,
            )

            // Set service params
            VideoProcessService.reset()
            VideoProcessService.pendingInputPath = inputPath
            VideoProcessService.pendingOptions = opts

            // Start foreground service
            val intent = Intent(context, VideoProcessService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            state = state.copy(processStatus = "Background မှာ process လုပ်နေသည်... Notification ကြည့်ပါ")

            // Poll service status
            while (VideoProcessService.isRunning) {
                state = state.copy(processStatus = VideoProcessService.currentStatus.ifBlank { "Processing..." })
                delay(500)
            }

            // Service done — check result
            val success = VideoProcessService.resultSuccess
            val message = VideoProcessService.resultMessage

            if (success == true) {
                val outputPath = VideoProcessService.resultOutputPath ?: ""
                historyDao.insert(VideoHistoryEntity(
                    fileName = state.videoFilename ?: "video.mp4",
                    filePath = outputPath, status = "completed",
                    duration = state.videoDuration,
                ))
                state = state.copy(isProcessing = false, processStatus = "", success = message)
                loadCoins()
            } else {
                if (cost > 0) {
                    repo.refundCoins(cost, "Process failed", if (coinTypeUsed == "gold") "gold" else "silver")
                    loadCoins()
                }
                historyDao.insert(VideoHistoryEntity(fileName = state.videoFilename ?: "video.mp4", filePath = "", status = "failed", duration = state.videoDuration))
                state = state.copy(isProcessing = false, processStatus = "", error = message ?: "Processing failed")
            }
            loadHistory()

            // Cleanup
            ttsAudioPath?.let { File(it).delete() }
            logoPath?.let { File(it).delete() }
            VideoProcessService.reset()
        }
    }

    // ═══ HISTORY ═══
    fun loadHistory() { viewModelScope.launch { historyDao.getAll().collect { state = state.copy(history = it) } } }
    fun toggleHistory() { state = state.copy(showHistory = !state.showHistory) }
    fun deleteHistoryItem(item: VideoHistoryEntity) { viewModelScope.launch { historyDao.delete(item) } }
    fun clearError() { state = state.copy(error = null) }
    fun clearSuccess() { state = state.copy(success = null) }
}
