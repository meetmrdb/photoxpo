package app.photoxpo.history

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.photoxpo.R
import app.photoxpo.backup.BackupSettingsStore
import app.photoxpo.core.ArchiveDrive
import app.photoxpo.core.BackupRunSummary
import app.photoxpo.data.DriveStatus
import app.photoxpo.data.SafArchiveDrive
import app.photoxpo.data.probeArchiveDrive
import app.photoxpo.photos.PhotosDatabase
import app.photoxpo.ui.theme.PhotoxpoTheme
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(appContext) {
        BackupHistoryRepository(
            dao = PhotosDatabase.get(appContext).backupRunDao(),
            settings = BackupSettingsStore(appContext),
        )
    }
    val zone = remember { ZoneId.systemDefault() }
    val locale = remember { Locale.getDefault() }
    var grouping by remember { mutableStateOf(HistoryGrouping.ByMonth) }
    var summaries by remember { mutableStateOf<List<BackupRunSummary>>(emptyList()) }
    var driveAttached by remember { mutableStateOf(false) }
    var lastConnectedAtEpochMillis by remember { mutableStateOf<Long?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, repository) {
        var job: Job? = null
        fun reload() {
            job?.cancel()
            job = scope.launch {
                val cached = repository.cached()
                summaries = cached
                lastConnectedAtEpochMillis = repository.lastConnectedAtEpochMillis()
                loaded = true
                val drive = withContext(Dispatchers.IO) { openAttachedArchiveDrive(appContext) }
                if (drive == null) {
                    driveAttached = false
                    return@launch
                }
                try {
                    summaries = repository.refresh(drive)
                    lastConnectedAtEpochMillis = repository.lastConnectedAtEpochMillis()
                    driveAttached = true
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    driveAttached = false
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reload()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            reload()
        }
        onDispose {
            job?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!loaded) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
        return
    }
    val model = historyUiModel(
        summaries = summaries,
        grouping = grouping,
        driveAttached = driveAttached,
        lastConnectedAtEpochMillis = lastConnectedAtEpochMillis,
        zone = zone,
        locale = locale,
    )
    HistoryScreen(
        model = model,
        onGrouping = { grouping = it },
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    model: HistoryUiModel,
    onGrouping: (HistoryGrouping) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Backup history") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                model.asOfLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GroupingToggle(grouping = model.grouping, onGrouping = onGrouping)
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (model.grouping) {
                    HistoryGrouping.ByMonth -> {
                        if (model.sections.isEmpty()) {
                            item(key = "empty") { EmptyHistory() }
                        } else {
                            for (section in model.sections) {
                                item(key = "month-${section.monthLabel}") {
                                    MonthHeading(section)
                                }
                                items(
                                    items = section.rows,
                                    key = { row -> row.runFile },
                                ) { row ->
                                    BackupRow(row)
                                }
                            }
                        }
                    }
                    HistoryGrouping.ByBackup -> {
                        if (model.rows.isEmpty()) {
                            item(key = "empty") { EmptyHistory() }
                        } else {
                            items(
                                items = model.rows,
                                key = { row -> row.runFile },
                            ) { row ->
                                BackupRow(row)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupingToggle(
    grouping: HistoryGrouping,
    onGrouping: (HistoryGrouping) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = grouping == HistoryGrouping.ByMonth,
            onClick = { onGrouping(HistoryGrouping.ByMonth) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text("By month", style = MaterialTheme.typography.titleMedium)
        }
        SegmentedButton(
            selected = grouping == HistoryGrouping.ByBackup,
            onClick = { onGrouping(HistoryGrouping.ByBackup) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text("By backup", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MonthHeading(section: HistoryMonthSection) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = section.monthLabel,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = section.totalsLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BackupRow(row: HistoryBackupRow) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = row.dateLabel,
                style = MaterialTheme.typography.titleMedium,
            )
            row.stoppedEarlyLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                text = row.filesLabel,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = row.sizeLabel,
                style = MaterialTheme.typography.bodyLarge,
            )
            row.speedLabel?.let { speed ->
                Text(
                    text = speed,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Text(
        text = "No Backup runs yet",
        style = MaterialTheme.typography.titleMedium,
    )
}

private fun openAttachedArchiveDrive(context: Context): ArchiveDrive? {
    val uri = BackupSettingsStore(context).treeUri() ?: return null
    val status = try {
        probeArchiveDrive(context, uri)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        return null
    }
    if (status != DriveStatus.Ready) return null
    return SafArchiveDrive(context, uri)
}

@Preview(name = "Backup history", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun HistoryScreenPreview() {
    PreviewFrame {
        HistoryScreen(model = previewModel(), onGrouping = {}, onBack = {})
    }
}

@Preview(name = "Large text", showBackground = true, fontScale = 2.0f, widthDp = 360, heightDp = 1600)
@Composable
private fun HistoryScreenLargeTextPreview() {
    PreviewFrame {
        HistoryScreen(model = previewModel(), onGrouping = {}, onBack = {})
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
private fun HistoryScreenDarkPreview() {
    PhotoxpoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            HistoryScreen(
                model = previewModel(
                    grouping = HistoryGrouping.ByBackup,
                    driveAttached = true,
                ),
                onGrouping = {},
                onBack = {},
            )
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

private fun previewModel(
    grouping: HistoryGrouping = HistoryGrouping.ByMonth,
    driveAttached: Boolean = false,
): HistoryUiModel {
    return historyUiModel(
        summaries = listOf(
            BackupRunSummary(
                runFile = "20261011T183000000Z.jsonl",
                startedAt = "2026-10-11T18:30:00.000Z",
                endedAt = "2026-10-11T18:32:00.000Z",
                fileCount = 48,
                bytes = 120_000_000,
                checked = true,
                elapsedMs = 120_000,
                mbPerSecond = 1.25,
                interrupted = false,
            ),
            BackupRunSummary(
                runFile = "20261001T080000000Z.jsonl",
                startedAt = "2026-10-01T08:00:00.000Z",
                endedAt = null,
                fileCount = 12,
                bytes = 4_000_000,
                checked = false,
                elapsedMs = null,
                mbPerSecond = null,
                interrupted = true,
            ),
        ),
        grouping = grouping,
        driveAttached = driveAttached,
        lastConnectedAtEpochMillis = Instant.parse("2026-10-12T03:06:00Z").toEpochMilli(),
        zone = ZoneOffset.UTC,
        locale = Locale.ENGLISH,
    )
}
