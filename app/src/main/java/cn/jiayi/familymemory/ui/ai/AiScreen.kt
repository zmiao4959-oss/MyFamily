package cn.jiayi.familymemory.ui.ai

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.jiayi.familymemory.data.local.PersonEntity
import cn.jiayi.familymemory.data.local.RecordEntity
import cn.jiayi.familymemory.network.AiArtifactDto

@Composable
fun AiScreen(
    viewModel: AiViewModel,
    persons: List<PersonEntity>,
    records: List<RecordEntity>,
    initialRecordId: String?,
    initialPersonId: String?,
    onBack: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onOpenMedia: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var recordId by rememberSaveable(initialRecordId) { mutableStateOf(initialRecordId ?: records.firstOrNull()?.id) }
    var personId by rememberSaveable(initialPersonId) { mutableStateOf(initialPersonId ?: persons.firstOrNull()?.id) }
    var question by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(initialRecordId, initialPersonId) {
        viewModel.refreshStatus()
        viewModel.loadLatest(initialRecordId, initialPersonId)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text("返回") }
        Text("AI 助手", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("发送前请确认", fontWeight = FontWeight.SemiBold)
                Text("所选记录文字或人物相关资料将由你电脑上的本地服务最小化整理后发送给 DeepSeek。不会发送媒体原文件、API Key 或整个资料库。")
                val status = state.status
                Text(
                    when {
                        status == null -> "正在读取 AI 状态"
                        !status.enabled -> "AI 已关闭，基础功能不受影响"
                        !status.configured -> "AI 已开启，但电脑端尚未配置 DeepSeek Key"
                        else -> "已配置 ${status.provider} · ${status.fastModel} / ${status.mainModel}"
                    },
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (state.busy) CircularProgressIndicator()
        state.message?.let { Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        if (state.failedJobId != null) Button(onClick = viewModel::retry, enabled = !state.busy) { Text("重试失败任务") }

        Text("整理一条记录", style = MaterialTheme.typography.titleLarge)
        Selector(records, recordId, { it.id }, { it.title }) { recordId = it }
        Button(onClick = { recordId?.let(viewModel::organize) }, enabled = recordId != null && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("生成标题、摘要、标签和待确认问题") }

        Text("人物资料辅助", style = MaterialTheme.typography.titleLarge)
        Selector(persons, personId, { it.id }, { it.name }) { personId = it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { personId?.let(viewModel::biography) }, enabled = personId != null && !state.busy, modifier = Modifier.weight(1f)) { Text("小传草稿") }
            Button(onClick = { personId?.let(viewModel::interview) }, enabled = personId != null && !state.busy, modifier = Modifier.weight(1f)) { Text("采访问题") }
        }

        Text("资料问答", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(question, { question = it }, label = { Text("例如：奶奶提到春节的记录有哪些？") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Button(onClick = { viewModel.ask(question, personId); question = "" }, enabled = question.length >= 2 && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("检索资料后回答") }

        state.artifact?.let {
            ArtifactCard(
                it, onConfirm = { viewModel.confirm(true) }, onReject = { viewModel.confirm(false) },
                onOpenRecord = onOpenRecord, onOpenMedia = onOpenMedia,
            )
        }
    }
}

@Composable
private fun <T> Selector(items: List<T>, selectedId: String?, id: (T) -> String, label: (T) -> String, onSelect: (String) -> Unit) {
    if (items.isEmpty()) { Text("暂无可选资料"); return }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item -> OutlinedButton(onClick = { onSelect(id(item)) }) { Text(if (id(item) == selectedId) "✓ ${label(item)}" else label(item)) } }
    }
}

@Composable
private fun ArtifactCard(artifact: AiArtifactDto, onConfirm: () -> Unit, onReject: () -> Unit, onOpenRecord: (String) -> Unit, onOpenMedia: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(artifactTitle(artifact.artifactType), style = MaterialTheme.typography.titleLarge)
            Text("AI 建议 · ${artifact.model} · ${if (artifact.userConfirmed) "已由你确认" else "尚未确认"}", color = MaterialTheme.colorScheme.primary)
            if (!artifact.userConfirmed && artifact.artifactType != "answer" && artifact.status != "rejected") {
                Text("确认表示采纳并保存这份 AI 建议；原始记录不会被覆盖。")
            }
            when (artifact.artifactType) {
                "organized_record" -> {
                    Field("建议标题", artifact.outputJson.text("title"))
                    Field("摘要", artifact.outputJson.text("summary"))
                    Field("标签", artifact.outputJson.stringList("tags").joinToString("、"))
                    Field("待确认问题", artifact.outputJson.objectTextList("questions_for_user").joinToString("\n"))
                }
                "biography_draft" -> {
                    Field("小传草稿", artifact.outputJson.text("biography"))
                    Field("不确定内容", artifact.outputJson.stringList("uncertainties").joinToString("\n"))
                    SourceRecords(artifact.outputJson.stringList("source_record_ids"), onOpenRecord)
                }
                "interview_questions" -> {
                    Field("采访问题", artifact.outputJson.nestedTextList("questions", "question").joinToString("\n"))
                    Button(onClick = onOpenMedia, modifier = Modifier.fillMaxWidth()) { Text("开始录音回答") }
                }
                "answer" -> {
                    Field("回答", artifact.outputJson.text("answer"))
                    SourceRecords(artifact.outputJson.stringList("source_record_ids"), onOpenRecord)
                }
            }
            if (!artifact.userConfirmed && artifact.artifactType != "answer" && artifact.status != "rejected") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) { Text("忽略建议") }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("采纳并保存") }
                }
            }
        }
    }
}

@Composable private fun Field(label: String, value: String) { if (value.isNotBlank()) Column { Text(label, fontWeight = FontWeight.SemiBold); Text(value) } }
@Composable private fun SourceRecords(ids: List<String>, onOpen: (String) -> Unit) {
    if (ids.isEmpty()) return
    Text("来源记录", fontWeight = FontWeight.SemiBold)
    ids.forEach { id -> OutlinedButton(onClick = { onOpen(id) }, modifier = Modifier.fillMaxWidth()) { Text("查看原记录 ${id.take(8)}") } }
}
private fun artifactTitle(type: String) = when (type) { "organized_record" -> "记录整理结果"; "biography_draft" -> "人物小传草稿"; "interview_questions" -> "采访问题"; "answer" -> "资料问答"; else -> "AI 建议" }
private fun Map<String, Any?>.text(key: String) = this[key]?.toString().orEmpty()
private fun Map<String, Any?>.stringList(key: String) = (this[key] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
private fun Map<String, Any?>.objectTextList(key: String) = stringList(key)
private fun Map<String, Any?>.nestedTextList(key: String, field: String) = (this[key] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get(field)?.toString() }.orEmpty()
