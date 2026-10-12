package app.photoxpo.home

import app.photoxpo.backup.BackupStatus
import app.photoxpo.backup.BackupUiState
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class RunPaceTest {
    private val minute = 60_000L
    private val start = 1_000_000L

    private fun running(
        copiedBytes: Long = 0L,
        firstAt: Long? = null,
        firstBytes: Long = 0L,
        toCopy: Long = 0L,
    ) = BackupUiState(
        status = BackupStatus.Running,
        runStartedAtMillis = start,
        runBytesToCopy = toCopy,
        runBytesCopied = copiedBytes,
        firstCopyAtMillis = firstAt,
        firstCopyBytes = firstBytes,
    )

    @Test
    fun underAMinute_saysNothing() {
        assertNull(runPaceLine(running(), start + 30_000L))
    }

    @Test
    fun beforeAnythingIsCopied_saysItIsCheckingWhatIsAlreadyOnTheDrive() {
        assertEquals(
            "Running for 12 min · checking photos already on your drive",
            runPaceLine(running(), start + 12 * minute),
        )
    }

    @Test
    fun afterCopyingStarts_estimatesFromTheSteadyRate() {
        // 200 MB landed after the first file in 4 min => 50 MB/min; 1 GB left => 20 min.
        val state = running(
            copiedBytes = 300_000_000L,
            firstAt = start + 10 * minute,
            firstBytes = 100_000_000L,
            toCopy = 1_300_000_000L,
        )
        assertEquals(
            "Running for 14 min · about 20 min left",
            runPaceLine(state, start + 14 * minute),
        )
    }

    @Test
    fun justAfterTheFirstCopy_doesNotGuess() {
        val state = running(
            copiedBytes = 150_000_000L,
            firstAt = start + 10 * minute,
            firstBytes = 100_000_000L,
            toCopy = 1_000_000_000L,
        )
        assertEquals(
            "Running for 10 min · working out the time left",
            runPaceLine(state, start + 10 * minute + 30_000L),
        )
    }

    @Test
    fun everythingNewIsCopied_saysFinishingUp() {
        val state = running(
            copiedBytes = 500_000_000L,
            firstAt = start + 2 * minute,
            firstBytes = 100_000_000L,
            toCopy = 500_000_000L,
        )
        assertEquals(
            "Running for 20 min · finishing up",
            runPaceLine(state, start + 20 * minute),
        )
    }

    @Test
    fun durations_readInMinutesAndHours() {
        assertEquals("1 min", formatDuration(10_000L))
        assertEquals("59 min", formatDuration(59 * minute))
        assertEquals("1 h", formatDuration(60 * minute))
        assertEquals("1 h 05 min", formatDuration(65 * minute))
    }

    @Test
    fun notRunning_hasNoPaceLine() {
        assertNull(runPaceLine(running().copy(status = BackupStatus.Done), start + 30 * minute))
    }
}
