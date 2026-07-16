package cn.jiayi.familymemory.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreDaoTest {
    private lateinit var database: FamilyDatabase
    private lateinit var dao: CoreDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FamilyDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.coreDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun offlinePersonAndOutboxAreSavedTogether() = runBlocking {
        val person = PersonEntity(id = "p1", clientUuid = "p1", name = "离线人物", updatedAt = 1)
        val pending = PendingSyncEntity(
            entityType = "person",
            clientUuid = "p1",
            operation = "upsert",
            payloadJson = "{\"id\":\"p1\",\"name\":\"离线人物\"}",
            createdAt = 1,
        )

        dao.savePersonAndQueue(person, pending)

        assertEquals(listOf(person), dao.observePersons().first())
        assertEquals(1, dao.observePendingCount().first())
    }

    @Test
    fun serverSnapshotKeepsRelationshipForeignKeysValid() = runBlocking {
        val first = PersonEntity(id = "p1", clientUuid = "p1", name = "甲", updatedAt = 1)
        val second = PersonEntity(id = "p2", clientUuid = "p2", name = "乙", updatedAt = 1)
        val relationship = RelationshipEntity(
            id = "r1", clientUuid = "r1", personAId = "p1", personBId = "p2", relationType = "spouse", updatedAt = 1,
        )

        dao.replaceServerSnapshot(listOf(first, second), listOf(relationship), emptyList(), emptyList(), emptyList())

        assertEquals(2, dao.observePersons().first().size)
        assertEquals(listOf(relationship), dao.observeRelationships().first())
    }

    @Test
    fun mediaUploadQueueKeepsProgressAndOriginalPath() = runBlocking {
        val record = RecordEntity(id = "record-1", clientUuid = "record-1", title = "录音", updatedAt = 1)
        dao.upsertRecord(record)
        val media = MediaEntity(
            id = "media-1",
            clientUuid = "media-1",
            recordId = record.id,
            mediaType = "audio",
            mimeType = "audio/mp4",
            originalFilename = "recording.m4a",
            localPath = "/private/original/recording.m4a",
            sizeBytes = 100,
            sha256 = "a".repeat(64),
            createdAt = 1,
        )
        dao.upsertMedia(media)

        dao.updateMediaUpload(media.id, "uploading", 50, "upload-1", 50, null)

        val queued = dao.observeMedia().first().single()
        assertEquals("/private/original/recording.m4a", queued.localPath)
        assertEquals("uploading", queued.uploadStatus)
        assertEquals(50, queued.uploadProgress)
        assertEquals(50, queued.uploadedBytes)
    }

    @Test
    fun recordDraftSurvivesAndCanBeCleared() = runBlocking {
        val draft = RecordDraftEntity(title = "未完成的回忆", originalText = "先写到这里", occurredYear = "1998")
        dao.saveRecordDraft(draft)

        assertEquals(draft, dao.observeRecordDraft().first())

        dao.clearRecordDraft()
        assertEquals(null, dao.observeRecordDraft().first())
    }

    @Test
    fun recordAndTagsAreSavedWithOutbox() = runBlocking {
        val record = RecordEntity(id = "record-2", clientUuid = "record-2", title = "春节", updatedAt = 2)
        val tag = TagEntity(id = "tag-1", clientUuid = "tag-1", name = "春节", updatedAt = 1)
        dao.upsertTag(tag)
        dao.saveRecordWithTagsAndQueue(
            record,
            listOf(RecordTagEntity(record.id, tag.id)),
            PendingSyncEntity(entityType = "record", clientUuid = record.clientUuid, operation = "upsert", payloadJson = "{}", createdAt = 2),
        )

        assertEquals(listOf(RecordTagEntity(record.id, tag.id)), dao.observeRecordTags().first())
        assertEquals(1, dao.observePendingCount().first())
    }
}
