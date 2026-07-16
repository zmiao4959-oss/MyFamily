package cn.jiayi.familymemory.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CoreDao {
    @Query("SELECT * FROM media_assets ORDER BY createdAt DESC")
    fun observeMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_assets WHERE id = :id")
    suspend fun mediaById(id: String): MediaEntity?

    @Upsert
    suspend fun upsertMedia(media: MediaEntity)

    @Query("UPDATE media_assets SET uploadStatus = :status, uploadProgress = :progress, uploadId = :uploadId, uploadedBytes = :uploadedBytes, lastError = :error WHERE id = :id")
    suspend fun updateMediaUpload(id: String, status: String, progress: Int, uploadId: String?, uploadedBytes: Long, error: String?)

    @Query("UPDATE media_assets SET serverId = :serverId, uploadStatus = 'uploaded', uploadProgress = 100, lastError = NULL WHERE id = :id")
    suspend fun markMediaUploaded(id: String, serverId: String)

    @Query("DELETE FROM media_assets WHERE id = :id")
    suspend fun deleteMedia(id: String)

    @Query("SELECT * FROM persons WHERE deletedAt IS NULL ORDER BY isSelf DESC, name")
    fun observePersons(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM relationships WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeRelationships(): Flow<List<RelationshipEntity>>

    @Query("SELECT * FROM records WHERE deletedAt IS NULL ORDER BY occurredAt DESC, updatedAt DESC")
    fun observeRecords(): Flow<List<RecordEntity>>

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY name")
    fun observeTags(): Flow<List<TagEntity>>

    @Upsert
    suspend fun upsertPerson(person: PersonEntity)

    @Upsert
    suspend fun upsertPersons(persons: List<PersonEntity>)

    @Upsert
    suspend fun upsertRelationship(relationship: RelationshipEntity)

    @Upsert
    suspend fun upsertRelationships(relationships: List<RelationshipEntity>)

    @Upsert
    suspend fun upsertRecord(record: RecordEntity)

    @Upsert
    suspend fun upsertRecords(records: List<RecordEntity>)

    @Upsert
    suspend fun upsertTag(tag: TagEntity)

    @Upsert
    suspend fun upsertTags(tags: List<TagEntity>)

    @Upsert
    suspend fun upsertRecordTags(items: List<RecordTagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: PendingSyncEntity)

    @Query("SELECT * FROM pending_sync ORDER BY createdAt LIMIT :limit")
    suspend fun pending(limit: Int = 100): List<PendingSyncEntity>

    @Query("DELETE FROM pending_sync WHERE queueId IN (:ids)")
    suspend fun removePending(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM pending_sync")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM sync_state WHERE `key` = 'server'")
    suspend fun syncState(): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSyncState(state: SyncStateEntity)

    @Query("SELECT COUNT(*) FROM persons WHERE sourceText = :marker AND deletedAt IS NULL")
    suspend fun demoPersonCount(marker: String): Int

    @Query("DELETE FROM record_tags")
    suspend fun clearRecordTags()

    @Query("DELETE FROM media_assets")
    suspend fun clearMedia()

    @Query("DELETE FROM pending_sync")
    suspend fun clearPending()

    @Query("DELETE FROM relationships")
    suspend fun clearRelationships()

    @Query("DELETE FROM records")
    suspend fun clearRecords()

    @Query("DELETE FROM tags")
    suspend fun clearTags()

    @Query("DELETE FROM persons")
    suspend fun clearPersons()

    @Transaction
    suspend fun savePersonAndQueue(person: PersonEntity, item: PendingSyncEntity) {
        upsertPerson(person)
        enqueue(item)
    }

    @Transaction
    suspend fun saveRecordAndQueue(record: RecordEntity, item: PendingSyncEntity) {
        upsertRecord(record)
        enqueue(item)
    }

    @Transaction
    suspend fun saveRelationshipAndQueue(relationship: RelationshipEntity, item: PendingSyncEntity) {
        upsertRelationship(relationship)
        enqueue(item)
    }

    @Transaction
    suspend fun saveTagAndQueue(tag: TagEntity, item: PendingSyncEntity) {
        upsertTag(tag)
        enqueue(item)
    }

    @Transaction
    suspend fun replaceServerSnapshot(
        persons: List<PersonEntity>,
        relationships: List<RelationshipEntity>,
        records: List<RecordEntity>,
        tags: List<TagEntity>,
        recordTags: List<RecordTagEntity>,
    ) {
        upsertPersons(persons)
        upsertRelationships(relationships)
        upsertRecords(records)
        upsertTags(tags)
        upsertRecordTags(recordTags)
    }

    @Transaction
    suspend fun clearAllCoreData() {
        clearRecordTags()
        clearPending()
        clearMedia()
        clearRelationships()
        clearRecords()
        clearTags()
        clearPersons()
    }
}
