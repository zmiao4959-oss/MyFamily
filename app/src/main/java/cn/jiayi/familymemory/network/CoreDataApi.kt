package cn.jiayi.familymemory.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

data class PersonDto(
    val id: String,
    @SerializedName("client_uuid") val clientUuid: String,
    val name: String,
    val surname: String? = null,
    @SerializedName("given_name") val givenName: String? = null,
    @SerializedName("former_names") val formerNames: List<String>? = null,
    val nickname: String? = null,
    val gender: String? = null,
    @SerializedName("birth_year") val birthYear: Int? = null,
    @SerializedName("birth_date_precision") val birthDatePrecision: String? = null,
    @SerializedName("death_year") val deathYear: Int? = null,
    @SerializedName("death_date_precision") val deathDatePrecision: String? = null,
    @SerializedName("birth_place") val birthPlace: String? = null,
    @SerializedName("ancestral_home") val ancestralHome: String? = null,
    @SerializedName("former_residences") val formerResidences: List<String>? = null,
    val biography: String? = null,
    @SerializedName("is_self") val isSelf: Boolean? = null,
    val notes: String? = null,
    @SerializedName("source_text") val sourceText: String? = null,
    @SerializedName("verification_status") val verificationStatus: String? = null,
    val visibility: String? = null,
    @SerializedName("sync_version") val syncVersion: Int,
    @SerializedName("updated_at") val updatedAt: String,
)

data class RelationshipDto(
    val id: String,
    @SerializedName("client_uuid") val clientUuid: String,
    @SerializedName("person_a_id") val personAId: String,
    @SerializedName("person_b_id") val personBId: String,
    @SerializedName("relation_type") val relationType: String,
    val notes: String? = null,
    @SerializedName("source_text") val sourceText: String? = null,
    @SerializedName("verification_status") val verificationStatus: String? = null,
    val visibility: String? = null,
    @SerializedName("sync_version") val syncVersion: Int,
    @SerializedName("updated_at") val updatedAt: String,
)

data class TagDto(
    val id: String,
    @SerializedName("client_uuid") val clientUuid: String,
    val name: String,
    val color: String? = null,
    @SerializedName("sync_version") val syncVersion: Int,
    @SerializedName("updated_at") val updatedAt: String,
)

data class RecordDto(
    val id: String,
    @SerializedName("client_uuid") val clientUuid: String,
    val title: String,
    @SerializedName("original_text") val originalText: String? = null,
    @SerializedName("edited_text") val editedText: String? = null,
    @SerializedName("record_type") val recordType: String? = null,
    @SerializedName("occurred_at_start") val occurredAtStart: String? = null,
    @SerializedName("date_precision") val datePrecision: String? = null,
    @SerializedName("location_text") val locationText: String? = null,
    @SerializedName("person_ids") val personIds: List<String>? = null,
    @SerializedName("tag_ids") val tagIds: List<String>? = null,
    val visibility: String? = null,
    @SerializedName("source_type") val sourceType: String? = null,
    @SerializedName("verification_status") val verificationStatus: String? = null,
    @SerializedName("sync_version") val syncVersion: Int,
    @SerializedName("updated_at") val updatedAt: String,
)

data class SyncPushItemDto(
    @SerializedName("entity_type") val entityType: String,
    val operation: String,
    @SerializedName("client_uuid") val clientUuid: String,
    val payload: Map<String, Any?>,
)
data class SyncPushRequestDto(val changes: List<SyncPushItemDto>)
data class SyncPushResultDto(@SerializedName("client_uuid") val clientUuid: String, val status: String)
data class SyncPushResponseDto(val results: List<SyncPushResultDto>)
data class DemoResponseDto(val persons: Int, val relationships: Int, val records: Int, val tags: Int)

interface CoreDataApi {
    @GET("persons") suspend fun persons(@Header("Authorization") authorization: String): List<PersonDto>
    @GET("relationships") suspend fun relationships(@Header("Authorization") authorization: String): List<RelationshipDto>
    @GET("records") suspend fun records(@Header("Authorization") authorization: String): List<RecordDto>
    @GET("tags") suspend fun tags(@Header("Authorization") authorization: String): List<TagDto>
    @POST("sync/push") suspend fun push(@Header("Authorization") authorization: String, @Body body: SyncPushRequestDto): SyncPushResponseDto
    @POST("demo/load") suspend fun loadDemo(@Header("Authorization") authorization: String): DemoResponseDto
}
