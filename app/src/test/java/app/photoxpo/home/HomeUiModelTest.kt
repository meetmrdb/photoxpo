package app.photoxpo.home

import app.photoxpo.backup.BackupStatus
import app.photoxpo.backup.BackupUiState
import app.photoxpo.backup.PauseReason
import app.photoxpo.data.DriveStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiModelTest {
    @Test
    fun idle_saysHowManyPhotosAreBackedUp_andOffersTheFileCap() {
        val model = homeUiModel(
            state = base(status = BackupStatus.Idle, backedUpCount = 12, fileCap = 200),
            nowEpochMillis = NOW,
        )
        assertEquals("12 photos backed up", model.statusSentence)
        assertEquals("Back up 200 files", model.primaryLabel)
        assertFalse(model.primaryPauses)
        assertEquals("Backed up 12", model.progressText)
        assertNull(model.progressFraction)
    }

    @Test
    fun idleWithNothingBackedUp_saysNoneYet() {
        val model = homeUiModel(state = base(backedUpCount = 0), nowEpochMillis = NOW)
        assertEquals("No photos backed up yet", model.statusSentence)
        assertEquals("Back up 1 file", homeUiModel(base(fileCap = 1), NOW).primaryLabel)
    }

    @Test
    fun onePhoto_usesTheSingular() {
        val model = homeUiModel(base(backedUpCount = 1, totalCount = 1), NOW)
        assertEquals("1 photo backed up", model.statusSentence)
        assertEquals("Backed up 1 of 1", model.progressText)
        assertEquals(1f, model.progressFraction!!, 0.0001f)
    }

    @Test
    fun running_offersPause_andShowsProgress() {
        val model = homeUiModel(
            base(
                status = BackupStatus.Running,
                backedUpCount = 48,
                totalCount = 200,
                fileCap = 200,
            ),
            NOW,
        )
        assertEquals("Backing up your photos", model.statusSentence)
        assertEquals("Pause", model.primaryLabel)
        assertTrue(model.primaryPauses)
        assertEquals("Backed up 48 of 200", model.progressText)
        assertEquals(0.24f, model.progressFraction!!, 0.0001f)
        assertFalse(model.showDriveMismatch)
    }

    @Test
    fun runningWithNoTotal_hasNoFraction() {
        val model = homeUiModel(
            base(status = BackupStatus.Running, backedUpCount = 0, totalCount = 0),
            NOW,
        )
        assertNull(model.progressFraction)
        assertEquals("Backed up 0", model.progressText)
        assertTrue(model.primaryPauses)
    }

    @Test
    fun pausedByThePerson_offersTheFileCapAgain() {
        val model = homeUiModel(
            base(
                status = BackupStatus.Paused(PauseReason.User),
                backedUpCount = 4,
                totalCount = 10,
                fileCap = 50,
            ),
            NOW,
        )
        assertEquals("Backup run paused", model.statusSentence)
        assertEquals("Back up 50 files", model.primaryLabel)
        assertFalse(model.primaryPauses)
        assertEquals("Backed up 4 of 10", model.progressText)
        assertEquals(0.4f, model.progressFraction!!, 0.0001f)
    }

    @Test
    fun pausedBecauseTheArchiveDriveLeft_saysItIsNotConnected() {
        val model = homeUiModel(
            base(
                status = BackupStatus.Paused(PauseReason.DriveOut),
                backedUpCount = 4,
                totalCount = 10,
                driveStatus = DriveStatus.Absent,
            ),
            NOW,
        )
        assertEquals("Your Archive Drive is not connected", model.statusSentence)
        assertEquals("Back up 200 files", model.primaryLabel)
        assertNotNull(model.offlineBanner)
        assertEquals("Your Archive Drive is not connected", model.offlineBanner!!.message)
    }

    @Test
    fun done_offersAnotherCappedRun() {
        val model = homeUiModel(
            base(
                status = BackupStatus.Done,
                backedUpCount = 200,
                totalCount = 200,
                fileCap = 200,
            ),
            NOW,
        )
        assertEquals("200 photos backed up", model.statusSentence)
        assertEquals("Back up 200 more files", model.primaryLabel)
        assertFalse(model.primaryPauses)
        val oneMore = homeUiModel(base(status = BackupStatus.Done, fileCap = 1), NOW)
        assertEquals("Back up 1 more file", oneMore.primaryLabel)
    }

    @Test
    fun driveMismatch_asksToConfirmThisArchiveDrive() {
        val model = homeUiModel(
            base(
                status = BackupStatus.DriveMismatch,
                backedUpCount = 4,
                totalCount = 10,
                driveStatus = DriveStatus.Ready,
            ),
            NOW,
        )
        assertEquals(
            "This Archive Drive is not the one this phone remembers",
            model.statusSentence,
        )
        assertTrue(model.showDriveMismatch)
        assertEquals("Back up 200 files", model.primaryLabel)
        assertNull(model.offlineBanner)
    }

    @Test
    fun needsAttention_namesTheOriginal() {
        val model = homeUiModel(
            base(status = BackupStatus.NeedsAttention, backedUpCount = 3, totalCount = 4),
            NOW,
        )
        assertEquals("An Original needs attention", model.statusSentence)
        assertEquals("Back up 200 files", model.primaryLabel)
        assertFalse(model.showDriveMismatch)
    }

    @Test
    fun offlineBanner_showsWhenTheArchiveDriveIsMissing_andHidesWhenItIsReady() {
        val missing = homeUiModel(
            base(
                backedUpCount = 8,
                lastBackedUpAtEpochMillis = NOW - 2 * HOUR_MS,
                driveStatus = DriveStatus.Absent,
            ),
            NOW,
        )
        assertNotNull(missing.offlineBanner)
        assertEquals("Your Archive Drive is not connected", missing.offlineBanner!!.message)
        assertEquals("Last backed up 2 hours ago", missing.offlineBanner.lastBackedUpLine)
        assertEquals("Last backed up 2 hours ago", missing.lastBackedUpLine)

        val ready = homeUiModel(base(driveStatus = DriveStatus.Ready), NOW)
        assertNull(ready.offlineBanner)

        val readOnly = homeUiModel(base(driveStatus = DriveStatus.ReadOnly), NOW)
        assertEquals(
            "Photoxpo cannot copy onto this Archive Drive",
            readOnly.offlineBanner!!.message,
        )
    }

    @Test
    fun lastBackedUp_usesTheInjectedClock() {
        val last = 1_000_000L
        fun line(now: Long): String {
            return homeUiModel(base(lastBackedUpAtEpochMillis = last), now).lastBackedUpLine
        }

        assertEquals(
            "Last backed up never",
            homeUiModel(base(lastBackedUpAtEpochMillis = null), NOW).lastBackedUpLine,
        )
        assertEquals("Last backed up just now", line(last))
        assertEquals("Last backed up just now", line(last + MINUTE_MS - 1))
        assertEquals("Last backed up just now", line(last - 5_000L))
        assertEquals("Last backed up 1 minute ago", line(last + MINUTE_MS))
        assertEquals("Last backed up 5 minutes ago", line(last + 5 * MINUTE_MS))
        assertEquals("Last backed up 1 hour ago", line(last + HOUR_MS))
        assertEquals("Last backed up 3 hours ago", line(last + 3 * HOUR_MS))
        assertEquals("Last backed up yesterday", line(last + DAY_MS))
        assertEquals("Last backed up 4 days ago", line(last + 4 * DAY_MS))
    }

    private fun base(
        status: BackupStatus = BackupStatus.Idle,
        backedUpCount: Int = 0,
        totalCount: Int = 0,
        lastBackedUpAtEpochMillis: Long? = null,
        driveStatus: DriveStatus = DriveStatus.Ready,
        fileCap: Int = 200,
    ): BackupUiState {
        return BackupUiState(
            status = status,
            backedUpCount = backedUpCount,
            totalCount = totalCount,
            lastBackedUpAtEpochMillis = lastBackedUpAtEpochMillis,
            driveStatus = driveStatus,
            fileCap = fileCap,
        )
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60L * MINUTE_MS
        const val DAY_MS = 24L * HOUR_MS
    }
}
