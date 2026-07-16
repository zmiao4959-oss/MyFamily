package cn.jiayi.familymemory.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import cn.jiayi.familymemory.R
import cn.jiayi.familymemory.data.local.PersonEntity
import cn.jiayi.familymemory.data.local.RelationshipEntity
import cn.jiayi.familymemory.ui.components.EmptyState
import cn.jiayi.familymemory.ui.components.PersonAvatar
import kotlin.math.roundToInt

@Composable
fun FamilyTreeScreen(
    persons: List<PersonEntity>,
    relationships: List<RelationshipEntity>,
    onOpenPerson: (String) -> Unit,
    onShowList: () -> Unit,
) {
    var centerId by remember(persons) { mutableStateOf(persons.firstOrNull { it.isSelf }?.id ?: persons.firstOrNull()?.id) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val positions = remember(persons, relationships, centerId) { calculateTreeLayout(persons, relationships, centerId) }
    val map = positions.associateBy { it.personId }
    val density = LocalDensity.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            OutlinedButton(onClick = onShowList) { Text("人物列表") }
            Button(
                onClick = {
                    centerId = persons.firstOrNull { it.isSelf }?.id ?: persons.firstOrNull()?.id
                    scale = 1f
                    pan = Offset.Zero
                },
                modifier = Modifier.padding(start = 10.dp),
            ) { Text("回到本人") }
        }
        if (persons.isEmpty()) {
            EmptyState(
                image = R.drawable.empty_people,
                title = "家族树还没有人物",
                description = "先添加本人或一位家人，家族树就会从这里生长。",
                actionLabel = "返回人物列表",
                onAction = onShowList,
            )
            return@Column
        }
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoom, _ ->
                        scale = (scale * zoom).coerceIn(.55f, 2.5f)
                        pan += panChange
                    }
                },
        ) {
            Image(
                painterResource(R.drawable.family_tree_background), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .42f,
            )
            Box(
                Modifier.size(1400.dp, 1000.dp).graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y
                },
            ) {
                val lineColor = MaterialTheme.colorScheme.outline
                Canvas(Modifier.fillMaxSize()) {
                    relationships.forEach { relation ->
                        val a = map[relation.personAId] ?: return@forEach
                        val b = map[relation.personBId] ?: return@forEach
                        drawLine(
                            lineColor,
                            Offset((a.x + 75f).dp.toPx(), (a.y + 46f).dp.toPx()),
                            Offset((b.x + 75f).dp.toPx(), (b.y + 46f).dp.toPx()),
                            strokeWidth = 3.dp.toPx(),
                        )
                    }
                }
                positions.forEach { node ->
                    val person = persons.first { it.id == node.personId }
                    Card(
                        Modifier.offset {
                            with(density) { IntOffset(node.x.dp.toPx().roundToInt(), node.y.dp.toPx().roundToInt()) }
                        }.size(150.dp, 92.dp)
                            .clickable { centerId = person.id; onOpenPerson(person.id) },
                    ) {
                        Row(Modifier.padding(9.dp)) {
                            PersonAvatar(person, size = 42.dp, modifier = Modifier.padding(end = 8.dp))
                            Column {
                                Text(person.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(yearRange(person), style = MaterialTheme.typography.bodySmall)
                                if (person.isSelf) Text("本人", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun yearRange(person: PersonEntity): String = when {
    person.birthYear != null && person.deathYear != null -> "${person.birthYear}—${person.deathYear}"
    person.birthYear != null -> "${person.birthYear}—"
    person.deathYear != null -> "—${person.deathYear}"
    else -> "年份未知"
}
