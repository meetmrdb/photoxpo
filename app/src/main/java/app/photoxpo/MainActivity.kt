package app.photoxpo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.photoxpo.backup.BackupSettingsStore
import app.photoxpo.nav.PhotoxpoScaffold
import app.photoxpo.ui.theme.PhotoxpoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as PhotoxpoApp).services.photosRepository
        val startOnboarding = !BackupSettingsStore(this).onboardingDone()
        setContent {
            PhotoxpoTheme {
                PhotoxpoScaffold(
                    repository = repository,
                    startOnboarding = startOnboarding,
                )
            }
        }
    }
}
