package app.photoxpo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.photoxpo.ui.HomeScreen
import app.photoxpo.ui.theme.PhotoxpoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoxpoTheme {
                HomeScreen()
            }
        }
    }
}
