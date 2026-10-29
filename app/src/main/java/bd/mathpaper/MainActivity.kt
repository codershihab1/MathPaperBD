package bd.mathpaper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private val vm: VM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Fx.init(this)
        setContent {
            AppTheme {
                BackHandler(vm.doc != null) { vm.close() }
                App(vm)
            }
        }
    }

    override fun onStop() { super.onStop(); vm.flush() }
}
