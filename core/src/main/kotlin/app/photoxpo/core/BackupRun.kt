package app.photoxpo.core

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
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
) {
    fun start(): BackupRunResult {
        val startedAt = clock.instant()
        val manifestPath = manifestPath(startedAt)
        archiveDrive.appendText(manifestPath, headerLine(startedAt) + "\n")

        var committedCount = 0
        var committedBytes = 0L
        val items = mutableListOf<BackupItemOutcome>()
        for (file in mediaSource.listCameraFolder().sortedBy { it.modifiedAtEpochMillis }) {
            val item = backUpFile(file, manifestPath)
            if (item.state == ItemState.COMMITTED) {
                committedCount += 1
                committedBytes += file.sizeBytes
            }
            items += item
        }

        val endedAt = clock.instant()
        archiveDrive.appendText(
            manifestPath,
            footerLine(startedAt, endedAt, committedCount, committedBytes) + "\n",
        )
        return BackupRunResult(manifestPath, items)
    }

    private fun backUpFile(file: MediaFile, manifestPath: String): BackupItemOutcome {
        val chosen = chooseDate.choose(file)
        val finalPath = originalPath(chosen.localDate, file.name)
        val partPath = "$finalPath.photoxpo-part"

        if (archiveDrive.exists(partPath)) {
            archiveDrive.delete(partPath)
        }
        when (archiveDrive.create(partPath)) {
            is CreateResult.Collision -> return attention(file)
            CreateResult.Created -> Unit
        }

        val copied = mediaSource.openOriginal(file.id).use { input ->
            archiveDrive.write(partPath).use { output ->
                copyHashed(input, output)
            }
        }
        val readBack = archiveDrive.read(partPath).use { input ->
            copyHashed(input, OutputStream.nullOutputStream())
        }
        val matches = copied.sha256 == readBack.sha256 &&
            copied.byteCount == file.sizeBytes &&
            readBack.byteCount == file.sizeBytes &&
            archiveDrive.byteCount(partPath) == file.sizeBytes
        if (!matches) {
            archiveDrive.delete(partPath)
            return attention(file)
        }

        when (archiveDrive.rename(partPath, finalPath)) {
            is RenameResult.Collision -> {
                archiveDrive.delete(partPath)
                return attention(file)
            }
            RenameResult.Renamed -> Unit
        }

        // The manifest line is the commit point.
        archiveDrive.appendText(manifestPath, fileLine(file, chosen, finalPath, copied.sha256) + "\n")
        return BackupItemOutcome(file.id, file.name, ItemState.COMMITTED)
    }

    private fun attention(file: MediaFile): BackupItemOutcome {
        return BackupItemOutcome(file.id, file.name, ItemState.NEEDS_ATTENTION)
    }
}

fun startBackupRun(
    mediaSource: MediaSource,
    archiveDrive: ArchiveDrive,
    chooseDate: DateChooser = DateChooser(::chooseCaptureDate),
    clock: Clock = Clock.systemUTC(),
): BackupRunResult {
    return BackupRunner(mediaSource, archiveDrive, chooseDate, clock).start()
}

private data class HashedBytes(val sha256: String, val byteCount: Long)

private fun copyHashed(input: InputStream, output: OutputStream): HashedBytes {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    var byteCount = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        digest.update(buffer, 0, read)
        output.write(buffer, 0, read)
        byteCount += read
    }
    output.flush()
    return HashedBytes(digest.digest().toHex(), byteCount)
}

private fun originalPath(localDate: String, filename: String): String {
    require(localDate.length >= 10 && localDate[4] == '-' && localDate[7] == '-') {
        "chosen date must be YYYY-MM-DD"
    }
    val year = localDate.substring(0, 4)
    val yearMonth = localDate.substring(0, 7)
    return "Photoxpo/Originals/$year/$yearMonth/$filename"
}

private fun manifestPath(startedAt: Instant): String {
    return "Photoxpo/.photoxpo/runs/${RUN_FILE_STAMP.format(startedAt)}.jsonl"
}

private fun headerLine(startedAt: Instant): String {
    return JsonLine()
        .field("type", "header")
        .field("startedAt", ISO_MILLIS.format(startedAt))
        .nullField("previewPreset")
        .build()
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
        .nullField("previewFileName")
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

private fun MediaKind.wireName(): String = when (this) {
    MediaKind.PHOTO -> "photo"
    MediaKind.VIDEO -> "video"
}

private fun ByteArray.toHex(): String {
    val digits = "0123456789abcdef"
    val chars = CharArray(size * 2)
    for (index in indices) {
        val value = this[index].toInt() and 0xff
        chars[index * 2] = digits[value ushr 4]
        chars[index * 2 + 1] = digits[value and 0x0f]
    }
    return String(chars)
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

private const val COPY_BUFFER_BYTES = 64 * 1024

private val ISO_MILLIS: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

private val RUN_FILE_STAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC)
