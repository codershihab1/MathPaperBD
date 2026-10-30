package bd.mathpaper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: VM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Fx.init(this)
        setContent {
            AppTheme {
                // Back peels off one layer at a time: dialog -> selection / tool -> document
                BackHandler(vm.doc != null) {
                    when {
                        vm.editId != null -> vm.endEdit()
                        vm.sel != null || vm.tool != Tool.SELECT -> { vm.sel = null; vm.tool = Tool.SELECT }
                        else -> vm.close()
                    }
                }
                App(vm)
            }
        }
    }

    override fun onStop() { super.onStop(); vm.flush() }
}
