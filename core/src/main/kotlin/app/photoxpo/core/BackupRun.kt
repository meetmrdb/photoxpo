package app.photoxpo.core

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ItemState {
    FOUND,
    COPYING,
    VERIFYING,
    COMMITTED,
    NEEDS_ATTENTION,
    PAUSED_DRIVE_OUT,
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
)

class BackupRunner(
    private val mediaSource: MediaSource,
    private val archiveDrive: ArchiveDrive,
    private val chooseDate: DateChooser = DateChooser(::chooseCaptureDate),
    private val clock: Clock = Clock.systemUTC(),
    private val previewPreset: PreviewPreset? = null,
) {
    fun start(): BackupRunResult {
        val startedAt = clock.instant()
        val manifestPath = manifestPath(startedAt)
        val files = mediaSource.listCameraFolder().sortedBy { it.modifiedAtEpochMillis }
        val items = mutableListOf<BackupItemOutcome>()
        try {
            val committed = ManifestReader().readAll(archiveDrive)
                .flatMap { it.entries }
                .toMutableList()
            archiveDrive.appendText(manifestPath, headerLine(startedAt, previewPreset) + "\n")
            val copier = OriginalCopier(
                mediaSource = mediaSource,
                archiveDrive = archiveDrive,
                chooseDate = chooseDate,
                committed = committed,
                onCommit = { file, chosen, drivePath, hash ->
                    // The manifest line is the commit point.
                    archiveDrive.appendText(
                        manifestPath,
                        fileLine(file, chosen, drivePath, hash) + "\n",
                    )
                },
            )
            var committedCount = 0
            var committedBytes = 0L
            for (file in files) {
                val copied = copier.copy(file)
                if (copied.wroteManifestLine) {
                    committedCount += 1
                    committedBytes += file.sizeBytes
                }
                items += copied.outcome
            }
            val endedAt = clock.instant()
            archiveDrive.appendText(
                manifestPath,
                footerLine(startedAt, endedAt, committedCount, committedBytes) + "\n",
            )
            return BackupRunResult(manifestPath, items)
        } catch (_: DriveUnavailableException) {
            return pausedResult(manifestPath, files, items)
        }
    }
}

private fun pausedResult(
    manifestPath: String,
    files: List<MediaFile>,
    completed: List<BackupItemOutcome>,
): BackupRunResult {
    val remaining = files.drop(completed.size).map { file ->
        BackupItemOutcome(file.id, file.name, ItemState.PAUSED_DRIVE_OUT)
    }
    return BackupRunResult(manifestPath, completed + remaining)
}

fun startBackupRun(
    mediaSource: MediaSource,
    archiveDrive: ArchiveDrive,
    chooseDate: DateChooser = DateChooser(::chooseCaptureDate),
    clock: Clock = Clock.systemUTC(),
    previewPreset: PreviewPreset? = null,
): BackupRunResult {
    return BackupRunner(mediaSource, archiveDrive, chooseDate, clock, previewPreset).start()
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

private fun fileLine(
    file: MediaFile,
    chosen: ChosenDate,
    drivePath: String,
    hash: String,
): String {
    return JsonLine()
        .field("type", "file")
        .field("hash", hash)
        .field("drivePath", drivePath)
        .field("size", file.sizeBytes)
        .field("kind", file.kind.wireName())
        .field("chosenDate", chosen.localDate)
        .field("sourceTag", chosen.sourceTag)
        .field("originalFilename", file.name)
        .field("previewFileName", previewFileName(hash))
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
