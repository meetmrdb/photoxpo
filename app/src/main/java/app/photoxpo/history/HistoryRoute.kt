package app.photoxpo.history

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import app.photoxpo.photos.groupByMonth
import app.photoxpo.photos.PreviewImage
import app.photoxpo.photos.PreviewBitmapCache
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.photoxpo.R
import app.photoxpo.backup.BackupSettingsStore
import app.photoxpo.core.ArchiveDrive
import app.photoxpo.core.BackupRunSummary
import app.photoxpo.core.VerifyEverything
import app.photoxpo.data.MediaStoreSource
import app.photoxpo.data.DriveStatus
import app.photoxpo.data.SafArchiveDrive
import app.photoxpo.data.probeArchiveDrive
import app.photoxpo.photos.PhotosDatabase
import app.photoxpo.ui.theme.PhotoxpoTheme
import app.photoxpo.ui.theme.figuresInSans
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
    var strips by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val previewCache = remember { PreviewBitmapCache() }
    var verifying by remember { mutableStateOf(false) }
    var verifyMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, repository) {
        var job: Job? = null
        fun reload() {
            job?.cancel()
            job = scope.launch {
                val cached = repository.cached()
                summaries = cached
                strips = withContext(Dispatchers.IO) { monthStrips(appContext, zone, locale) }
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
        strips = strips,
        previewCache = previewCache,
        verify = VerifyEverythingUi(
            enabled = driveAttached && !verifying,
            running = verifying,
            message = verifyMessage,
            onVerify = {
                verifying = true
                verifyMessage = null
                scope.launch {
                    verifyMessage = try {
                        withContext(Dispatchers.IO) {
                            val drive = openAttachedArchiveDrive(appContext)
                                ?: return@withContext "Connect your drive to verify everything"
                            val report = VerifyEverything(
                                MediaStoreSource(appContext),
                                drive,
                                BackupSettingsStore(appContext),
                            ).run()
                            verifyEverythingMessage(report)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        "Could not verify. Check your drive and try again"
                    } finally {
                        verifying = false
                    }
                }
            },
        ),
    )
}

/** State for the "Verify everything" action in Backup history. */
data class VerifyEverythingUi(
    val enabled: Boolean,
    val running: Boolean,
    val message: String?,
    val onVerify: () -> Unit,
)

/** Up to four Preview hashes per month label, newest first, for the month thumbnail strips. */
private suspend fun monthStrips(
    context: Context,
    zone: ZoneId,
    locale: Locale,
): Map<String, List<String>> {
    val dao = PhotosDatabase.get(context).photoDao()
    val count = dao.count()
    val key = Triple(count, zone, locale)
    stripsCache?.let { (cachedKey, strips) -> if (cachedKey == key) return strips }
    val strips = groupByMonth(dao.all(), zone, locale)
        .associate { group -> group.label to group.photos.take(4).map { it.hash } }
    stripsCache = key to strips
    return strips
}

/** Last month strips, reused while the number of photos is unchanged so revisits skip the full table read. */
@Volatile
private var stripsCache: Pair<Triple<Int, ZoneId, Locale>, Map<String, List<String>>>? = null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    model: HistoryUiModel,
    onGrouping: (HistoryGrouping) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    strips: Map<String, List<String>> = emptyMap(),
    previewCache: PreviewBitmapCache? = null,
    verify: VerifyEverythingUi? = null,
) {
    var open by rememberSaveable { mutableStateOf(setOf<String>()) }
    val toggle: (String) -> Unit = { run -> open = if (run in open) open - run else open + run }
    val empty = if (model.grouping == HistoryGrouping.ByMonth) model.sections.isEmpty() else model.rows.isEmpty()
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusStrip(model, verify)
                verify?.message?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!empty) GroupingToggle(grouping = model.grouping, onGrouping = onGrouping)
            }
            if (empty) {
                EmptyHistory()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (model.grouping) {
                        HistoryGrouping.ByMonth -> items(
                            items = model.sections,
                            key = { section -> "month-${section.monthLabel}" },
                        ) { section ->
                            MonthCard(section, strips[section.monthLabel], previewCache, open, toggle)
                        }
                        HistoryGrouping.ByBackup -> item(key = "backups") {
                            RunsCard(model.rows, open, toggle, withYear = true)
                        }
                    }
                }
            }
        }
    }
}

