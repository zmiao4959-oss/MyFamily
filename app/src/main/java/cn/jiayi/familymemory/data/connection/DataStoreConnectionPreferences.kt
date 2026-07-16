package cn.jiayi.familymemory.data.connection

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.connectionDataStore by preferencesDataStore(name = "connection_settings")

@Singleton
class DataStoreConnectionPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tokenStore: AccessTokenStore,
) : ConnectionPreferences {
    override val connection: Flow<SavedConnection> = context.connectionDataStore.data.map { preferences ->
        SavedConnection(
            serverUrl = preferences[SERVER_URL].orEmpty(),
            hasAccessToken = tokenStore.hasToken(),
        )
    }

    override suspend fun saveServerUrl(serverUrl: String) {
        context.connectionDataStore.edit { it[SERVER_URL] = serverUrl }
    }

    override suspend fun clear() {
        context.connectionDataStore.edit { it.remove(SERVER_URL) }
        tokenStore.clear()
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
    }
}
