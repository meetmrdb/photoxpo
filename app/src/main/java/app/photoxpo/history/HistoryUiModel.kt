package app.photoxpo.history

import app.photoxpo.formatByteCount
import app.photoxpo.core.BackupRunSummary
import app.photoxpo.core.byBackup
import app.photoxpo.core.byMonth
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class HistoryGrouping {
    ByMonth,
    ByBackup,
}

data class HistoryBackupRow(
    val runFile: String,
    val dateLabel: String,
    val filesLabel: String,
    val sizeLabel: String,
    val speedLabel: String?,
    val stoppedEarlyLabel: String?,
    val checked: Boolean = false,
)

data class HistoryMonthSection(
    val monthLabel: String,
    val totalsLabel: String,
    val rows: List<HistoryBackupRow>,
)

data class HistoryUiModel(
    val grouping: HistoryGrouping,
    val asOfLabel: String?,
    val sections: List<HistoryMonthSection>,
    val rows: List<HistoryBackupRow>,
)

/**
 * Maps cached Backup runs into the history screen.
 * When [driveAttached] is false, [HistoryUiModel.asOfLabel] names the last
 * time the Archive Drive was connected. Speed is shown only when the footer
 * recorded MB/s. An interrupted Backup run is labelled "Stopped early".
 */
fun historyUiModel(
    summaries: List<BackupRunSummary>,
    grouping: HistoryGrouping,
    driveAttached: Boolean,
    lastConnectedAtEpochMillis: Long?,
    zone: ZoneId,
    locale: Locale = Locale.ENGLISH,
): HistoryUiModel {
    val rows = byBackup(summaries).map { backupRow(it, zone, locale) }
    val sections = if (grouping == HistoryGrouping.ByMonth) {
        byMonth(summaries).map { group ->
            HistoryMonthSection(
                monthLabel = monthLabel(group.month, locale),
                totalsLabel = "${filesLabel(group.fileCount)}, ${sizeLabel(group.bytes)}",
                rows = group.runs.map { backupRow(it, zone, locale) },
            )
        }
    } else {
        emptyList()
    }
    return HistoryUiModel(
        grouping = grouping,
        asOfLabel = if (driveAttached) null else asOfLabel(lastConnectedAtEpochMillis, zone, locale),
        sections = sections,
        rows = if (grouping == HistoryGrouping.ByBackup) rows else emptyList(),
    )
}

private fun backupRow(summary: BackupRunSummary, zone: ZoneId, locale: Locale): HistoryBackupRow {
    return HistoryBackupRow(
        runFile = summary.runFile,
        dateLabel = dateLabel(summary.startedAt, zone, locale),
        filesLabel = filesLabel(summary.fileCount),
        sizeLabel = sizeLabel(summary.bytes),
        speedLabel = if (summary.interrupted) null else speedLabel(summary.mbPerSecond),
        stoppedEarlyLabel = if (summary.interrupted) "Stopped early" else null,
        checked = summary.checked && !summary.interrupted,
    )
}

private fun asOfLabel(epochMillis: Long?, zone: ZoneId, locale: Locale): String {
    if (epochMillis == null) return "As of the last time the Archive Drive was connected"
    val formatted = dateTime(locale).format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    return "As of ${plainAmPm(formatted)}"
}

private fun dateLabel(startedAt: String, zone: ZoneId, locale: Locale): String {
    val instant = try {
        Instant.parse(startedAt)
    } catch (_: Exception) {
        return startedAt
    }
    return plainAmPm(dateTime(locale).format(instant.atZone(zone)))
}

private fun monthLabel(month: String, locale: Locale): String {
    val parsed = try {
        YearMonth.parse(month)
    } catch (_: Exception) {
        return month
    }
    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    return parsed.atDay(1).format(formatter)
}

private fun filesLabel(count: Long): String {
    return if (count == 1L) "1 file" else "$count files"
}

private fun sizeLabel(bytes: Long): String = formatByteCount(bytes)

private fun speedLabel(mbPerSecond: Double?): String? {
    if (mbPerSecond == null || mbPerSecond.isNaN() || mbPerSecond.isInfinite()) return null
    return "${trimNumber(mbPerSecond)} MB/s"
}

private fun trimNumber(value: Double): String {
    return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}

private fun dateTime(locale: Locale): DateTimeFormatter {
    return DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", locale)
}

private fun plainAmPm(formatted: String): String {
    return formatted.replace("AM", "am").replace("PM", "pm")
}
