package cn.jiayi.familymemory.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class AiStatusDto(
    val enabled: Boolean,
    val configured: Boolean,
    val provider: String,
    @SerializedName("fast_model") val fastModel: String,
    @SerializedName("main_model") val mainModel: String,
)

data class AiObjectRequestDto(
    @SerializedName("record_id") val recordId: String? = null,
    @SerializedName("person_id") val personId: String? = null,
)

data class AiAskRequestDto(val question: String, @SerializedName("person_id") val personId: String? = null)
data class AiConfirmRequestDto(val confirmed: Boolean)

data class AiJobDto(
    val id: String,
    @SerializedName("job_type") val jobType: String,
    @SerializedName("record_id") val recordId: String? = null,
    @SerializedName("person_id") val personId: String? = null,
    val status: String,
    val provider: String,
    val model: String,
    @SerializedName("result_artifact_id") val resultArtifactId: String? = null,
    @SerializedName("attempt_count") val attemptCount: Int,
    @SerializedName("max_attempts") val maxAttempts: Int,
    @SerializedName("last_error") val lastError: String? = null,
)

data class AiArtifactDto(
    val id: String,
    @SerializedName("record_id") val recordId: String? = null,
    @SerializedName("person_id") val personId: String? = null,
    @SerializedName("artifact_type") val artifactType: String,
    val provider: String,
    val model: String,
    @SerializedName("prompt_version") val promptVersion: String,
    @SerializedName("output_json") val outputJson: Map<String, Any?>,
    val status: String,
    @SerializedName("user_confirmed") val userConfirmed: Boolean,
)

interface AiApi {
    @GET("ai/status") suspend fun status(@Header("Authorization") authorization: String): AiStatusDto
    @POST("ai/organize-record") suspend fun organize(@Header("Authorization") authorization: String, @Body body: AiObjectRequestDto): AiJobDto
    @POST("ai/generate-biography") suspend fun biography(@Header("Authorization") authorization: String, @Body body: AiObjectRequestDto): AiJobDto
    @POST("ai/generate-interview-questions") suspend fun interview(@Header("Authorization") authorization: String, @Body body: AiObjectRequestDto): AiJobDto
    @POST("ai/ask") suspend fun ask(@Header("Authorization") authorization: String, @Body body: AiAskRequestDto): AiJobDto
    @GET("ai/jobs/{id}") suspend fun job(@Header("Authorization") authorization: String, @Path("id") id: String): AiJobDto
    @POST("ai/jobs/{id}/retry") suspend fun retry(@Header("Authorization") authorization: String, @Path("id") id: String): AiJobDto
    @GET("ai/artifacts") suspend fun artifacts(
        @Header("Authorization") authorization: String,
        @Query("record_id") recordId: String? = null,
        @Query("person_id") personId: String? = null,
    ): List<AiArtifactDto>
    @GET("ai/artifacts/{id}") suspend fun artifact(@Header("Authorization") authorization: String, @Path("id") id: String): AiArtifactDto
    @POST("ai/artifacts/{id}/confirm") suspend fun confirm(@Header("Authorization") authorization: String, @Path("id") id: String, @Body body: AiConfirmRequestDto): AiArtifactDto
}
