package cn.jiayi.familymemory.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.jiayi.familymemory.data.core.PersonInput
import cn.jiayi.familymemory.data.local.PersonEntity
import cn.jiayi.familymemory.data.local.RecordEntity
import cn.jiayi.familymemory.data.local.RelationshipEntity

@Composable
fun PeopleListScreen(
    persons: List<PersonEntity>,
    onOpenPerson: (String) -> Unit,
    onAddPerson: (PersonInput) -> Unit,
    onShowTree: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val filtered = persons.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.nickname.contains(query.trim(), true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { adding = true }, modifier = Modifier.weight(1f)) { Text("添加人物") }
            OutlinedButton(onClick = onShowTree, modifier = Modifier.weight(1f)) { Text("查看家族树") }
        }
        OutlinedTextField(query, { query = it }, label = { Text("搜索姓名或小名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (filtered.isEmpty()) {
            Text(if (persons.isEmpty()) "还没有人物，先添加第一位家人。" else "没有找到匹配的人物。", modifier = Modifier.padding(top = 28.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { person ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenPerson(person.id) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(person.name, style = MaterialTheme.typography.titleLarge)
                                Text(personSummary(person), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (person.isSelf) Text("本人", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    if (adding) PersonEditorDialog(null, onDismiss = { adding = false }) { onAddPerson(it); adding = false }
}

@Composable
fun PersonDetailScreen(
    person: PersonEntity,
    allPersons: List<PersonEntity>,
    relationships: List<RelationshipEntity>,
    records: List<RecordEntity>,
    onBack: () -> Unit,
    onUpdate: (PersonInput) -> Unit,
    onAddRelationship: (String, String) -> Unit,
    onOpenRecord: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var relationshipDialog by remember { mutableStateOf(false) }
    val related = relationships.filter { it.personAId == person.id || it.personBId == person.id }
    val personRecords = records.filter { person.id in it.personIds }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) { Text("返回") }
                Button(onClick = { editing = true }) { Text("编辑资料") }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(person.name, style = MaterialTheme.typography.headlineMedium)
                    if (person.nickname.isNotBlank()) Text("小名：${person.nickname}")
                    Text(personSummary(person))
                    if (person.birthPlace.isNotBlank()) Text("出生地：${person.birthPlace}")
                    if (person.ancestralHome.isNotBlank()) Text("籍贯：${person.ancestralHome}")
                    if (person.biography.isNotBlank()) Text(person.biography)
                    if (person.notes.isNotBlank()) Text("备注：${person.notes}")
                    Text(if (person.verificationStatus == "confirmed_fact") "已确认资料" else "资料待确认", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("人物关系", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { relationshipDialog = true }) { Text("添加关系") }
            }
        }
        if (related.isEmpty()) item { Text("尚未记录人物关系") }
        items(related, key = { it.id }) { relation ->
            val otherId = if (relation.personAId == person.id) relation.personBId else relation.personAId
            val other = allPersons.firstOrNull { it.id == otherId }
            Card(Modifier.fillMaxWidth()) { Text("${relationLabel(relation.relationType)} · ${other?.name ?: "未知人物"}", Modifier.padding(14.dp)) }
        }
        item { Text("相关记录", style = MaterialTheme.typography.titleLarge) }
        if (personRecords.isEmpty()) item { Text("还没有关联记录") }
        items(personRecords, key = { it.id }) { record ->
            Card(Modifier.fillMaxWidth().clickable { onOpenRecord(record.id) }) {
                Column(Modifier.padding(14.dp)) { Text(record.title, fontWeight = FontWeight.SemiBold); Text(record.originalText, maxLines = 2) }
            }
        }
    }
    if (editing) PersonEditorDialog(person, onDismiss = { editing = false }) { onUpdate(it); editing = false }
    if (relationshipDialog) RelationshipDialog(person, allPersons, onDismiss = { relationshipDialog = false }) { otherId, type ->
        onAddRelationship(otherId, type); relationshipDialog = false
    }
}

@Composable
private fun PersonEditorDialog(person: PersonEntity?, onDismiss: () -> Unit, onSave: (PersonInput) -> Unit) {
    var name by rememberSaveable(person?.id) { mutableStateOf(person?.name.orEmpty()) }
    var nickname by rememberSaveable(person?.id) { mutableStateOf(person?.nickname.orEmpty()) }
    var birthYear by rememberSaveable(person?.id) { mutableStateOf(person?.birthYear?.toString().orEmpty()) }
    var deathYear by rememberSaveable(person?.id) { mutableStateOf(person?.deathYear?.toString().orEmpty()) }
    var birthPlace by rememberSaveable(person?.id) { mutableStateOf(person?.birthPlace.orEmpty()) }
    var ancestralHome by rememberSaveable(person?.id) { mutableStateOf(person?.ancestralHome.orEmpty()) }
    var biography by rememberSaveable(person?.id) { mutableStateOf(person?.biography.orEmpty()) }
    var isSelf by rememberSaveable(person?.id) { mutableStateOf(person?.isSelf ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (person == null) "添加人物" else "编辑人物资料") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text("姓名（必填）") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(nickname, { nickname = it }, label = { Text("小名") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(birthYear, { birthYear = it.filter(Char::isDigit).take(4) }, label = { Text("出生年份") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(deathYear, { deathYear = it.filter(Char::isDigit).take(4) }, label = { Text("去世年份") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(birthPlace, { birthPlace = it }, label = { Text("出生地") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(ancestralHome, { ancestralHome = it }, label = { Text("籍贯") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(biography, { biography = it }, label = { Text("简介") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(isSelf, { isSelf = it }); Text("这是我本人") } }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onSave(PersonInput(name, nickname, birthYear = birthYear.toIntOrNull(), deathYear = deathYear.toIntOrNull(), birthPlace = birthPlace, ancestralHome = ancestralHome, biography = biography, notes = person?.notes.orEmpty(), isSelf = isSelf)) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RelationshipDialog(person: PersonEntity, persons: List<PersonEntity>, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    val candidates = persons.filter { it.id != person.id }
    var selectedId by remember { mutableStateOf(candidates.firstOrNull()?.id.orEmpty()) }
    var type by remember { mutableStateOf("spouse") }
    val types = listOf("father", "mother", "spouse", "former_spouse", "sibling", "child", "adoptive_parent", "step_parent", "guardian", "other")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("为 ${person.name} 添加关系") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("选择另一位人物", fontWeight = FontWeight.SemiBold) }
                items(candidates) { candidate ->
                    OutlinedButton(onClick = { selectedId = candidate.id }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selectedId == candidate.id) "✓ ${candidate.name}" else candidate.name)
                    }
                }
                item { Text("关系类型", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)) }
                items(types) { value ->
                    TextButton(onClick = { type = value }, modifier = Modifier.fillMaxWidth()) { Text(if (type == value) "✓ ${relationLabel(value)}" else relationLabel(value)) }
                }
            }
        },
        confirmButton = { Button(enabled = selectedId.isNotBlank(), onClick = { onSave(selectedId, type) }) { Text("保存关系") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun personSummary(person: PersonEntity): String {
    val years = listOfNotNull(person.birthYear?.toString(), person.deathYear?.let { "—$it" }).joinToString("")
    return listOfNotNull(years.ifBlank { null }, person.nickname.takeIf { it.isNotBlank() }?.let { "小名 $it" }).joinToString(" · ").ifBlank { "年份暂未记录" }
}

fun relationLabel(type: String) = when (type) {
    "father" -> "父亲"; "mother" -> "母亲"; "parent" -> "父母"; "child" -> "子女"; "spouse" -> "配偶"
    "former_spouse" -> "前配偶"; "sibling" -> "兄弟姐妹"; "adoptive_parent" -> "收养关系"; "step_parent" -> "继亲关系"
    "guardian" -> "监护关系"; else -> "其他关系"
}
