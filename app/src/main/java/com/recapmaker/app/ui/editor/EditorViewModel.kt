package com.recapmaker.app.ui.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recapmaker.app.data.model.*
import com.recapmaker.app.data.repository.MainRepository
import com.recapmaker.app.data.repository.Result
import com.recapmaker.app.util.copyToFile
import com.recapmaker.app.util.getCostForDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class EditorState(
    // Coins
    val gold: Int = 0, val silver: Int = 0,
    val pricingTiers: List<PricingTier> = emptyList(),
    // Video
    val videoUri: Uri? = null, val videoFilename: String? = null,
    val videoServerPath: String? = null, val videoDuration: Int = 0,
    val urlInput: String = "", val isDownloading: Boolean = false,
    // Effects
    val flipEnabled: Boolean = false, val speedEnabled: Boolean = false,
    val pitchEnabled: Boolean = false, val noiseEnabled: Boolean = false,
    val blurEnabled: Boolean = false, val blurBoxCount: Int = 1,
    // Watermark
    val watermarkText: String = "", val watermarkPosition: String = "bottom_center",
    val watermarkSize: Int = 24, val watermarkColor: String = "#FFFFFF",
    val watermarkScroll: Boolean = false, val watermarkBox: Boolean = false,
    val watermarkBoxColor: String = "#000000", val watermarkBoxOpacity: Float = 0.5f,
    // Logo
    val logoUri: Uri? = null,
    // AI TTS
    val aiText: String = "", val selectedVoice: String = "ThihaNeural",
    val voiceTab: String = "microsoft", val voiceSearch: String = "",
    val isAnalyzing: Boolean = false,
    // Processing
    val isProcessing: Boolean = false,
    val error: String? = null, val successMessage: String? = null,
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repo: MainRepository,
) : ViewModel() {
    var state by mutableStateOf(EditorState()); private set

    init { loadUserInfo() }

    // ── Derived: cost text ──
    val costDisplayText: String
        get() {
            val dur = state.videoDuration
            if (dur == 0) return ""
            val cost = getCostForDuration(dur, state.pricingTiers)
            if (cost == -1) return "(Video ရှည်လွန်းသည်)"
            if (cost == 0) return "(အခမဲ့)"
            val isGemini = state.aiText.isNotBlank() && VoiceData.isGeminiVoice(state.selectedVoice)
            return if (isGemini) "(🥇 $cost Gold)" else "($cost Coins)"
        }

    // ── Derived: filtered voices ──
    val filteredVoices: List<VoiceInfo>
        get() {
            val source = if (state.voiceTab == "google") VoiceData.googleVoices else VoiceData.microsoftVoices
            val q = state.voiceSearch.lowercase().trim()
            if (q.isEmpty()) return source
            return source.filter {
                it.label.lowercase().contains(q) || it.name.lowercase().contains(q) || it.gender.name.lowercase().contains(q)
            }
        }

    private fun loadUserInfo() {
        viewModelScope.launch {
            when (val r = repo.getUserInfo()) {
                is Result.Success -> state = state.copy(
                    gold = r.data.gold, silver = r.data.silver,
                    pricingTiers = r.data.pricing_tiers,
                )
                is Result.Error -> {} // silent
            }
        }
    }

    // ── Video Selection ──

    fun onVideoSelected(uri: Uri, context: Context) {
        viewModelScope.launch {
            state = state.copy(videoUri = uri, isDownloading = true, error = null)
            try {
                // Get duration
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(context, uri)
                val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                mmr.release()

                // Copy to temp file & upload
                val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.mp4")
                uri.copyToFile(context, tempFile)

                when (val r = repo.uploadVideo(tempFile)) {
                    is Result.Success -> state = state.copy(
                        videoFilename = r.data.filename,
                        videoServerPath = r.data.path,
                        videoDuration = (durationMs / 1000).toInt(),
                        isDownloading = false,
                    )
                    is Result.Error -> state = state.copy(isDownloading = false, error = r.message)
                }
                tempFile.delete()
            } catch (e: Exception) {
                state = state.copy(isDownloading = false, error = "Video upload failed: ${e.message}")
            }
        }
    }

    fun updateUrl(url: String) { state = state.copy(urlInput = url) }

    fun downloadFromUrl() {
        if (state.urlInput.isBlank()) return
        viewModelScope.launch {
            state = state.copy(isDownloading = true, error = null)
            when (val r = repo.downloadFromUrl(state.urlInput)) {
                is Result.Success -> state = state.copy(
                    videoFilename = r.data.filename,
                    videoServerPath = r.data.path,
                    isDownloading = false,
                    videoDuration = 0, // server doesn't always return duration; we'll estimate from tier
                )
                is Result.Error -> state = state.copy(isDownloading = false, error = r.message)
            }
        }
    }

    // ── Effects ──

    fun toggleFlip(v: Boolean) { state = state.copy(flipEnabled = v) }
    fun toggleSpeed(v: Boolean) { state = state.copy(speedEnabled = v) }
    fun togglePitch(v: Boolean) { state = state.copy(pitchEnabled = v) }
    fun toggleNoise(v: Boolean) { state = state.copy(noiseEnabled = v) }
    fun toggleBlur(v: Boolean) { state = state.copy(blurEnabled = v) }
    fun setBlurBoxCount(c: Int) { state = state.copy(blurBoxCount = c) }

    // ── Watermark ──

    fun updateWatermarkText(t: String) { state = state.copy(watermarkText = t) }
    fun updateWatermarkPosition(p: String) { state = state.copy(watermarkPosition = p) }
    fun updateWatermarkSize(s: Int) { state = state.copy(watermarkSize = s) }
    fun updateWatermarkColor(c: String) { state = state.copy(watermarkColor = c) }
    fun updateWatermarkScroll(v: Boolean) { state = state.copy(watermarkScroll = v) }
    fun updateWatermarkBox(v: Boolean) { state = state.copy(watermarkBox = v) }
    fun updateWatermarkBoxOpacity(v: Float) { state = state.copy(watermarkBoxOpacity = v) }

    // ── Logo ──

    fun onLogoSelected(uri: Uri) { state = state.copy(logoUri = uri) }
    fun removeLogo() { state = state.copy(logoUri = null) }

    // ── AI TTS ──

    fun updateAiText(t: String) { state = state.copy(aiText = t) }
    fun selectVoice(name: String) {
        val voice = VoiceData.allVoices.find { it.name == name } ?: return
        state = state.copy(
            selectedVoice = name,
            voiceTab = if (voice.provider == VoiceProvider.Google) "google" else "microsoft",
        )
    }
    fun switchVoiceTab(tab: String) { state = state.copy(voiceTab = tab, voiceSearch = "") }
    fun updateVoiceSearch(q: String) { state = state.copy(voiceSearch = q) }

    fun analyzeScript() {
        if (state.aiText.isBlank()) return
        viewModelScope.launch {
            state = state.copy(isAnalyzing = true)
            val instruction = (
                "Target Audience: Myanmar (Burmese) viewers.\n"
                + "Translate the input English text directly into natural spoken Burmese (မြန်မာစကားပြော).\n"
                + "DO NOT summarize. Translate line-by-line.\n"
                + "Transliterate proper nouns phonetically.\n"
                + "Output ONLY the Burmese spoken translation text, no markdown."
            )
            when (val r = repo.analyzeText(state.aiText, instruction)) {
                is Result.Success -> state = state.copy(
                    aiText = r.data.text ?: state.aiText,
                    isAnalyzing = false,
                )
                is Result.Error -> state = state.copy(isAnalyzing = false, error = r.message)
            }
        }
    }

    // ── Process ──

    fun startProcessing(context: Context) {
        val filename = state.videoFilename ?: return
        viewModelScope.launch {
            state = state.copy(isProcessing = true, error = null)

            // Prepare logo file if selected
            var logoFile: File? = null
            state.logoUri?.let { uri ->
                try {
                    val f = File(context.cacheDir, "logo_${System.currentTimeMillis()}.png")
                    uri.copyToFile(context, f)
                    logoFile = f
                } catch (_: Exception) {}
            }

            val opts = VideoProcessOptions(
                bypassFlip = state.flipEnabled,
                bypassSpeed = state.speedEnabled,
                bypassPitch = state.pitchEnabled,
                bypassNoise = state.noiseEnabled,
                blurAreas = if (state.blurEnabled) (1..state.blurBoxCount).map { BlurArea(50 * it, 50 * it, 120, 60) } else emptyList(),
                textWatermarkText = state.watermarkText,
                textWatermarkPosition = state.watermarkPosition,
                textWatermarkSize = state.watermarkSize,
                textWatermarkColor = state.watermarkColor,
                textWatermarkScroll = state.watermarkScroll,
                textWatermarkBox = state.watermarkBox,
                textWatermarkBoxColor = state.watermarkBoxColor,
                textWatermarkBoxOpacity = state.watermarkBoxOpacity,
                aiText = state.aiText,
                voiceName = state.selectedVoice,
            )

            when (val r = repo.processVideo(filename, opts, logoFile)) {
                is Result.Success -> {
                    state = state.copy(isProcessing = false, successMessage = "Video processing started! Check history for results.")
                    loadUserInfo() // refresh coins
                }
                is Result.Error -> state = state.copy(isProcessing = false, error = r.message)
            }

            logoFile?.delete()
        }
    }

    fun clearError() { state = state.copy(error = null) }
    fun clearSuccess() { state = state.copy(successMessage = null) }
}
