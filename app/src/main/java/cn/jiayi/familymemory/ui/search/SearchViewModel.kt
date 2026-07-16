package cn.jiayi.familymemory.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.jiayi.familymemory.data.search.SearchMode
import cn.jiayi.familymemory.data.search.SearchRepository
import cn.jiayi.familymemory.network.SearchRequestDto
import cn.jiayi.familymemory.network.SearchResultDto
import cn.jiayi.familymemory.network.SearchStatusDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val status: SearchStatusDto? = null,
    val mode: SearchMode = SearchMode.TEXT,
    val results: List<SearchResultDto> = emptyList(),
    val busy: Boolean = false,
    val searched: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(private val repository: SearchRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = mutableState.asStateFlow()
    private var statusPollingJob: Job? = null

    init { refreshStatus() }

    fun setMode(mode: SearchMode) = mutableState.update { it.copy(mode = mode, results = emptyList(), searched = false, message = null) }

    fun refreshStatus() {
        viewModelScope.launch {
            runCatching { repository.status() }
                .onSuccess { value -> mutableState.update { it.copy(status = value) } }
        }
    }

    fun search(query: String, personId: String?, mediaType: String?) {
        if (query.trim().isEmpty() || mutableState.value.busy) return
        val mode = mutableState.value.mode
        mutableState.update { it.copy(busy = true, message = "正在搜索…", isError = false) }
        viewModelScope.launch {
            runCatching { repository.search(mode, SearchRequestDto(query.trim(), personId = personId, mediaType = mediaType)) }
                .onSuccess { response -> mutableState.update { it.copy(busy = false, results = response.results, searched = true, message = response.message.ifBlank { null }, isError = false) } }
                .onFailure { error -> mutableState.update { it.copy(busy = false, searched = true, message = SearchRepository.userMessage(error), isError = true) } }
        }
    }

    fun reindex() {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, message = "正在创建重新生成任务…", isError = false) }
        viewModelScope.launch {
            runCatching { repository.reindex() }
                .onSuccess { value ->
                    mutableState.update { it.copy(busy = false, message = "已加入 ${value.queued} 项索引任务，电脑会在后台处理", isError = false) }
                    pollStatusUntilSettled()
                }
                .onFailure { error -> mutableState.update { it.copy(busy = false, message = SearchRepository.userMessage(error), isError = true) } }
        }
    }

    private fun pollStatusUntilSettled() {
        statusPollingJob?.cancel()
        statusPollingJob = viewModelScope.launch {
            repeat(60) {
                val value = runCatching { repository.status() }.getOrElse { return@launch }
                mutableState.update { it.copy(status = value) }
                if (value.pending == 0) return@launch
                delay(2_000)
            }
        }
    }
}
