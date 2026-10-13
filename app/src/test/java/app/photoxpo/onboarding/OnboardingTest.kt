package app.photoxpo.onboarding

import app.photoxpo.core.PreviewPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {
    @Test
    fun freshState_startsAtWelcomeWithBalancedPreset() {
        val state = OnboardingState()
        assertEquals(OnboardingStep.Welcome, state.step)
        assertFalse(state.driveChosen)
        assertFalse(state.driveDeferred)
        assertEquals(PreviewPreset.BALANCED, state.preset)
    }

    @Test
    fun nextAndBack_moveInEnumOrderAndClampAtTheEnds() {
        var state = OnboardingState()
        state = reduce(state, OnboardingEvent.Back)
        assertEquals(OnboardingStep.Welcome, state.step)

        state = reduce(state, OnboardingEvent.Next)
        assertEquals(OnboardingStep.PhotosAccess, state.step)

        state = reduce(state, OnboardingEvent.Next)
        assertEquals(OnboardingStep.ArchiveDrive, state.step)
        state = reduce(state, OnboardingEvent.DriveDeferred)
        state = reduce(state, OnboardingEvent.Next)
        assertEquals(OnboardingStep.Ready, state.step)
        state = reduce(state, OnboardingEvent.Next)
        assertEquals(OnboardingStep.Ready, state.step)

        state = reduce(state, OnboardingEvent.Back)
        assertEquals(OnboardingStep.ArchiveDrive, state.step)
        state = reduce(state, OnboardingEvent.Back)
        assertEquals(OnboardingStep.PhotosAccess, state.step)
        state = reduce(state, OnboardingEvent.Back)
        assertEquals(OnboardingStep.Welcome, state.step)
    }

    @Test
    fun theFlowIsFourScreens_andPreviewQualityIsNotOneOfThem() {
        assertEquals(
            listOf("Welcome", "PhotosAccess", "ArchiveDrive", "Ready"),
            OnboardingStep.entries.map { it.name },
        )
    }

    @Test
    fun previewsSheet_opensAndClosesOnReady_andBackClosesItBeforeLeavingTheScreen() {
        val ready = OnboardingState(step = OnboardingStep.Ready)
        assertFalse(ready.choosingPreviews)

        val open = reduce(ready, OnboardingEvent.OpenPreviews)
        assertTrue(open.choosingPreviews)
        assertEquals(OnboardingStep.Ready, open.step)

        val closed = reduce(open, OnboardingEvent.ClosePreviews)
        assertFalse(closed.choosingPreviews)
        assertEquals(OnboardingStep.Ready, closed.step)

        val backed = reduce(open, OnboardingEvent.Back)
        assertFalse(backed.choosingPreviews)
        assertEquals(OnboardingStep.Ready, backed.step)
    }

    @Test
    fun archiveDriveNext_staysUntilADriveChoiceIsRecorded() {
        val waiting = OnboardingState(step = OnboardingStep.ArchiveDrive)
        assertEquals(waiting, reduce(waiting, OnboardingEvent.Next))

        val deferred = reduce(waiting, OnboardingEvent.DriveDeferred)
        assertTrue(deferred.driveDeferred)
        assertFalse(deferred.driveChosen)
        assertEquals(OnboardingStep.Ready, reduce(deferred, OnboardingEvent.Next).step)

        val chosen = reduce(
            OnboardingState(step = OnboardingStep.ArchiveDrive, driveDeferred = true),
            OnboardingEvent.DriveChosen,
        )
        assertTrue(chosen.driveChosen)
        assertFalse(chosen.driveDeferred)
        assertEquals(OnboardingStep.Ready, reduce(chosen, OnboardingEvent.Next).step)
    }

    @Test
    fun presetChosen_keepsTheStepAndStoresThePreset() {
        val state = OnboardingState(step = OnboardingStep.Ready, choosingPreviews = true)
        val next = reduce(state, OnboardingEvent.PresetChosen(PreviewPreset.SHARPER))
        assertEquals(OnboardingStep.Ready, next.step)
        assertTrue(next.choosingPreviews)
        assertEquals(PreviewPreset.SHARPER, next.preset)
        assertEquals(
            PreviewPreset.SMALLER,
            reduce(next, OnboardingEvent.PresetChosen(PreviewPreset.SMALLER)).preset,
        )
    }

    @Test
    fun accessWarning_isNullOnlyWhenEveryPhotoIsVisible() {
        assertNull(accessWarning(MediaAccessState.Full))

        val partial = accessWarning(MediaAccessState.Partial)
        assertEquals("Only some photos are visible", partial?.title)
        assertEquals(
            "Only some photos are visible, so the rest will not be Backed up.",
            partial?.message,
        )
        assertEquals(AccessAction.OpenSettings, partial?.action)

        val denied = accessWarning(MediaAccessState.Denied)
        assertEquals("Cannot see photos", denied?.title)
        assertEquals("Photoxpo cannot see your photos.", denied?.message)
        assertEquals(AccessAction.TryAgain, denied?.action)
    }

    @Test
    fun projectedTotalBytes_scalesOnePreviewByTheLibraryCount() {
        assertEquals(0L, projectedTotalBytes(0L, 10))
        assertEquals(9L, projectedTotalBytes(3L, 3))
        assertEquals(0L, projectedTotalBytes(-8L, 4))
        assertEquals(0L, projectedTotalBytes(10L, -3))
        assertEquals(0L, projectedTotalBytes(Long.MIN_VALUE, Int.MIN_VALUE))
    }

    @Test
    fun projectedTotalBytes_saturatesAtLongMax() {
        assertEquals(Long.MAX_VALUE, projectedTotalBytes(Long.MAX_VALUE, 2))
        assertEquals(Long.MAX_VALUE, projectedTotalBytes(Long.MAX_VALUE / 2 + 1, 3))
    }

    @Test
    fun readySummary_copiesDriveAndPresetAndFlagsTheFirstRun() {
        val summary = readySummary(
            OnboardingState(
                step = OnboardingStep.Ready,
                driveChosen = true,
                preset = PreviewPreset.SMALLER,
            ),
        )
        assertEquals(
            ReadySummary(
                driveChosen = true,
                preset = PreviewPreset.SMALLER,
                firstBackupTakesHoursAndCanBePaused = true,
            ),
            summary,
        )
        assertFalse(readySummary(OnboardingState(driveDeferred = true)).driveChosen)
    }
}
