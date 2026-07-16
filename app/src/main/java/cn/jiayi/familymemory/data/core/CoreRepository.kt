package cn.jiayi.familymemory.data.core

import cn.jiayi.familymemory.data.connection.AccessTokenStore
import cn.jiayi.familymemory.data.connection.ConnectionPreferences
import cn.jiayi.familymemory.data.local.CoreDao
import cn.jiayi.familymemory.data.local.PendingSyncEntity
import cn.jiayi.familymemory.data.local.PersonEntity
import cn.jiayi.familymemory.data.local.RecordEntity
import cn.jiayi.familymemory.data.local.RecordDraftEntity
import cn.jiayi.familymemory.data.local.RecordTagEntity
import cn.jiayi.familymemory.data.local.RelationshipEntity
import cn.jiayi.familymemory.data.local.SyncStateEntity
import cn.jiayi.familymemory.data.local.TagEntity
import cn.jiayi.familymemory.network.CoreDataApi
import cn.jiayi.familymemory.network.RecordDto
import cn.jiayi.familymemory.network.SyncPushItemDto
import cn.jiayi.familymemory.network.SyncPushRequestDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class SyncSummary(val uploaded: Int, val persons: Int, val records: Int)

data class PersonInput(
    val name: String,
    val nickname: String = "",
    val gender: String? = null,
    val birthYear: Int? = null,
    val deathYear: Int? = null,
    val birthPlace: String = "",
    val ancestralHome: String = "",
    val biography: String = "",
    val notes: String = "",
    val isSelf: Boolean = false,
)

data class RecordInput(
    val title: String,
    val text: String,
    val occurredAt: Long? = null,
    val datePrecision: String = "unknown",
    val locationText: String = "",
    val personIds: List<String> = emptyList(),
    val recordType: String = "text",
    val tagIds: List<String> = emptyList(),
)

