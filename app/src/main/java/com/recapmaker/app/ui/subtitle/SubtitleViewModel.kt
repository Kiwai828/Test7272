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
import com.recapmaker.app.media.VideoDownloader
import com.recapmaker.app.util.copyToFile
import com.recapmaker.app.util.getCostForDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SubtitleState(
    val gold: Int = 0, val silver: Int = 0,
    val pricingTiers: List<PricingTier> = emptyList(),
    val videoUri: Uri? = null, val videoLocalPath: String? = null,
    val videoFilename: String? = null, val videoDuration: Int = 0,
    val urlInput: String = "", val isDownloading: Boolean = false, val downloadProgress: Float = 0f,
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
            state = state.copy(videoUri = uri, error = null)
            try {
                val temp = File(context.cacheDir, "sub_in_${System.currentTimeMillis()}.mp4")
                uri.copyToFile(context, temp)
                val mmr = MediaMetadataRetriever(); mmr.setDataSource(temp.absolutePath)
                val dur = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000
                mmr.release()
                state = state.copy(videoLocalPath = temp.absolutePath, videoDuration = dur.toInt(), videoFilename = uri.lastPathSegment ?: "video.mp4")
            } catch (e: Exception) { state = state.copy(error = "Video ဖတ်၍မရပါ: ${e.message}") }
        }
    }

    fun updateUrl(v: String) { state = state.copy(urlInput = v) }
    fun downloadFromUrl(context: Context) {
        if (state.urlInput.isBlank()) return
        viewModelScope.launch {
            state = state.copy(isDownloading = true, downloadProgress = 0f, error = null)
            val result = VideoDownloader.download(state.urlInput.trim(), context) { dl, total ->
                state = state.copy(downloadProgress = if (total > 0) dl.toFloat() / total else 0f)
            }
            if (result.success && result.file != null) {
                var dur = 0
                try { val mmr = MediaMetadataRetriever(); mmr.setDataSource(result.file.absolutePath); dur = ((mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000).toInt(); mmr.release() } catch (_: Exception) {}
                state = state.copy(isDownloading = false, videoUri = Uri.fromFile(result.file), videoLocalPath = result.file.absolutePath, videoFilename = result.file.name, videoDuration = dur, urlInput = "")
            } else state = state.copy(isDownloading = false, error = result.error ?: "Download failed")
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
        if (state.videoLocalPath == null) { state = state.copy(error = "Video ရွေးပါ"); return }
        viewModelScope.launch {
            state = state.copy(isProcessing = true, error = null)
            val cost = getCostForDuration(state.videoDuration, state.pricingTiers)
            if (cost > 0) {
                when (val r = repo.deductCoins(cost, "Subtitle ${state.videoDuration}s")) {
                    is Result.Success -> state = state.copy(gold = r.data.gold, silver = r.data.silver)
                    is Result.Error -> { state = state.copy(isProcessing = false, error = "Coins မလုံလောက်: ${r.message}"); return@launch }
                }
            }
            // TODO: Extract audio → /api/ai/stt → SRT → FFmpeg-Kit burn subtitles
            kotlinx.coroutines.delay(2000)
            state = state.copy(isProcessing = false)
            loadCoins()
        }
    }

    fun clearError() { state = state.copy(error = null) }
}
