package cn.jiayi.familymemory.data.core

import cn.jiayi.familymemory.data.connection.AccessTokenStore
import cn.jiayi.familymemory.data.connection.ConnectionPreferences
import cn.jiayi.familymemory.data.local.CoreDao
import cn.jiayi.familymemory.data.local.PendingSyncEntity
import cn.jiayi.familymemory.data.local.PersonEntity
import cn.jiayi.familymemory.data.local.RecordEntity
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
import javax.inject.Inject
import javax.inject.Singleton

data class SyncSummary(val uploaded: Int, val persons: Int, val records: Int)

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
    val pendingCount: Flow<Int> = dao.observePendingCount()

    suspend fun addPerson(name: String): PersonEntity {
        require(name.isNotBlank()) { "姓名不能为空" }
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val person = PersonEntity(id = id, clientUuid = id, name = name.trim(), updatedAt = timestamp)
        dao.savePersonAndQueue(person, queue("person", id, mapOf("id" to id, "name" to person.name), timestamp))
        return person
    }

    suspend fun addRecord(title: String, text: String, personIds: List<String> = emptyList()): RecordEntity {
        require(title.isNotBlank()) { "标题不能为空" }
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val record = RecordEntity(
            id = id,
            clientUuid = id,
            title = title.trim(),
            originalText = text.trim(),
            personIds = personIds,
            updatedAt = timestamp,
        )
        val payload = mapOf("id" to id, "title" to record.title, "original_text" to record.originalText, "person_ids" to personIds)
        dao.saveRecordAndQueue(record, queue("record", id, payload, timestamp))
        return record
    }

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

    private fun RecordDto.toEntity(timestamp: Long) = RecordEntity(
        id = id, clientUuid = clientUuid, serverId = id, title = title, originalText = originalText.orEmpty(),
        editedText = editedText.orEmpty(), recordType = recordType ?: "text", datePrecision = datePrecision ?: "unknown",
        locationText = locationText.orEmpty(), personIds = personIds.orEmpty(), visibility = visibility ?: "private",
        sourceType = sourceType ?: "personal_memory", verificationStatus = verificationStatus ?: "unverified_information",
        syncVersion = syncVersion, updatedAt = timestamp,
    )
}
