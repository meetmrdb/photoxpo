package app.photoxpo.core

import java.time.Instant

/**
 * One Backup run, read from its run file on the Archive Drive.
 *
 * [runFile] is the file name under Photoxpo/.photoxpo/runs, for example
 * `20261011T183000000Z.jsonl`. [startedAt] and [endedAt] are the timestamps
 * written in that file. [fileCount] and [bytes] count distinct hashes on
 * committed file lines. A second line with the same hash is another source
 * name, not another file.
 * [elapsedMs] and [mbPerSecond] come from the footer. [interrupted] is true
 * when the run file has no footer.
 */
data class BackupRunSummary(
    val runFile: String,
    val startedAt: String,
    val endedAt: String?,
    val fileCount: Long,
    val bytes: Long,
    val checked: Boolean,
    val elapsedMs: Long?,
    val mbPerSecond: Double?,
    val interrupted: Boolean,
    /** How the run ended, from the footer; null for older footers without a reason and for interrupted runs. */
    val runEnd: RunEnd? = null,
    /** Battery percent the run used, from the footer; null for older footers, charging runs and interrupted runs. */
    val batteryDropPercent: Int? = null,
)

/**
 * Backup runs whose [BackupRunSummary.startedAt] falls in one month.
 * [month] is `yyyy-MM` taken from that timestamp. [fileCount] and [bytes]
 * are the totals for [runs].
 */
data class BackupMonthGroup(
    val month: String,
    val fileCount: Long,
    val bytes: Long,
    val runs: List<BackupRunSummary>,
)

/**
 * Groups Backup runs by the month of [BackupRunSummary.startedAt].
 * The newest month is first. Runs inside a month are newest first.
 */
fun byMonth(summaries: List<BackupRunSummary>): List<BackupMonthGroup> {
    if (summaries.isEmpty()) return emptyList()
    val grouped = summaries.groupBy { monthOf(it.startedAt) }
    return grouped.keys.sortedDescending().map { month ->
        val runs = byBackup(grouped.getValue(month))
        BackupMonthGroup(
            month = month,
            fileCount = runs.sumOf { it.fileCount },
            bytes = runs.sumOf { it.bytes },
            runs = runs,
        )
    }
}

/** Newest [BackupRunSummary.startedAt] first. */
fun byBackup(summaries: List<BackupRunSummary>): List<BackupRunSummary> {
    return summaries.sortedWith(newestFirst)
}

private val newestFirst = compareByDescending<BackupRunSummary> { startedAtMillis(it.startedAt) }
    .thenByDescending { it.runFile }

private fun monthOf(startedAt: String): String {
    if (startedAt.length >= 8 &&
        startedAt[4] == '-' &&
        startedAt[7] == '-' &&
        startedAt.substring(0, 4).all(Char::isDigit) &&
        startedAt.substring(5, 7).all(Char::isDigit)
    ) {
        return startedAt.substring(0, 7)
    }
    return startedAt
}

private fun startedAtMillis(startedAt: String): Long {
    return try {
        Instant.parse(startedAt).toEpochMilli()
    } catch (_: Exception) {
        Long.MIN_VALUE
    }
}
