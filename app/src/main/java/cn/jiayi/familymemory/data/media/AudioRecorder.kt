package cn.jiayi.familymemory.data.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class AudioDraft(val file: File, val durationMs: Long)

class AudioRecorder @Inject constructor(private val repository: MediaRepository, @param:ApplicationContext private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt = 0L
    private var pausedAt = 0L
    private var totalPaused = 0L

    @Suppress("DEPRECATION")
    fun start() {
        check(recorder == null) { "录音已经开始" }
        val file = repository.recordingDirectory().resolve("draft-${System.currentTimeMillis()}.m4a")
        val current = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        current.setAudioSource(MediaRecorder.AudioSource.MIC)
        current.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        current.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        current.setAudioEncodingBitRate(128_000)
        current.setAudioSamplingRate(44_100)
        current.setOutputFile(file.absolutePath)
        current.prepare()
        current.start()
        recorder = current
        output = file
        startedAt = SystemClock.elapsedRealtime()
        pausedAt = 0
        totalPaused = 0
    }

    fun pause() {
        check(pausedAt == 0L) { "录音已经暂停" }
        recorder?.pause() ?: error("录音尚未开始")
        pausedAt = SystemClock.elapsedRealtime()
    }

    fun resume() {
        check(pausedAt > 0L) { "录音没有暂停" }
        recorder?.resume() ?: error("录音尚未开始")
        totalPaused += SystemClock.elapsedRealtime() - pausedAt
        pausedAt = 0
    }

    fun stop(): AudioDraft {
        val current = recorder ?: error("录音尚未开始")
        val end = if (pausedAt > 0) pausedAt else SystemClock.elapsedRealtime()
        val duration = (end - startedAt - totalPaused).coerceAtLeast(0)
        try {
            current.stop()
        } catch (error: RuntimeException) {
            output?.delete()
            throw IllegalStateException("录音时间太短，请重新录制", error)
        } finally {
            current.release()
            recorder = null
        }
        val draft = AudioDraft(checkNotNull(output), duration)
        output = null
        return draft
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        output?.delete()
        output = null
    }
}
