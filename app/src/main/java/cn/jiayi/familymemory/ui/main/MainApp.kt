package cn.jiayi.familymemory.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import cn.jiayi.familymemory.ui.ai.AiScreen
import cn.jiayi.familymemory.ui.ai.AiViewModel
import cn.jiayi.familymemory.ui.search.SearchScreen
import cn.jiayi.familymemory.ui.search.SearchViewModel

private enum class MainTab(val label: String, val mark: String) {
    HOME("首页", "家"), FAMILY("家族", "人"), RECORDS("记录", "记"), TIMELINE("时间线", "时"), SETTINGS("我的", "我")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel, aiViewModel: AiViewModel, searchViewModel: SearchViewModel, onOpenConnection: () -> Unit, onOpenMedia: () -> Unit, onOpenSecurity: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val aiState by aiViewModel.uiState.collectAsStateWithLifecycle()
    var tabName by rememberSaveable { mutableStateOf(MainTab.HOME.name) }
    var selectedPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    var showTree by rememberSaveable { mutableStateOf(false) }
    var showAi by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var aiRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    var aiPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    val tab = MainTab.valueOf(tabName)
    val title = when {
        showAi -> "AI 助手"
        showSearch -> "搜索"
        selectedPersonId != null -> "人物详情"
        selectedRecordId != null -> "记录详情"
        showTree -> "家族树"
        else -> tab.label
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("家忆 · $title") }) },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item && selectedPersonId == null && selectedRecordId == null && !showTree && !showAi && !showSearch,
                        onClick = { tabName = item.name; selectedPersonId = null; selectedRecordId = null; showTree = false; showAi = false; showSearch = false },
                        icon = { Text(item.mark, fontWeight = FontWeight.Bold) }, label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) CircularProgressIndicator(Modifier.padding(horizontal = 16.dp))
            state.message?.let { message ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { viewModel.dismissMessage() }) {
                    Text(message, Modifier.padding(12.dp), color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
            when {
                showSearch -> SearchScreen(
                    searchViewModel, state.persons,
                    onBack = { showSearch = false },
                    onOpenRecord = { id -> showSearch = false; selectedRecordId = id },
                    onOpenPerson = { id -> showSearch = false; selectedPersonId = id },
                )
                showAi -> AiScreen(
                    aiViewModel, state.persons, state.records, aiRecordId, aiPersonId,
                    onBack = { showAi = false; aiRecordId = null; aiPersonId = null },
                    onOpenRecord = { id -> showAi = false; aiRecordId = null; aiPersonId = null; selectedRecordId = id },
                    onOpenMedia = onOpenMedia,
                )
                selectedPersonId != null -> {
                    val person = state.persons.firstOrNull { it.id == selectedPersonId }
                    LaunchedEffect(person?.id) { if (person != null) aiViewModel.loadLatest(null, person.id) }
                    val biography = aiState.artifacts.firstOrNull {
                        it.personId == person?.id && it.artifactType == "biography_draft" && it.userConfirmed
                    }?.outputJson?.get("biography")?.toString()
                    if (person == null) selectedPersonId = null else PersonDetailScreen(
                        person, state.persons, state.relationships, state.records,
                        onBack = { selectedPersonId = null },
                        onUpdate = { viewModel.updatePerson(person, it) },
                        onAddRelationship = { otherId, type -> viewModel.addRelationship(person.id, otherId, type) },
                        onOpenRecord = { selectedRecordId = it; selectedPersonId = null },
                        onOpenAi = { aiPersonId = person.id; showAi = true },
                        aiBiography = biography,
                    )
                }
                selectedRecordId != null -> {
                    val record = state.records.firstOrNull { it.id == selectedRecordId }
                    LaunchedEffect(record?.id) { if (record != null) aiViewModel.loadLatest(record.id, null) }
                    val summary = aiState.artifacts.firstOrNull {
                        it.recordId == record?.id && it.artifactType == "organized_record" && it.userConfirmed
                    }?.outputJson?.get("summary")?.toString()
                    if (record == null) selectedRecordId = null else RecordDetailScreen(
                        record, state.persons, state.tags, state.recordTags, state.media,
                        onBack = { selectedRecordId = null },
                        onOpenAi = { aiRecordId = record.id; showAi = true },
                        aiSummary = summary,
                    )
                }
                showTree -> FamilyTreeScreen(state.persons, state.relationships, onOpenPerson = { selectedPersonId = it; showTree = false }, onShowList = { showTree = false })
                tab == MainTab.HOME -> HomeScreen(
                    state,
                    onAddPerson = { tabName = MainTab.FAMILY.name },
                    onAddRecord = { tabName = MainTab.RECORDS.name },
                    onOpenMedia = onOpenMedia,
                    onOpenPerson = { selectedPersonId = it },
                    onOpenRecord = { selectedRecordId = it },
                    onOpenAi = { showAi = true },
                    onOpenSearch = { showSearch = true },
                )
                tab == MainTab.FAMILY -> PeopleListScreen(state.persons, { selectedPersonId = it }, { viewModel.addPerson(it) }, { showTree = true })
                tab == MainTab.RECORDS -> RecordCreateScreen(
                    state.persons, state.tags, state.draft, viewModel::saveDraft, viewModel::clearDraft,
                    onSave = { viewModel.addRecord(it) { id -> selectedRecordId = id } }, onOpenMedia = onOpenMedia,
                )
                tab == MainTab.TIMELINE -> TimelineScreen(state.records, state.persons, state.tags, state.recordTags) { selectedRecordId = it }
                else -> SettingsScreen(state, viewModel::sync, viewModel::loadDemo, onOpenConnection, onOpenMedia, { showAi = true }, { showSearch = true }, onOpenSecurity, viewModel::clearLocal)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: MainUiState,
    onAddPerson: () -> Unit,
    onAddRecord: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenRecord: (String) -> Unit,
    onOpenAi: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("把家人的故事，安静地留在自己手中。", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("家庭资料库", style = MaterialTheme.typography.titleLarge)
                Text("${state.persons.size} 位人物 · ${state.records.size} 条记录 · ${state.media.size} 个媒体附件")
                Text(if (state.hasAccessToken) "本地服务已配对${if (state.pendingCount > 0) " · ${state.pendingCount} 条待同步" else ""}" else "当前离线使用，资料先保存在手机", color = MaterialTheme.colorScheme.primary)
            }
        }
        Text("快速记录", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddRecord, modifier = Modifier.weight(1f)) { Text("写一段") }
            Button(onClick = onOpenMedia, modifier = Modifier.weight(1f)) { Text("说一段") }
            Button(onClick = onOpenMedia, modifier = Modifier.weight(1f)) { Text("拍一张") }
        }
        OutlinedButton(onClick = onAddPerson, modifier = Modifier.fillMaxWidth()) { Text("添加一位家人") }
        if (state.draft != null) {
            Card(Modifier.fillMaxWidth().clickable { onAddRecord() }) {
                Column(Modifier.padding(16.dp)) { Text("继续未完成记录", fontWeight = FontWeight.SemiBold); Text(state.draft.title.ifBlank { "尚未填写标题" }) }
            }
        }
        RecentPeople(state.persons.take(4), onOpenPerson)
        RecentRecords(state.records.take(5), onOpenRecord)
        Card(Modifier.fillMaxWidth().clickable { onOpenAi() }) {
            Column(Modifier.padding(14.dp)) {
                Text("AI 助手", fontWeight = FontWeight.SemiBold)
                Text("整理记录、生成小传和采访问题；所有结果都需要你确认。")
            }
        }
        Card(Modifier.fillMaxWidth().clickable { onOpenSearch() }) {
            Column(Modifier.padding(14.dp)) {
                Text("搜索家族资料", fontWeight = FontWeight.SemiBold)
                Text("按关键词、自然语言或图片内容查找人物与记录。")
            }
        }
    }
}

