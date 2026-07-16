package cn.jiayi.familymemory.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

data class SearchStatusDto(
    val enabled: Boolean,
    val configured: Boolean,
    val provider: String,
    val model: String,
    @SerializedName("endpoint_id") val endpointId: String,
    val ready: Int,
    val pending: Int,
    val failed: Int,
)

data class SearchRequestDto(
    val query: String,
    @SerializedName("person_id") val personId: String? = null,
    @SerializedName("tag_id") val tagId: String? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    @SerializedName("year_from") val yearFrom: Int? = null,
    @SerializedName("year_to") val yearTo: Int? = null,
    val limit: Int = 20,
)

data class SearchResultDto(
    @SerializedName("result_type") val resultType: String,
    @SerializedName("object_id") val objectId: String,
    @SerializedName("record_id") val recordId: String? = null,
    @SerializedName("person_id") val personId: String? = null,
    @SerializedName("media_id") val mediaId: String? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    val title: String,
    val snippet: String,
    val score: Double,
)

data class SearchResponseDto(val mode: String, val results: List<SearchResultDto>, val message: String)
data class ReindexResponseDto(val queued: Int, val records: Int, val persons: Int, val images: Int)

interface SearchApi {
    @GET("search/status") suspend fun status(@Header("Authorization") authorization: String): SearchStatusDto
    @POST("search/text") suspend fun text(@Header("Authorization") authorization: String, @Body body: SearchRequestDto): SearchResponseDto
    @POST("search/semantic") suspend fun semantic(@Header("Authorization") authorization: String, @Body body: SearchRequestDto): SearchResponseDto
    @POST("search/multimodal") suspend fun multimodal(@Header("Authorization") authorization: String, @Body body: SearchRequestDto): SearchResponseDto
    @POST("search/reindex") suspend fun reindex(@Header("Authorization") authorization: String): ReindexResponseDto
}
