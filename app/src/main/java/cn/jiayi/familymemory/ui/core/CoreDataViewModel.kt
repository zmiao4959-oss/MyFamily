package cn.jiayi.familymemory.ui.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.jiayi.familymemory.data.core.CoreRepository
import cn.jiayi.familymemory.data.local.PersonEntity
import cn.jiayi.familymemory.data.local.RecordEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoreDataUiState(
    val persons: List<PersonEntity> = emptyList(),
    val records: List<RecordEntity> = emptyList(),
    val relationshipCount: Int = 0,
    val tagCount: Int = 0,
    val pendingCount: Int = 0,
    val personName: String = "",
    val recordTitle: String = "",
    val recordText: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

private data class CoreDataSnapshot(
    val persons: List<PersonEntity>,
    val records: List<RecordEntity>,
    val relationshipCount: Int,
    val tagCount: Int,
    val pendingCount: Int,
)

@HiltViewModel
class CoreDataViewModel @Inject constructor(private val repository: CoreRepository) : ViewModel() {
    private val editing = MutableStateFlow(CoreDataUiState())
    private val snapshot = combine(
        repository.persons,
        repository.records,
        repository.relationships,
        repository.tags,
        repository.pendingCount,
    ) { persons, records, relationships, tags, pending ->
        CoreDataSnapshot(persons, records, relationships.size, tags.size, pending)
    }
    val uiState: StateFlow<CoreDataUiState> = combine(snapshot, editing) { data, form ->
        form.copy(
            persons = data.persons,
            records = data.records,
            relationshipCount = data.relationshipCount,
            tagCount = data.tagCount,
            pendingCount = data.pendingCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoreDataUiState())

    fun updatePersonName(value: String) = editing.update { it.copy(personName = value, message = null) }
    fun updateRecordTitle(value: String) = editing.update { it.copy(recordTitle = value, message = null) }
    fun updateRecordText(value: String) = editing.update { it.copy(recordText = value, message = null) }

    fun addPerson() = runAction("人物已保存在手机，等待同步") {
        repository.addPerson(editing.value.personName)
        editing.update { it.copy(personName = "") }
    }

    fun addRecord() = runAction("记录已保存在手机，等待同步") {
        repository.addRecord(editing.value.recordTitle, editing.value.recordText)
        editing.update { it.copy(recordTitle = "", recordText = "") }
    }

    fun sync() = runMessageAction {
        val result = repository.sync()
        "同步完成：上传 ${result.uploaded} 条，现有 ${result.persons} 位人物、${result.records} 条记录"
    }

    fun loadDemo() = runMessageAction {
        val result = repository.loadDemo()
        "演示家族已载入：${result.persons} 位人物、${result.records} 条记录"
    }

    fun clearLocal() = runAction("手机本地资料已清空，服务器资料未删除") { repository.clearLocalData() }

    private fun runAction(successMessage: String? = null, action: suspend () -> Unit) {
        if (editing.value.busy) return
        editing.update { it.copy(busy = true, message = null, isError = false) }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { editing.update { state -> state.copy(busy = false, message = successMessage, isError = false) } }
                .onFailure { error -> editing.update { state -> state.copy(busy = false, message = error.message ?: "操作失败，请稍后重试", isError = true) } }
        }
    }

    private fun runMessageAction(action: suspend () -> String) {
        if (editing.value.busy) return
        editing.update { it.copy(busy = true, message = null, isError = false) }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { message -> editing.update { it.copy(busy = false, message = message, isError = false) } }
                .onFailure { error -> editing.update { it.copy(busy = false, message = error.message ?: "操作失败，请稍后重试", isError = true) } }
        }
    }
}
