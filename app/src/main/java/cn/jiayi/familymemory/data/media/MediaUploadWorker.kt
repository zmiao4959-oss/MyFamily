package cn.jiayi.familymemory.data.media

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaWorkerEntryPoint {
    fun mediaRepository(): MediaRepository
}

class MediaUploadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val mediaId = inputData.getString(MEDIA_ID) ?: return Result.failure()
        val repository = EntryPointAccessors.fromApplication(
            applicationContext,
            MediaWorkerEntryPoint::class.java,
        ).mediaRepository()
        return runCatching { repository.upload(mediaId) }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { error ->
                    val message = error.message ?: error.javaClass.simpleName
                    if (runAttemptCount >= 5) {
                        repository.markFailed(mediaId, message)
                        Result.failure()
                    } else {
                        repository.markWaiting(mediaId, message)
                        Result.retry()
                    }
                },
            )
    }

    companion object {
        const val MEDIA_ID = "media_id"
    }
}
