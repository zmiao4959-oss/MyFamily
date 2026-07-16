package cn.jiayi.familymemory.data.ai

import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class AiRepositoryTest {
    @Test
    fun disabledAndMissingKeyHaveActionableMessages() {
        val disabled = HttpException(Response.error<Unit>(409, "".toResponseBody(null)))
        val missingKey = HttpException(Response.error<Unit>(503, "".toResponseBody(null)))

        assertTrue(AiRepository.userMessage(disabled).contains("FEATURE_AI=true"))
        assertTrue(AiRepository.userMessage(missingKey).contains("DeepSeek Key"))
    }
}
