package cn.jiayi.familymemory.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.jiayi.familymemory.BuildConfig
import cn.jiayi.familymemory.data.connection.ConnectionPreferences
import cn.jiayi.familymemory.data.connection.ConnectionRepository
import cn.jiayi.familymemory.data.connection.ConnectionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionUiState(
    val serverUrl: String = BuildConfig.DEFAULT_SERVER_URL,
    val pairingToken: String = "",
    val isConnecting: Boolean = false,
    val isPaired: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val repository: ConnectionRepository,
    private val preferences: ConnectionPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = preferences.connection.first()
            _uiState.update {
                it.copy(
                    serverUrl = saved.serverUrl.ifBlank { it.serverUrl },
                    isPaired = saved.hasAccessToken,
                    message = if (saved.hasAccessToken) "已保存本地服务配对" else null,
                )
            }
        }
    }

    fun updateServerUrl(value: String) = _uiState.update { it.copy(serverUrl = value, message = null) }

    fun updatePairingToken(value: String) = _uiState.update { it.copy(pairingToken = value, message = null) }

    fun connect() {
        if (_uiState.value.isConnecting) return
        val current = _uiState.value
        _uiState.update { it.copy(isConnecting = true, message = null, isError = false) }
        viewModelScope.launch {
            when (val result = repository.connect(current.serverUrl, current.pairingToken)) {
                is ConnectionResult.Success -> _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isPaired = true,
                        pairingToken = "",
                        message = "连接成功，服务版本 ${result.version}",
                    )
                }
                is ConnectionResult.Failure -> _uiState.update {
                    it.copy(isConnecting = false, message = result.message, isError = true)
                }
            }
        }
    }

    fun clearPairing() {
        viewModelScope.launch {
            repository.clear()
            _uiState.value = ConnectionUiState(message = "已清除本机配对信息")
        }
    }
}
