package cn.jiayi.familymemory.ui.security

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.jiayi.familymemory.data.security.SecurityRepository
import cn.jiayi.familymemory.network.BackupDto
import cn.jiayi.familymemory.network.DeviceDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SecurityUiState(
    val backups: List<BackupDto> = emptyList(), val devices: List<DeviceDto> = emptyList(),
    val busy: Boolean = false, val message: String? = null, val isError: Boolean = false,
    val oneTimePairingToken: String? = null,
)

@HiltViewModel
class SecurityViewModel @Inject constructor(private val repository: SecurityRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = mutableState.asStateFlow()

    fun refresh() = action(null) {
        val backups = repository.backups()
        val devices = repository.devices()
        mutableState.update { it.copy(backups = backups, devices = devices) }
    }
    fun createBackup(password: String, confirmation: String) {
        if (password.length < 10) return error("备份密码至少 10 位")
        if (password != confirmation) return error("两次输入的备份密码不一致")
        action("加密备份已创建，可导出保存") { repository.createBackup(password); refreshAfterAction() }
    }
    fun restore(id: String, password: String) {
        if (password.length < 10) return error("请输入创建备份时使用的密码")
        action("恢复完成；服务器已先自动创建恢复前安全备份，请返回首页重新同步") { repository.restore(id, password); refreshAfterAction() }
    }
    fun revokeDevice(id: String) = action("设备授权已撤销") { repository.revokeDevice(id); refreshAfterAction() }
    fun regeneratePairingToken() = action(null) {
        val token = repository.regeneratePairingToken()
        mutableState.update { it.copy(oneTimePairingToken = token, message = "新配对令牌只显示这一次，请立即保存") }
    }
    fun dismissToken() = mutableState.update { it.copy(oneTimePairingToken = null) }
    fun download(id: String, destination: Uri) = action("备份文件已导出") { repository.download(id, destination) }

    private suspend fun refreshAfterAction() {
        mutableState.update { it.copy(backups = repository.backups(), devices = repository.devices()) }
    }
    private fun error(message: String) = mutableState.update { it.copy(message = message, isError = true) }
    private fun action(success: String?, block: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, message = null, isError = false) }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { mutableState.update { it.copy(busy = false, message = success ?: it.message, isError = false) } }
                .onFailure { failure -> mutableState.update { it.copy(busy = false, message = failure.message ?: "操作失败", isError = true) } }
        }
    }
}
