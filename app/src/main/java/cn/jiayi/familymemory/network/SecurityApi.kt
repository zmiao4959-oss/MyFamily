package cn.jiayi.familymemory.network

import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

data class PasswordDto(val password: String)
data class RestoreDto(val password: String, val confirmation: String = "RESTORE")
data class BackupDto(
    val id: String,
    val filename: String,
    @SerializedName("size_bytes") val sizeBytes: Long,
    @SerializedName("persons_count") val personsCount: Int,
    @SerializedName("records_count") val recordsCount: Int,
    @SerializedName("media_count") val mediaCount: Int,
    @SerializedName("created_at") val createdAt: String,
)
data class DeviceDto(
    val id: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("last_used_at") val lastUsedAt: String?,
    @SerializedName("revoked_at") val revokedAt: String?,
    val current: Boolean,
)
data class PairingTokenDto(@SerializedName("pairing_token") val pairingToken: String)
data class RestoreResultDto(val restored: Boolean, @SerializedName("safety_backup_id") val safetyBackupId: String)

interface SecurityApi {
    @POST("backup/create") suspend fun createBackup(@Header("Authorization") auth: String, @Body body: PasswordDto): BackupDto
    @GET("backup/list") suspend fun backups(@Header("Authorization") auth: String): List<BackupDto>
    @Streaming @GET("backup/{id}/download") suspend fun download(@Header("Authorization") auth: String, @Path("id") id: String): ResponseBody
    @POST("backup/{id}/restore") suspend fun restore(@Header("Authorization") auth: String, @Path("id") id: String, @Body body: RestoreDto): RestoreResultDto
    @GET("pair/tokens") suspend fun devices(@Header("Authorization") auth: String): List<DeviceDto>
    @DELETE("pair/tokens/{id}") suspend fun revokeDevice(@Header("Authorization") auth: String, @Path("id") id: String)
    @POST("pair/admin-token/regenerate") suspend fun regeneratePairingToken(@Header("Authorization") auth: String): PairingTokenDto
}
