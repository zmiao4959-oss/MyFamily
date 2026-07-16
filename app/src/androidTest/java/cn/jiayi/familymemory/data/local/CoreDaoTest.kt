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
}
