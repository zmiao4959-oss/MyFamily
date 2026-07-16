package cn.jiayi.familymemory.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import cn.jiayi.familymemory.data.connection.AccessTokenStore
import cn.jiayi.familymemory.data.connection.ConnectionPreferences
import cn.jiayi.familymemory.data.core.CoreRepository
import cn.jiayi.familymemory.data.local.CoreDao
import cn.jiayi.familymemory.data.local.MediaEntity
import cn.jiayi.familymemory.network.MediaApi
import cn.jiayi.familymemory.network.MediaInitRequestDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: CoreDao,
    private val coreRepository: CoreRepository,
    private val client: OkHttpClient,
    private val preferences: ConnectionPreferences,
    private val tokenStore: AccessTokenStore,
) {
    val media: Flow<List<MediaEntity>> = dao.observeMedia()

    suspend fun importUri(uri: Uri, requestedType: String): MediaEntity = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: error("无法识别文件类型")
        val mediaType = when {
            mime.startsWith("image/") -> "image"
            mime.startsWith("video/") -> "video"
            mime.startsWith("audio/") -> "audio"
            else -> error("只支持图片、音频或视频")
        }
        check(requestedType == mediaType) { "选择的文件类型不正确" }
        val name = queryName(uri) ?: "imported-${System.currentTimeMillis()}${extensionFor(mime)}"
        val id = UUID.randomUUID().toString()
        val destination = originalDirectory().resolve("$id${extensionFor(mime)}")
        resolver.openInputStream(uri).use { input ->
            checkNotNull(input) { "无法读取所选文件" }
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        registerFile(destination, name, mime, mediaType)
    }

    fun newCameraFile(): File = originalDirectory().resolve("capture-${UUID.randomUUID()}.jpg")

    suspend fun registerCameraPhoto(file: File): MediaEntity =
        registerFile(file, "camera-${System.currentTimeMillis()}.jpg", "image/jpeg", "image")

    suspend fun registerAudio(file: File, durationMs: Long, transcript: String): MediaEntity =
        registerFile(file, "recording-${System.currentTimeMillis()}.m4a", "audio/mp4", "audio", durationMs, transcript)

    suspend fun retry(mediaId: String) {
        val item = dao.mediaById(mediaId) ?: return
        dao.updateMediaUpload(item.id, "pending", item.uploadProgress, item.uploadId, item.uploadedBytes, null)
        enqueue(item.id)
    }

    suspend fun upload(mediaId: String) = withContext(Dispatchers.IO) {
        coreRepository.sync()
        var item = dao.mediaById(mediaId) ?: return@withContext
        val file = File(item.localPath)
        check(file.isFile) { "本地原始文件不存在" }
        val (api, auth) = connectedApi()
        dao.updateMediaUpload(item.id, "uploading", item.uploadProgress, item.uploadId, item.uploadedBytes, null)
        val initialized = api.initUpload(
            auth,
            MediaInitRequestDto(item.recordId, item.clientUuid, item.originalFilename, item.mimeType, item.sizeBytes, item.sha256),
        )
        var offset = initialized.bytesReceived.coerceAtMost(item.sizeBytes)
        dao.updateMediaUpload(item.id, "uploading", progress(offset, item.sizeBytes), initialized.uploadId, offset, null)
        RandomAccessFile(file, "r").use { source ->
            source.seek(offset)
            val buffer = ByteArray(initialized.chunkSize)
            while (offset < item.sizeBytes) {
                val count = source.read(buffer, 0, minOf(buffer.size.toLong(), item.sizeBytes - offset).toInt())
                check(count > 0) { "读取本地媒体失败" }
                val response = api.uploadChunk(
                    auth,
                    initialized.uploadId,
                    offset,
                    buffer.copyOf(count).toRequestBody("application/octet-stream".toMediaType()),
                )
                offset = response.bytesReceived
                dao.updateMediaUpload(item.id, "uploading", progress(offset, item.sizeBytes), initialized.uploadId, offset, null)
            }
        }
        val completed = api.complete(auth, initialized.uploadId)
        dao.markMediaUploaded(item.id, completed.id)
    }

    suspend fun markFailed(mediaId: String, message: String) {
        val item = dao.mediaById(mediaId) ?: return
        dao.updateMediaUpload(item.id, "failed", item.uploadProgress, item.uploadId, item.uploadedBytes, message.take(240))
    }

    suspend fun markWaiting(mediaId: String, message: String) {
        val item = dao.mediaById(mediaId) ?: return
        dao.updateMediaUpload(item.id, "pending", item.uploadProgress, item.uploadId, item.uploadedBytes, message.take(240))
    }

    private suspend fun registerFile(
        file: File,
        originalName: String,
        mime: String,
        mediaType: String,
        knownDurationMs: Long? = null,
        transcript: String = "",
    ): MediaEntity = withContext(Dispatchers.IO) {
        check(file.isFile && file.length() > 0) { "媒体文件为空" }
        val record = coreRepository.addRecord(
            title = when (mediaType) { "image" -> "照片：$originalName"; "video" -> "视频：$originalName"; else -> "录音：$originalName" },
            text = transcript.ifBlank { "原始媒体保存在手机私有目录，上传后服务器仍保留原文件。" },
        )
        val id = UUID.randomUUID().toString()
        val metadata = readMetadata(file, mediaType)
        val thumb = createThumbnail(file, mediaType, id)
        val item = MediaEntity(
            id = id,
            clientUuid = id,
            recordId = record.id,
            mediaType = mediaType,
            mimeType = mime,
            originalFilename = originalName.takeLast(255),
            localPath = file.absolutePath,
            localThumbnailPath = thumb?.absolutePath,
            sizeBytes = file.length(),
            sha256 = sha256(file),
            durationMs = knownDurationMs ?: metadata.durationMs,
            width = metadata.width,
            height = metadata.height,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsertMedia(item)
        enqueue(item.id)
        item
    }

    private fun enqueue(mediaId: String) {
        val request = OneTimeWorkRequestBuilder<MediaUploadWorker>()
            .setInputData(workDataOf(MediaUploadWorker.MEDIA_ID to mediaId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("media-upload-$mediaId", ExistingWorkPolicy.REPLACE, request)
    }

    private suspend fun connectedApi(): Pair<MediaApi, String> {
        val connection = preferences.connection.first()
        val token = tokenStore.read()
        check(connection.serverUrl.isNotBlank() && !token.isNullOrBlank()) { "请先完成服务器配对" }
        return Retrofit.Builder()
            .baseUrl("${connection.serverUrl.trimEnd('/')}/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MediaApi::class.java) to "Bearer $token"
    }

    private fun originalDirectory() = File(context.filesDir, "media/original").apply { mkdirs() }
    fun recordingDirectory() = File(context.filesDir, "media/recordings").apply { mkdirs() }
    private fun thumbnailDirectory() = File(context.filesDir, "media/thumbnails").apply { mkdirs() }

    private fun queryName(uri: Uri): String? = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0)?.substringAfterLast('/')?.substringAfterLast('\\') else null
    }

    private data class LocalMetadata(val durationMs: Long?, val width: Int?, val height: Int?)

    private fun readMetadata(file: File, mediaType: String): LocalMetadata {
        if (mediaType == "image") {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            return LocalMetadata(null, options.outWidth.takeIf { it > 0 }, options.outHeight.takeIf { it > 0 })
        }
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                LocalMetadata(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                )
            } finally {
                retriever.release()
            }
        }.getOrDefault(LocalMetadata(null, null, null))
    }

    @Suppress("DEPRECATION")
    private fun createThumbnail(file: File, mediaType: String, id: String): File? = runCatching {
        if (mediaType == "audio") return null
        val bitmap = if (mediaType == "video") {
            if (android.os.Build.VERSION.SDK_INT >= 29) ThumbnailUtils.createVideoThumbnail(file, Size(512, 512), null)
            else ThumbnailUtils.createVideoThumbnail(file.absolutePath, android.provider.MediaStore.Video.Thumbnails.MINI_KIND)
        } else {
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
            val orientation = runCatching { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true).also { if (it !== decoded) decoded.recycle() }
        } ?: return null
        val output = thumbnailDirectory().resolve("$id.jpg")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        output
    }.getOrNull()

    private fun extensionFor(mime: String) = when (mime.lowercase()) {
        "image/jpeg" -> ".jpg"; "image/png" -> ".png"; "image/webp" -> ".webp"; "image/heic" -> ".heic"; "image/heif" -> ".heif"
        "video/mp4" -> ".mp4"; "video/webm" -> ".webm"; "video/quicktime" -> ".mov"
        "audio/mp4" -> ".m4a"; "audio/mpeg" -> ".mp3"; "audio/wav" -> ".wav"; "audio/ogg" -> ".ogg"
        else -> ".bin"
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun progress(bytes: Long, total: Long) = if (total <= 0) 0 else ((bytes * 100) / total).toInt().coerceIn(0, 100)
}
