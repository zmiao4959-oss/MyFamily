package cn.jiayi.familymemory.data.ai

import cn.jiayi.familymemory.data.connection.AccessTokenStore
import cn.jiayi.familymemory.data.connection.ConnectionPreferences
import cn.jiayi.familymemory.data.core.CoreRepository
import cn.jiayi.familymemory.network.AiApi
import cn.jiayi.familymemory.network.AiArtifactDto
import cn.jiayi.familymemory.network.AiAskRequestDto
import cn.jiayi.familymemory.network.AiConfirmRequestDto
import cn.jiayi.familymemory.network.AiJobDto
import cn.jiayi.familymemory.network.AiObjectRequestDto
import cn.jiayi.familymemory.network.AiStatusDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

class AiTaskFailure(val jobId: String, message: String) : IllegalStateException(message)

@Singleton
class AiRepository @Inject constructor(
    private val client: OkHttpClient,
    private val preferences: ConnectionPreferences,
    private val tokenStore: AccessTokenStore,
    private val coreRepository: CoreRepository,
) {
    suspend fun status(): AiStatusDto {
        val (api, auth) = connectedApi()
        return api.status(auth)
    }

    suspend fun organize(recordId: String): AiArtifactDto {
        coreRepository.sync()
        return runJob { api, auth -> api.organize(auth, AiObjectRequestDto(recordId = recordId)) }
    }

    suspend fun biography(personId: String): AiArtifactDto {
        coreRepository.sync()
        return runJob { api, auth -> api.biography(auth, AiObjectRequestDto(personId = personId)) }
    }

    suspend fun interview(personId: String): AiArtifactDto {
        coreRepository.sync()
        return runJob { api, auth -> api.interview(auth, AiObjectRequestDto(personId = personId)) }
    }

    suspend fun ask(question: String, personId: String?): AiArtifactDto {
        coreRepository.sync()
        return runJob { api, auth -> api.ask(auth, AiAskRequestDto(question, personId)) }
    }

    suspend fun retry(jobId: String): AiArtifactDto {
        val (api, auth) = connectedApi()
        return waitFor(api, auth, api.retry(auth, jobId))
    }

    suspend fun confirm(artifactId: String, confirmed: Boolean): AiArtifactDto {
        val (api, auth) = connectedApi()
        return api.confirm(auth, artifactId, AiConfirmRequestDto(confirmed))
    }

    suspend fun artifacts(recordId: String? = null, personId: String? = null): List<AiArtifactDto> {
        val (api, auth) = connectedApi()
        return api.artifacts(auth, recordId, personId)
    }

    private suspend fun runJob(create: suspend (AiApi, String) -> AiJobDto): AiArtifactDto {
        val (api, auth) = connectedApi()
        return waitFor(api, auth, create(api, auth))
    }

    private suspend fun waitFor(api: AiApi, auth: String, initial: AiJobDto): AiArtifactDto {
        var job = initial
        repeat(80) {
            when (job.status) {
                "completed" -> return api.artifact(auth, checkNotNull(job.resultArtifactId))
                "failed" -> throw AiTaskFailure(job.id, job.lastError ?: "AI 任务失败")
            }
            delay(1_000)
            job = api.job(auth, job.id)
        }
        throw AiTaskFailure(job.id, "AI 任务仍在后台处理中，请稍后再查看")
    }

    private suspend fun connectedApi(): Pair<AiApi, String> {
        val connection = preferences.connection.first()
        val token = tokenStore.read()
        check(connection.serverUrl.isNotBlank() && !token.isNullOrBlank()) { "请先完成服务器配对" }
        return Retrofit.Builder()
            .baseUrl("${connection.serverUrl.trimEnd('/')}/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AiApi::class.java) to "Bearer $token"
    }

    companion object {
        fun userMessage(error: Throwable): String = when {
            error is HttpException && error.code() == 404 -> "这条资料还没有同步到电脑服务，请确认网络正常后重试"
            error is HttpException && error.code() == 409 -> "本地服务已关闭 AI，请在电脑的 .env 中设置 FEATURE_AI=true"
            error is HttpException && error.code() == 503 -> "DeepSeek Key 尚未配置，请在电脑端设置后重启服务"
            error is AiTaskFailure && error.message.orEmpty().contains("provider", ignoreCase = true) -> "AI 服务暂时不可用，任务已安全保留，可以稍后重试"
            error is AiTaskFailure -> "AI 任务没有完成：${error.message.orEmpty().substringAfter(": ").take(180)}"
            else -> error.message ?: "AI 操作失败，请稍后重试"
        }
    }
}
