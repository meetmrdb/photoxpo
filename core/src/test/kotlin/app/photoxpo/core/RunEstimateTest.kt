package app.photoxpo.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class RunEstimateTest {
    private val gb = 1_000_000_000L

    private fun run(bytes: Long, drop: Int?, elapsedMinutes: Long? = null) =
        RecentRun(bytes = bytes, batteryDropPercent = drop, elapsed = elapsedMinutes?.let { Duration.ofMinutes(it) })

    @Test
    fun withNoHistory_usesTheCautiousFallback() {
        assertEquals(FALLBACK_BATTERY_PERCENT_PER_GB, batteryPercentPerGb(emptyList()), 0.0001)
        assertEquals(FALLBACK_COPY_BYTES_PER_SECOND, copyBytesPerSecond(emptyList()), 0.0001)
    }

    @Test
    fun learnsFromRecentRuns_dropAgainstBytes() {
        // 6% over 3 GB and 4% over 2 GB: 10% over 5 GB = 2% per GB.
        val recent = listOf(run(3 * gb, 6), run(2 * gb, 4))
        assertEquals(2.0, batteryPercentPerGb(recent), 0.0001)
    }

    @Test
    fun onlyTheLastFewRunsCount() {
        // Newest first. The old 10% per GB run is beyond the last three.
        val recent = listOf(run(gb, 1), run(gb, 1), run(gb, 1), run(gb, 10))
        assertEquals(1.0, batteryPercentPerGb(recent), 0.0001)
    }

    @Test
    fun runsWithoutAReadingOrTooSmallToTellAreIgnored() {
        val recent = listOf(run(5 * gb, null), run(1_000_000L, 3), run(2 * gb, 6))
        assertEquals(3.0, batteryPercentPerGb(recent), 0.0001)
        assertEquals(FALLBACK_BATTERY_PERCENT_PER_GB, batteryPercentPerGb(listOf(run(5 * gb, null))), 0.0001)
    }

    @Test
    fun learnsCopySpeedFromRecentRuns() {
        // 6 GB in 30 min plus 3 GB in 30 min: 9 GB in 3600 s = 2.5 MB/s.
        val recent = listOf(run(6 * gb, 1, 30), run(3 * gb, 1, 30))
        assertEquals(2_500_000.0, copyBytesPerSecond(recent), 1.0)
    }

    @Test
    fun theEstimateIsOnlyForWhatOneRunCopies() {
        // 2 GB in 30 min (about 1.1 MB/s) at 2% per GB. 100 GB are left, but one run stops at 45 minutes:
        // that reaches 3 GB, so about 6%.
        val recent = listOf(run(2 * gb, 4, 30))
        val estimate = estimateRun(100 * gb, recent)
        assertEquals(RUN_TIME_LIMIT, estimate.duration)
        assertEquals(6, estimate.batteryPercent)
    }

    @Test
    fun aSmallRun_isEstimatedFromItsOwnBytes() {
        val recent = listOf(run(2 * gb, 4, 20)) // 2%/GB, about 1.67 MB/s
        val estimate = estimateRun(gb, recent)
        assertEquals(Duration.ofMinutes(10), estimate.duration)
        assertEquals(2, estimate.batteryPercent)
    }

    @Test
    fun nothingToCopy_needsNothing() {
        val estimate = estimateRun(0L, emptyList())
        assertEquals(Duration.ZERO, estimate.duration)
        assertEquals(0, estimate.batteryPercent)
    }
}
