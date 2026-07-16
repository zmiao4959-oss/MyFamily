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
import cn.jiayi.familymemory.ui.core.CoreDataScreen
import cn.jiayi.familymemory.ui.core.CoreDataViewModel
import cn.jiayi.familymemory.ui.connection.ConnectionScreen
import cn.jiayi.familymemory.ui.connection.ConnectionViewModel
import cn.jiayi.familymemory.ui.theme.FamilyMemoryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ConnectionViewModel by viewModels()
    private val coreDataViewModel: CoreDataViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FamilyMemoryTheme {
                var showCoreData by rememberSaveable { mutableStateOf(false) }
                if (showCoreData) {
                    CoreDataScreen(viewModel = coreDataViewModel, onBack = { showCoreData = false })
                } else {
                    ConnectionScreen(viewModel = viewModel, onOpenData = { showCoreData = true })
                }
            }
        }
    }
}
