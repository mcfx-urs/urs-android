package ch.mcfx.urs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ch.mcfx.urs.navigation.AppNavigation
import ch.mcfx.urs.ui.theme.UrsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UrsTheme {
                AppNavigation()
            }
        }
    }
}
