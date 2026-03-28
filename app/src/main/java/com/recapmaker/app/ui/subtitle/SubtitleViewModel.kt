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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SubtitleState(
    val gold: Int = 0, val silver: Int = 0,
    val pricingTiers: List<PricingTier> = emptyList(),
    val videoUri: Uri? = null, val videoFilename: String? = null,
    val videoDuration: Int = 0,
    val urlInput: String = "", val isDownloading: Boolean = false,
    val fontColor: String = "#FFFFFF", val fontSize: Float = 16f,
    val boxEnabled: Boolean = true, val position: String = "bottom_center",
    val flipEnabled: Boolean = false, val speedEnabled: Boolean = false,
    val noiseEnabled: Boolean = false, val blurEnabled: Boolean = false,
    val isProcessing: Boolean = false, val error: String? = null,
)

@HiltViewModel
class SubtitleViewModel @Inject constructor(
    private val repo: MainRepository,
) : ViewModel() {
    var state by mutableStateOf(SubtitleState()); private set

    init { loadUserInfo() }

    private fun loadUserInfo() {
        viewModelScope.launch {
            when (val r = repo.getUserInfo()) {
                is Result.Success -> state = state.copy(
                    gold = r.data.gold, silver = r.data.silver,
                    pricingTiers = r.data.pricing_tiers,
                )
                is Result.Error -> {}
            }
        }
    }

    fun onVideoSelected(uri: Uri, context: Context) {
        viewModelScope.launch {
            state = state.copy(videoUri = uri, isDownloading = true, error = null)
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(context, uri)
                val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                mmr.release()

                val tempFile = File(context.cacheDir, "sub_upload_${System.currentTimeMillis()}.mp4")
                uri.copyToFile(context, tempFile)

                when (val r = repo.uploadVideo(tempFile)) {
                    is Result.Success -> state = state.copy(
                        videoFilename = r.data.filename,
                        videoDuration = (durationMs / 1000).toInt(),
                        isDownloading = false,
                    )
                    is Result.Error -> state = state.copy(isDownloading = false, error = r.message)
                }
                tempFile.delete()
            } catch (e: Exception) {
                state = state.copy(isDownloading = false, error = "Upload failed: ${e.message}")
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
                    isDownloading = false,
                )
                is Result.Error -> state = state.copy(isDownloading = false, error = r.message)
            }
        }
    }

    fun updateFontSize(v: Float) { state = state.copy(fontSize = v) }
    fun updateFontColor(c: String) { state = state.copy(fontColor = c) }
    fun toggleBox(v: Boolean) { state = state.copy(boxEnabled = v) }
    fun updatePosition(p: String) { state = state.copy(position = p) }
    fun toggleFlip(v: Boolean) { state = state.copy(flipEnabled = v) }
    fun toggleSpeed(v: Boolean) { state = state.copy(speedEnabled = v) }
    fun toggleNoise(v: Boolean) { state = state.copy(noiseEnabled = v) }
    fun toggleBlur(v: Boolean) { state = state.copy(blurEnabled = v) }

    fun startSubtitleProcessing(context: Context) {
        val filename = state.videoFilename ?: return
        viewModelScope.launch {
            state = state.copy(isProcessing = true, error = null)
            // TODO: Send subtitle processing request to server
            // For now, simulate with a delay
            kotlinx.coroutines.delay(2000)
            state = state.copy(isProcessing = false)
            loadUserInfo()
        }
    }

    fun clearError() { state = state.copy(error = null) }
}
