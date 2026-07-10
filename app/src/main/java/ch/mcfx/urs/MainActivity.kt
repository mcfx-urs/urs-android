package ch.mcfx.urs

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavHostController
import ch.mcfx.urs.navigation.AppNavigation
import ch.mcfx.urs.ui.theme.UrsTheme

class MainActivity : ComponentActivity() {

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
