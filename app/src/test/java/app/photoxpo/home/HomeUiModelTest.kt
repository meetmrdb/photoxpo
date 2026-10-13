package app.photoxpo.home

import app.photoxpo.backup.BackupStatus
import app.photoxpo.backup.BackupUiState
import app.photoxpo.backup.PauseReason
import app.photoxpo.backup.SessionAccess
import app.photoxpo.backup.sessionAccessBlock
import app.photoxpo.core.RunStage
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
        assertEquals("Backup paused", model.statusSentence)
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
        assertEquals("Size limit reached. 3,660 files left.", model.statusSentence)
        assertEquals("Backed up 340 of 4000", model.progressText)
        assertEquals("Back up more", model.primaryLabel)
        assertTrue(model.showProgress)
        assertFalse(model.primaryPauses)
        assertFalse(model.allBackedUp)
        assertNull(model.offlineBanner)
    }

    @Test
    fun lowBatteryStop_showsPausedHeadlineWithFilesLeft_asNormalProgress() {
        val model = homeUiModel(
            base(
                status = BackupStatus.Done,
                backedUpCount = 340,
                totalCount = 4000,
                filesLeft = 3660,
                stopReason = StopReason.LowBattery,
            ),
            NOW,
            locale = java.util.Locale.US,
        )
        assertEquals(
            "Paused at 15% battery. 3,660 files left. Charge, then reconnect your drive.",
            model.statusSentence,
        )
        assertEquals("Back up more", model.primaryLabel)
        assertNull(model.offlineBanner)
        assertFalse(model.primaryPauses)
    }

    @Test
    fun oneFileLeft_usesTheSingular_andNoStopMeansNoLine() {
        val one = homeUiModel(
            base(status = BackupStatus.Done, backedUpCount = 3, totalCount = 4, filesLeft = 1, stopReason = StopReason.TimeLimit),
            NOW,
            locale = java.util.Locale.US,
        )
        assertEquals("Time limit reached. 1 file left.", one.statusSentence)
        assertEquals(
            "4 photos backed up",
            homeUiModel(base(status = BackupStatus.Done, backedUpCount = 4, totalCount = 4), NOW).statusSentence,
        )
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

    @Test
    fun idle_saysHowManyNewPhotosAndVideosAreWaiting() {
        val state = base(backedUpCount = 509).copy(filesToCopy = 12, videosToCopy = 1, bytesToCopy = 1_400_000_000L)
        assertEquals("12 new photos and videos · 1.4 GB to back up", homeUiModel(state, NOW).newLine)
    }

    @Test
    fun newLine_namesOnlyPhotos_orOnlyVideos_andHandlesOne() {
        val photos = base().copy(filesToCopy = 3, videosToCopy = 0, bytesToCopy = 9_000_000L)
        assertEquals("3 new photos · 9 MB to back up", homeUiModel(photos, NOW).newLine)
        val video = base().copy(filesToCopy = 1, videosToCopy = 1, bytesToCopy = 86_000_000L)
        assertEquals("1 new video · 86 MB to back up", homeUiModel(video, NOW).newLine)
    }

    @Test
    fun newLine_isAbsentWhenNothingIsWaiting_orWhileRunning_orWithoutTheDrive() {
        assertNull(homeUiModel(base().copy(filesToCopy = 0), NOW).newLine)
        assertNull(homeUiModel(base().copy(filesToCopy = null), NOW).newLine)
        assertNull(homeUiModel(base(status = BackupStatus.Running).copy(filesToCopy = 5), NOW).newLine)
        assertNull(homeUiModel(base(driveStatus = DriveStatus.Absent).copy(filesToCopy = 5), NOW).newLine)
    }

    @Test
    fun running_tellsThePersonTheyCanUnplugTheDrive() {
        val running = homeUiModel(base(status = BackupStatus.Running), NOW)
        assertEquals(UNPLUG_REASSURANCE, running.unplugReassurance)
        assertNull(homeUiModel(base(), NOW).unplugReassurance)
    }

    @Test
    fun unpluggedDrive_saysNothingIsLost() {
        assertTrue(app.photoxpo.backup.MESSAGE_DRIVE_OUT.contains("Nothing is lost"))
    }

    @Test
    fun running_saysWhetherItIsBackingUpOrChecking_andHowFarTheCheckIs() {
        val copying = base(status = BackupStatus.Running, backedUpCount = 492, totalCount = 497)
            .copy(runStage = RunStage.Copying, stageDone = 1, stageTotal = 5)
        val copyingModel = homeUiModel(copying, NOW)
        assertEquals("Backing up your photos", copyingModel.statusSentence)
        assertEquals("Backed up 492 of 497", copyingModel.progressText)

        val checking = copying.copy(runStage = RunStage.Checking, stageDone = 320, stageTotal = 509)
        val checkingModel = homeUiModel(checking, NOW)
        assertEquals("Checking your backed up photos", checkingModel.statusSentence)
        assertEquals("Checked 320 of 509 already on your drive", checkingModel.progressText)
        assertEquals(320f / 509f, checkingModel.progressFraction!!, 0.001f)
    }

    @Test
    fun checkingStageIsIgnoredOnceTheRunIsNotRunning() {
        val paused = base(status = BackupStatus.Paused(PauseReason.User), backedUpCount = 5)
            .copy(runStage = RunStage.Checking, stageDone = 3, stageTotal = 9)
        assertEquals("Backed up 5", homeUiModel(paused, NOW).progressText)
    }

    @Test
    fun running_barShowsThisRunsFiles_notTheWholeLibrary() {
        val running = base(status = BackupStatus.Running, backedUpCount = 503, totalCount = 506)
            .copy(runStage = RunStage.Copying, runFilesToCopy = 4, runFilesDone = 1)
        val model = homeUiModel(running, NOW)
        assertEquals("Backed up 1 of 4", model.progressText)
        assertEquals(0.25f, model.progressFraction!!, 0.0001f)
    }

    @Test
    fun nothingLeft_saysEverythingIsSafe_andHintsAtPullDown() {
        val done = homeUiModel(base(backedUpCount = 12, totalCount = 12).copy(filesToCopy = 0), NOW)
        assertEquals("Everything on your phone is safe on your Archive Drive.", done.heroSubline)
        assertEquals("Pull down to check for new photos", done.pullHint)
        val waiting = homeUiModel(base(backedUpCount = 12, totalCount = 15).copy(filesToCopy = 3), NOW)
        assertNull(waiting.heroSubline)
        assertNull(waiting.pullHint)
    }

    @Test
    fun stoppedAtASizeOrTimeLimit_saysItIsANormalStop_butLowBatteryDoesNot() {
        fun stopped(reason: StopReason) = homeUiModel(
            base(status = BackupStatus.Done, backedUpCount = 340, totalCount = 4000, filesLeft = 3660, stopReason = reason),
            NOW,
        )
        val normal = "That is a normal stop. Back up more continues where it left off."
        assertEquals(normal, stopped(StopReason.SizeLimit).heroSubline)
        assertEquals(normal, stopped(StopReason.TimeLimit).heroSubline)
        assertNull(stopped(StopReason.LowBattery).heroSubline)
    }

    @Test
    fun newWaiting_isSplitIntoTheCountAndTheSize() {
        val withSize = homeUiModel(
            base(backedUpCount = 509).copy(filesToCopy = 12, videosToCopy = 1, bytesToCopy = 1_400_000_000L),
            NOW,
        )
        assertEquals("12 new photos and videos", withSize.newTitle)
        assertEquals("1.4 GB to back up", withSize.newDetail)
        val withoutSize = homeUiModel(base().copy(filesToCopy = 3, videosToCopy = 0), NOW)
        assertEquals("3 new photos", withoutSize.newTitle)
        assertNull(withoutSize.newDetail)
        assertEquals("3 new photos to back up", withoutSize.newLine)
    }

    @Test
    fun historyCard_carriesWhenItLastRun_andTheFreeSpace() {
        val last = 1_000_000L
        val roomy = homeUiModel(
            base(lastBackedUpAtEpochMillis = last).copy(driveFreeBytes = 3_300_000_000L),
            last + 3 * HOUR_MS,
        )
        assertEquals(
            listOf("Last backed up 3 hours ago", "3.3 GB free on your Archive Drive"),
            roomy.historyLines,
        )
        val noSpace = homeUiModel(base(lastBackedUpAtEpochMillis = last), last + 3 * HOUR_MS)
        assertEquals(listOf("Last backed up 3 hours ago"), noSpace.historyLines)
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
