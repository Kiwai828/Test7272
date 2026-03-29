package com.recapmaker.app.ui.editor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
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
    val urlInput: String = "",
    val isCheckingUrl: Boolean = false,
    val videoInfo: VideoDownloader.VideoInfo? = null,
    val showResolutionPopup: Boolean = false,
    val isDownloading: Boolean = false, val downloadProgress: Float = 0f,
    val flipEnabled: Boolean = false, val speedEnabled: Boolean = false,
    val pitchEnabled: Boolean = false, val noiseEnabled: Boolean = false,
    val blurEnabled: Boolean = false, val blurAreas: List<BlurArea> = emptyList(),
    val logoUri: Uri? = null, val logoArea: BlurArea = BlurArea(),
    val wmText: String = "", val wmPosition: String = "bottom_center",
    val wmSize: Int = 24, val wmColor: String = "#FFFFFF",
    val wmScroll: Boolean = false, val wmBox: Boolean = false, val wmBoxOpacity: Float = 0.5f,
    val aiText: String = "", val selectedVoice: String = "Puck",
    val voiceTab: String = "google", val voiceSearch: String = "",
    val isAnalyzing: Boolean = false,
    val isProcessing: Boolean = false, val processStatus: String = "",
    val error: String? = null, val success: String? = null,
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
        val dur = state.videoDuration; if (dur == 0 && state.videoLocalPath == null) return ""
        val cost = getCostForDuration(dur, state.pricingTiers)
        if (cost == -1) return "(ရှည်လွန်းသည်)"; if (cost == 0) return "(အခမဲ့)"
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
                val temp = File(context.cacheDir, "input_${System.currentTimeMillis()}.mp4")
                uri.copyToFile(context, temp)
                if (!temp.exists() || temp.length() == 0L) { state = state.copy(error = "File ဖတ်မရ"); return@launch }
                val mmr = MediaMetadataRetriever(); mmr.setDataSource(temp.absolutePath)
                val dur = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000
                mmr.release()
                state = state.copy(videoLocalPath = temp.absolutePath, videoDuration = dur.toInt(), videoFilename = uri.lastPathSegment ?: "video.mp4")
            } catch (e: Exception) { state = state.copy(error = "Video: ${e.message}") }
        }
    }

    // ═══ URL DOWNLOAD — yt-dlp ═══
    fun updateUrl(v: String) { state = state.copy(urlInput = v) }
    fun checkUrlInfo(context: Context) {
        val url = state.urlInput.trim(); if (url.isBlank()) return
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
        state = state.copy(showResolutionPopup = false, isDownloading = true, downloadProgress = 0f, error = null)
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
                    is Result.Success -> { val text = r.data.text ?: ""; if (text.isBlank()) state = state.copy(isAnalyzing = false, processStatus = "", error = "စကားပြောသံ မတွေ့ပါ") else state = state.copy(aiText = text, isAnalyzing = false, processStatus = "") }
                    is Result.Error -> state = state.copy(isAnalyzing = false, processStatus = "", error = "AI: ${r.message}")
                }
            } catch (e: Exception) { state = state.copy(isAnalyzing = false, processStatus = "", error = "${e.message}") }
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

    // ═══════════════════════════════════════════
    // PROCESS VIDEO — ForegroundService
    // ═══════════════════════════════════════════

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
            } else if (cost == -1) { state = state.copy(isProcessing = false, processStatus = "", error = "Video ရှည်လွန်းပါသည်"); return@launch }

            // ── 2. TTS (NO timeout) ──
            var ttsAudioPath: String? = null
            if (state.aiText.isNotBlank()) {
                state = state.copy(processStatus = "AI Voice ဖန်တီးနေသည်...")
                when (val r = repo.geminiTts(state.aiText, state.selectedVoice)) {
                    is Result.Success -> {
                        if (r.data.audio_data != null) {
                            try {
                                val rawFile = File(context.cacheDir, "tts_raw_${System.currentTimeMillis()}.raw")
                                rawFile.writeBytes(android.util.Base64.decode(r.data.audio_data, android.util.Base64.DEFAULT))
                                // Detect format by file header
                                val headerBytes = ByteArray(4)
                                rawFile.inputStream().use { it.read(headerBytes) }
                                val isMP3 = (headerBytes.size >= 2 && (headerBytes[0] == 0xFF.toByte() && (headerBytes[1].toInt() and 0xE0) == 0xE0))
                                        || (headerBytes.size >= 3 && headerBytes[0] == 'I'.code.toByte() && headerBytes[1] == 'D'.code.toByte() && headerBytes[2] == '3'.code.toByte())
                                val isWAV = headerBytes.size >= 4 && String(headerBytes) == "RIFF"
                                if (isMP3) {
                                    val mp3File = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
                                    rawFile.renameTo(mp3File); ttsAudioPath = mp3File.absolutePath
                                } else if (isWAV) {
                                    state = state.copy(processStatus = "TTS WAV → AAC...")
                                    val aacFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.m4a")
                                    com.arthenica.ffmpegkit.FFmpegKit.execute("-i ${rawFile.absolutePath} -c:a aac -b:a 128k -y ${aacFile.absolutePath}")
                                    rawFile.delete(); ttsAudioPath = if (aacFile.exists() && aacFile.length() > 0) aacFile.absolutePath else null
                                } else {
                                    state = state.copy(processStatus = "TTS PCM → AAC...")
                                    val aacPath = FFmpegProcessor.convertPcmToMp3(rawFile.absolutePath, context)
                                    rawFile.delete(); ttsAudioPath = aacPath
                                }
                            } catch (e: Exception) { Log.e("Editor", "TTS decode error: ${e.message}") }
                        }
                    }
                    is Result.Error -> { state = state.copy(processStatus = "⚠ TTS: ${r.message}"); delay(1500) }
                }
            }

            // ── 3. Logo ──
            var logoPath: String? = null
            state.logoUri?.let { uri -> val f = File(context.cacheDir, "logo_${System.currentTimeMillis()}.png"); if (uri.copyToFile(context, f) && f.exists()) logoPath = f.absolutePath }

            // ── 4. Build options ──
            val opts = FFmpegProcessor.ProcessOptions(
                flip = state.flipEnabled, speed = state.speedEnabled, pitch = state.pitchEnabled, noise = state.noiseEnabled,
                blurAreas = if (state.blurEnabled) state.blurAreas else emptyList(),
                logoPath = logoPath, logoX = state.logoArea.x, logoY = state.logoArea.y, logoW = state.logoArea.w.coerceAtLeast(10), logoH = state.logoArea.h.coerceAtLeast(10),
                watermarkText = state.wmText, watermarkPosition = state.wmPosition, watermarkSize = state.wmSize, watermarkColor = state.wmColor,
                watermarkScroll = state.wmScroll, watermarkBox = state.wmBox, watermarkBoxOpacity = state.wmBoxOpacity,
                ttsAudioPath = ttsAudioPath,
            )

            // ── 5. Start ForegroundService ──
            state = state.copy(processStatus = "Background process starting...")
            VideoProcessService.reset()
            VideoProcessService.pendingInputPath = inputPath
            VideoProcessService.pendingOptions = opts

            try {
                val intent = Intent(context, VideoProcessService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            } catch (e: Exception) {
                Log.e("Editor", "Failed to start service: ${e.message}")
                // Fallback: process directly (no background service)
                state = state.copy(processStatus = "Direct processing...")
                val result = FFmpegProcessor.process(inputPath, context, opts)
                handleProcessResult(result, cost, coinTypeUsed)
                ttsAudioPath?.let { File(it).delete() }; logoPath?.let { File(it).delete() }
                VideoProcessService.reset()
                return@launch
            }

            // ── 6. Wait for service to start (race condition fix) ──
            delay(1000) // Give service time to set isRunning=true

            // ── 7. Poll service status ──
            var maxWait = 600 // 5 minutes max (600 × 500ms)
            while (VideoProcessService.isRunning && maxWait > 0) {
                state = state.copy(processStatus = VideoProcessService.currentStatus.ifBlank { "Processing..." })
                delay(500)
                maxWait--
            }

            // ── 8. Handle result ──
            val success = VideoProcessService.resultSuccess
            val message = VideoProcessService.resultMessage

            if (success == true) {
                historyDao.insert(VideoHistoryEntity(fileName = state.videoFilename ?: "video.mp4", filePath = VideoProcessService.resultOutputPath ?: "", status = "completed", duration = state.videoDuration))
                state = state.copy(isProcessing = false, processStatus = "", success = message)
                loadCoins()
            } else if (success == false) {
                if (cost > 0) { repo.refundCoins(cost, "Process failed", if (coinTypeUsed == "gold") "gold" else "silver"); loadCoins() }
                historyDao.insert(VideoHistoryEntity(fileName = state.videoFilename ?: "video.mp4", filePath = "", status = "failed", duration = state.videoDuration))
                state = state.copy(isProcessing = false, processStatus = "", error = message ?: "Processing failed")
            } else {
                // Service never responded (timed out or never started)
                if (cost > 0) { repo.refundCoins(cost, "Service timeout", if (coinTypeUsed == "gold") "gold" else "silver"); loadCoins() }
                state = state.copy(isProcessing = false, processStatus = "", error = "Process timeout — service did not respond")
            }
            loadHistory()
            ttsAudioPath?.let { File(it).delete() }; logoPath?.let { File(it).delete() }
            VideoProcessService.reset()
        }
    }

    /** Fallback: handle result when processing directly (no service) */
    private suspend fun handleProcessResult(result: FFmpegProcessor.ProcessResult, cost: Int, coinTypeUsed: String) {
        if (result.success && result.outputPath != null) {
            val outputFile = File(result.outputPath)
            val galleryUri = FFmpegProcessor.saveToGallery(android.app.Application(), outputFile)
            historyDao.insert(VideoHistoryEntity(fileName = state.videoFilename ?: "video.mp4", filePath = galleryUri ?: result.outputPath, status = "completed", duration = state.videoDuration))
            outputFile.delete()
            state = state.copy(isProcessing = false, processStatus = "", success = "✅ ပြီးပါပြီ! (${result.durationMs / 1000}s)")
            loadCoins()
        } else {
            if (cost > 0) { repo.refundCoins(cost, "Process failed", if (coinTypeUsed == "gold") "gold" else "silver"); loadCoins() }
            historyDao.insert(VideoHistoryEntity(fileName = state.videoFilename ?: "video.mp4", filePath = "", status = "failed", duration = state.videoDuration))
            state = state.copy(isProcessing = false, processStatus = "", error = result.error ?: "Failed")
        }
        loadHistory()
    }

    // ═══ HISTORY ═══
    fun loadHistory() { viewModelScope.launch { historyDao.getAll().collect { state = state.copy(history = it) } } }
    fun toggleHistory() { state = state.copy(showHistory = !state.showHistory) }
    fun deleteHistoryItem(item: VideoHistoryEntity) { viewModelScope.launch { historyDao.delete(item) } }
    fun clearError() { state = state.copy(error = null) }
    fun clearSuccess() { state = state.copy(success = null) }
}
