package cn.jiayi.familymemory.ui.media

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

@Composable
fun CameraCapture(
    destination: File,
    onCaptured: (File) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    DisposableEffect(lifecycleOwner, previewView) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var boundPreview: Preview? = null
        var boundCapture: ImageCapture? = null
        var disposed = false
        future.addListener({
            runCatching {
                if (disposed) return@runCatching
                val cameraProvider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                provider = cameraProvider
                boundPreview = preview
                boundCapture = capture
                imageCapture = capture
            }.onFailure { onError(it.message ?: "相机启动失败") }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            imageCapture = null
            boundPreview?.setSurfaceProvider(null)
            val useCases = listOfNotNull(boundPreview, boundCapture).toTypedArray()
            if (useCases.isNotEmpty()) runCatching { provider?.unbind(*useCases) }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AndroidView(factory = { previewView }, modifier = Modifier.weight(1f).fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(
                onClick = { capture(context, imageCapture, destination, onCaptured, onError) },
                enabled = imageCapture != null,
                modifier = Modifier.weight(1f),
            ) { Text("拍照并保留原图") }
        }
    }
}

private fun capture(
    context: Context,
    imageCapture: ImageCapture?,
    destination: File,
    onCaptured: (File) -> Unit,
    onError: (String) -> Unit,
) {
    val capture = imageCapture ?: return
    capture.takePicture(
        ImageCapture.OutputFileOptions.Builder(destination).build(),
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = onCaptured(destination)
            override fun onError(exception: ImageCaptureException) = onError(exception.message ?: "拍照失败")
        },
    )
}
