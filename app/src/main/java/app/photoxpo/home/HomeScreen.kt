package app.photoxpo.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.photoxpo.backup.BackupStatus
import app.photoxpo.backup.BackupUiState
import app.photoxpo.R
import app.photoxpo.data.DriveStatus
import app.photoxpo.ui.components.RowCard
import app.photoxpo.ui.theme.PhotoxpoTheme
import app.photoxpo.ui.theme.figuresInSans

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    model: HomeUiModel,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {},
    onChooseDrive: () -> Unit = {},
    onUseThisDriveInstead: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    bottomClearance: Dp = 0.dp,
) {
    var hideMismatch by remember { mutableStateOf(false) }
    LaunchedEffect(model.showDriveMismatch) {
        if (!model.showDriveMismatch) hideMismatch = false
    }
    val canStart = model.primaryPauses || model.driveConnected

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp + bottomClearance),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_logo_full),
                    contentDescription = "Photoxpo",
                    modifier = Modifier
                        .height(40.dp)
                        .semantics { heading() },
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    DrivePill(model)
                }
            }
            model.accessWarning?.let { warning ->
                AccessWarningBanner(
                    warning = warning,
                    onOpenSettings = onOpenSettings,
                )
            }
            Column(
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = figuresInSans(model.statusSentence, figureSize = 30.sp),
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                model.heroSubline?.let { line ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (model.allBackedUp) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check_circle),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 1.dp).size(22.dp),
                            )
                        }
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (hasStateCard(model)) StateCard(model)
            if (model.allBackedUp) {
                // Nothing to start until a new photo shows up.
            } else if (model.primaryPauses) {
                FilledTonalButton(
                    onClick = onPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    PrimaryLabel(model.primaryLabel)
                }
            } else {
                Button(
                    onClick = if (model.driveFull) onChooseDrive else onPrimary,
                    enabled = canStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    PrimaryLabel(if (canStart) model.primaryLabel else "Plug in your drive")
                }
                if (!model.driveConnected) {
                    TextButton(
                        onClick = onChooseDrive,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .heightIn(min = 48.dp),
                    ) {
                        Text("Reconnect")
                    }
                }
            }
            RowCard(
                title = "Backup history",
                supporting = model.historyLines.joinToString("\n"),
                icon = painterResource(R.drawable.ic_history),
                onClick = onOpenHistory,
            )
            model.pullHint?.let { hint ->
                Text(
                    text = hint,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (model.showDriveMismatch && !hideMismatch) {
        AlertDialog(
            onDismissRequest = { hideMismatch = true },
            title = { Text("Different Archive Drive", style = MaterialTheme.typography.headlineMedium) },
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

/** Everything about what is happening now, or what is about to, lives in one card. */
private fun hasStateCard(model: HomeUiModel): Boolean =
    model.showProgress || model.newTitle != null || model.spaceWarning != null || model.longRunWarning != null

@Composable
private fun DrivePill(model: HomeUiModel) {
    val colors = MaterialTheme.colorScheme
    val connected = model.driveConnected
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (connected) Color.Transparent else colors.tertiaryContainer)
            .padding(horizontal = if (connected) 0.dp else 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (connected) R.drawable.ic_check_circle else R.drawable.ic_schedule),
            contentDescription = null,
            tint = if (connected) colors.primary else colors.onTertiaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = model.driveChipLabel,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (connected) colors.onSurfaceVariant else colors.onTertiaryContainer,
        )
    }
}

@Composable
private fun StateCard(model: HomeUiModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (model.primaryPauses) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = model.progressText,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    model.paceElapsed?.let { CardDetail(it) }
                }
                Progress(model)
                model.paceLeft?.let { CardDetail(it) }
                model.unplugReassurance?.let { CardDetail(it) }
            } else {
                if (model.newTitle != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = model.newTitle, style = MaterialTheme.typography.titleMedium)
                        model.newDetail?.let { CardDetail(it) }
                    }
                }
                if (model.showProgress) {
                    Progress(model)
                    CardDetail(model.progressText)
                    model.lastRunLine?.let { CardDetail(it) }
                }
            }
            model.spaceWarning?.let { WarningNote(it) }
            model.longRunWarning?.let { WarningNote(it) }
        }
    }
}

@Composable
private fun CardDetail(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WarningNote(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(top = 1.dp).size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun AccessWarningBanner(
    warning: HomeAccessWarning,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = warning.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = warning.message,
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = onOpenSettings) {
                Text(warning.actionLabel)
            }
        }
    }
}

@Composable
private fun Progress(model: HomeUiModel) {
    if (!model.showProgress) return
    val fraction = model.progressFraction
    val modifier = Modifier
        .fillMaxWidth()
        .height(8.dp)
        .semantics { stateDescription = model.progressText }
    if (fraction != null) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = modifier,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    } else if (model.primaryPauses) {
        LinearProgressIndicator(
            modifier = modifier,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
        )
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
        ),
        nowEpochMillis = 5_000L + 60_000L,
    )
}
