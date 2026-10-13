package app.photoxpo.history

import app.photoxpo.formatByteCount
import app.photoxpo.core.BackupRunSummary
import app.photoxpo.core.byBackup
import app.photoxpo.core.byMonth
import app.photoxpo.core.runEndWords
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
    val endReasonLabel: String? = null,
    val checked: Boolean = false,
    /** The date without the year, for rows that sit under their month. */
    val shortDateLabel: String = dateLabel,
    /** "4 files · 10 MB": what the run copied, on one line. */
    val summaryLabel: String = "$filesLabel · $sizeLabel",
    /** The one word at the end of the row: "Checked" or "Stopped early". Null when the run was neither. */
    val statusLabel: String? = null,
    /** Why it ended and how fast, shown when the row is opened. Null when there is nothing to add. */
    val detailLabel: String? = null,
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
    /** One sentence on whether every Backup run is checked. Null when there are no runs. */
    val statusLine: String? = null,
    /** False when something in [statusLine] needs a look, so the screen does not paint it as good news. */
    val statusOk: Boolean = true,
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
    // A run that found nothing new to copy has nothing worth listing.
    val summaries = summaries.filter { it.fileCount > 0 }
    val rows = byBackup(summaries).map { backupRow(it, zone, locale) }
    val sections = if (grouping == HistoryGrouping.ByMonth) {
        byMonth(summaries).map { group ->
            HistoryMonthSection(
                monthLabel = monthLabel(group.month, locale),
                totalsLabel = "${filesLabel(group.fileCount)} · ${sizeLabel(group.bytes)}",
                rows = group.runs.map { backupRow(it, zone, locale) },
            )
        }
    } else {
        emptyList()
    }
    val stoppedEarly = summaries.count { it.interrupted }
    val notChecked = summaries.count { !it.checked && !it.interrupted }
    return HistoryUiModel(
        grouping = grouping,
        asOfLabel = if (driveAttached) null else asOfLabel(lastConnectedAtEpochMillis, zone, locale),
        sections = sections,
        rows = if (grouping == HistoryGrouping.ByBackup) rows else emptyList(),
        statusLine = when {
            summaries.isEmpty() -> null
            stoppedEarly > 0 -> runsLabel(stoppedEarly, "stopped early")
            notChecked > 0 -> runsLabel(notChecked, "not checked")
            else -> "Everything is Backed up and checked"
        },
        statusOk = stoppedEarly == 0 && notChecked == 0,
    )
}

private fun runsLabel(count: Int, what: String): String {
    return if (count == 1) "1 Backup run $what" else "$count Backup runs $what"
}

private fun backupRow(summary: BackupRunSummary, zone: ZoneId, locale: Locale): HistoryBackupRow {
    return HistoryBackupRow(
        runFile = summary.runFile,
        dateLabel = dateLabel(summary.startedAt, zone, locale),
        filesLabel = filesLabel(summary.fileCount),
        sizeLabel = sizeLabel(summary.bytes),
        speedLabel = if (summary.interrupted) null else speedLabel(summary.mbPerSecond),
        stoppedEarlyLabel = if (summary.interrupted) "Stopped early" else null,
        endReasonLabel = summary.runEnd?.let(::runEndWords),
        checked = summary.checked && !summary.interrupted,
        shortDateLabel = shortDateLabel(summary.startedAt, zone, locale),
        statusLabel = when {
            summary.interrupted -> "Stopped early"
            summary.checked -> "Checked"
            else -> null
        },
        detailLabel = if (summary.interrupted) {
            null
        } else {
            listOfNotNull(summary.runEnd?.let(::runEndWords), speedLabel(summary.mbPerSecond))
                .joinToString(" · ")
                .ifEmpty { null }
        },
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

private fun shortDateLabel(startedAt: String, zone: ZoneId, locale: Locale): String {
    val instant = try {
        Instant.parse(startedAt)
    } catch (_: Exception) {
        return startedAt
    }
    return plainAmPm(DateTimeFormatter.ofPattern("d MMM, h:mm a", locale).format(instant.atZone(zone)))
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
