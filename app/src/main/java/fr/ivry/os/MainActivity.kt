package fr.ivry.os

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import fr.ivry.os.ui.IvryRoot
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.extras?.let { vm.applyExtras(it) }

        // Affichage permanent : FLAG_KEEP_SCREEN_ON n'agit que tant que la fenêtre est visible.
        // La mise en veille par la télécommande du téléviseur reste prioritaire.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.settings.prefs.map { it.permanentDisplay }.distinctUntilChanged().collect { on ->
                    if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
        setContent { IvryRoot(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.extras?.let { vm.applyExtras(it) }
    }

    override fun onStart() {
        super.onStart()
        vm.engine.setForeground(true)
        vm.refreshApps()
    }

    override fun onStop() {
        vm.engine.setForeground(false)
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        vm.engine.onUserInteraction()
        return super.dispatchKeyEvent(event)
    }
}
