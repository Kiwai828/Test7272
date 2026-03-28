package com.recapmaker.app.ui.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
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
    val gold: Int = 0, val silver: Int = 0,
    val pricingTiers: List<PricingTier> = emptyList(),
    // Video
    val videoUri: Uri? = null, val videoFilename: String? = null,
    val videoDuration: Int = 0,
    val urlInput: String = "", val isUploading: Boolean = false,
    // Effects
    val flipEnabled: Boolean = false, val speedEnabled: Boolean = false,
    val pitchEnabled: Boolean = false, val noiseEnabled: Boolean = false,
    val blurEnabled: Boolean = false, val blurBoxCount: Int = 1,
    // Watermark
    val wmText: String = "", val wmPosition: String = "bottom_center",
    val wmSize: Int = 24, val wmColor: String = "#FFFFFF",
    val wmScroll: Boolean = false, val wmBox: Boolean = false,
    val wmBoxOpacity: Float = 0.5f,
    // Logo
    val logoUri: Uri? = null,
    // AI TTS
    val aiText: String = "", val selectedVoice: String = "ThihaNeural",
    val voiceTab: String = "microsoft", val voiceSearch: String = "",
    val isAnalyzing: Boolean = false,
    // Processing
    val isProcessing: Boolean = false,
    val error: String? = null, val success: String? = null,
)

@HiltViewModel
class EditorViewModel @Inject constructor(private val repo: MainRepository) : ViewModel() {
    var state by mutableStateOf(EditorState()); private set

    init { loadCoins() }

    private fun loadCoins() {
        viewModelScope.launch {
            when (val r = repo.getUserInfo()) {
                is Result.Success -> state = state.copy(
                    gold = r.data.gold, silver = r.data.silver,
                    pricingTiers = r.data.pricing_tiers ?: emptyList(),
                )
                is Result.Error -> {}
            }
        }
    }

    val costText: String get() {
        if (state.videoDuration == 0) return ""
        val cost = getCostForDuration(state.videoDuration, state.pricingTiers)
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

    // ── Video ──
    fun onVideoSelected(uri: Uri, context: Context) {
        viewModelScope.launch {
            state = state.copy(videoUri = uri, isUploading = true, error = null)
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(context, uri)
                val dur = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000
                mmr.release()
                state = state.copy(videoDuration = dur.toInt(), videoFilename = uri.lastPathSegment ?: "video.mp4", isUploading = false)
            } catch (e: Exception) {
                state = state.copy(isUploading = false, videoFilename = uri.lastPathSegment ?: "video.mp4")
            }
        }
    }

    fun updateUrl(v: String) { state = state.copy(urlInput = v) }

    fun downloadFromUrl() {
        if (state.urlInput.isBlank()) return
        viewModelScope.launch {
            state = state.copy(isUploading = true, error = null)
            when (val r = repo.downloadFromUrl(state.urlInput)) {
                is Result.Success -> state = state.copy(
                    videoFilename = r.data.filename, isUploading = false,
                )
                is Result.Error -> state = state.copy(isUploading = false, error = r.message)
            }
        }
    }

    // ── Toggles ──
    fun toggleFlip(v: Boolean) { state = state.copy(flipEnabled = v) }
    fun toggleSpeed(v: Boolean) { state = state.copy(speedEnabled = v) }
    fun togglePitch(v: Boolean) { state = state.copy(pitchEnabled = v) }
    fun toggleNoise(v: Boolean) { state = state.copy(noiseEnabled = v) }
    fun toggleBlur(v: Boolean) { state = state.copy(blurEnabled = v) }
    fun setBlurCount(c: Int) { state = state.copy(blurBoxCount = c) }

    // ── Watermark ──
    fun setWmText(v: String) { state = state.copy(wmText = v) }
    fun setWmPosition(v: String) { state = state.copy(wmPosition = v) }
    fun setWmSize(v: Int) { state = state.copy(wmSize = v) }
    fun setWmColor(v: String) { state = state.copy(wmColor = v) }
    fun setWmScroll(v: Boolean) { state = state.copy(wmScroll = v) }
    fun setWmBox(v: Boolean) { state = state.copy(wmBox = v) }
    fun setWmBoxOpacity(v: Float) { state = state.copy(wmBoxOpacity = v) }

    // ── Logo ──
    fun onLogoSelected(uri: Uri) { state = state.copy(logoUri = uri) }
    fun removeLogo() { state = state.copy(logoUri = null) }

    // ── Voice ──
    fun setAiText(v: String) { state = state.copy(aiText = v) }
    fun selectVoice(name: String) {
        val v = VoiceData.allVoices.find { it.name == name } ?: return
        state = state.copy(selectedVoice = name, voiceTab = if (v.provider == VoiceProvider.Google) "google" else "microsoft")
    }
    fun switchVoiceTab(tab: String) { state = state.copy(voiceTab = tab, voiceSearch = "") }
    fun setVoiceSearch(q: String) { state = state.copy(voiceSearch = q) }

    fun analyzeScript() {
        if (state.aiText.isBlank()) return
        viewModelScope.launch {
            state = state.copy(isAnalyzing = true)
            when (val r = repo.analyzeText(state.aiText,
                "Translate to natural spoken Burmese. Transliterate proper nouns phonetically. Output ONLY Burmese text, no markdown.")) {
                is Result.Success -> state = state.copy(aiText = r.data.text ?: state.aiText, isAnalyzing = false)
                is Result.Error -> state = state.copy(isAnalyzing = false, error = r.message)
            }
        }
    }

    // ── Process ──
    fun startProcessing(context: Context) {
        val dur = state.videoDuration
        if (state.videoUri == null && state.videoFilename == null) { state = state.copy(error = "Video ရွေးပါ"); return }

        viewModelScope.launch {
            state = state.copy(isProcessing = true, error = null)
            // 1) Deduct coins
            val cost = getCostForDuration(dur, state.pricingTiers)
            if (cost > 0) {
                val isGemini = state.aiText.isNotBlank() && VoiceData.isGeminiVoice(state.selectedVoice)
                val coinType = if (isGemini) "gold" else "auto"
                when (val r = repo.deductCoins(cost, "Video processing ${dur}s", coinType)) {
                    is Result.Success -> state = state.copy(gold = r.data.gold, silver = r.data.silver)
                    is Result.Error -> {
                        state = state.copy(isProcessing = false, error = "Coins မလုံလောက်ပါ: ${r.message}")
                        return@launch
                    }
                }
            }
            // 2) TODO: On-device FFmpegX processing (placeholder)
            kotlinx.coroutines.delay(2000)
            state = state.copy(isProcessing = false, success = "Video processing queued!")
            loadCoins()
        }
    }

    fun clearError() { state = state.copy(error = null) }
    fun clearSuccess() { state = state.copy(success = null) }
}
