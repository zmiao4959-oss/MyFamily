package cn.jiayi.familymemory.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.jiayi.familymemory.data.connection.ConnectionPreferences
import cn.jiayi.familymemory.data.core.CoreRepository
import cn.jiayi.familymemory.data.core.PersonInput
import cn.jiayi.familymemory.data.core.RecordInput
import cn.jiayi.familymemory.data.local.MediaEntity
import cn.jiayi.familymemory.data.local.PersonEntity
import cn.jiayi.familymemory.data.local.RecordDraftEntity
import cn.jiayi.familymemory.data.local.RecordEntity
import cn.jiayi.familymemory.data.local.RecordTagEntity
import cn.jiayi.familymemory.data.local.RelationshipEntity
import cn.jiayi.familymemory.data.local.TagEntity
import cn.jiayi.familymemory.data.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val persons: List<PersonEntity> = emptyList(),
    val relationships: List<RelationshipEntity> = emptyList(),
    val records: List<RecordEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val media: List<MediaEntity> = emptyList(),
    val recordTags: List<RecordTagEntity> = emptyList(),
    val draft: RecordDraftEntity? = null,
    val pendingCount: Int = 0,
    val serverUrl: String = "",
    val hasAccessToken: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

private data class CoreSnapshot(
    val persons: List<PersonEntity>,
    val relationships: List<RelationshipEntity>,
    val records: List<RecordEntity>,
    val tags: List<TagEntity>,
    val pendingCount: Int,
)

private data class DetailSnapshot(
    val media: List<MediaEntity>,
    val recordTags: List<RecordTagEntity>,
    val draft: RecordDraftEntity?,
    val serverUrl: String,
    val hasAccessToken: Boolean,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: CoreRepository,
    mediaRepository: MediaRepository,
    connectionPreferences: ConnectionPreferences,
) : ViewModel() {
    private val transient = MutableStateFlow(MainUiState())
    private val core = combine(
        repository.persons, repository.relationships, repository.records, repository.tags, repository.pendingCount,
    ) { persons, relationships, records, tags, pending -> CoreSnapshot(persons, relationships, records, tags, pending) }

    private val details = combine(
        mediaRepository.media, repository.recordTags, repository.recordDraft, connectionPreferences.connection,
    ) { media, recordTags, draft, connection -> DetailSnapshot(media, recordTags, draft, connection.serverUrl, connection.hasAccessToken) }

    val uiState: StateFlow<MainUiState> = combine(core, details, transient) { coreData, detail, temporary ->
        temporary.copy(
            persons = coreData.persons, relationships = coreData.relationships, records = coreData.records,
            tags = coreData.tags, pendingCount = coreData.pendingCount, media = detail.media, recordTags = detail.recordTags,
            draft = detail.draft, serverUrl = detail.serverUrl, hasAccessToken = detail.hasAccessToken,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun addPerson(input: PersonInput, onSaved: (String) -> Unit = {}) = runAction("人物已保存到手机") {
        onSaved(repository.addPerson(input).id)
    }

    fun updatePerson(person: PersonEntity, input: PersonInput) = runAction("人物资料已更新") {
        repository.updatePerson(person, input)
    }

    fun addRelationship(personAId: String, personBId: String, type: String) = runAction("关系已保存") {
        repository.addRelationship(personAId, personBId, type)
    }

    fun addRecord(input: RecordInput, onSaved: (String) -> Unit = {}) = runAction("记录已保存到手机") {
        onSaved(repository.addRecord(input).id)
    }

    fun saveDraft(draft: RecordDraftEntity) {
        viewModelScope.launch { repository.saveRecordDraft(draft) }
    }

    fun clearDraft() {
        viewModelScope.launch { repository.clearRecordDraft() }
    }

    fun sync() = runMessageAction {
        val result = repository.sync()
        "同步完成：上传 ${result.uploaded} 条资料"
    }

    fun loadDemo() = runMessageAction {
        val result = repository.loadDemo()
        "演示家族已载入：${result.persons} 位人物、${result.records} 条记录"
    }

    fun clearLocal() = runAction("手机本地资料已清空，服务器资料没有删除") { repository.clearLocalData() }
    fun dismissMessage() = transient.update { it.copy(message = null, isError = false) }

    private fun runAction(successMessage: String, action: suspend () -> Unit) {
        if (transient.value.busy) return
        transient.update { it.copy(busy = true, message = null, isError = false) }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { transient.update { it.copy(busy = false, message = successMessage, isError = false) } }
                .onFailure { error -> transient.update { it.copy(busy = false, message = error.message ?: "操作失败", isError = true) } }
        }
    }

    private fun runMessageAction(action: suspend () -> String) {
        if (transient.value.busy) return
        transient.update { it.copy(busy = true, message = null, isError = false) }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { message -> transient.update { it.copy(busy = false, message = message, isError = false) } }
                .onFailure { error -> transient.update { it.copy(busy = false, message = error.message ?: "操作失败", isError = true) } }
        }
    }
}
