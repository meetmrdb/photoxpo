package app.photoxpo.drive

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.photoxpo.backup.BackupSettingsStore
import app.photoxpo.data.DriveStatus
import app.photoxpo.data.probeArchiveDrive
import app.photoxpo.data.takePersistableTreeGrant

sealed interface DrivePickResult {
    data class Chosen(val uri: Uri) : DrivePickResult

    data object ReadOnly : DrivePickResult

    data object Cancelled : DrivePickResult

    data class Failed(val message: String) : DrivePickResult
}

/**
 * Returns a lambda that opens the system tree picker.
 * A Home Reconnect button can call the same lambda.
 */
@Composable
fun rememberDrivePicker(onResult: (DrivePickResult) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            currentOnResult(DrivePickResult.Cancelled)
            return@rememberLauncherForActivityResult
        }
        try {
            takePersistableTreeGrant(context.contentResolver, uri)
        } catch (error: Exception) {
            currentOnResult(DrivePickResult.Failed(error.message ?: MESSAGE_GRANT_FAILED))
            return@rememberLauncherForActivityResult
        }
        val status = try {
            probeArchiveDrive(context, uri)
        } catch (error: Exception) {
            currentOnResult(DrivePickResult.Failed(error.message ?: MESSAGE_ARCHIVE_DRIVE_NOT_FOUND))
            return@rememberLauncherForActivityResult
        }
        when (status) {
            DriveStatus.ReadOnly -> currentOnResult(DrivePickResult.ReadOnly)
            DriveStatus.Absent -> currentOnResult(DrivePickResult.Failed(MESSAGE_ARCHIVE_DRIVE_NOT_FOUND))
            DriveStatus.Ready -> {
                BackupSettingsStore(context).setTreeUri(uri)
                currentOnResult(DrivePickResult.Chosen(uri))
            }
        }
    }
    return { launcher.launch(null) }
}

private const val MESSAGE_ARCHIVE_DRIVE_NOT_FOUND = "Archive Drive not found."

private const val MESSAGE_GRANT_FAILED = "Could not keep access to the Archive Drive."