/** One sentence on whether everything is checked (or when the list was last true), with Verify everything beside it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusStrip(model: HistoryUiModel, verify: VerifyEverythingUi?) {
    val colors = MaterialTheme.colorScheme
    val asOf = model.asOfLabel
    val sentence = asOf ?: model.statusLine
    val good = asOf == null && model.statusOk
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.Center,
    ) {
        if (sentence != null) {
            Row(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .defaultMinSize(minHeight = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(if (good) R.drawable.ic_check_circle else R.drawable.ic_schedule),
                    contentDescription = null,
                    tint = if (good) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = sentence,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (good) colors.primary else colors.onSurfaceVariant,
                )
            }
        }
        if (verify != null && sentence != null) {
            TextButton(onClick = verify.onVerify, enabled = verify.enabled) {
                Text(if (verify.running) "Verifying..." else "Verify everything")
            }
        }
    }
}

@Composable
private fun GroupingToggle(
    grouping: HistoryGrouping,
    onGrouping: (HistoryGrouping) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(start = 4.dp)) {
        SegmentedButton(
            selected = grouping == HistoryGrouping.ByMonth,
            onClick = { onGrouping(HistoryGrouping.ByMonth) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text("By month")
        }
        SegmentedButton(
            selected = grouping == HistoryGrouping.ByBackup,
            onClick = { onGrouping(HistoryGrouping.ByBackup) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text("By backup")
        }
    }
}

/** A month and what it holds, with its Backup runs listed inside, so a run is a row and not a card. */
@Composable
private fun MonthCard(
    section: HistoryMonthSection,
    stripHashes: List<String>?,
    previewCache: PreviewBitmapCache?,
    open: Set<String>,
    onToggle: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            MonthHeading(section, stripHashes, previewCache)
            section.rows.forEach { row -> RunRow(row, row.runFile in open, onToggle, withYear = false) }
        }
    }
}

@Composable
private fun RunsCard(
    rows: List<HistoryBackupRow>,
    open: Set<String>,
    onToggle: (String) -> Unit,
    withYear: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            rows.forEachIndexed { index, row ->
                RunRow(row, row.runFile in open, onToggle, withYear, divider = index > 0)
            }
        }
    }
}

@Composable
private fun MonthHeading(
    section: HistoryMonthSection,
    stripHashes: List<String>?,
    previewCache: PreviewBitmapCache?,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!stripHashes.isNullOrEmpty() && previewCache != null) {
            Row(
                modifier = Modifier
                    .width(if (stripHashes.size >= 3) 84.dp else (42 * stripHashes.size).dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (hash in stripHashes) {
                    PreviewImage(
                        hash = hash,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        cache = previewCache,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        maxEdge = 160,
                    )
                }
            }
        }
        Column {
            Text(
                text = figuresInSans(section.monthLabel, figureSize = 19.sp),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = section.totalsLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Two lines and one word. Tapping opens why it ended and how fast, when there is something to say. */
@Composable
private fun RunRow(
    row: HistoryBackupRow,
    expanded: Boolean,
    onToggle: (String) -> Unit,
    withYear: Boolean,
    divider: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        if (divider) HorizontalDivider(color = colors.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .then(
                    if (row.detailLabel != null) {
                        Modifier.clickable(
                            onClickLabel = if (expanded) "Hide details" else "Show details",
                            role = Role.Button,
                        ) { onToggle(row.runFile) }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (withYear) row.dateLabel else row.shortDateLabel,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = row.summaryLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            row.statusLabel?.let { StatusWord(it) }
        }
        if (expanded) {
            row.detailLabel?.let { detail ->
                Text(
                    text = detail,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Checked" in teal with a tick, or "Stopped early" on amber with a clock: the words carry the state, not the colour. */
@Composable
private fun StatusWord(label: String) {
    val colors = MaterialTheme.colorScheme
    val checked = label == "Checked"
    Row(
        modifier = Modifier
            .then(
                if (checked) {
                    Modifier
                } else {
                    Modifier
                        .background(colors.tertiaryContainer, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                },
            )
            .semantics { if (checked) contentDescription = "Backed up and checked" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (checked) R.drawable.ic_check else R.drawable.ic_schedule),
            contentDescription = null,
            tint = if (checked) colors.primary else colors.onTertiaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (checked) colors.primary else colors.onTertiaryContainer,
        )
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No Backup runs yet",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Each Backup run is listed here once it has finished.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
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
