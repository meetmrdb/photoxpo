package app.photoxpo.core

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

enum class ItemState {
    FOUND,
    COPYING,
    VERIFYING,
    COMMITTED,
    NEEDS_ATTENTION,
    PAUSED_DRIVE_OUT,
}

enum class BackupRunStatus {
    Completed,
    Paused,
    DriveMismatch,
}

data class ChosenDate(
    val localDate: String,
    val sourceTag: String,
)

fun interface DateChooser {
    fun choose(file: MediaFile): ChosenDate
}

fun chooseModifiedTimeUtc(file: MediaFile): ChosenDate {
    val date = Instant.ofEpochMilli(file.modifiedAtEpochMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
    return ChosenDate(localDate = date.toString(), sourceTag = "modified")
}

data class BackupItemOutcome(
    val id: String,
    val name: String,
    val state: ItemState,
)

data class BackupRunResult(
    val manifestPath: String,
    val items: List<BackupItemOutcome>,
    val status: BackupRunStatus = BackupRunStatus.Completed,
    /** Why the run ended before every Original was Backed up; null when nothing was left behind. */
    val stopReason: StopReason? = null,
    /** How many Originals still need copying when the run stopped for [stopReason]. */
    val filesLeft: Int = 0,
)

class BackupRunner(
    private val mediaSource: MediaSource,
    private val archiveDrive: ArchiveDrive,
    private val chooseDate: DateChooser = DateChooser(::chooseCaptureDate),
    private val clock: Clock = Clock.systemUTC(),
    private val driveIdentity: DriveIdentityStore = InMemoryDriveIdentityStore(),
    private val backedUpLedger: BackedUpLedger = InMemoryBackedUpLedger(),
    private val previews: PreviewRecordStore = InMemoryPreviewRecordStore(),
    private val newDriveId: () -> String = { UUID.randomUUID().toString() },
    private val stopPolicy: StopPolicy = DefaultStopPolicy(),
    private val batteryInput: BatteryInput = BatteryInput { null },
    private val previewPreset: PreviewPreset? = null,
    private val isCancelled: () -> Boolean = { false },
    private val checkPolicy: CheckPolicy = CheckPolicy.skipRecentlyChecked(),
) {
    fun start(): BackupRunResult {
        val startedAt = clock.instant()
        val manifestPath = manifestPath(startedAt)
        val files = sortedFiles()
        val items = mutableListOf<BackupItemOutcome>()
        val deferred = mutableSetOf<String>()
        try {
            if (driveRejected()) {
                return BackupRunResult(
                    manifestPath = "",
                    items = emptyList(),
                    status = BackupRunStatus.DriveMismatch,
                )
            }
            val committed = mergeByHash(
                ManifestReader().readAll(archiveDrive).flatMap { it.entries },
            )
            val header = headerLine(startedAt, previewPreset)
            archiveDrive.appendText(manifestPath, header + "\n")
            val linesByHash = linkedMapOf<String, String>()
            val manifest = object : ManifestCommit {
                override fun appendStored(entry: ManifestFileEntry) {
                    val line = fileLine(entry)
                    linesByHash[entry.hash] = line
                    // The manifest line is the commit point.
                    archiveDrive.appendText(manifestPath, line + "\n")
                }

                override fun updateNames(entry: ManifestFileEntry) {
                    val line = fileLine(entry)
                    linesByHash[entry.hash] = line
                    // The earlier line for this hash stays. ManifestReader merges both.
                    archiveDrive.appendText(manifestPath, line + "\n")
                }
            }
            val copier = OriginalCopier(
                mediaSource = mediaSource,
                archiveDrive = archiveDrive,
                chooseDate = chooseDate,
                committed = committed,
                manifest = manifest,
            )
            var copiedBytes = 0L
            var stopReason: StopReason? = null
            var committedCount = 0
            var committedBytes = 0L
            for (file in files) {
                // Between Originals, so a pause does not leave a partial Original.
                if (isCancelled()) {
                    return cancelBetweenFiles(
                        manifestPath = manifestPath,
                        files = files,
                        items = items,
                        deferred = deferred,
                        manifestHasNoFileLine = linesByHash.isEmpty(),
                    )
                }
                if (checkedRecentlyAndStillOnDrive(file, committed)) {
                    items += BackupItemOutcome(file.id, file.name, ItemState.COMMITTED)
                    continue
                }
                // Once the run decides to stop, it only finishes Originals that need no new copy.
                val reasonNow = stopReason ?: run {
                    val battery = batteryInput.read()
                    stopPolicy.reasonToStop(
                        RunProgress(
                            copiedBytes = copiedBytes,
                            elapsed = Duration.between(startedAt, clock.instant()),
                            nextFileBytes = file.sizeBytes,
                            batteryPercent = battery?.percent,
                            charging = battery?.charging ?: false,
                        ),
                    )
                }
                val copied = copier.copy(file, allowNewCopy = reasonNow == null)
                if (copied.deferredByCap) {
                    stopReason = reasonNow
                    deferred += file.id
                    continue
                }
                if (copied.hash != null && copied.outcome.state == ItemState.COMMITTED) {
                    backedUpLedger.recordChecked(file, copied.hash, clock.instant())
                }
                if (copied.wroteNewFileLine) {
                    committedCount += 1
                    committedBytes += file.sizeBytes
                }
                if (copied.copiedFile) {
                    copiedBytes += file.sizeBytes
                }
                items += copied.outcome
            }
            val endedAt = clock.instant()
            archiveDrive.appendText(
                manifestPath,
                footerLine(startedAt, endedAt, committedCount, committedBytes) + "\n",
            )
            return BackupRunResult(
                manifestPath = manifestPath,
                items = items,
                status = BackupRunStatus.Completed,
                stopReason = stopReason,
                filesLeft = deferred.size,
            )
        } catch (_: DriveUnavailableException) {
            return pausedResult(manifestPath, files, items, deferred)
        }
    }

    private fun checkedRecentlyAndStillOnDrive(file: MediaFile, committed: List<ManifestFileEntry>): Boolean {
        val record = backedUpLedger.checkedOriginal(file.id)
        if (!checkPolicy.isRecentlyChecked(record, file, clock.instant())) return false
        // The Archive Drive is the record: the Manifest must still list it and the file must still be there.
        val entry = committed.firstOrNull { it.hash == record?.hash } ?: return false
        return archiveDrive.exists(entry.drivePath) && archiveDrive.byteCount(entry.drivePath) == file.sizeBytes
    }

    private fun cancelBetweenFiles(
        manifestPath: String,
        files: List<MediaFile>,
        items: List<BackupItemOutcome>,
        deferred: Set<String>,
        manifestHasNoFileLine: Boolean,
    ): BackupRunResult {
        if (manifestHasNoFileLine && archiveDrive.exists(manifestPath)) {
            archiveDrive.delete(manifestPath)
            return pausedResult("", files, items, deferred)
        }
        return pausedResult(manifestPath, files, items, deferred)
    }

    fun useThisDriveInstead() {
        backedUpLedger.clear()
        previews.markOriginalNotOnThisDrive()
        val id = newDriveId()
        writeDriveId(id)
        driveIdentity.remember(id)
    }

    private fun sortedFiles(): List<MediaFile> {
        return mediaSource.listCameraFolder().sortedWith(
            compareBy<MediaFile> { chooseDate.choose(it).localDate }
                .thenBy { it.modifiedAtEpochMillis }
                .thenBy { it.id },
        )
    }

    private fun driveRejected(): Boolean {
        val onDrive = readDriveId()
        val expected = driveIdentity.expected()
        if (expected != null && onDrive != expected) return true
        if (onDrive == null) {
            val id = newDriveId()
            writeDriveId(id)
            driveIdentity.remember(id)
        } else if (expected == null) {
            driveIdentity.remember(onDrive)
        }
        return false
    }

    private fun readDriveId(): String? {
        if (!archiveDrive.exists(DRIVE_ID_PATH)) return null
        val text = archiveDrive.readText(DRIVE_ID_PATH).trim()
        return text.ifEmpty { null }
    }

    private fun writeDriveId(id: String) {
        if (archiveDrive.exists(DRIVE_ID_PATH)) {
            archiveDrive.delete(DRIVE_ID_PATH)
        }
        archiveDrive.appendText(DRIVE_ID_PATH, id.trim() + "\n")
    }
}

private fun mergeByHash(entries: List<ManifestFileEntry>): MutableList<ManifestFileEntry> {
    val merged = linkedMapOf<String, ManifestFileEntry>()
    for (entry in entries) {
        val prior = merged[entry.hash]
        if (prior == null) {
            val names = entry.originalFilenames.ifEmpty { listOf(entry.originalFilename) }
            merged[entry.hash] = entry.copy(originalFilenames = names)
        } else {
            val names = (prior.originalFilenames + entry.originalFilenames).distinct()
            merged[entry.hash] = prior.copy(originalFilenames = names)
        }
    }
    return merged.values.toMutableList()
}

private fun pausedResult(
    manifestPath: String,
    files: List<MediaFile>,
    completed: List<BackupItemOutcome>,
    deferred: Set<String>,
): BackupRunResult {
    val done = completed.map { it.id }.toSet()
    val remaining = files.filter { it.id !in done && it.id !in deferred }.map { file ->
        BackupItemOutcome(file.id, file.name, ItemState.PAUSED_DRIVE_OUT)
    }
    return BackupRunResult(manifestPath, completed + remaining, BackupRunStatus.Paused)
}

fun startBackupRun(
    mediaSource: MediaSource,
    archiveDrive: ArchiveDrive,
    chooseDate: DateChooser = DateChooser(::chooseCaptureDate),
    clock: Clock = Clock.systemUTC(),
    driveIdentity: DriveIdentityStore = InMemoryDriveIdentityStore(),
    backedUpLedger: BackedUpLedger = InMemoryBackedUpLedger(),
    previews: PreviewRecordStore = InMemoryPreviewRecordStore(),
    newDriveId: () -> String = { UUID.randomUUID().toString() },
    stopPolicy: StopPolicy = DefaultStopPolicy(),
    batteryInput: BatteryInput = BatteryInput { null },
    previewPreset: PreviewPreset? = null,
): BackupRunResult {
    return BackupRunner(
        mediaSource = mediaSource,
        archiveDrive = archiveDrive,
        chooseDate = chooseDate,
        clock = clock,
        driveIdentity = driveIdentity,
        backedUpLedger = backedUpLedger,
        previews = previews,
        newDriveId = newDriveId,
        stopPolicy = stopPolicy,
        batteryInput = batteryInput,
        previewPreset = previewPreset,
    ).start()
}

internal fun originalPath(localDate: String, filename: String): String {
    require(localDate.length >= 10 && localDate[4] == '-' && localDate[7] == '-') {
        "chosen date must be YYYY-MM-DD"
    }
    val year = localDate.substring(0, 4)
    val yearMonth = localDate.substring(0, 7)
    return "Photoxpo/Originals/$year/$yearMonth/$filename"
}

private fun manifestPath(startedAt: Instant): String {
    return "$BACKUP_RUNS_DIRECTORY/${RUN_FILE_STAMP.format(startedAt)}.jsonl"
}

private fun headerLine(startedAt: Instant, previewPreset: PreviewPreset?): String {
    val line = JsonLine()
        .field("type", "header")
        .field("startedAt", ISO_MILLIS.format(startedAt))
    return if (previewPreset == null) {
        line.nullField("previewPreset").build()
    } else {
        line.field("previewPreset", previewPreset.wireName()).build()
    }
}

private fun fileLine(entry: ManifestFileEntry): String {
    val names = entry.originalFilenames.ifEmpty { listOf(entry.originalFilename) }
    return JsonLine()
        .field("type", "file")
        .field("hash", entry.hash)
        .field("drivePath", entry.drivePath)
        .field("size", entry.size)
        .field("kind", checkNotNull(entry.kind))
        .field("chosenDate", checkNotNull(entry.chosenDate))
        .field("sourceTag", checkNotNull(entry.sourceTag))
        .field("originalFilename", entry.originalFilename)
        .field("originalFilenames", names)
        .field("previewFileName", previewFileName(entry.hash))
        .build()
}

private fun footerLine(
    startedAt: Instant,
    endedAt: Instant,
    fileCount: Int,
    bytes: Long,
): String {
    val elapsedMs = Duration.between(startedAt, endedAt).toMillis().coerceAtLeast(0)
    val mbPerSecond = if (elapsedMs == 0L) {
        0.0
    } else {
        (bytes.toDouble() / 1_000_000.0) / (elapsedMs.toDouble() / 1000.0)
    }
    return JsonLine()
        .field("type", "footer")
        .field("startedAt", ISO_MILLIS.format(startedAt))
        .field("endedAt", ISO_MILLIS.format(endedAt))
        .field("fileCount", fileCount.toLong())
        .field("bytes", bytes)
        .field("checked", true)
        .field("elapsedMs", elapsedMs)
        .field("mbPerSecond", mbPerSecond)
        .build()
}

internal fun MediaKind.wireName(): String = when (this) {
    MediaKind.PHOTO -> "photo"
    MediaKind.VIDEO -> "video"
}

private class JsonLine {
    private val fields = StringBuilder("{")
    private var first = true

    fun field(name: String, value: String): JsonLine = raw(name, jsonString(value))

    fun field(name: String, value: Long): JsonLine = raw(name, value.toString())

    fun field(name: String, value: Boolean): JsonLine = raw(name, value.toString())

    fun field(name: String, value: Double): JsonLine = raw(name, jsonDouble(value))

    fun field(name: String, values: List<String>): JsonLine {
        val encoded = values.joinToString(prefix = "[", postfix = "]", separator = ",") { jsonString(it) }
        return raw(name, encoded)
    }

    fun nullField(name: String): JsonLine = raw(name, "null")

    fun build(): String {
        fields.append('}')
        return fields.toString()
    }

    private fun raw(name: String, encoded: String): JsonLine {
        if (!first) fields.append(',')
        first = false
        fields.append('"').append(name).append('"').append(':').append(encoded)
        return this
    }
}

private fun jsonString(value: String): String {
    val encoded = StringBuilder(value.length + 2)
    encoded.append('"')
    for (ch in value) {
        when (ch) {
            '"' -> encoded.append("\\\"")
            '\\' -> encoded.append("\\\\")
            '\n' -> encoded.append("\\n")
            '\r' -> encoded.append("\\r")
            '\t' -> encoded.append("\\t")
            else -> {
                if (ch.code < 0x20) {
                    encoded.append(String.format(Locale.US, "\\u%04x", ch.code))
                } else {
                    encoded.append(ch)
                }
            }
        }
    }
    encoded.append('"')
    return encoded.toString()
}

private fun jsonDouble(value: Double): String {
    if (!value.isFinite()) return "0"
    return String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.').ifEmpty { "0" }
}

private val ISO_MILLIS: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

private val RUN_FILE_STAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC)
