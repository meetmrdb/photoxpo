package app.photoxpo.home

import app.photoxpo.backup.BackupStatus
import app.photoxpo.backup.BackupUiState
import app.photoxpo.backup.PauseReason
import app.photoxpo.backup.SessionAccess
import app.photoxpo.backup.sessionAccessBlock
import app.photoxpo.core.StopReason
import app.photoxpo.data.DriveStatus
import app.photoxpo.onboarding.MediaAccessState
import app.photoxpo.onboarding.accessWarning
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
            state = base(status = BackupStatus.Idle, backedUpCount = 12),
            nowEpochMillis = NOW,
        )
        assertEquals("12 photos backed up", model.statusSentence)
        assertEquals("Back up", model.primaryLabel)
        assertFalse(model.primaryPauses)
        assertEquals("Backed up 12", model.progressText)
        assertNull(model.progressFraction)
    }

    @Test
    fun idleWithNothingBackedUp_saysNoneYet() {
        val model = homeUiModel(state = base(backedUpCount = 0), nowEpochMillis = NOW)
        assertEquals("No photos backed up yet", model.statusSentence)
        assertEquals("Back up", homeUiModel(base(), NOW).primaryLabel)
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
    fun pausedByThePerson_offersToBackUpAgain() {
        val model = homeUiModel(
            base(
                status = BackupStatus.Paused(PauseReason.User),
                backedUpCount = 4,
                totalCount = 10,
            ),
            NOW,
        )
        assertEquals("Backup run paused", model.statusSentence)
        assertEquals("Back up", model.primaryLabel)
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
        assertEquals("Back up", model.primaryLabel)
        assertNotNull(model.offlineBanner)
        assertEquals("Your Archive Drive is not connected", model.offlineBanner!!.message)
    }

    @Test
    fun pausedForTheDriveButTheDriveIsBack_saysSoAndOffersToContinue() {
        val model = homeUiModel(
            base(
                status = BackupStatus.Paused(PauseReason.DriveOut),
                backedUpCount = 4,
                totalCount = 10,
                driveStatus = DriveStatus.Ready,
            ),
            NOW,
        )
        assertEquals("Your Archive Drive is back", model.statusSentence)
        assertEquals("Continue backing up", model.primaryLabel)
    }

    @Test
    fun driveFull_saysSoAndOffersAnotherDrive() {
        val model = homeUiModel(
            base(
                status = BackupStatus.DriveFull,
                backedUpCount = 443,
                totalCount = 528,
                driveStatus = DriveStatus.Ready,
            ),
            NOW,
        )
        assertEquals("Your Archive Drive is full", model.statusSentence)
        assertEquals("Choose another drive", model.primaryLabel)
        assertEquals(true, model.driveFull)
    }

    @Test
    fun freeSpace_isShown_andWarnsWhenWhatIsLeftWillNotFit() {
        val roomy = homeUiModel(
            base(status = BackupStatus.Idle, driveStatus = DriveStatus.Ready)
                .copy(driveFreeBytes = 3_300_000_000L, bytesToCopy = 1_000_000_000L),
            NOW,
        )
        assertEquals("3.3 GB free on your Archive Drive", roomy.spaceLine)
        assertEquals(null, roomy.spaceWarning)
        val tight = homeUiModel(
            base(status = BackupStatus.Idle, driveStatus = DriveStatus.Ready)
                .copy(driveFreeBytes = 3_300_000_000L, bytesToCopy = 4_890_000_000L),
            NOW,
        )
        assertEquals(
            "4.9 GB still to back up, but only 3.3 GB is free on this drive",
            tight.spaceWarning,
        )
    }

    @Test
    fun done_offersAnotherRun() {
        val model = homeUiModel(
            base(
                status = BackupStatus.Done,
                backedUpCount = 200,
                totalCount = 200,
            ),
            NOW,
        )
        assertEquals("200 photos backed up", model.statusSentence)
        assertEquals("Back up more", model.primaryLabel)
        assertFalse(model.primaryPauses)
    }

    @Test
    fun runStoppedAtALimit_showsFilesLeftAsNormalProgress_andOffersTheNextRun() {
        val model = homeUiModel(
            base(
                status = BackupStatus.Done,
                backedUpCount = 340,
                totalCount = 4000,
                filesLeft = 3660,
                stopReason = StopReason.SizeLimit,
            ),
            NOW,
            locale = java.util.Locale.US,
        )
        assertEquals("3,660 files left", model.filesLeftLine)
        assertEquals("340 photos backed up", model.statusSentence)
        assertEquals("Backed up 340 of 4000", model.progressText)
        assertEquals("Back up more", model.primaryLabel)
        assertTrue(model.showProgress)
        assertFalse(model.primaryPauses)
        assertFalse(model.allBackedUp)
        assertNull(model.offlineBanner)
    }

    @Test
    fun oneFileLeft_usesTheSingular_andNoStopMeansNoLine() {
        val one = homeUiModel(
            base(status = BackupStatus.Done, backedUpCount = 3, totalCount = 4, filesLeft = 1, stopReason = StopReason.TimeLimit),
            NOW,
            locale = java.util.Locale.US,
        )
        assertEquals("1 file left", one.filesLeftLine)
        assertNull(homeUiModel(base(status = BackupStatus.Done, backedUpCount = 4, totalCount = 4), NOW).filesLeftLine)
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
        assertEquals("Back up", model.primaryLabel)
        assertNull(model.offlineBanner)
    }

    @Test
    fun needsAttention_namesTheOriginal() {
        val model = homeUiModel(
            base(status = BackupStatus.NeedsAttention, backedUpCount = 3, totalCount = 4),
            NOW,
        )
        assertEquals("An Original needs attention", model.statusSentence)
        assertEquals("Back up", model.primaryLabel)
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
    fun partialPhotoAccess_reusesTheOnboardingWarning_andOffersOpenSettings() {
        val model = homeUiModel(
            base(status = BackupStatus.Idle, sessionAccess = SessionAccess.PartialPhotos),
            NOW,
        )
        val warning = checkNotNull(model.accessWarning)
        val copied = checkNotNull(accessWarning(MediaAccessState.Partial))
        assertEquals(copied.title, warning.title)
        assertEquals(copied.message, warning.message)
        assertEquals("Open Settings", warning.actionLabel)
        assertEquals("No photos backed up yet", model.statusSentence)
        assertFalse(model.primaryPauses)
    }

    @Test
    fun deniedPhotoAccess_reusesTheOnboardingWarning_andOffersOpenSettings() {
        val model = homeUiModel(
            base(sessionAccess = SessionAccess.DeniedPhotos),
            NOW,
        )
        val warning = checkNotNull(model.accessWarning)
        val copied = checkNotNull(accessWarning(MediaAccessState.Denied))
        assertEquals(copied.title, warning.title)
        assertEquals(copied.message, warning.message)
        assertEquals("Open Settings", warning.actionLabel)
    }

    @Test
    fun missingPhotoLocation_showsAPlainWarning_andOffersOpenSettings() {
        val model = homeUiModel(
            base(status = BackupStatus.NeedsAttention, sessionAccess = SessionAccess.Location),
            NOW,
        )
        val warning = checkNotNull(model.accessWarning)
        assertEquals("Photo locations are not visible", warning.title)
        assertEquals(
            "Photoxpo cannot see where your photos were taken, so the Original on the Archive Drive would not include it.",
            warning.message,
        )
        assertEquals("Open Settings", warning.actionLabel)
        assertEquals("An Original needs attention", model.statusSentence)
    }

    @Test
    fun fullAccess_hidesTheWarning() {
        assertNull(homeUiModel(base(sessionAccess = null), NOW).accessWarning)
        assertNull(
            sessionAccessBlock(
                hasFullMediaAccess = true,
                hasAccessMediaLocation = true,
            ),
        )
        assertEquals(
            SessionAccess.DeniedPhotos,
            sessionAccessBlock(hasFullMediaAccess = false, hasAccessMediaLocation = true),
        )
        assertEquals(
            SessionAccess.PartialPhotos,
            sessionAccessBlock(
                hasFullMediaAccess = false,
                hasAccessMediaLocation = true,
                imagesGranted = true,
            ),
        )
        assertEquals(
            SessionAccess.Location,
            sessionAccessBlock(hasFullMediaAccess = true, hasAccessMediaLocation = false),
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
        filesLeft: Int = 0,
        stopReason: StopReason? = null,
        sessionAccess: SessionAccess? = null,
    ): BackupUiState {
        return BackupUiState(
            status = status,
            backedUpCount = backedUpCount,
            totalCount = totalCount,
            lastBackedUpAtEpochMillis = lastBackedUpAtEpochMillis,
            driveStatus = driveStatus,
            filesLeft = filesLeft,
            stopReason = stopReason,
            sessionAccess = sessionAccess,
        )
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60L * MINUTE_MS
        const val DAY_MS = 24L * HOUR_MS
    }
}
