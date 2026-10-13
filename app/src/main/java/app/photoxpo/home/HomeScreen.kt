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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
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
import app.photoxpo.ui.components.ChipTone
import app.photoxpo.ui.components.RowCard
import app.photoxpo.ui.components.StatusChip
import app.photoxpo.ui.theme.PhotoxpoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    model: HomeUiModel,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {},
    onOpenPhotos: () -> Unit = {},
    onChooseDrive: () -> Unit = {},
    onUseThisDriveInstead: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    var hideMismatch by remember { mutableStateOf(false) }
    LaunchedEffect(model.showDriveMismatch) {
        if (!model.showDriveMismatch) hideMismatch = false
    }
    val canStart = model.primaryPauses || model.driveConnected

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 20.dp)
                .semantics { heading() },
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_logo_full),
                contentDescription = "Photoxpo",
                modifier = Modifier.height(40.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            model.accessWarning?.let { warning ->
                AccessWarningBanner(
                    warning = warning,
                    onOpenSettings = onOpenSettings,
                )
            }
            StatusChip(
                label = model.driveChipLabel,
                icon = painterResource(
                    if (model.driveConnected) R.drawable.ic_check_circle else R.drawable.ic_schedule,
                ),
                tone = if (model.driveConnected) ChipTone.Ok else ChipTone.Warn,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (model.allBackedUp) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_circle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Text(
                    text = figuresInSans(model.statusSentence),
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (model.showProgress) {
                    Text(
                        text = model.progressText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (model.allBackedUp) {
                    Text(
                        text = "Everything on your phone is safe on your Archive Drive. Pull down to check for new photos.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                model.newLine?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                model.unplugReassurance?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                model.paceLine?.let { pace ->
                    Text(
                        text = pace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Progress(model)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_schedule),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Column {
                    Text(
                        text = model.lastBackedUpLine,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    model.lastBackedUpDate?.let { date ->
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    model.lastRunLine?.let { took ->
                        Text(
                            text = took,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            model.spaceLine?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            model.spaceWarning?.let { warning ->
                StatusChip(
                    label = warning,
                    icon = painterResource(R.drawable.ic_warning),
                    tone = ChipTone.Warn,
                )
            }
            model.longRunWarning?.let { warning ->
                StatusChip(
                    label = warning,
                    icon = painterResource(R.drawable.ic_warning),
                    tone = ChipTone.Warn,
                )
            }
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
                title = "Your photos",
                supporting = "Browse your Previews",
                icon = painterResource(R.drawable.ic_folder_photos),
                onClick = onOpenPhotos,
            )
            RowCard(
                title = "Backup history",
                supporting = "By month and by backup",
                icon = painterResource(R.drawable.ic_history),
                onClick = onOpenHistory,
            )
        }
        }
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

// Instrument Serif's "1" has no flag and reads as a lowercase L, so counts stay in the sans.
private fun figuresInSans(sentence: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    Regex("[0-9][0-9,]*").findAll(sentence).forEach { match ->
        append(sentence.substring(last, match.range.first))
        withStyle(
            SpanStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 30.sp),
        ) { append(match.value) }
        last = match.range.last + 1
    }
    append(sentence.substring(last))
}
