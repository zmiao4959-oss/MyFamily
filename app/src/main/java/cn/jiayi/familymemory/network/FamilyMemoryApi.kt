package cn.jiayi.familymemory.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

data class HealthResponse(
    val status: String,
    val version: String,
    val database: String,
)

data class PairRequest(
    val pairing_token: String,
    val device_name: String,
)

data class PairResponse(
    val access_token: String,
    val token_type: String,
    val server_version: String,
)

interface FamilyMemoryApi {
    @GET("health")
    suspend fun health(): HealthResponse

    @POST("pair")
    suspend fun pair(@Body request: PairRequest): PairResponse

    @POST("pair/revoke")
    suspend fun revoke(@Header("Authorization") authorization: String)
}
