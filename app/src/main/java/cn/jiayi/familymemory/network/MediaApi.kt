package cn.jiayi.familymemory.network

import com.google.gson.annotations.SerializedName
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class MediaInitRequestDto(
    @SerializedName("record_id") val recordId: String,
    @SerializedName("client_uuid") val clientUuid: String,
    @SerializedName("original_filename") val originalFilename: String,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("size_bytes") val sizeBytes: Long,
    val sha256: String,
)

data class MediaInitResponseDto(
    @SerializedName("upload_id") val uploadId: String,
    @SerializedName("chunk_size") val chunkSize: Int,
    @SerializedName("bytes_received") val bytesReceived: Long,
)

data class MediaChunkResponseDto(
    @SerializedName("upload_id") val uploadId: String,
    @SerializedName("bytes_received") val bytesReceived: Long,
    val complete: Boolean,
)

data class MediaAssetDto(
    val id: String,
    @SerializedName("client_uuid") val clientUuid: String,
    @SerializedName("record_id") val recordId: String,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("size_bytes") val sizeBytes: Long,
    val sha256: String,
    @SerializedName("has_thumbnail") val hasThumbnail: Boolean,
)

interface MediaApi {
    @POST("media/init-upload")
    suspend fun initUpload(@Header("Authorization") authorization: String, @Body body: MediaInitRequestDto): MediaInitResponseDto

    @PUT("media/{uploadId}/chunk")
    suspend fun uploadChunk(
        @Header("Authorization") authorization: String,
        @Path("uploadId") uploadId: String,
        @Query("offset") offset: Long,
        @Body body: RequestBody,
    ): MediaChunkResponseDto

    @POST("media/{uploadId}/complete")
    suspend fun complete(@Header("Authorization") authorization: String, @Path("uploadId") uploadId: String): MediaAssetDto
}
