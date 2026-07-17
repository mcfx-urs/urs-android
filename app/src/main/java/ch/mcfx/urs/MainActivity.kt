package ch.mcfx.urs

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import ch.mcfx.urs.navigation.AppNavigation
import ch.mcfx.urs.ui.theme.UrsTheme

// FragmentActivity (a ComponentActivity subclass, so Compose's setContent
// still works unchanged) instead of plain ComponentActivity — BiometricPrompt
// requires one for its constructor (see ch.mcfx.urs.auth.BiometricUnlockScreen).
class MainActivity : FragmentActivity() {

    // Needed so a notification tap can deep-link while the app is already
    // running (onNewIntent) — launchMode="singleTop" in the manifest keeps
    // this the same Activity instance rather than creating a new one.
    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UrsTheme {
                AppNavigation(
                    onNavControllerReady = { controller ->
                        navController = controller
                        controller.handleDeepLink(intent)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navController?.handleDeepLink(intent)
    }
}
