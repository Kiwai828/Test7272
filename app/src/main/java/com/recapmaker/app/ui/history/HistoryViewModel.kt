package com.recapmaker.app.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recapmaker.app.data.local.VideoHistoryDao
import com.recapmaker.app.data.local.VideoHistoryEntity
import com.recapmaker.app.ui.editor.EditorViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryState(
    val entries: List<VideoHistoryEntity> = emptyList(),
    val filteredEntries: List<VideoHistoryEntity> = emptyList(),
    val filterStatus: String = "all",
    val filterEffect: String = "",
    val filterQuery: String = "",
    val selectedEntry: VideoHistoryEntity? = null,
    val showDetail: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(private val historyDao: VideoHistoryDao) : ViewModel() {
    var state by mutableStateOf(HistoryState()); private set

    private val _effectsList = MutableStateFlow<List<String>>(emptyList())
    val effectsList: StateFlow<List<String>> = _effectsList.asStateFlow()

    private var allEntries: List<VideoHistoryEntity> = emptyList()

    init { observeAll() }

    private fun observeAll() {
        viewModelScope.launch {
            historyDao.getAll().collect { list ->
                allEntries = list
                val effects = list.flatMap { parseEffects(it.effectsApplied) }.distinct().sorted()
                _effectsList.value = effects
                state = state.copy(entries = list, filteredEntries = applyFilters(list), isLoading = false)
            }
        }
    }

    fun onFilterStatusChange(status: String) {
        state = state.copy(filterStatus = status, filterEffect = "", filterQuery = "")
        state = state.copy(filteredEntries = applyFilters(allEntries))
    }

    fun onFilterEffectChange(effect: String) {
        state = state.copy(filterEffect = effect, filterQuery = "")
        state = state.copy(filteredEntries = applyFilters(allEntries))
    }

    fun onQueryChange(query: String) {
        state = state.copy(filterQuery = query)
        state = state.copy(filteredEntries = applyFilters(state.entries))
    }

    fun showDetail(entry: VideoHistoryEntity) {
        state = state.copy(selectedEntry = entry, showDetail = true)
    }

    fun hideDetail() {
        state = state.copy(showDetail = false, selectedEntry = null)
    }

    fun deleteEntry(entry: VideoHistoryEntity) {
        viewModelScope.launch {
            historyDao.delete(entry)
            hideDetail()
        }
    }

    fun reprocessEntry(entry: VideoHistoryEntity) {
        EditorViewModel.ReprocessCache.pendingId = entry.id
        hideDetail()
    }

    fun clearError() { state = state.copy(error = null) }

    private fun applyFilters(list: List<VideoHistoryEntity>): List<VideoHistoryEntity> {
        var result = list
        val statusFilter = state.filterStatus
        val effectFilter = state.filterEffect
        val q = state.filterQuery
        if (statusFilter != "all") {
            result = result.filter { it.status == statusFilter }
        }
        if (effectFilter.isNotBlank()) {
            result = result.filter { parseEffects(it.effectsApplied).contains(effectFilter) }
        }
        if (q.isNotBlank()) {
            result = result.filter { e ->
                e.inputVideoName.contains(q, ignoreCase = true) ||
                e.outputVideoName.contains(q, ignoreCase = true) ||
                e.errorMessage.contains(q, ignoreCase = true)
            }
        }
        return result
    }

    fun getStats(): HistoryStats {
        val entries = state.entries
        val total = entries.size
        val succeeded = entries.count { it.status == "completed" }
        val failed = total - succeeded
        val totalCoins = entries.sumOf { it.coinsSpent }
        val totalTimeMs = entries.sumOf { it.processingTimeMs }
        val successRate = if (total > 0) (succeeded * 100f / total) else 0f
        return HistoryStats(total, succeeded, failed, totalCoins, totalTimeMs, successRate)
    }

    data class HistoryStats(
        val total: Int,
        val succeeded: Int,
        val failed: Int,
        val totalCoins: Int,
        val totalTimeMs: Long,
        val successRate: Float,
    )
}

fun parseEffects(json: String): List<String> {
    if (json.isBlank() || json == "[]") return emptyList()
    return json.removeSurrounding("[", "]")
        .split("\",\"").map { it.trim().removeSurrounding("\"") }
        .filter { it.isNotBlank() }
}
