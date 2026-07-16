package cn.jiayi.familymemory.data.search

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SearchRepositoryTest {
    @Test
    fun disabledAndMissingConfigurationHaveClearMessages() {
        val disabled = HttpException(Response.error<Unit>(409, "".toResponseBody(null)))
        val missing = HttpException(Response.error<Unit>(503, "".toResponseBody(null)))
        assertTrue(SearchRepository.userMessage(disabled).contains("关键词搜索"))
        assertTrue(SearchRepository.userMessage(missing).contains("火山引擎"))
    }
}
