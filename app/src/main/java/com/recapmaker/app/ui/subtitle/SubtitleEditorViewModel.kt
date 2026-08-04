package com.recapmaker.app.ui.subtitle

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recapmaker.app.data.model.SubtitleEntry
import com.recapmaker.app.data.repository.MainRepository
import com.recapmaker.app.data.repository.Result
import com.recapmaker.app.media.FFmpegProcessor
import com.recapmaker.app.util.copyToFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SubtitleEditorState(
    val gold: Int = 0, val silver: Int = 0,
    val videoUri: Uri? = null, val videoLocalPath: String? = null,
    val videoFilename: String? = null, val videoDurationMs: Long = 0,
    val subtitles: List<SubtitleEntry> = emptyList(),
    val selectedIndex: Int = -1,
    val isProcessing: Boolean = false, val processStatus: String = "",
    val error: String? = null, val success: String? = null,
)

@HiltViewModel
class SubtitleEditorViewModel @Inject constructor(private val repo: MainRepository) : ViewModel() {
    var state by mutableStateOf(SubtitleEditorState()); private set
    init { loadCoins() }

    private fun loadCoins() {
        viewModelScope.launch {
            when (val r = repo.getUserInfo()) {
                is Result.Success -> state = state.copy(gold = r.data.gold, silver = r.data.silver)
                is Result.Error -> {}
            }
        }
    }

    fun onVideoSelected(uri: Uri, context: Context) {
        viewModelScope.launch {
            state = state.copy(videoUri = uri, error = null)
            try {
                val temp = File(context.cacheDir, "sub_edit_in_${System.currentTimeMillis()}.mp4")
                uri.copyToFile(context, temp)
                if (!temp.exists() || temp.length() == 0L) { state = state.copy(error = "File read fail"); return@launch }
                val mmr = android.media.MediaMetadataRetriever(); mmr.setDataSource(temp.absolutePath)
                val dur = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                mmr.release()
                state = state.copy(videoLocalPath = temp.absolutePath, videoFilename = uri.lastPathSegment ?: "video.mp4", videoDurationMs = dur)
            } catch (e: Exception) { state = state.copy(error = "Video: ${e.message}") }
        }
    }

    fun importSrt(uri: Uri, context: Context) {
        viewModelScope.launch {
            state = state.copy(error = null, success = null)
            try {
                val temp = File(context.cacheDir, "import_${System.currentTimeMillis()}.srt")
                uri.copyToFile(context, temp)
                if (!temp.exists() || temp.length() == 0L) { state = state.copy(error = "SRT file read fail"); return@launch }
                val entries = FFmpegProcessor.parseSrt(temp.absolutePath)
                if (entries.isEmpty()) { state = state.copy(error = "No valid subtitle entries found"); return@launch }
                state = state.copy(subtitles = entries, selectedIndex = 0, success = "Imported ${entries.size} subtitles")
            } catch (e: Exception) { state = state.copy(error = "Import: ${e.message}") }
        }
    }

    fun selectEntry(index: Int) { state = state.copy(selectedIndex = index) }

    fun updateEntryText(index: Int, newText: String) {
        val subs = state.subtitles.toMutableList()
        if (index in subs.indices) {
            subs[index] = subs[index].copy(text = newText)
            state = state.copy(subtitles = subs)
        }
    }

    fun updateEntryTime(index: Int, startMs: Long? = null, endMs: Long? = null) {
        val subs = state.subtitles.toMutableList()
        if (index in subs.indices) {
            val entry = subs[index]
            subs[index] = entry.copy(
                startMs = startMs ?: entry.startMs,
                endMs = endMs ?: entry.endMs,
            )
            state = state.copy(subtitles = subs)
        }
    }

    fun splitEntry(index: Int, splitMs: Long) {
        val subs = state.subtitles.toMutableList()
        if (index !in subs.indices) return
        val entry = subs[index]
        if (splitMs <= entry.startMs || splitMs >= entry.endMs) return
        val first = entry.copy(index = entry.index, endMs = splitMs)
        val second = entry.copy(index = entry.index, startMs = splitMs, text = "")
        subs[index] = first
        subs.add(index + 1, second)
        reindex(subs)
        state = state.copy(subtitles = subs, selectedIndex = index + 1)
    }

    fun mergeEntries(index: Int) {
        val subs = state.subtitles.toMutableList()
        if (index !in subs.indices || index + 1 !in subs.indices) return
        val first = subs[index]
        val second = subs.removeAt(index + 1)
        val merged = first.copy(endMs = second.endMs, text = first.text + " " + second.text)
        subs[index] = merged
        reindex(subs)
        state = state.copy(subtitles = subs)
    }

    private fun reindex(subs: MutableList<SubtitleEntry>) {
        subs.forEachIndexed { i, entry -> subs[i] = entry.copy(index = i + 1) }
    }

    fun exportSrt(context: Context) {
        if (state.subtitles.isEmpty()) { state = state.copy(error = "No subtitles to export"); return }
        viewModelScope.launch {
            state = state.copy(isProcessing = true, processStatus = "Exporting SRT...", error = null)
            val outFile = File(context.cacheDir, "edited_${System.currentTimeMillis()}.srt")
            val success = FFmpegProcessor.writeSrt(state.subtitles, outFile.absolutePath)
            if (success) {
                state = state.copy(isProcessing = false, processStatus = "", success = "SRT saved to ${outFile.name}")
                shareFile(context, outFile, "application/x-subrip")
            } else {
                state = state.copy(isProcessing = false, processStatus = "", error = "Export failed")
            }
        }
    }

    fun burnToVideo(context: Context) {
        if (state.videoLocalPath == null) { state = state.copy(error = "Select a video first"); return }
        if (state.subtitles.isEmpty()) { state = state.copy(error = "No subtitles to burn"); return }
        viewModelScope.launch {
            state = state.copy(isProcessing = true, processStatus = "Burning subtitles...", error = null)
            val srtFile = File(context.cacheDir, "burn_${System.currentTimeMillis()}.srt")
            val writeOk = FFmpegProcessor.writeSrt(state.subtitles, srtFile.absolutePath)
            if (!writeOk) { state = state.copy(isProcessing = false, processStatus = "", error = "SRT write failed"); return@launch }
            val outFile = File(context.cacheDir, "burned_${System.currentTimeMillis()}.mp4")
            val result = FFmpegProcessor.burnSubtitles(state.videoLocalPath!!, srtFile.absolutePath, outFile.absolutePath)
            if (result.success && result.outputPath != null) {
                state = state.copy(isProcessing = false, processStatus = "", success = "Subtitles burned successfully!")
                shareFile(context, File(result.outputPath), "video/mp4")
            } else {
                state = state.copy(isProcessing = false, processStatus = "", error = result.error ?: "Burn failed")
            }
        }
    }

    private fun shareFile(context: Context, file: File, mimeType: String) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share"))
        } catch (e: Exception) {
            state = state.copy(error = "Share failed: ${e.message}")
        }
    }

    fun clearError() { state = state.copy(error = null) }
    fun clearSuccess() { state = state.copy(success = null) }
}