@Singleton
class CoreRepository @Inject constructor(
    private val dao: CoreDao,
    private val client: OkHttpClient,
    private val preferences: ConnectionPreferences,
    private val tokenStore: AccessTokenStore,
) {
    private val gson = Gson()
    val persons: Flow<List<PersonEntity>> = dao.observePersons()
    val relationships: Flow<List<RelationshipEntity>> = dao.observeRelationships()
    val records: Flow<List<RecordEntity>> = dao.observeRecords()
    val tags: Flow<List<TagEntity>> = dao.observeTags()
    val recordTags: Flow<List<RecordTagEntity>> = dao.observeRecordTags()
    val recordDraft: Flow<RecordDraftEntity?> = dao.observeRecordDraft()
    val pendingCount: Flow<Int> = dao.observePendingCount()

    suspend fun addPerson(name: String): PersonEntity {
        return addPerson(PersonInput(name))
    }

    suspend fun addPerson(input: PersonInput): PersonEntity {
        require(input.name.isNotBlank()) { "姓名不能为空" }
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val person = PersonEntity(
            id = id, clientUuid = id, name = input.name.trim(), nickname = input.nickname.trim(), gender = input.gender,
            birthYear = input.birthYear, birthDatePrecision = if (input.birthYear == null) "unknown" else "year",
            deathYear = input.deathYear, deathDatePrecision = if (input.deathYear == null) "unknown" else "year",
            birthPlace = input.birthPlace.trim(), ancestralHome = input.ancestralHome.trim(), biography = input.biography.trim(),
            notes = input.notes.trim(), isSelf = input.isSelf, updatedAt = timestamp,
        )
        dao.savePersonAndQueue(person, queue("person", id, personPayload(person), timestamp))
        return person
    }

    suspend fun updatePerson(person: PersonEntity, input: PersonInput): PersonEntity {
        require(input.name.isNotBlank()) { "姓名不能为空" }
        val timestamp = System.currentTimeMillis()
        val updated = person.copy(
            name = input.name.trim(), nickname = input.nickname.trim(), gender = input.gender, birthYear = input.birthYear,
            birthDatePrecision = if (input.birthYear == null) "unknown" else "year", deathYear = input.deathYear,
            deathDatePrecision = if (input.deathYear == null) "unknown" else "year", birthPlace = input.birthPlace.trim(),
            ancestralHome = input.ancestralHome.trim(), biography = input.biography.trim(), notes = input.notes.trim(),
            isSelf = input.isSelf, syncVersion = person.syncVersion + 1, updatedAt = timestamp,
        )
        dao.savePersonAndQueue(updated, queue("person", updated.clientUuid, personPayload(updated), timestamp))
        return updated
    }

    suspend fun addRecord(title: String, text: String, personIds: List<String> = emptyList()): RecordEntity {
        return addRecord(RecordInput(title, text, personIds = personIds))
    }

    suspend fun addRecord(input: RecordInput): RecordEntity {
        require(input.title.isNotBlank()) { "标题不能为空" }
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val record = RecordEntity(
            id = id,
            clientUuid = id,
            title = input.title.trim(), originalText = input.text.trim(), recordType = input.recordType,
            occurredAt = input.occurredAt, datePrecision = input.datePrecision, locationText = input.locationText.trim(),
            personIds = input.personIds,
            updatedAt = timestamp,
        )
        val payload = recordPayload(record) + ("tag_ids" to input.tagIds)
        dao.saveRecordWithTagsAndQueue(record, input.tagIds.map { RecordTagEntity(id, it) }, queue("record", id, payload, timestamp))
        dao.clearRecordDraft()
        return record
    }

    suspend fun saveRecordDraft(draft: RecordDraftEntity) = dao.saveRecordDraft(draft.copy(updatedAt = System.currentTimeMillis()))
    suspend fun clearRecordDraft() = dao.clearRecordDraft()

    suspend fun addRelationship(personAId: String, personBId: String, type: String): RelationshipEntity {
        require(personAId != personBId) { "关系双方不能是同一人" }
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val item = RelationshipEntity(id, id, personAId = personAId, personBId = personBId, relationType = type, updatedAt = timestamp)
        val payload = mapOf("id" to id, "person_a_id" to personAId, "person_b_id" to personBId, "relation_type" to type)
        dao.saveRelationshipAndQueue(item, queue("relationship", id, payload, timestamp))
        return item
    }

    suspend fun addTag(name: String, color: String = ""): TagEntity {
        require(name.isNotBlank()) { "标签名不能为空" }
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val tag = TagEntity(id, id, name = name.trim(), color = color, updatedAt = timestamp)
        dao.saveTagAndQueue(tag, queue("tag", id, mapOf("id" to id, "name" to tag.name, "color" to color), timestamp))
        return tag
    }

    suspend fun sync(): SyncSummary {
        val (api, authorization) = connectedApi()
        val pending = dao.pending()
        if (pending.isNotEmpty()) {
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val request = SyncPushRequestDto(
                pending.map { item ->
                    SyncPushItemDto(item.entityType, item.operation, item.clientUuid, gson.fromJson(item.payloadJson, mapType))
                },
            )
            val response = api.push(authorization, request)
            val accepted = response.results.filter { it.status == "synced" || it.status == "deleted" }.map { it.clientUuid }.toSet()
            val acceptedIds = pending.filter { it.clientUuid in accepted }.map { it.queueId }
            if (acceptedIds.isNotEmpty()) dao.removePending(acceptedIds)
            val rejected = response.results.count { it.status == "rejected" }
            check(rejected == 0) { "有 $rejected 条本地资料未通过服务器校验" }
        }
        return pullSnapshot(api, authorization, pending.size)
    }

    suspend fun loadDemo(): SyncSummary {
        val (api, authorization) = connectedApi()
        api.loadDemo(authorization)
        return pullSnapshot(api, authorization, 0)
    }

    suspend fun clearLocalData() = dao.clearAllCoreData()

    private suspend fun pullSnapshot(api: CoreDataApi, authorization: String, uploaded: Int): SyncSummary {
        val people = api.persons(authorization)
        val relationships = api.relationships(authorization)
        val records = api.records(authorization)
        val tags = api.tags(authorization)
        val timestamp = System.currentTimeMillis()
        dao.replaceServerSnapshot(
            people.map { dto ->
                PersonEntity(
                    id = dto.id, clientUuid = dto.clientUuid, serverId = dto.id, name = dto.name,
                    surname = dto.surname.orEmpty(), givenName = dto.givenName.orEmpty(), formerNames = dto.formerNames.orEmpty(),
                    nickname = dto.nickname.orEmpty(), gender = dto.gender, birthYear = dto.birthYear,
                    birthDatePrecision = dto.birthDatePrecision ?: "unknown", deathYear = dto.deathYear,
                    deathDatePrecision = dto.deathDatePrecision ?: "unknown", birthPlace = dto.birthPlace.orEmpty(),
                    ancestralHome = dto.ancestralHome.orEmpty(), formerResidences = dto.formerResidences.orEmpty(),
                    biography = dto.biography.orEmpty(), isSelf = dto.isSelf ?: false, notes = dto.notes.orEmpty(),
                    sourceText = dto.sourceText.orEmpty(), verificationStatus = dto.verificationStatus ?: "unverified_information",
                    visibility = dto.visibility ?: "private", syncVersion = dto.syncVersion, updatedAt = timestamp,
                )
            },
            relationships.map { dto ->
                RelationshipEntity(
                    dto.id, dto.clientUuid, dto.id, dto.personAId, dto.personBId, dto.relationType,
                    dto.notes.orEmpty(), dto.sourceText.orEmpty(), dto.verificationStatus ?: "unverified_information",
                    dto.visibility ?: "private", dto.syncVersion, timestamp,
                )
            },
            records.map { it.toEntity(timestamp) },
            tags.map { dto -> TagEntity(dto.id, dto.clientUuid, dto.id, dto.name, dto.color.orEmpty(), dto.syncVersion, timestamp) },
            records.flatMap { record -> record.tagIds.orEmpty().map { tagId -> RecordTagEntity(record.id, tagId) } },
        )
        dao.saveSyncState(SyncStateEntity(latestServerVersion = 0, lastSyncedAt = timestamp))
        return SyncSummary(uploaded, people.size, records.size)
    }

    private suspend fun connectedApi(): Pair<CoreDataApi, String> {
        val connection = preferences.connection.first()
        val token = tokenStore.read()
        check(connection.serverUrl.isNotBlank() && !token.isNullOrBlank()) { "请先完成服务器配对" }
        val api = Retrofit.Builder()
            .baseUrl("${connection.serverUrl.trimEnd('/')}/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(CoreDataApi::class.java)
        return api to "Bearer $token"
    }

    private fun queue(entityType: String, clientUuid: String, payload: Map<String, Any?>, timestamp: Long) =
        PendingSyncEntity(entityType = entityType, clientUuid = clientUuid, operation = "upsert", payloadJson = gson.toJson(payload), createdAt = timestamp)

    private fun personPayload(person: PersonEntity): Map<String, Any?> = mapOf(
        "id" to person.id, "name" to person.name, "nickname" to person.nickname, "gender" to person.gender,
        "birth_year" to person.birthYear, "birth_date_precision" to person.birthDatePrecision,
        "death_year" to person.deathYear, "death_date_precision" to person.deathDatePrecision,
        "birth_place" to person.birthPlace, "ancestral_home" to person.ancestralHome, "biography" to person.biography,
        "notes" to person.notes, "is_self" to person.isSelf, "verification_status" to person.verificationStatus,
        "visibility" to person.visibility,
    )

    private fun recordPayload(record: RecordEntity): Map<String, Any?> = mapOf(
        "id" to record.id, "title" to record.title, "original_text" to record.originalText, "edited_text" to record.editedText,
        "record_type" to record.recordType, "occurred_at_start" to record.occurredAt?.let(::formatIsoTime),
        "date_precision" to record.datePrecision, "location_text" to record.locationText, "person_ids" to record.personIds,
        "visibility" to record.visibility, "source_type" to record.sourceType, "verification_status" to record.verificationStatus,
    )

    private fun RecordDto.toEntity(timestamp: Long) = RecordEntity(
        id = id, clientUuid = clientUuid, serverId = id, title = title, originalText = originalText.orEmpty(),
        editedText = editedText.orEmpty(), recordType = recordType ?: "text",
        occurredAt = occurredAtStart?.let(::parseIsoTime), datePrecision = datePrecision ?: "unknown",
        locationText = locationText.orEmpty(), personIds = personIds.orEmpty(), visibility = visibility ?: "private",
        sourceType = sourceType ?: "personal_memory", verificationStatus = verificationStatus ?: "unverified_information",
        syncVersion = syncVersion, updatedAt = timestamp,
    )

    private fun formatIsoTime(epoch: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epoch))

    private fun parseIsoTime(value: String): Long? {
        val normalized = value.replace(Regex("(\\.\\d{3})\\d+"), "$1")
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX")
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).parse(normalized)?.time }.getOrNull()
        }
    }
}
