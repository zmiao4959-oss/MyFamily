package cn.jiayi.familymemory.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.jiayi.familymemory.data.ai.AiRepository
import cn.jiayi.familymemory.data.ai.AiTaskFailure
import cn.jiayi.familymemory.network.AiArtifactDto
import cn.jiayi.familymemory.network.AiStatusDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiUiState(
    val status: AiStatusDto? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val artifact: AiArtifactDto? = null,
    val artifacts: List<AiArtifactDto> = emptyList(),
    val failedJobId: String? = null,
)

@HiltViewModel
class AiViewModel @Inject constructor(private val repository: AiRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = mutableState.asStateFlow()

    init { refreshStatus() }

    fun refreshStatus() = launchStatus { repository.status() }
    fun organize(recordId: String) = launchArtifact { repository.organize(recordId) }
    fun biography(personId: String) = launchArtifact { repository.biography(personId) }
    fun interview(personId: String) = launchArtifact { repository.interview(personId) }
    fun ask(question: String, personId: String?) = launchArtifact { repository.ask(question, personId) }
    fun retry() {
        val id = mutableState.value.failedJobId ?: return
        launchArtifact { repository.retry(id) }
    }
    fun confirm(confirmed: Boolean) {
        val id = mutableState.value.artifact?.id ?: return
        launchArtifact(success = if (confirmed) "AI 建议已由你确认" else "AI 建议已忽略") { repository.confirm(id, confirmed) }
    }
    fun loadLatest(recordId: String?, personId: String?) {
        viewModelScope.launch {
            runCatching { repository.artifacts(recordId, personId) }
                .onSuccess { artifacts -> mutableState.update { it.copy(artifacts = artifacts, artifact = artifacts.firstOrNull()) } }
        }
    }
    fun clearResult() = mutableState.update { it.copy(artifact = null, message = null, isError = false, failedJobId = null) }

    private fun launchStatus(action: suspend () -> AiStatusDto) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { value -> mutableState.update { it.copy(status = value, message = null, isError = false) } }
                .onFailure { error -> mutableState.update { it.copy(message = AiRepository.userMessage(error), isError = true) } }
        }
    }

    private fun launchArtifact(success: String? = null, action: suspend () -> AiArtifactDto) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, message = "AI 正在本地服务后台处理，请稍候…", isError = false, failedJobId = null) }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { artifact -> mutableState.update {
                    val updated = listOf(artifact) + it.artifacts.filterNot { existing -> existing.id == artifact.id }
                    it.copy(busy = false, artifact = artifact, artifacts = updated, message = success, isError = false)
                } }
                .onFailure { error -> mutableState.update {
                    it.copy(busy = false, message = AiRepository.userMessage(error), isError = true, failedJobId = (error as? AiTaskFailure)?.jobId)
                } }
        }
    }
}
