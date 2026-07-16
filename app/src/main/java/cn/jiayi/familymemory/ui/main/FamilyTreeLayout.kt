package cn.jiayi.familymemory.ui.main

import cn.jiayi.familymemory.data.local.PersonEntity
import cn.jiayi.familymemory.data.local.RelationshipEntity

data class TreeNodePosition(val personId: String, val x: Float, val y: Float, val generation: Int)

fun calculateTreeLayout(
    persons: List<PersonEntity>,
    relationships: List<RelationshipEntity>,
    centerPersonId: String?,
): List<TreeNodePosition> {
    if (persons.isEmpty()) return emptyList()
    val ids = persons.map { it.id }.toSet()
    val center = centerPersonId?.takeIf { it in ids } ?: persons.firstOrNull { it.isSelf }?.id ?: persons.first().id
    val generations = mutableMapOf(center to 0)
    repeat(persons.size.coerceAtMost(20)) {
        var changed = false
        relationships.forEach { relation ->
            if (relation.personAId !in ids || relation.personBId !in ids) return@forEach
            val parentLink = relation.relationType in setOf("father", "mother", "parent", "adoptive_parent", "step_parent", "guardian")
            val sameGeneration = relation.relationType in setOf("spouse", "former_spouse", "sibling")
            val a = generations[relation.personAId]
            val b = generations[relation.personBId]
            when {
                parentLink && a != null && b == null -> { generations[relation.personBId] = a + 1; changed = true }
                parentLink && b != null && a == null -> { generations[relation.personAId] = b - 1; changed = true }
                sameGeneration && a != null && b == null -> { generations[relation.personBId] = a; changed = true }
                sameGeneration && b != null && a == null -> { generations[relation.personAId] = b; changed = true }
            }
        }
        if (!changed) return@repeat
    }
    val orphanGeneration = (generations.values.maxOrNull() ?: 0) + 1
    persons.filter { it.id !in generations }.forEach { generations[it.id] = orphanGeneration }
    return generations.entries.groupBy { it.value }.toSortedMap().flatMap { (generation, members) ->
        members.sortedBy { id -> persons.first { it.id == id.key }.name }.mapIndexed { index, entry ->
            TreeNodePosition(entry.key, 80f + index * 190f, 80f + (generation - (generations.values.minOrNull() ?: 0)) * 150f, generation)
        }
    }
}
