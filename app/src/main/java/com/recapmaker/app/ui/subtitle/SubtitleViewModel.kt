package com.recapmaker.app.ui.subtitle

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recapmaker.app.data.model.PricingTier
import com.recapmaker.app.data.repository.MainRepository
import com.recapmaker.app.data.repository.Result
import com.recapmaker.app.util.copyToFile
import com.recapmaker.app.util.getCostForDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SubtitleState(
    val gold: Int = 0, val silver: Int = 0,
    val pricingTiers: List<PricingTier> = emptyList(),
    val videoUri: Uri? = null, val videoFilename: String? = null,
    val videoDuration: Int = 0,
    val urlInput: String = "", val isUploading: Boolean = false,
    val fontColor: String = "#FFFFFF", val fontSize: Float = 16f,
    val boxEnabled: Boolean = true, val position: String = "bottom_center",
    val flipEnabled: Boolean = false, val speedEnabled: Boolean = false,
    val noiseEnabled: Boolean = false, val blurEnabled: Boolean = false,
    val isProcessing: Boolean = false, val error: String? = null,
)

@HiltViewModel
class SubtitleViewModel @Inject constructor(private val repo: MainRepository) : ViewModel() {
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
            state = state.copy(videoUri = uri, isUploading = true, error = null)
            try {
                val mmr = MediaMetadataRetriever(); mmr.setDataSource(context, uri)
                val dur = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000
                mmr.release()
                state = state.copy(videoDuration = dur.toInt(), videoFilename = uri.lastPathSegment ?: "video.mp4", isUploading = false)
            } catch (_: Exception) {
                state = state.copy(isUploading = false, videoFilename = uri.lastPathSegment)
            }
        }
    }

    fun updateUrl(v: String) { state = state.copy(urlInput = v) }
    fun downloadFromUrl() {
        if (state.urlInput.isBlank()) return
        viewModelScope.launch {
            state = state.copy(isUploading = true, error = null)
            when (val r = repo.downloadFromUrl(state.urlInput)) {
                is Result.Success -> state = state.copy(videoFilename = r.data.filename, isUploading = false)
                is Result.Error -> state = state.copy(isUploading = false, error = r.message)
            }
        }
    }

    fun setFontSize(v: Float) { state = state.copy(fontSize = v) }
    fun setFontColor(c: String) { state = state.copy(fontColor = c) }
    fun toggleBox(v: Boolean) { state = state.copy(boxEnabled = v) }
    fun setPosition(p: String) { state = state.copy(position = p) }
    fun toggleFlip(v: Boolean) { state = state.copy(flipEnabled = v) }
    fun toggleSpeed(v: Boolean) { state = state.copy(speedEnabled = v) }
    fun toggleNoise(v: Boolean) { state = state.copy(noiseEnabled = v) }
    fun toggleBlur(v: Boolean) { state = state.copy(blurEnabled = v) }

    fun startProcessing(context: Context) {
        if (state.videoUri == null && state.videoFilename == null) { state = state.copy(error = "Video ရွေးပါ"); return }
        viewModelScope.launch {
            state = state.copy(isProcessing = true, error = null)
            val cost = getCostForDuration(state.videoDuration, state.pricingTiers)
            if (cost > 0) {
                when (val r = repo.deductCoins(cost, "Subtitle ${state.videoDuration}s")) {
                    is Result.Success -> state = state.copy(gold = r.data.gold, silver = r.data.silver)
                    is Result.Error -> { state = state.copy(isProcessing = false, error = "Coins မလုံလောက်ပါ: ${r.message}"); return@launch }
                }
            }
            // TODO: Extract audio → /api/ai/stt → SRT → FFmpegX burn
            kotlinx.coroutines.delay(2000)
            state = state.copy(isProcessing = false)
            loadCoins()
        }
    }

    fun clearError() { state = state.copy(error = null) }
}
