package cn.jiayi.familymemory.ui.main

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.jiayi.familymemory.data.core.RecordInput
import cn.jiayi.familymemory.data.local.MediaEntity
import cn.jiayi.familymemory.data.local.PersonEntity
import cn.jiayi.familymemory.data.local.RecordDraftEntity
import cn.jiayi.familymemory.data.local.RecordEntity
import cn.jiayi.familymemory.data.local.RecordTagEntity
import cn.jiayi.familymemory.data.local.TagEntity
import java.io.File
import java.util.Calendar

@Composable
fun RecordCreateScreen(
    persons: List<PersonEntity>,
    tags: List<TagEntity>,
    draft: RecordDraftEntity?,
    onSaveDraft: (RecordDraftEntity) -> Unit,
    onClearDraft: () -> Unit,
    onSave: (RecordInput) -> Unit,
    onOpenMedia: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var recordType by rememberSaveable { mutableStateOf("text") }
    var personIds by remember { mutableStateOf(emptySet<String>()) }
    var tagIds by remember { mutableStateOf(emptySet<String>()) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(draft?.updatedAt) {
        if (draft != null) {
            title = draft.title; text = draft.originalText; year = draft.occurredYear; location = draft.locationText; personIds = draft.personIds.toSet()
        }
        initialized = true
    }
    LaunchedEffect(title, text, year, location, personIds, initialized) {
        if (initialized && listOf(title, text, year, location).any { it.isNotBlank() }) {
            onSaveDraft(RecordDraftEntity(title = title, originalText = text, occurredYear = year, locationText = location, personIds = personIds.toList()))
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("留下这段记忆", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("先写最重要的内容，人物和时间可以稍后补充。输入会自动保存为草稿。")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("text" to "写一段", "image" to "照片", "audio" to "说一段", "video" to "视频").forEach { (value, label) ->
                OutlinedButton(onClick = { if (value == "text") recordType = value else onOpenMedia() }, modifier = Modifier.weight(1f)) {
                    Text(if (recordType == value) "✓$label" else label)
                }
            }
        }
        OutlinedTextField(title, { title = it }, label = { Text("标题（必填）") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(text, { text = it }, label = { Text("原始文字") }, minLines = 6, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("发生年份") }, modifier = Modifier.weight(1f))
            OutlinedTextField(location, { location = it }, label = { Text("地点") }, modifier = Modifier.weight(1f))
        }
        if (persons.isNotEmpty()) {
            Text("关联人物", style = MaterialTheme.typography.titleMedium)
            persons.forEach { person ->
                Row(Modifier.fillMaxWidth().clickable { personIds = personIds.toggle(person.id) }) {
                    Checkbox(person.id in personIds, { personIds = personIds.toggle(person.id) }); Text(person.name, Modifier.padding(top = 12.dp))
                }
            }
        }
        if (tags.isNotEmpty()) {
            Text("标签", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag -> OutlinedButton(onClick = { tagIds = tagIds.toggle(tag.id) }) { Text(if (tag.id in tagIds) "✓ ${tag.name}" else tag.name) } }
            }
        }
        Button(
            enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth(),
            onClick = {
                onSave(
                    RecordInput(
                        title = title, text = text, occurredAt = year.toIntOrNull()?.let(::startOfYear),
                        datePrecision = if (year.isBlank()) "unknown" else "year", locationText = location,
                        personIds = personIds.toList(), recordType = recordType, tagIds = tagIds.toList(),
                    ),
                )
                title = ""; text = ""; year = ""; location = ""; personIds = emptySet(); tagIds = emptySet(); onClearDraft()
            },
        ) { Text("确认并保存记录") }
        if (draft != null) OutlinedButton(onClick = { title = ""; text = ""; year = ""; location = ""; personIds = emptySet(); onClearDraft() }, modifier = Modifier.fillMaxWidth()) { Text("放弃当前草稿") }
    }
}

@Composable
fun RecordDetailScreen(
    record: RecordEntity,
    persons: List<PersonEntity>,
    tags: List<TagEntity>,
    recordTags: List<RecordTagEntity>,
    media: List<MediaEntity>,
    onBack: () -> Unit,
    onOpenAi: () -> Unit,
    aiSummary: String? = null,
) {
    val relatedPeople = persons.filter { it.id in record.personIds }
    val relatedTagIds = recordTags.filter { it.recordId == record.id }.map { it.tagId }.toSet()
    val relatedTags = tags.filter { it.id in relatedTagIds }
    val attachments = media.filter { it.recordId == record.id }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Button(onClick = onOpenAi) { Text("让 AI 整理") }
        }
        Text(record.title, style = MaterialTheme.typography.headlineMedium)
        Text(recordDateText(record), color = MaterialTheme.colorScheme.primary)
        if (record.locationText.isNotBlank()) Text("地点：${record.locationText}")
        if (relatedPeople.isNotEmpty()) Text("相关人物：${relatedPeople.joinToString("、") { it.name }}")
        if (relatedTags.isNotEmpty()) Text("标签：${relatedTags.joinToString("、") { it.name }}")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("原始记录", fontWeight = FontWeight.SemiBold)
                Text(record.originalText.ifBlank { "未填写文字" }, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (!aiSummary.isNullOrBlank()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("已确认的 AI 摘要", fontWeight = FontWeight.SemiBold)
                    Text(aiSummary, modifier = Modifier.padding(top = 8.dp))
                    Text("原始记录未被修改", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text("附件（${attachments.size}）", style = MaterialTheme.typography.titleLarge)
        if (attachments.isEmpty()) Text("这条记录没有附件")
        attachments.forEach { attachment ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (attachment.mediaType == "image") LocalImage(attachment.localThumbnailPath ?: attachment.localPath)
                    Text(attachment.originalFilename, fontWeight = FontWeight.SemiBold)
                    Text("${attachment.mediaType} · ${attachment.sizeBytes / 1024} KB · ${if (attachment.uploadStatus == "uploaded") "已上传" else "保存在手机"}")
                }
            }
        }
        Text("资料状态：${if (record.verificationStatus == "confirmed_fact") "已确认事实" else "尚未确认"}")
    }
}

