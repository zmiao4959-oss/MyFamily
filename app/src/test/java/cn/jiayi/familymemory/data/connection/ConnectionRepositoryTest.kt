package cn.jiayi.familymemory.data.connection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectionRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var preferences: FakePreferences
    private lateinit var tokenStore: FakeTokenStore
    private lateinit var repository: ConnectionRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        tokenStore = FakeTokenStore()
        preferences = FakePreferences(tokenStore)
        repository = ConnectionRepository(OkHttpClient(), preferences, tokenStore)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `successful pairing saves normalized address and access token`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":"ok","version":"0.1.0","database":"ok"}""").setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setBody("""{"access_token":"secret-access","token_type":"bearer","server_version":"0.1.0"}""").setHeader("Content-Type", "application/json"))

        val result = repository.connect(server.url("/").toString(), "pair-me")

        assertTrue(result is ConnectionResult.Success)
        assertEquals(server.url("/").toString().trimEnd('/'), preferences.serverUrl)
        assertEquals("secret-access", tokenStore.token)
    }

    @Test
    fun `invalid address fails before network request`() = runBlocking {
        val result = repository.connect("192.168.1.2:8080", "pair-me")
        assertTrue(result is ConnectionResult.Failure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `clear pairing revokes server token and removes local values`() = runBlocking {
        preferences.saveServerUrl(server.url("/").toString().trimEnd('/'))
        tokenStore.save("secret-access")
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"revoked\":true}"))

        repository.clear()

        val request = server.takeRequest()
        assertEquals("/pair/revoke", request.path)
        assertEquals("Bearer secret-access", request.getHeader("Authorization"))
        assertEquals("", preferences.serverUrl)
        assertEquals(null, tokenStore.token)
    }

    @Test
    fun `address with path or credentials is rejected`() {
        assertEquals(null, ConnectionRepository.normalizeServerUrl("https://example.com/api"))
        assertEquals(null, ConnectionRepository.normalizeServerUrl("https://user:pass@example.com"))
    }

    private class FakeTokenStore : AccessTokenStore {
        var token: String? = null
        override fun hasToken() = token != null
        override fun read() = token
        override fun save(token: String) { this.token = token }
        override fun clear() { token = null }
    }

    private class FakePreferences(private val tokenStore: AccessTokenStore) : ConnectionPreferences {
        var serverUrl = ""
        private val state = MutableStateFlow(SavedConnection())
        override val connection: Flow<SavedConnection> = state
        override suspend fun saveServerUrl(serverUrl: String) {
            this.serverUrl = serverUrl
            state.value = SavedConnection(serverUrl, tokenStore.hasToken())
        }
        override suspend fun clear() {
            serverUrl = ""
            tokenStore.clear()
            state.value = SavedConnection()
        }
    }
}
