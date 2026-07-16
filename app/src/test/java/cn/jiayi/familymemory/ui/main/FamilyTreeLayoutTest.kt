package cn.jiayi.familymemory.ui.main

import cn.jiayi.familymemory.data.local.PersonEntity
import cn.jiayi.familymemory.data.local.RelationshipEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyTreeLayoutTest {
    private val parent = PersonEntity("p1", "p1", name = "母亲", updatedAt = 1)
    private val self = PersonEntity("p2", "p2", name = "本人", isSelf = true, updatedAt = 1)
    private val spouse = PersonEntity("p3", "p3", name = "伴侣", updatedAt = 1)

    @Test
    fun parentIsAboveCenterAndSpouseSharesGeneration() {
        val positions = calculateTreeLayout(
            listOf(parent, self, spouse),
            listOf(
                RelationshipEntity("r1", "r1", personAId = parent.id, personBId = self.id, relationType = "mother", updatedAt = 1),
                RelationshipEntity("r2", "r2", personAId = self.id, personBId = spouse.id, relationType = "spouse", updatedAt = 1),
            ),
            self.id,
        ).associateBy { it.personId }

        assertTrue(positions.getValue(parent.id).generation < positions.getValue(self.id).generation)
        assertEquals(positions.getValue(self.id).generation, positions.getValue(spouse.id).generation)
    }

    @Test
    fun incompleteFamilyStillPlacesEveryPerson() {
        val positions = calculateTreeLayout(listOf(parent, self, spouse), emptyList(), self.id)
        assertEquals(setOf(parent.id, self.id, spouse.id), positions.map { it.personId }.toSet())
    }
}
