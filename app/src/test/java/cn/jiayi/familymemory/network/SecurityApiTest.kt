package cn.jiayi.familymemory.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SecurityApiTest {
    @Test
    fun backupEntryParsesEncryptedBackupMetadata() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(201).setBody("""
            {"id":"backup-1","filename":"family.fmbackup","size_bytes":2048,
             "persons_count":9,"records_count":5,"media_count":3,"created_at":"2026-07-17T00:00:00Z"}
        """.trimIndent()).addHeader("Content-Type", "application/json"))
        server.start()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(SecurityApi::class.java)
            val result = api.createBackup("Bearer test", PasswordDto("long-password"))
            assertEquals("family.fmbackup", result.filename)
            assertEquals(9, result.personsCount)
            assertEquals("Bearer test", server.takeRequest().getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }
}
