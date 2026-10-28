package app.safevault.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.safevault.SafeVaultApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** FragmentActivity (not plain ComponentActivity) because BiometricPrompt requires it. */
class MainActivity : FragmentActivity() {
    private val vaultApp get() = application as SafeVaultApp

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Blocks screenshots/screen recording and blanks the app in the recents list.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent { SafeVaultTheme { AppRoot() } }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(1000)
                    vaultApp.session.checkIdle()
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        vaultApp.session.touch()
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        vaultApp.session.touch()
        return super.dispatchKeyEvent(event)
    }
}
