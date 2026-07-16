package cn.jiayi.familymemory

import android.os.Bundle
import android.os.Build
import android.app.KeyguardManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import cn.jiayi.familymemory.ui.media.MediaTestScreen
import cn.jiayi.familymemory.ui.media.MediaViewModel
import cn.jiayi.familymemory.ui.main.MainApp
import cn.jiayi.familymemory.ui.main.MainViewModel
import cn.jiayi.familymemory.ui.ai.AiViewModel
import cn.jiayi.familymemory.ui.search.SearchViewModel
import cn.jiayi.familymemory.ui.security.SecurityScreen
import cn.jiayi.familymemory.ui.security.SecurityViewModel
import cn.jiayi.familymemory.security.AppLockPreferences
import cn.jiayi.familymemory.ui.connection.ConnectionScreen
import cn.jiayi.familymemory.ui.connection.ConnectionViewModel
import cn.jiayi.familymemory.ui.theme.FamilyMemoryTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: ConnectionViewModel by viewModels()
    private val mediaViewModel: MediaViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private val aiViewModel: AiViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val securityViewModel: SecurityViewModel by viewModels()
    @Inject lateinit var appLockPreferences: AppLockPreferences
    private var unlocked by mutableStateOf(false)
    private var lockEnabled by mutableStateOf(false)
    private var authInProgress = false
    private var pendingExportId: String? = null
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val id = pendingExportId
        pendingExportId = null
        if (uri != null && id != null) securityViewModel.download(id, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockEnabled = appLockPreferences.enabled
        unlocked = !lockEnabled
        enableEdgeToEdge()
        setContent {
            FamilyMemoryTheme {
                if (!unlocked) {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("家忆已锁定")
                        Button(onClick = { authenticate() }, modifier = Modifier.padding(top = 16.dp)) { Text("验证身份") }
                    }
                } else {
                    var screen by rememberSaveable { mutableStateOf("main") }
                    when (screen) {
                        "media" -> MediaTestScreen(viewModel = mediaViewModel, onBack = { screen = "main" })
                        "connection" -> ConnectionScreen(viewModel = viewModel, onOpenData = { screen = "main" })
                        "security" -> SecurityScreen(
                            viewModel = securityViewModel, appLockEnabled = lockEnabled,
                            onAppLockChanged = ::changeAppLock,
                            onExport = { id, filename -> pendingExportId = id; exportLauncher.launch(filename) },
                            onBack = { screen = "main" },
                        )
                        else -> MainApp(viewModel = mainViewModel, aiViewModel = aiViewModel, searchViewModel = searchViewModel, onOpenConnection = { screen = "connection" }, onOpenMedia = { screen = "media" }, onOpenSecurity = { screen = "security" })
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (appLockPreferences.enabled && !unlocked) authenticate()
    }

    override fun onStop() {
        super.onStop()
        if (appLockPreferences.enabled && !isChangingConfigurations) unlocked = false
    }

    private fun changeAppLock(enable: Boolean) {
        authenticate("验证后${if (enable) "启用" else "关闭"} App 锁") {
            appLockPreferences.enabled = enable
            lockEnabled = enable
            unlocked = true
        }
    }

    private fun authenticate(title: String = "解锁家忆", afterSuccess: () -> Unit = {}) {
        if (authInProgress) return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val available = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BiometricManager.from(this).canAuthenticate(authenticators)
            else BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        val legacyDeviceSecure = getSystemService(KeyguardManager::class.java).isDeviceSecure
        if (available != BiometricManager.BIOMETRIC_SUCCESS && Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !legacyDeviceSecure) {
            Toast.makeText(this, "请先在系统设置中配置指纹、面容或锁屏密码", Toast.LENGTH_LONG).show()
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authInProgress = false
                unlocked = true
                afterSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authInProgress = false
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) Toast.makeText(this@MainActivity, errString, Toast.LENGTH_SHORT).show()
            }
        })
        val builder = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle("使用设备身份验证保护家族资料")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) builder.setAllowedAuthenticators(authenticators) else builder.setDeviceCredentialAllowed(true)
        authInProgress = true
        prompt.authenticate(builder.build())
    }
}
