package cn.jiayi.familymemory.data.connection

data class SavedConnection(
    val serverUrl: String = "",
    val hasAccessToken: Boolean = false,
)

sealed interface ConnectionResult {
    data class Success(val version: String) : ConnectionResult
    data class Failure(val message: String) : ConnectionResult
}

interface ConnectionPreferences {
    val connection: kotlinx.coroutines.flow.Flow<SavedConnection>
    suspend fun saveServerUrl(serverUrl: String)
    suspend fun clear()
}

interface AccessTokenStore {
    fun hasToken(): Boolean
    fun read(): String?
    fun save(token: String)
    fun clear()
}
