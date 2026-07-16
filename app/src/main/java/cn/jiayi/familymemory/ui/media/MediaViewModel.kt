package cn.jiayi.familymemory.ui.media

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.jiayi.familymemory.data.local.MediaEntity
import cn.jiayi.familymemory.data.media.AudioDraft
import cn.jiayi.familymemory.data.media.AudioRecorder
import cn.jiayi.familymemory.data.media.AsrProvider
import cn.jiayi.familymemory.data.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class MediaUiState(
    val media: List<MediaEntity> = emptyList(),
    val busy: Boolean = false,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val draftPath: String? = null,
    val draftDurationMs: Long = 0,
    val transcript: String = "",
    val asrAvailable: Boolean = false,
    val recognizing: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class MediaViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val recorder: AudioRecorder,
    private val asrProvider: AsrProvider,
) : ViewModel() {
    private val local = MutableStateFlow(MediaUiState(asrAvailable = asrProvider.isAvailable()))
    val uiState: StateFlow<MediaUiState> = combine(repository.media, local) { media, state -> state.copy(media = media) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaUiState())

    fun import(uri: Uri, type: String) = action("已保存在手机私有目录，并加入后台上传队列") { repository.importUri(uri, type) }

    fun cameraPhotoSaved(file: File) = action("照片已保存在手机，并加入后台上传队列") { repository.registerCameraPhoto(file) }

    fun newCameraFile(): File = repository.newCameraFile()

    fun startRecording() {
        runCatching { recorder.start() }
            .onSuccess { local.update { it.copy(isRecording = true, isPaused = false, draftPath = null, message = "正在录音，原始音频不会被语音识别覆盖", isError = false) } }
            .onFailure(::showError)
    }

    fun updateTranscript(value: String) = local.update { it.copy(transcript = value) }

    fun recognizeShortSpeech() {
        runCatching {
            local.update { it.copy(recognizing = true, message = "请说一小段，识别失败不影响录音保存", isError = false) }
            asrProvider.start(
                onResult = { text -> local.update { it.copy(transcript = text, recognizing = false, message = "系统识别完成，请确认或修改文字") } },
                onError = { message -> local.update { it.copy(recognizing = false, message = message, isError = true) } },
            )
        }.onFailure(::showError)
    }

    fun pauseRecording() {
        runCatching { recorder.pause() }
            .onSuccess { local.update { it.copy(isPaused = true, message = "录音已暂停") } }
            .onFailure(::showError)
    }

    fun resumeRecording() {
        runCatching { recorder.resume() }
            .onSuccess { local.update { it.copy(isPaused = false, message = "继续录音") } }
            .onFailure(::showError)
    }

    fun stopRecording() {
        runCatching { recorder.stop() }
            .onSuccess { draft -> local.update { it.copy(isRecording = false, isPaused = false, draftPath = draft.file.absolutePath, draftDurationMs = draft.durationMs, message = "录音已停止，请试听后保存或删除") } }
            .onFailure { error ->
                local.update { it.copy(isRecording = false, isPaused = false) }
                showError(error)
            }
    }

    fun saveDraft() {
        val state = local.value
        val file = state.draftPath?.let(::File) ?: return
        action("原始录音已保存，并加入后台上传队列") {
            repository.registerAudio(file, state.draftDurationMs, state.transcript)
            local.update { it.copy(draftPath = null, draftDurationMs = 0, transcript = "") }
        }
    }

    fun deleteDraft() {
        local.value.draftPath?.let(::File)?.delete()
        local.update { it.copy(draftPath = null, draftDurationMs = 0, message = "未保存录音已删除") }
    }

    fun retry(mediaId: String) = action("已重新加入后台上传队列") { repository.retry(mediaId) }

    private fun action(success: String, block: suspend () -> Any?) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, message = null, isError = false) }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { local.update { it.copy(busy = false, message = success, isError = false) } }
                .onFailure { error -> local.update { it.copy(busy = false, message = error.message ?: "操作失败", isError = true) } }
        }
    }

    private fun showError(error: Throwable) = local.update { it.copy(message = error.message ?: "操作失败", isError = true) }

    override fun onCleared() {
        recorder.cancel()
        local.value.draftPath?.let(::File)?.delete()
        asrProvider.destroy()
        super.onCleared()
    }
}
