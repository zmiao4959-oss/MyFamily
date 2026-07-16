package cn.jiayi.familymemory.ui.search

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.jiayi.familymemory.data.local.PersonEntity
import cn.jiayi.familymemory.data.search.SearchMode

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    persons: List<PersonEntity>,
    onBack: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var personId by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaType by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.refreshStatus() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text("返回") }
        Text("查找家族资料", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val status = state.status
                Text("搜索索引", fontWeight = FontWeight.SemiBold)
                Text(when {
                    status == null -> "正在读取状态"
                    !status.enabled -> "关键词搜索可用；语义和图片搜索未启用"
                    !status.configured -> "已启用，但电脑端尚未配置火山引擎 Key"
                    else -> "可用 · 已完成 ${status.ready} · 待处理 ${status.pending} · 失败 ${status.failed}"
                })
                Text("启用后，搜索文字或所选图片会由电脑端最小化发送给火山引擎生成向量；原始资料不会被修改。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = viewModel::reindex, enabled = state.status?.enabled == true && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("重新生成搜索索引") }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeButton("关键词", SearchMode.TEXT, state.mode, viewModel::setMode, Modifier.weight(1f))
            ModeButton("语义", SearchMode.SEMANTIC, state.mode, viewModel::setMode, Modifier.weight(1f))
            ModeButton("找图片", SearchMode.IMAGES, state.mode, viewModel::setMode, Modifier.weight(1f))
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), minLines = 2,
            label = { Text(if (state.mode == SearchMode.TEXT) "输入姓名、标题或正文关键词" else "例如：小时候在老房子过春节") },
        )
        Text("人物范围", fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { personId = null }) { Text(if (personId == null) "✓ 全部人物" else "全部人物") }
            persons.forEach { person -> OutlinedButton(onClick = { personId = person.id }) { Text(if (personId == person.id) "✓ ${person.name}" else person.name) } }
        }
        if (state.mode == SearchMode.TEXT) {
            Text("记录类型", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "全部", "image" to "图片", "audio" to "录音", "video" to "视频").forEach { (value, label) ->
                    OutlinedButton(onClick = { mediaType = value }) { Text(if (mediaType == value) "✓ $label" else label) }
                }
            }
        }
        Button(onClick = { viewModel.search(query, personId, if (state.mode == SearchMode.TEXT) mediaType else null) }, enabled = query.isNotBlank() && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("开始搜索") }
        if (state.busy) CircularProgressIndicator()
        state.message?.let { Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        if (state.searched && state.results.isEmpty() && !state.busy) Text("没有找到相关资料。可以换个说法或检查索引状态。")
        state.results.forEach { result ->
            Card(Modifier.fillMaxWidth().clickable {
                result.recordId?.let(onOpenRecord) ?: result.personId?.let(onOpenPerson)
            }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(result.title, fontWeight = FontWeight.SemiBold)
                    Text(when (result.resultType) { "image" -> "相关图片"; "person" -> "人物资料"; else -> "记录" }, color = MaterialTheme.colorScheme.primary)
                    if (result.snippet.isNotBlank()) Text(result.snippet, maxLines = 3)
                    if (state.mode != SearchMode.TEXT) Text("相关度 ${(result.score * 100).toInt().coerceIn(0, 100)}%", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, mode: SearchMode, selected: SearchMode, onSelect: (SearchMode) -> Unit, modifier: Modifier) {
    if (mode == selected) Button(onClick = { onSelect(mode) }, modifier = modifier) { Text(label) }
    else OutlinedButton(onClick = { onSelect(mode) }, modifier = modifier) { Text(label) }
}
