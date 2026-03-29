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
import com.recapmaker.app.media.FFmpegProcessor
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
    val urlInput: String = "",
    val isCheckingUrl: Boolean = false,
    val videoInfo: VideoDownloader.VideoInfo? = null,
    val showResolutionPopup: Boolean = false,
    val isDownloading: Boolean = false, val downloadProgress: Float = 0f,
    val fontColor: String = "#FFFFFF", val fontSize: Float = 16f,
    val boxEnabled: Boolean = true, val position: String = "bottom_center",
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

    fun startProcessing(context: Context) {
        if (state.videoLocalPath == null) { state = state.copy(error = "Video ရွေးပါ"); return }
        viewModelScope.launch {
            state = state.copy(isProcessing = true, error = null)
            val cost = getCostForDuration(state.videoDuration, state.pricingTiers)
            if (cost > 0) {
                when (val r = repo.deductCoins(cost, "Subtitle ${state.videoDuration}s")) {
                    is Result.Success -> state = state.copy(gold = r.data.gold, silver = r.data.silver)
                    is Result.Error -> { state = state.copy(isProcessing = false, error = "Coins: ${r.message}"); return@launch }
                }
            }
            // TODO: Extract audio → /api/ai/stt → SRT → FFmpeg burn subtitles
            kotlinx.coroutines.delay(2000)
            state = state.copy(isProcessing = false)
            loadCoins()
        }
    }

    fun clearError() { state = state.copy(error = null) }
}
