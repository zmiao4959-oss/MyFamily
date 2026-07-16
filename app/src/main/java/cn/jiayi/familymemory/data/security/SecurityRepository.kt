package cn.jiayi.familymemory.data.security

import android.content.Context
import android.net.Uri
import cn.jiayi.familymemory.data.connection.AccessTokenStore
import cn.jiayi.familymemory.data.connection.ConnectionPreferences
import cn.jiayi.familymemory.network.BackupDto
import cn.jiayi.familymemory.network.DeviceDto
import cn.jiayi.familymemory.network.PasswordDto
import cn.jiayi.familymemory.network.RestoreDto
import cn.jiayi.familymemory.network.SecurityApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val preferences: ConnectionPreferences,
    private val tokenStore: AccessTokenStore,
) {
    suspend fun backups(): List<BackupDto> { val (api, auth) = api(); return api.backups(auth) }
    suspend fun devices(): List<DeviceDto> { val (api, auth) = api(); return api.devices(auth) }
    suspend fun createBackup(password: String): BackupDto { val (api, auth) = api(); return api.createBackup(auth, PasswordDto(password)) }
    suspend fun restore(id: String, password: String) { val (api, auth) = api(); api.restore(auth, id, RestoreDto(password)) }
    suspend fun revokeDevice(id: String) { val (api, auth) = api(); api.revokeDevice(auth, id) }
    suspend fun regeneratePairingToken(): String { val (api, auth) = api(); return api.regeneratePairingToken(auth).pairingToken }
    suspend fun download(id: String, destination: Uri) {
        val (api, auth) = api()
        val body = api.download(auth, id)
        context.contentResolver.openOutputStream(destination)?.use { output -> body.byteStream().use { it.copyTo(output) } }
            ?: error("无法写入所选位置")
    }

    private suspend fun api(): Pair<SecurityApi, String> {
        val connection = preferences.connection.first()
        val token = tokenStore.read()
        check(connection.serverUrl.isNotBlank() && !token.isNullOrBlank()) { "请先完成服务器配对" }
        val longClient = client.newBuilder().readTimeout(5, TimeUnit.MINUTES).writeTimeout(5, TimeUnit.MINUTES).build()
        val api = Retrofit.Builder().baseUrl("${connection.serverUrl.trimEnd('/')}/").client(longClient)
            .addConverterFactory(GsonConverterFactory.create()).build().create(SecurityApi::class.java)
        return api to "Bearer $token"
    }
}
