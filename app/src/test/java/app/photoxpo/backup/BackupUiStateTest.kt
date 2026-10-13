package app.photoxpo.backup

import app.photoxpo.core.BackupItemOutcome
import app.photoxpo.core.BackupRunResult
import app.photoxpo.core.BackupRunStatus
import app.photoxpo.core.ItemState
import app.photoxpo.core.RunStage
import app.photoxpo.core.StopReason
import app.photoxpo.data.DriveStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupUiStateTest {
    @Test
    fun freshState_isIdle() {
        val state = BackupUiState()
        assertEquals(BackupStatus.Idle, state.status)
        assertEquals(0, state.backedUpCount)
        assertEquals(0, state.totalCount)
        assertNull(state.lastBackedUpAtEpochMillis)
        assertEquals(DriveStatus.Absent, state.driveStatus)
        assertNull(state.message)
    }

    @Test
    fun completedRun_isDoneAndRecordsWhenItWasBackedUp() {
        val previous = BackupUiState(driveStatus = DriveStatus.Ready, totalCount = 2)
        val next = reduceBackupRun(
            state = previous,
            result = result(
                BackupRunStatus.Completed,
                item("a", ItemState.COMMITTED),
                item("b", ItemState.COMMITTED),
            ),
            nowEpochMillis = 50L,
        )
        assertEquals(BackupStatus.Done, next.status)
        assertEquals(2, next.backedUpCount)
        assertEquals(2, next.totalCount)
        assertEquals(50L, next.lastBackedUpAtEpochMillis)
        assertEquals(DriveStatus.Ready, next.driveStatus)
        assertNull(next.message)
    }

    @Test
    fun completedRun_withNeedsAttention_keepsTheBackedUpTime() {
        val previous = BackupUiState(lastBackedUpAtEpochMillis = 9L, totalCount = 2)
        val next = reduceBackupRun(
            state = previous,
            result = result(
                BackupRunStatus.Completed,
                item("a", ItemState.COMMITTED),
                item("b", ItemState.NEEDS_ATTENTION),
            ),
            nowEpochMillis = 80L,
        )
        assertEquals(BackupStatus.NeedsAttention, next.status)
        assertEquals(MESSAGE_NEEDS_ATTENTION, next.message)
        assertEquals(1, next.backedUpCount)
        assertEquals(80L, next.lastBackedUpAtEpochMillis)
    }

    @Test
    fun pausedByUser_countsOnlyBackedUpOriginals() {
        val next = reduceBackupRun(
            state = BackupUiState(totalCount = 3, driveStatus = DriveStatus.Ready),
            result = result(
                BackupRunStatus.Paused,
                item("a", ItemState.COMMITTED),
                item("b", ItemState.PAUSED_DRIVE_OUT),
                item("c", ItemState.PAUSED_DRIVE_OUT),
            ),
            nowEpochMillis = 11L,
            pauseReason = PauseReason.User,
        )
        assertEquals(BackupStatus.Paused(PauseReason.User), next.status)
        assertEquals(MESSAGE_PAUSED_USER, next.message)
        assertEquals(1, next.backedUpCount)
        assertEquals(3, next.totalCount)
        assertEquals(11L, next.lastBackedUpAtEpochMillis)
    }

    @Test
    fun pausedBecauseArchiveDriveLeft_usesDriveOut() {
        val next = reduceBackupRun(
            state = BackupUiState(driveStatus = DriveStatus.Ready),
            result = result(
                BackupRunStatus.Paused,
                item("a", ItemState.PAUSED_DRIVE_OUT),
            ),
            nowEpochMillis = 12L,
            pauseReason = PauseReason.DriveOut,
        )
        assertEquals(BackupStatus.Paused(PauseReason.DriveOut), next.status)
        assertEquals(MESSAGE_DRIVE_OUT, next.message)
        assertEquals(0, next.backedUpCount)
        assertNull(next.lastBackedUpAtEpochMillis)
    }

    @Test
    fun driveMismatch_keepsCountsAndTime() {
        val previous = BackupUiState(
            status = BackupStatus.Running,
            backedUpCount = 4,
            totalCount = 10,
            lastBackedUpAtEpochMillis = 3L,
            driveStatus = DriveStatus.Ready,
        )
        val next = reduceBackupRun(
            state = previous,
            result = BackupRunResult(
                manifestPath = "",
                items = emptyList(),
                status = BackupRunStatus.DriveMismatch,
            ),
            nowEpochMillis = 99L,
        )
        assertEquals(BackupStatus.DriveMismatch, next.status)
        assertEquals(MESSAGE_DRIVE_MISMATCH, next.message)
        assertEquals(4, next.backedUpCount)
        assertEquals(10, next.totalCount)
        assertEquals(3L, next.lastBackedUpAtEpochMillis)
        assertEquals(DriveStatus.Ready, next.driveStatus)
    }

    @Test
    fun runStarted_clearsTheCountAndKeepsTheFallbackNote() {
        val started = BackupUiState(
            message = MESSAGE_CONNECTED_DEVICE_FALLBACK,
            backedUpCount = 5,
            lastBackedUpAtEpochMillis = 7L,
        ).runStarted(totalCount = 12, driveStatus = DriveStatus.Ready)
        assertEquals(BackupStatus.Running, started.status)
        assertEquals(0, started.backedUpCount)
        assertEquals(12, started.totalCount)
        assertEquals(DriveStatus.Ready, started.driveStatus)
        assertEquals(7L, started.lastBackedUpAtEpochMillis)
        assertEquals(MESSAGE_CONNECTED_DEVICE_FALLBACK, started.message)
        assertEquals("Backed up 0 of 12", backupProgressText(started))
    }

    @Test
    fun backedUpCount_updatesOnlyWhileRunning() {
        val running = BackupUiState(status = BackupStatus.Running).withBackedUpCount(3)
        assertEquals(3, running.backedUpCount)
        val paused = BackupUiState(status = BackupStatus.Paused(PauseReason.User), backedUpCount = 1)
        assertEquals(1, paused.withBackedUpCount(9).backedUpCount)
    }

    @Test
    fun runStoppedBecauseTheDriveIsFull_isTheDriveFullStatusWithFilesLeft_notAnError() {
        val next = reduceBackupRun(
            state = BackupUiState(totalCount = 5, driveStatus = DriveStatus.Ready),
            result = BackupRunResult(
                manifestPath = "Photoxpo/.photoxpo/runs/run.jsonl",
                items = listOf(item("a", ItemState.COMMITTED), item("b", ItemState.COMMITTED)),
                status = BackupRunStatus.Completed,
                stopReason = StopReason.DriveFull,
                filesLeft = 3,
            ),
            nowEpochMillis = 50L,
        )
        assertEquals(BackupStatus.DriveFull, next.status)
        assertEquals(StopReason.DriveFull, next.stopReason)
        assertEquals(3, next.filesLeft)
        assertNull(next.message)
    }

    @Test
    fun runStoppedAtALimit_isDoneWithFilesLeft_notAnErrorOrAMessage() {
        val next = reduceBackupRun(
            state = BackupUiState(totalCount = 5, driveStatus = DriveStatus.Ready),
            result = BackupRunResult(
                manifestPath = "Photoxpo/.photoxpo/runs/run.jsonl",
                items = listOf(item("a", ItemState.COMMITTED), item("b", ItemState.COMMITTED)),
                status = BackupRunStatus.Completed,
                stopReason = StopReason.SizeLimit,
                filesLeft = 3,
            ),
            nowEpochMillis = 50L,
        )
        assertEquals(BackupStatus.Done, next.status)
        assertEquals(StopReason.SizeLimit, next.stopReason)
        assertEquals(3, next.filesLeft)
        assertNull(next.message)
        assertEquals(50L, next.lastBackedUpAtEpochMillis)
    }

    @Test
    fun nextRunStarting_clearsTheStop() {
        val started = BackupUiState(stopReason = StopReason.TimeLimit, filesLeft = 9)
            .runStarted(totalCount = 9, driveStatus = DriveStatus.Ready)
        assertNull(started.stopReason)
        assertEquals(0, started.filesLeft)
    }

    @Test
    fun runStarted_remembersHowManyFilesThisRunHasToBackUp() {
        val started = BackupUiState().runStarted(totalCount = 506, driveStatus = DriveStatus.Ready, filesToCopy = 4)
        assertEquals(4, started.runFilesToCopy)
        assertEquals(0, started.runFilesDone)
    }

    @Test
    fun eachNewFile_addsToTheRunsOwnCount() {
        val running = BackupUiState().runStarted(totalCount = 506, driveStatus = DriveStatus.Ready, filesToCopy = 4)
        val after = running.withCopied(10L, 1L).withCopied(20L, 2L)
        assertEquals(2, after.runFilesDone)
    }

    @Test
    fun whileCopying_progressIsThisRunsOwn_notTheWholeLibrarys() {
        val state = BackupUiState().runStarted(totalCount = 506, driveStatus = DriveStatus.Ready, filesToCopy = 4)
            .copy(backedUpCount = 503, runStage = RunStage.Copying, runFilesDone = 1)
        assertEquals("Backed up 1 of 4", backupProgressText(state))
        assertEquals(RunFiles(done = 1, total = 4), state.runFiles())
    }

    @Test
    fun pausedMidRun_keepsThisRunsProgress() {
        val paused = BackupUiState().runStarted(totalCount = 506, driveStatus = DriveStatus.Ready, filesToCopy = 4)
            .copy(status = BackupStatus.Paused(PauseReason.User), runFilesDone = 2)
        assertEquals("Backed up 2 of 4", backupProgressText(paused))
    }

    @Test
    fun whileChecking_theCheckKeepsItsOwnWords() {
        val checking = BackupUiState().runStarted(totalCount = 506, driveStatus = DriveStatus.Ready, filesToCopy = 4)
            .copy(runStage = RunStage.Checking, stageDone = 320, stageTotal = 509)
        assertEquals("Checked 320 of 509 already on your drive", backupProgressText(checking))
        assertNull(checking.runFiles())
    }

    @Test
    fun afterTheRun_progressIsTheWholeLibrarys_andARunWithNothingNewHasNoRunCount() {
        val done = BackupUiState(status = BackupStatus.Done, backedUpCount = 340, totalCount = 4000, runFilesToCopy = 4, runFilesDone = 4)
        assertEquals("Backed up 340 of 4000", backupProgressText(done))
        val nothingNew = BackupUiState().runStarted(totalCount = 506, driveStatus = DriveStatus.Ready, filesToCopy = 0)
        assertNull(nothingNew.runFiles())
    }

    private fun result(status: BackupRunStatus, vararg items: BackupItemOutcome): BackupRunResult {
        return BackupRunResult(
            manifestPath = "Photoxpo/.photoxpo/runs/run.jsonl",
            items = items.toList(),
            status = status,
        )
    }

    private fun item(id: String, state: ItemState): BackupItemOutcome {
        return BackupItemOutcome(id = id, name = "$id.jpg", state = state)
    }
}
