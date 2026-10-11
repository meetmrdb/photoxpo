package app.photoxpo.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.photoxpo.backup.BackupStatus
import app.photoxpo.backup.BackupUiState
import app.photoxpo.data.DriveStatus
import app.photoxpo.ui.theme.PhotoxpoTheme

@Composable
fun HomeScreen(
    model: HomeUiModel,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {},
    onChooseDrive: () -> Unit = {},
    onUseThisDriveInstead: () -> Unit = {},
) {
    var hideMismatch by remember { mutableStateOf(false) }
    LaunchedEffect(model.showDriveMismatch) {
        if (!model.showDriveMismatch) hideMismatch = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        model.offlineBanner?.let { banner ->
            OfflineBanner(
                banner = banner,
                onReconnect = onChooseDrive,
            )
        }
        Text(
            text = model.statusSentence,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = model.progressText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = model.lastBackedUpLine,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Progress(model)
        if (model.primaryPauses) {
            FilledTonalButton(
                onClick = onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                PrimaryLabel(model.primaryLabel)
            }
        } else {
            Button(
                onClick = onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                PrimaryLabel(model.primaryLabel)
            }
        }
        BackupHistoryRow(onClick = onOpenHistory)
    }

    if (model.showDriveMismatch && !hideMismatch) {
        AlertDialog(
            onDismissRequest = { hideMismatch = true },
            title = { Text("Different Archive Drive") },
            text = {
                Text(
                    "This Archive Drive is not the one this phone remembers. " +
                        "If you use this drive instead, Photoxpo starts over on it. " +
                        "Originals already Backed up stay on the other Archive Drive.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        hideMismatch = true
                        onUseThisDriveInstead()
                    },
                ) {
                    Text("Use this drive instead")
                }
            },
            dismissButton = {
                TextButton(onClick = { hideMismatch = true }) {
                    Text("Not now")
                }
            },
        )
    }
}

@Composable
private fun OfflineBanner(
    banner: HomeOfflineBanner,
    onReconnect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = banner.message,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = banner.lastBackedUpLine,
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = onReconnect) {
                Text("Reconnect")
            }
        }
    }
}

@Composable
private fun Progress(model: HomeUiModel) {
    val fraction = model.progressFraction
    if (fraction != null) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
    } else if (model.primaryPauses) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PrimaryLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BackupHistoryRow(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = "Backup history",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "By month and by backup",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "Home", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun HomeScreenPreview() {
    PreviewFrame {
        HomeScreen(model = previewOffline(), onPrimary = {})
    }
}

@Preview(name = "Large text", showBackground = true, fontScale = 2.0f, widthDp = 360, heightDp = 1400)
@Composable
private fun HomeScreenLargeTextPreview() {
    PreviewFrame {
        HomeScreen(model = previewOffline(), onPrimary = {})
    }
}

@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun HomeScreenDarkPreview() {
    PhotoxpoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            HomeScreen(model = previewRunning(), onPrimary = {})
        }
    }
}

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    PhotoxpoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

private fun previewOffline(): HomeUiModel {
    return homeUiModel(
        state = BackupUiState(
            status = BackupStatus.Idle,
            backedUpCount = 1204,
            totalCount = 1346,
            lastBackedUpAtEpochMillis = 1_000L,
            driveStatus = DriveStatus.Absent,
            fileCap = 200,
        ),
        nowEpochMillis = 1_000L + 3 * 60 * 60 * 1000L,
    )
}

private fun previewRunning(): HomeUiModel {
    return homeUiModel(
        state = BackupUiState(
            status = BackupStatus.Running,
            backedUpCount = 48,
            totalCount = 200,
            lastBackedUpAtEpochMillis = 5_000L,
            driveStatus = DriveStatus.Ready,
            fileCap = 200,
        ),
        nowEpochMillis = 5_000L + 60_000L,
    )
}
