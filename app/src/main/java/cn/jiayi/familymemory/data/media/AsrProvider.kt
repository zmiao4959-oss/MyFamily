package cn.jiayi.familymemory.data.media

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface AsrProvider {
    fun isAvailable(): Boolean
    fun start(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun stop()
    fun destroy()
}

class AndroidSystemAsrProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AsrProvider {
    private var recognizer: SpeechRecognizer? = null

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun start(onResult: (String) -> Unit, onError: (String) -> Unit) {
        check(isAvailable()) { "本机没有可用的系统语音识别，可手动填写文字" }
        destroy()
        recognizer = if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }.apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isBlank()) onError("没有识别到文字，可手动填写") else onResult(text)
                }
                override fun onError(error: Int) = onError("系统语音识别不可用或未识别成功，可继续录音并手动填写")
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                },
            )
        }
    }

    override fun stop() = recognizer?.stopListening() ?: Unit

    override fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}
