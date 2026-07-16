package cn.jiayi.familymemory.data.search

import cn.jiayi.familymemory.data.connection.AccessTokenStore
import cn.jiayi.familymemory.data.connection.ConnectionPreferences
import cn.jiayi.familymemory.data.core.CoreRepository
import cn.jiayi.familymemory.network.ReindexResponseDto
import cn.jiayi.familymemory.network.SearchApi
import cn.jiayi.familymemory.network.SearchRequestDto
import cn.jiayi.familymemory.network.SearchResponseDto
import cn.jiayi.familymemory.network.SearchStatusDto
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class SearchMode { TEXT, SEMANTIC, IMAGES }

@Singleton
class SearchRepository @Inject constructor(
    private val client: OkHttpClient,
    private val preferences: ConnectionPreferences,
    private val tokenStore: AccessTokenStore,
    private val coreRepository: CoreRepository,
) {
    suspend fun status(): SearchStatusDto {
        val (api, auth) = connectedApi()
        return api.status(auth)
    }

    suspend fun search(mode: SearchMode, request: SearchRequestDto): SearchResponseDto {
        coreRepository.sync()
        val (api, auth) = connectedApi()
        return when (mode) {
            SearchMode.TEXT -> api.text(auth, request)
            SearchMode.SEMANTIC -> api.semantic(auth, request)
            SearchMode.IMAGES -> api.multimodal(auth, request)
        }
    }

    suspend fun reindex(): ReindexResponseDto {
        coreRepository.sync()
        val (api, auth) = connectedApi()
        return api.reindex(auth)
    }

    private suspend fun connectedApi(): Pair<SearchApi, String> {
        val connection = preferences.connection.first()
        val token = tokenStore.read()
        check(connection.serverUrl.isNotBlank() && !token.isNullOrBlank()) { "请先完成服务器配对" }
        val searchClient = client.newBuilder().readTimeout(90, TimeUnit.SECONDS).build()
        val api = Retrofit.Builder()
            .baseUrl("${connection.serverUrl.trimEnd('/')}/")
            .client(searchClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SearchApi::class.java)
        return api to "Bearer $token"
    }

    companion object {
        fun userMessage(error: Throwable): String = when {
            error is HttpException && error.code() == 409 -> "语义和图片搜索尚未启用；你仍可使用关键词搜索"
            error is HttpException && error.code() == 503 -> "火山引擎 Embedding 尚未配置或暂时不可用"
            else -> error.message ?: "搜索失败，请稍后重试"
        }
    }
}
