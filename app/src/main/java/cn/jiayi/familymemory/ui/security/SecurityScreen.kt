package cn.jiayi.familymemory.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.jiayi.familymemory.network.BackupDto
import cn.jiayi.familymemory.R
import cn.jiayi.familymemory.ui.components.EmptyState
import cn.jiayi.familymemory.ui.components.StatusBanner

@Composable
fun SecurityScreen(
    viewModel: SecurityViewModel,
    appLockEnabled: Boolean,
    onAppLockChanged: (Boolean) -> Unit,
    onExport: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var restoreBackup by remember { mutableStateOf<BackupDto?>(null) }
    LaunchedEffect(Unit) { viewModel.refresh() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← 返回设置") }
        Text("安全与备份", style = MaterialTheme.typography.headlineSmall)
        if (state.busy) CircularProgressIndicator()
        state.message?.let { StatusBanner(it, state.isError, onRetry = if (state.isError) viewModel::refresh else null) }
        if (state.message?.startsWith("加密备份已创建") == true) {
            Image(
                painterResource(R.drawable.backup_complete), contentDescription = null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 56.dp), contentScale = ContentScale.Fit,
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text("App 锁"); Text("离开 App 后再次进入需用指纹、面容或锁屏密码", style = MaterialTheme.typography.bodySmall) }
                Switch(checked = appLockEnabled, onCheckedChange = onAppLockChanged)
            }
        }

        Text("加密备份", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(password, { password = it }, label = { Text("备份密码（至少 10 位）") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(confirmation, { confirmation = it }, label = { Text("再次输入密码") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button(onClick = { viewModel.createBackup(password, confirmation) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("创建加密备份") }
        Text("密码不会上传或保存，忘记后无法恢复备份。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (state.backups.isEmpty() && !state.busy) EmptyState(
            image = R.drawable.backup_complete,
            title = "还没有加密备份",
            description = "创建后可导出到移动硬盘或你信任的位置。密码不会被保存。",
        )
        state.backups.forEach { backup ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(backup.filename)
                    Text("${backup.createdAt.take(10)} · ${backup.personsCount} 人 · ${backup.recordsCount} 条记录 · ${backup.mediaCount} 个媒体")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onExport(backup.id, backup.filename) }) { Text("导出") }
                        OutlinedButton(onClick = { restoreBackup = backup }) { Text("恢复") }
                    }
                }
            }
        }

        Text("已授权设备", style = MaterialTheme.typography.titleLarge)
        state.devices.forEach { device ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text(device.deviceName + if (device.current) "（当前设备）" else ""); Text(if (device.revokedAt == null) "有效" else "已撤销", style = MaterialTheme.typography.bodySmall) }
                    if (!device.current && device.revokedAt == null) TextButton(onClick = { viewModel.revokeDevice(device.id) }) { Text("撤销") }
                }
            }
        }
        OutlinedButton(onClick = viewModel::regeneratePairingToken, modifier = Modifier.fillMaxWidth()) { Text("生成新的配对令牌") }
    }
    restoreBackup?.let { backup ->
        AlertDialog(
            onDismissRequest = { restoreBackup = null }, title = { Text("确认恢复备份？") },
            text = { Text("服务器会先自动备份当前资料，再恢复 ${backup.createdAt.take(10)} 的内容。恢复后请返回首页重新同步。") },
            confirmButton = { Button(onClick = { restoreBackup = null; viewModel.restore(backup.id, password) }) { Text("确认恢复") } },
            dismissButton = { TextButton(onClick = { restoreBackup = null }) { Text("取消") } },
        )
    }
    state.oneTimePairingToken?.let { token ->
        AlertDialog(
            onDismissRequest = viewModel::dismissToken, title = { Text("新配对令牌（仅显示一次）") },
            text = { SelectionContainer { Text(token) } }, confirmButton = { Button(onClick = viewModel::dismissToken) { Text("我已保存") } },
        )
    }
}
