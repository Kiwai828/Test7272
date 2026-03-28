package com.recapmaker.app.ui.editor

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
import com.recapmaker.app.data.model.*
import com.recapmaker.app.data.repository.MainRepository
import com.recapmaker.app.data.repository.Result
import com.recapmaker.app.media.FFmpegProcessor
import com.recapmaker.app.media.VideoDownloader
import com.recapmaker.app.util.copyToFile
import com.recapmaker.app.util.getCostForDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class EditorState(
    val gold: Int = 0, val silver: Int = 0,
    val pricingTiers: List<PricingTier> = emptyList(),
    // Video source
    val videoUri: Uri? = null,
    val videoLocalPath: String? = null,  // local file path (from picker or download)
    val videoFilename: String? = null,
    val videoDuration: Int = 0,
    // URL download
    val urlInput: String = "",
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,  // 0..1
    // Effects
    val flipEnabled: Boolean = false, val speedEnabled: Boolean = false,
    val pitchEnabled: Boolean = false, val noiseEnabled: Boolean = false,
    // Blur
    val blurEnabled: Boolean = false, val blurAreas: List<BlurArea> = emptyList(),
    // Logo
    val logoUri: Uri? = null, val logoArea: BlurArea = BlurArea(),
    // Watermark
    val wmText: String = "", val wmPosition: String = "bottom_center",
    val wmSize: Int = 24, val wmColor: String = "#FFFFFF",
    val wmScroll: Boolean = false, val wmBox: Boolean = false, val wmBoxOpacity: Float = 0.5f,
    // AI TTS
    val aiText: String = "", val selectedVoice: String = "ThihaNeural",
    val voiceTab: String = "microsoft", val voiceSearch: String = "",
    val isAnalyzing: Boolean = false,
    // Processing
    val isProcessing: Boolean = false,
    val processProgress: Float = 0f,
    val processStatus: String = "",
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
                // Copy to cache
                val tempFile = File(context.cacheDir, "input_${System.currentTimeMillis()}.mp4")
                uri.copyToFile(context, tempFile)
                // Get duration
                val mmr = MediaMetadataRetriever(); mmr.setDataSource(tempFile.absolutePath)
                val dur = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000
                mmr.release()
                state = state.copy(videoLocalPath = tempFile.absolutePath, videoDuration = dur.toInt(),
                    videoFilename = uri.lastPathSegment ?: "video.mp4")
            } catch (e: Exception) {
                state = state.copy(error = "Video ဖတ်၍မရပါ: ${e.message}")
            }
        }
    }

    fun updateUrl(v: String) { state = state.copy(urlInput = v) }

    fun downloadFromUrl(context: Context) {
        val url = state.urlInput.trim()
        if (url.isBlank()) return
        viewModelScope.launch {
            state = state.copy(isDownloading = true, downloadProgress = 0f, error = null)
            val result = VideoDownloader.download(url, context) { downloaded, total ->
                val progress = if (total > 0) downloaded.toFloat() / total else 0f
                state = state.copy(downloadProgress = progress)
            }
            if (result.success && result.file != null) {
                // Get duration
                var dur = 0
                try {
                    val mmr = MediaMetadataRetriever(); mmr.setDataSource(result.file.absolutePath)
                    dur = ((mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000).toInt()
                    mmr.release()
                } catch (_: Exception) {}
                state = state.copy(
                    isDownloading = false, videoUri = Uri.fromFile(result.file),
                    videoLocalPath = result.file.absolutePath,
                    videoFilename = result.file.name, videoDuration = dur, urlInput = "",
                )
            } else {
                state = state.copy(isDownloading = false, error = result.error ?: "Download failed")
            }
        }
    }

    // ═══ EFFECTS ═══
    fun toggleFlip(v: Boolean) { state = state.copy(flipEnabled = v) }
    fun toggleSpeed(v: Boolean) { state = state.copy(speedEnabled = v) }
    fun togglePitch(v: Boolean) { state = state.copy(pitchEnabled = v) }
    fun toggleNoise(v: Boolean) { state = state.copy(noiseEnabled = v) }

    // ═══ BLUR ═══
    fun toggleBlur(v: Boolean) {
        state = if (v && state.blurAreas.isEmpty()) state.copy(blurEnabled = true, blurAreas = listOf(BlurArea(50, 50, 120, 60)))
        else state.copy(blurEnabled = v)
    }
    fun addBlurBox() { state = state.copy(blurAreas = state.blurAreas + BlurArea(30 + state.blurAreas.size * 20, 30 + state.blurAreas.size * 20, 120, 60)) }
    fun removeBlurBox(index: Int) { state = state.copy(blurAreas = state.blurAreas.toMutableList().apply { if (index in indices) removeAt(index) }) }
    fun updateBlurBox(index: Int, area: BlurArea) { state = state.copy(blurAreas = state.blurAreas.toMutableList().apply { if (index in indices) set(index, area) }) }

    // ═══ LOGO ═══
    fun onLogoSelected(uri: Uri) { state = state.copy(logoUri = uri) }
    fun removeLogo() { state = state.copy(logoUri = null) }
    fun updateLogoArea(area: BlurArea) { state = state.copy(logoArea = area) }

    // ═══ WATERMARK ═══
    fun setWmText(v: String) { state = state.copy(wmText = v) }
    fun setWmPosition(v: String) { state = state.copy(wmPosition = v) }
    fun setWmSize(v: Int) { state = state.copy(wmSize = v) }
    fun setWmColor(v: String) { state = state.copy(wmColor = v) }
    fun setWmScroll(v: Boolean) { state = state.copy(wmScroll = v) }
    fun setWmBox(v: Boolean) { state = state.copy(wmBox = v) }
    fun setWmBoxOpacity(v: Float) { state = state.copy(wmBoxOpacity = v) }

    // ═══ VOICE ═══
    fun setAiText(v: String) { state = state.copy(aiText = v) }
    fun selectVoice(name: String) {
        val v = VoiceData.allVoices.find { it.name == name } ?: return
        state = state.copy(selectedVoice = name, voiceTab = if (v.provider == VoiceProvider.Google) "google" else "microsoft")
    }
    fun switchVoiceTab(tab: String) { state = state.copy(voiceTab = tab, voiceSearch = "") }
    fun setVoiceSearch(q: String) { state = state.copy(voiceSearch = q) }

    /**
     * Full AI Analyze flow:
     * 1. Extract audio from video (FFmpeg on-device)
     * 2. Send audio to /api/ai/stt (Groq Whisper) → English text
     * 3. Send English text to /api/ai/analyze (Gemini) → Myanmar script
     * 4. Auto-fill script box
     */
    fun analyzeScript(context: Context) {
        val videoPath = state.videoLocalPath
        if (videoPath == null) { state = state.copy(error = "Video ရွေးမှ Analyze လုပ်နိုင်ပါသည်"); return }
        viewModelScope.launch {
            state = state.copy(isAnalyzing = true, error = null)
            try {
                // Step 1: Extract audio from video
                state = state.copy(processStatus = "Audio extract လုပ်နေသည်...")
                val audioPath = FFmpegProcessor.extractAudio(videoPath, context)
                if (audioPath == null) {
                    state = state.copy(isAnalyzing = false, error = "Audio extract မရပါ")
                    return@launch
                }

                // Step 2: Send to Groq STT → English text
                state = state.copy(processStatus = "Speech-to-Text ပြောင်းနေသည်...")
                val audioFile = File(audioPath)
                val sttResult = repo.groqStt(audioFile, "en")
                audioFile.delete()

                val englishText = when (sttResult) {
                    is Result.Success -> sttResult.data.result?.text ?: ""
                    is Result.Error -> {
                        state = state.copy(isAnalyzing = false, processStatus = "", error = "STT failed: ${sttResult.message}")
                        return@launch
                    }
                }

                if (englishText.isBlank()) {
                    state = state.copy(isAnalyzing = false, processStatus = "", error = "Video ထဲတွင် စကားပြောသံ မတွေ့ပါ")
                    return@launch
                }

                // Step 3: Translate English → Myanmar via Gemini
                state = state.copy(processStatus = "Myanmar ဘာသာပြန်နေသည်...")
                val instruction = buildString {
                    append("Target Audience: Myanmar (Burmese) viewers.\n")
                    append("Translate the input English text directly into natural spoken Burmese (မြန်မာစကားပြော).\n")
                    append("DO NOT summarize. Translate line-by-line.\n")
                    append("Transliterate proper nouns phonetically (e.g. 'Harry' → 'ဟယ်ရီ').\n")
                    append("Output ONLY the Burmese spoken translation text, no markdown, no title.")
                }
                when (val r = repo.analyzeText(englishText, instruction)) {
                    is Result.Success -> {
                        val myanmarText = r.data.text ?: englishText
                        state = state.copy(aiText = myanmarText, isAnalyzing = false, processStatus = "")
                    }
                    is Result.Error -> state = state.copy(isAnalyzing = false, processStatus = "", error = "Translate failed: ${r.message}")
                }
            } catch (e: Exception) {
                state = state.copy(isAnalyzing = false, processStatus = "", error = "Analyze failed: ${e.message}")
            }
        }
    }

    /**
     * Translate existing script text (manual input) from English to Myanmar.
     * Used when user types/pastes English text and wants translation only.
     */
    fun translateScript() {
        if (state.aiText.isBlank()) return
        viewModelScope.launch {
            state = state.copy(isAnalyzing = true)
            when (val r = repo.analyzeText(state.aiText, "Translate to natural spoken Burmese. Transliterate proper nouns phonetically. Output ONLY Burmese text.")) {
                is Result.Success -> state = state.copy(aiText = r.data.text ?: state.aiText, isAnalyzing = false)
                is Result.Error -> state = state.copy(isAnalyzing = false, error = r.message)
            }
        }
    }

    // ═══ PROCESS (on-device FFmpeg) ═══

    fun startProcessing(context: Context) {
        val inputPath = state.videoLocalPath ?: run { state = state.copy(error = "Video ရွေးပါ"); return }
        viewModelScope.launch {
            state = state.copy(isProcessing = true, processStatus = "Coins စစ်ဆေးနေသည်...", error = null)

            // 1) Deduct coins via API
            val cost = getCostForDuration(state.videoDuration, state.pricingTiers)
            var coinTypeUsed = "auto"
            if (cost > 0) {
                val isGemini = state.aiText.isNotBlank() && VoiceData.isGeminiVoice(state.selectedVoice)
                coinTypeUsed = if (isGemini) "gold" else "auto"
                when (val r = repo.deductCoins(cost, "Video ${state.videoDuration}s", coinTypeUsed)) {
                    is Result.Success -> state = state.copy(gold = r.data.gold, silver = r.data.silver)
                    is Result.Error -> {
                        state = state.copy(isProcessing = false, error = "Coins မလုံလောက်ပါ: ${r.message}")
                        return@launch
                    }
                }
            }

            // 2) Get TTS audio if AI text provided
            var ttsAudioPath: String? = null
            if (state.aiText.isNotBlank()) {
                state = state.copy(processStatus = "AI Voice ဖန်တီးနေသည်...")
                when (val r = repo.geminiTts(state.aiText, state.selectedVoice)) {
                    is Result.Success -> {
                        if (r.data.audio_data != null) {
                            val audioFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
                            audioFile.writeBytes(android.util.Base64.decode(r.data.audio_data, android.util.Base64.DEFAULT))
                            ttsAudioPath = audioFile.absolutePath
                        }
                    }
                    is Result.Error -> {} // continue without TTS
                }
            }

            // 3) Prepare logo file
            var logoPath: String? = null
            state.logoUri?.let { uri ->
                val logoFile = File(context.cacheDir, "logo_${System.currentTimeMillis()}.png")
                if (uri.copyToFile(context, logoFile)) logoPath = logoFile.absolutePath
            }

            // 4) Run FFmpeg on-device
            state = state.copy(processStatus = "Video ပြုပြင်နေသည်...")
            val opts = FFmpegProcessor.ProcessOptions(
                flip = state.flipEnabled, speed = state.speedEnabled,
                pitch = state.pitchEnabled, noise = state.noiseEnabled,
                blurAreas = if (state.blurEnabled) state.blurAreas else emptyList(),
                logoPath = logoPath,
                logoX = state.logoArea.x, logoY = state.logoArea.y,
                logoW = state.logoArea.w, logoH = state.logoArea.h,
                watermarkText = state.wmText, watermarkPosition = state.wmPosition,
                watermarkSize = state.wmSize, watermarkColor = state.wmColor,
                watermarkScroll = state.wmScroll, watermarkBox = state.wmBox,
                watermarkBoxOpacity = state.wmBoxOpacity,
                ttsAudioPath = ttsAudioPath,
            )
            val result = FFmpegProcessor.process(inputPath, context, opts)

            if (result.success && result.outputPath != null) {
                // 5) Save to gallery
                state = state.copy(processStatus = "Gallery သို့ သိမ်းနေသည်...")
                val galleryUri = FFmpegProcessor.saveToGallery(context, File(result.outputPath))

                // 6) Save to Room history
                historyDao.insert(VideoHistoryEntity(
                    fileName = state.videoFilename ?: "video.mp4",
                    filePath = galleryUri ?: result.outputPath,
                    status = "completed",
                    duration = state.videoDuration,
                    fileSize = File(result.outputPath).length(),
                ))

                state = state.copy(
                    isProcessing = false, processStatus = "",
                    success = "✅ Video ပြုလုပ်ပြီးပါပြီ! Gallery ထဲ သိမ်းပြီးပါပြီ (${result.durationMs / 1000}s)",
                )
                loadHistory()
            } else {
                // Refund coins on failure
                if (cost > 0) {
                    repo.refundCoins(cost, "Process failed", if (coinTypeUsed == "gold") "gold" else "silver")
                    loadCoins()
                }
                // Save failed to history
                historyDao.insert(VideoHistoryEntity(
                    fileName = state.videoFilename ?: "video.mp4",
                    filePath = "", status = "failed",
                    duration = state.videoDuration,
                ))
                state = state.copy(isProcessing = false, processStatus = "", error = result.error ?: "Processing failed")
                loadHistory()
            }

            // Cleanup temp files
            ttsAudioPath?.let { File(it).delete() }
            logoPath?.let { File(it).delete() }
        }
    }

    // ═══ HISTORY (Room DB) ═══
    fun loadHistory() {
        viewModelScope.launch {
            historyDao.getAll().collect { state = state.copy(history = it) }
        }
    }
    fun toggleHistory() { state = state.copy(showHistory = !state.showHistory) }
    fun deleteHistoryItem(item: VideoHistoryEntity) {
        viewModelScope.launch { historyDao.delete(item) }
    }

    fun clearError() { state = state.copy(error = null) }
    fun clearSuccess() { state = state.copy(success = null) }
}
