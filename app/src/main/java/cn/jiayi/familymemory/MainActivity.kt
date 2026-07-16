package cn.jiayi.familymemory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import cn.jiayi.familymemory.ui.connection.ConnectionScreen
import cn.jiayi.familymemory.ui.connection.ConnectionViewModel
import cn.jiayi.familymemory.ui.theme.FamilyMemoryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ConnectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FamilyMemoryTheme {
                ConnectionScreen(viewModel = viewModel)
            }
        }
    }
}
