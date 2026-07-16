package cn.jiayi.familymemory.ui.media

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import cn.jiayi.familymemory.data.local.MediaEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MediaTestScreen(viewModel: MediaViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCamera by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var preview by remember { mutableStateOf<Pair<String, String>?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.import(it, "image") } }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.import(it, "video") } }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { cameraFile = viewModel.newCameraFile(); showCamera = true }
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) viewModel.startRecording() }

    if (showCamera && cameraFile != null) {
        CameraCapture(
            destination = cameraFile!!,
            onCaptured = { file -> showCamera = false; viewModel.cameraPhotoSaved(file) },
            onCancel = { cameraFile?.delete(); showCamera = false },
            onError = { showCamera = false },
        )
        return
    }

    Scaffold(topBar = { TopAppBar(title = { Text("第三阶段媒体测试") }, navigationIcon = { OutlinedButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("原始媒体优先", style = MaterialTheme.typography.headlineSmall)
            Text("文件先保存到 App 私有目录，网络可用后由后台任务分块上传；原图、原音频和原视频不会被缩略图或整理结果覆盖。")
            if (state.busy) CircularProgressIndicator()
            state.message?.let { Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("图片和视频", style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) { Text("选择照片") }
                        Button(onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, modifier = Modifier.weight(1f)) { Text("选择视频") }
                    }
                    OutlinedButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                cameraFile = viewModel.newCameraFile(); showCamera = true
                            } else cameraPermission.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("使用 CameraX 拍照") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("录音", style = MaterialTheme.typography.titleLarge)
                    if (!state.isRecording && state.draftPath == null) {
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) viewModel.startRecording()
                                else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("开始录音") }
                    }
                    if (state.isRecording) {
                        Text(if (state.isPaused) "已暂停" else "正在录音…")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = if (state.isPaused) viewModel::resumeRecording else viewModel::pauseRecording, modifier = Modifier.weight(1f)) { Text(if (state.isPaused) "继续" else "暂停") }
                            Button(onClick = viewModel::stopRecording, modifier = Modifier.weight(1f)) { Text("停止") }
                        }
                    }
                    state.draftPath?.let { path ->
                        Text("录音时长约 ${state.draftDurationMs / 1000} 秒，请先试听")
                        MediaPlayer(path)
                        OutlinedTextField(
                            value = state.transcript,
                            onValueChange = viewModel::updateTranscript,
                            label = { Text("转写文字（可手动填写或修改）") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(
                            onClick = viewModel::recognizeShortSpeech,
                            enabled = state.asrAvailable && !state.recognizing,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (state.asrAvailable) "重新短口述并使用系统识别" else "本机系统识别不可用，请手动填写") }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = viewModel::deleteDraft, modifier = Modifier.weight(1f)) { Text("删除未保存录音") }
                            Button(onClick = viewModel::saveDraft, modifier = Modifier.weight(1f)) { Text("保存为记录") }
                        }
                    }
                }
            }

            preview?.let { (path, type) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("本地预览")
                        if (type == "image") LocalImage(path) else MediaPlayer(path)
                        OutlinedButton(onClick = { preview = null }) { Text("关闭预览") }
                    }
                }
            }

            Text("本机媒体（${state.media.size}）", style = MaterialTheme.typography.titleLarge)
            state.media.forEach { item ->
                MediaRow(item, onPreview = { preview = (item.localThumbnailPath ?: item.localPath) to item.mediaType }, onRetry = { viewModel.retry(item.id) })
            }
        }
    }
}

@Composable
private fun MediaRow(item: MediaEntity, onPreview: () -> Unit, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(item.originalFilename, style = MaterialTheme.typography.titleMedium)
            Text("${item.mediaType} · ${item.sizeBytes / 1024} KB · ${statusText(item.uploadStatus)}")
            LinearProgressIndicator(progress = { item.uploadProgress / 100f }, modifier = Modifier.fillMaxWidth())
            item.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPreview) { Text(if (item.mediaType == "image") "查看" else "播放") }
                if (item.uploadStatus == "failed") Button(onClick = onRetry) { Text("重试上传") }
            }
        }
    }
}

private fun statusText(status: String) = when (status) {
    "pending" -> "等待上传（将自动重试）"; "uploading" -> "正在后台上传"; "uploaded" -> "已上传"; "failed" -> "上传失败"; else -> status
}

@Composable
private fun LocalImage(path: String) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = "本地图片预览", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(280.dp)) }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MediaPlayer(path: String) {
    val context = LocalContext.current
    val player = remember(path) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path)))); prepare() } }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { PlayerView(it).apply { this.player = player } }, modifier = Modifier.fillMaxWidth().height(180.dp))
}
