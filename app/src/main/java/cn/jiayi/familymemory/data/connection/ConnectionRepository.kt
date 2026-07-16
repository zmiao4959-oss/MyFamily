package cn.jiayi.familymemory.data.connection

import android.os.Build
import cn.jiayi.familymemory.network.FamilyMemoryApi
import cn.jiayi.familymemory.network.PairRequest
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepository @Inject constructor(
    private val client: OkHttpClient,
    private val preferences: ConnectionPreferences,
    private val tokenStore: AccessTokenStore,
) {
    suspend fun connect(serverUrl: String, pairingToken: String): ConnectionResult {
        val normalizedUrl = normalizeServerUrl(serverUrl)
            ?: return ConnectionResult.Failure("请输入正确的 http:// 或 https:// 服务地址")
        if (pairingToken.isBlank()) {
            return ConnectionResult.Failure("请输入电脑端显示的配对令牌")
        }

        return try {
            val api = Retrofit.Builder()
                .baseUrl("$normalizedUrl/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FamilyMemoryApi::class.java)
            val health = api.health()
            if (health.status != "ok") {
                return ConnectionResult.Failure("本地服务尚未就绪")
            }
            val pair = api.pair(
                PairRequest(
                    pairing_token = pairingToken.trim(),
                    device_name = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                ),
            )
            tokenStore.save(pair.access_token)
            preferences.saveServerUrl(normalizedUrl)
            ConnectionResult.Success(pair.server_version.ifBlank { health.version })
        } catch (error: HttpException) {
            val message = if (error.code() == 401) "配对令牌不正确" else "服务返回错误（${error.code()}）"
            ConnectionResult.Failure(message)
        } catch (_: IOException) {
            ConnectionResult.Failure("无法连接本地服务，请检查地址、网络和防火墙")
        } catch (_: Exception) {
            ConnectionResult.Failure("连接失败，请稍后重试")
        }
    }

    suspend fun clear() {
        val savedUrl = preferences.connection.first().serverUrl
        val accessToken = tokenStore.read()
        if (savedUrl.isNotBlank() && !accessToken.isNullOrBlank()) {
            runCatching {
                Retrofit.Builder()
                    .baseUrl("${savedUrl.trimEnd('/')}/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(FamilyMemoryApi::class.java)
                    .revoke("Bearer $accessToken")
            }
        }
        preferences.clear()
    }

    companion object {
        fun normalizeServerUrl(value: String): String? {
            val candidate = value.trim().trimEnd('/')
            val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
            if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
            if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null
            if (uri.path.orEmpty().isNotEmpty() && uri.path != "/") return null
            return candidate
        }
    }
}
