package bd.mathpaper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    private val vm: VM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fx.init(this)
        setContent {
            MaterialTheme {
                BackHandler(vm.doc != null) { vm.close() }
                App(vm)
            }
        }
    }

    override fun onStop() { super.onStop(); vm.flush() }
}
