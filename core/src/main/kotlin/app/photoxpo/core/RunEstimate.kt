package app.photoxpo.core

import java.time.Duration
import kotlin.math.ceil

/**
 * Cautious until there is data: battery percent per GB copied, used for a person's first runs.
 * A guess, to be replaced by a real reading on the test phone and drive (#46).
 */
const val FALLBACK_BATTERY_PERCENT_PER_GB: Double = 3.0

/** Cautious until there is data: slow, so a first run is warned about early rather than late. Replace after #46. */
const val FALLBACK_COPY_BYTES_PER_SECOND: Double = 3_000_000.0

/** How many of the latest Backup runs the estimate learns from. */
const val ESTIMATE_RECENT_RUNS: Int = 3

/** A run that copied less than this says little about battery or speed, so it is left out. */
const val ESTIMATE_MIN_RUN_BYTES: Long = 100_000_000L

/** What a finished Backup run tells the estimate. Null fields mean the run did not record them. */
data class RecentRun(
    val bytes: Long,
    val batteryDropPercent: Int?,
    val elapsed: Duration?,
)

data class RunEstimate(
    val duration: Duration,
    val batteryPercent: Int,
)

/** Percent of battery per GB copied, from the latest runs (newest first); the fallback with no usable run. */
fun batteryPercentPerGb(recent: List<RecentRun>): Double {
    val used = recent.take(ESTIMATE_RECENT_RUNS)
        .filter { it.bytes >= ESTIMATE_MIN_RUN_BYTES && it.batteryDropPercent != null }
    if (used.isEmpty()) return FALLBACK_BATTERY_PERCENT_PER_GB
    val drop = used.sumOf { it.batteryDropPercent!! }
    val gb = used.sumOf { it.bytes } / 1_000_000_000.0
    return drop / gb
}

/** Bytes per second a run copies, from the latest runs (newest first); the fallback with no usable run. */
fun copyBytesPerSecond(recent: List<RecentRun>): Double {
    val used = recent.take(ESTIMATE_RECENT_RUNS)
        .filter { it.bytes >= ESTIMATE_MIN_RUN_BYTES && it.elapsed != null && !it.elapsed.isZero }
    if (used.isEmpty()) return FALLBACK_COPY_BYTES_PER_SECOND
    val seconds = used.sumOf { it.elapsed!!.toMillis() } / 1000.0
    return used.sumOf { it.bytes } / seconds
}

/** How long the next run will take and how much battery it will use. One run stops at the size or time limit. */
fun estimateRun(bytesToCopy: Long, recent: List<RecentRun>): RunEstimate {
    val bytes = bytesToCopy.coerceIn(0L, RUN_SIZE_LIMIT_BYTES)
    if (bytes == 0L) return RunEstimate(Duration.ZERO, 0)
    val seconds = bytes / copyBytesPerSecond(recent)
    val duration = minOf(Duration.ofMillis(ceil(seconds * 1000).toLong()), RUN_TIME_LIMIT)
    // The time limit can end the run before the size limit does, so only the bytes it reaches use battery.
    val copied = minOf(bytes.toDouble(), copyBytesPerSecond(recent) * duration.toMillis() / 1000.0)
    val percent = ceil(copied / 1_000_000_000.0 * batteryPercentPerGb(recent)).toInt()
    return RunEstimate(duration, percent)
}
