package cn.jiayi.familymemory.ui.core

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreDataScreen(viewModel: CoreDataViewModel, onBack: () -> Unit, onOpenMedia: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("第二阶段资料测试") }, navigationIcon = { OutlinedButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("本地优先资料库", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("人物 ${state.persons.size} · 关系 ${state.relationshipCount} · 记录 ${state.records.size} · 标签 ${state.tagCount} · 待同步 ${state.pendingCount}")
            if (state.busy) CircularProgressIndicator()
            state.message?.let { Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::sync, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("立即同步") }
                OutlinedButton(onClick = viewModel::loadDemo, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("载入演示家族") }
            }
            Button(onClick = onOpenMedia, modifier = Modifier.fillMaxWidth()) { Text("进入第三阶段媒体测试") }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("新增人物（可断网测试）", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(state.personName, viewModel::updatePersonName, label = { Text("姓名") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = viewModel::addPerson, enabled = !state.busy && state.personName.isNotBlank()) { Text("保存到手机") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("新增文字记录", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(state.recordTitle, viewModel::updateRecordTitle, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(state.recordText, viewModel::updateRecordText, label = { Text("原始文字") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Button(onClick = viewModel::addRecord, enabled = !state.busy && state.recordTitle.isNotBlank()) { Text("保存到手机") }
                }
            }

            if (state.persons.isNotEmpty()) {
                Text("人物", style = MaterialTheme.typography.titleLarge)
                state.persons.forEach { Text("• ${it.name}${if (it.isSelf) "（本人）" else ""}") }
            }
            if (state.records.isNotEmpty()) {
                Text("记录", style = MaterialTheme.typography.titleLarge)
                state.records.forEach { Text("• ${it.title}") }
            }
            OutlinedButton(onClick = viewModel::clearLocal, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("仅清空手机本地测试数据")
            }
        }
    }
}