@Composable
private fun RecentPeople(persons: List<PersonEntity>, onOpen: (String) -> Unit) {
    Text("最近人物", style = MaterialTheme.typography.titleLarge)
    if (persons.isEmpty()) Text("还没有人物")
    persons.forEach { person ->
        Card(Modifier.fillMaxWidth().clickable { onOpen(person.id) }) { Text("${person.name}${if (person.isSelf) "（本人）" else ""}", Modifier.padding(14.dp)) }
    }
}

@Composable
private fun RecentRecords(records: List<RecordEntity>, onOpen: (String) -> Unit) {
    Text("最近记录", style = MaterialTheme.typography.titleLarge)
    if (records.isEmpty()) Text("还没有记录")
    records.forEach { record ->
        Card(Modifier.fillMaxWidth().clickable { onOpen(record.id) }) {
            Column(Modifier.padding(14.dp)) { Text(record.title, fontWeight = FontWeight.SemiBold); Text(record.originalText, maxLines = 2) }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: MainUiState,
    onSync: () -> Unit,
    onLoadDemo: () -> Unit,
    onConnection: () -> Unit,
    onMedia: () -> Unit,
    onAi: () -> Unit,
    onSearch: () -> Unit,
    onSecurity: () -> Unit,
    onClearLocal: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("本地服务器", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.hasAccessToken) "已配对" else "未配对，可离线使用", color = MaterialTheme.colorScheme.primary)
                Text(state.serverUrl.ifBlank { "尚未保存服务器地址" })
                Button(onClick = onConnection, modifier = Modifier.fillMaxWidth()) { Text("服务器连接设置") }
                Button(onClick = onSync, enabled = state.hasAccessToken && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("立即同步${if (state.pendingCount > 0) "（${state.pendingCount}）" else ""}") }
            }
        }
        Text("资料与调试", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = onLoadDemo, enabled = state.hasAccessToken && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("载入虚构演示家族") }
        OutlinedButton(onClick = onMedia, modifier = Modifier.fillMaxWidth()) { Text("媒体与录音管理") }
        OutlinedButton(onClick = onAi, modifier = Modifier.fillMaxWidth()) { Text("AI 设置与资料助手") }
        OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) { Text("搜索与索引管理") }
        OutlinedButton(onClick = onSecurity, modifier = Modifier.fillMaxWidth()) { Text("安全、备份与授权设备") }
        OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text("清空手机本地资料") }
        Text("备份使用 AES-256 加密；App 锁使用系统指纹、面容或锁屏密码。当前版本不含广告、行为分析或云端账号。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("家忆 0.7.0 · 第七阶段安全与备份", style = MaterialTheme.typography.labelLarge)
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false }, title = { Text("确认清空手机资料？") },
        text = { Text("这不会删除服务器上的资料。重新同步后可恢复服务器内容，但尚未上传的本地资料会丢失。") },
        confirmButton = { Button(onClick = { confirmClear = false; onClearLocal() }) { Text("确认清空") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
    )
}
