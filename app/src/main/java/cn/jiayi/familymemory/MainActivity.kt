package cn.jiayi.familymemory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cn.jiayi.familymemory.ui.media.MediaTestScreen
import cn.jiayi.familymemory.ui.media.MediaViewModel
import cn.jiayi.familymemory.ui.main.MainApp
import cn.jiayi.familymemory.ui.main.MainViewModel
import cn.jiayi.familymemory.ui.ai.AiViewModel
import cn.jiayi.familymemory.ui.search.SearchViewModel
import cn.jiayi.familymemory.ui.connection.ConnectionScreen
import cn.jiayi.familymemory.ui.connection.ConnectionViewModel
import cn.jiayi.familymemory.ui.theme.FamilyMemoryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ConnectionViewModel by viewModels()
    private val mediaViewModel: MediaViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private val aiViewModel: AiViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FamilyMemoryTheme {
                var screen by rememberSaveable { mutableStateOf("main") }
                when (screen) {
                    "media" -> MediaTestScreen(viewModel = mediaViewModel, onBack = { screen = "main" })
                    "connection" -> ConnectionScreen(viewModel = viewModel, onOpenData = { screen = "main" })
                    else -> MainApp(viewModel = mainViewModel, aiViewModel = aiViewModel, searchViewModel = searchViewModel, onOpenConnection = { screen = "connection" }, onOpenMedia = { screen = "media" })
                }
            }
        }
    }
}
