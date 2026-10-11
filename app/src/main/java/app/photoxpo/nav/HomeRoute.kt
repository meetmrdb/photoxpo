package app.photoxpo.nav

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.photoxpo.backup.BackupController
import app.photoxpo.backup.BatteryOptimization
import app.photoxpo.backup.NotificationPermission
import app.photoxpo.drive.rememberDrivePicker
import app.photoxpo.home.HomeScreen
import app.photoxpo.home.HomeViewModel

@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {},
    onChooseDrive: (() -> Unit)? = null,
    onUseThisDriveInstead: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val controller = remember { BackupController.get(context) }
    val viewModel: HomeViewModel = viewModel {
        HomeViewModel(controller)
    }
    LaunchedEffect(Unit) { controller.refreshDrive() }
    val pickDrive = rememberDrivePicker { controller.refreshDrive() }
    val model by viewModel.model.collectAsStateWithLifecycle()
    var showBatteryDialog by remember { mutableStateOf(false) }
    var waitingForPermission by remember { mutableStateOf(false) }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        showBatteryDialog = false
        waitingForPermission = false
        viewModel.start()
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        NotificationPermission.requestContract(),
    ) {
        waitingForPermission = false
        if (BatteryOptimization.isIgnoring(context)) {
            viewModel.start()
        } else {
            showBatteryDialog = true
        }
    }

    HomeScreen(
        model = model,
        onPrimary = {
            if (model.primaryPauses) {
                viewModel.pause()
            } else if (!waitingForPermission && !showBatteryDialog) {
                waitingForPermission = true
                notificationLauncher.launch(Unit)
            }
        },
        onOpenHistory = onOpenHistory,
        onChooseDrive = onChooseDrive ?: pickDrive,
        onUseThisDriveInstead = onUseThisDriveInstead ?: controller::useThisDriveInstead,
        modifier = modifier,
    )

    if (showBatteryDialog) {
        BatteryExemptionDialog(
            onAllow = {
                showBatteryDialog = false
                try {
                    batteryLauncher.launch(BatteryOptimization.requestIgnoreIntent(context))
                } catch (_: ActivityNotFoundException) {
                    waitingForPermission = false
                    viewModel.start()
                } catch (_: SecurityException) {
                    waitingForPermission = false
                    viewModel.start()
                }
            },
            onDecline = {
                showBatteryDialog = false
                waitingForPermission = false
                viewModel.start()
            },
        )
    }
}

@Composable
private fun BatteryExemptionDialog(
    onAllow: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Let the Backup run finish") },
        text = {
            Text(
                "A Backup run can take hours, including while the screen is off. " +
                    "Allow Photoxpo under Never sleeping apps so the phone does not stop it.",
            )
        },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text("Allow")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Not now")
            }
        },
    )
}