@Composable
fun TimelineScreen(
    records: List<RecordEntity>,
    persons: List<PersonEntity>,
    tags: List<TagEntity>,
    recordTags: List<RecordTagEntity>,
    onOpenRecord: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var personId by rememberSaveable { mutableStateOf<String?>(null) }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var tagId by rememberSaveable { mutableStateOf<String?>(null) }
    var uncertainOnly by rememberSaveable { mutableStateOf(false) }
    val filtered = records.filter { record ->
        (query.isBlank() || record.title.contains(query, true) || record.originalText.contains(query, true)) &&
            (personId == null || personId in record.personIds) && (type == null || record.recordType == type) &&
            (tagId == null || recordTags.any { it.recordId == record.id && it.tagId == tagId }) &&
            (!uncertainOnly || record.occurredAt == null)
    }
    val grouped = filtered.groupBy { it.occurredAt?.let(::yearOf) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(query, { query = it }, label = { Text("搜索标题或正文") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { uncertainOnly = !uncertainOnly }) { Text(if (uncertainOnly) "✓ 时间未知" else "时间未知") }
            OutlinedButton(onClick = { personId = cycle(persons.map { it.id }, personId) }) { Text(personId?.let { id -> persons.firstOrNull { it.id == id }?.name } ?: "全部人物") }
            OutlinedButton(onClick = { type = cycle(listOf("text", "image", "audio", "video"), type) }) { Text(type?.let(::recordTypeLabel) ?: "全部类型") }
            if (tags.isNotEmpty()) OutlinedButton(onClick = { tagId = cycle(tags.map { it.id }, tagId) }) { Text(tagId?.let { id -> tags.firstOrNull { it.id == id }?.name } ?: "全部标签") }
        }
        if (filtered.isEmpty()) Text("没有符合条件的记录", modifier = Modifier.padding(top = 24.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            grouped.keys.sortedWith(compareByDescending<Int?> { it ?: Int.MIN_VALUE }).forEach { year ->
                Text(year?.toString() ?: "时间未确定", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                grouped[year].orEmpty().forEach { record ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenRecord(record.id) }) {
                        Column(Modifier.padding(14.dp)) { Text(record.title, fontWeight = FontWeight.SemiBold); Text(record.originalText, maxLines = 2) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalImage(path: String) {
    val bitmap = remember(path) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
    if (bitmap != null) Image(bitmap, null, Modifier.fillMaxWidth().heightIn(max = 260.dp), contentScale = ContentScale.Fit)
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
private fun <T> cycle(values: List<T>, current: T?): T? = if (values.isEmpty()) null else if (current == null) values.first() else values.getOrNull(values.indexOf(current) + 1)
private fun startOfYear(year: Int): Long = Calendar.getInstance().apply {
    clear(); set(Calendar.YEAR, year); set(Calendar.MONTH, Calendar.JANUARY); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 12)
}.timeInMillis
private fun yearOf(epoch: Long): Int = Calendar.getInstance().apply { timeInMillis = epoch }.get(Calendar.YEAR)
private fun recordDateText(record: RecordEntity) = record.occurredAt?.let { "${yearOf(it)} 年 · ${if (record.datePrecision == "year") "年份记录" else "日期记录"}" } ?: "发生时间尚未确定"
private fun recordTypeLabel(value: String) = when (value) { "image" -> "图片"; "audio" -> "录音"; "video" -> "视频"; else -> "文字" }
