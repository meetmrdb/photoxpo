package app.photoxpo.home

import app.photoxpo.backup.BackupStatus
import app.photoxpo.backup.BackupUiState
import app.photoxpo.core.RecentRun
import app.photoxpo.data.DriveStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class LongRunWarningTest {
    private val gb = 1_000_000_000L
    private val now = 1_700_000_000_000L

    private fun idle(
        toCopy: Long? = 10 * gb,
        battery: Int? = 40,
        charging: Boolean = false,
        recent: List<RecentRun> = emptyList(),
        status: BackupStatus = BackupStatus.Idle,
    ) = BackupUiState(
        status = status,
        driveStatus = DriveStatus.Ready,
        bytesToCopy = toCopy,
        batteryPercent = battery,
        charging = charging,
        recentRuns = recent,
    )

    private fun warning(state: BackupUiState) = homeUiModel(state, now).longRunWarning

    @Test
    fun longRunOnALowBattery_warnsAndNamesAChargeTarget() {
        // Nothing learned yet: the cautious fallback says 45 min and about 25% for a full run.
        assertEquals(
            "This will take about 45 min and use about 25% battery. Charge to at least 55% first.",
            warning(idle(battery = 40)),
        )
    }

    @Test
    fun learnedFromRecentRuns_theEstimateUsesThem() {
        // 6 GB in 40 min used 12%: 2% per GB, 2.5 MB/s. A 10 GB run is capped at 45 min: 6.75 GB, 14%.
        val recent = listOf(RecentRun(bytes = 6 * gb, batteryDropPercent = 12, elapsed = Duration.ofMinutes(40)))
        assertEquals(
            "This will take about 45 min and use about 14% battery. Charge to at least 45% first.",
            warning(idle(battery = 30, recent = recent)),
        )
    }

    @Test
    fun plentyOfBattery_noWarning() {
        assertNull(warning(idle(battery = 90)))
        // 25% used would leave exactly 20%: not under 20.
        assertNull(warning(idle(battery = 45)))
    }

    @Test
    fun shortRun_noWarning_evenOnALowBattery() {
        // 1 GB at the fallback speed is under 6 minutes.
        assertNull(warning(idle(toCopy = gb, battery = 25)))
    }

    @Test
    fun exactlyThirtyMinutes_isNotOverThirty() {
        // 5.4 GB at 3 MB/s is exactly 30 min.
        assertNull(warning(idle(toCopy = 5_400_000_000L, battery = 25)))
    }

    @Test
    fun whileCharging_orWithAnUnknownBattery_noWarning() {
        assertNull(warning(idle(charging = true)))
        assertNull(warning(idle(battery = null)))
    }

    @Test
    fun nothingToCopy_orARunAlreadyGoing_noWarning() {
        assertNull(warning(idle(toCopy = 0L)))
        assertNull(warning(idle(toCopy = null)))
        assertNull(warning(idle(status = BackupStatus.Running)))
    }

    @Test
    fun backUpStaysEnabledWithTheWarning() {
        val model = homeUiModel(idle(battery = 40), now)
        assertTrue(model.longRunWarning != null)
        assertEquals("Back up", model.primaryLabel)
        assertFalse(model.primaryPauses)
    }

    @Test
    fun theWillNotFitWarningStaysAlongside() {
        val model = homeUiModel(idle(battery = 40).copy(driveFreeBytes = gb), now)
        assertTrue(model.spaceWarning != null)
        assertTrue(model.longRunWarning != null)
    }
}
