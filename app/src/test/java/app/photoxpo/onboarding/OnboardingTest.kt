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
        assertEquals(OnboardingStep.PreviewQuality, state.step)

        state = reduce(state, OnboardingEvent.Next)
        assertEquals(OnboardingStep.Ready, state.step)
        state = reduce(state, OnboardingEvent.Next)
        assertEquals(OnboardingStep.Ready, state.step)

        state = reduce(state, OnboardingEvent.Back)
        assertEquals(OnboardingStep.PreviewQuality, state.step)
        state = reduce(state, OnboardingEvent.Back)
        assertEquals(OnboardingStep.ArchiveDrive, state.step)
        state = reduce(state, OnboardingEvent.Back)
        assertEquals(OnboardingStep.PhotosAccess, state.step)
        state = reduce(state, OnboardingEvent.Back)
        assertEquals(OnboardingStep.Welcome, state.step)
    }

    @Test
    fun archiveDriveNext_staysUntilADriveChoiceIsRecorded() {
        val waiting = OnboardingState(step = OnboardingStep.ArchiveDrive)
        assertEquals(waiting, reduce(waiting, OnboardingEvent.Next))

        val deferred = reduce(waiting, OnboardingEvent.DriveDeferred)
        assertTrue(deferred.driveDeferred)
        assertFalse(deferred.driveChosen)
        assertEquals(OnboardingStep.PreviewQuality, reduce(deferred, OnboardingEvent.Next).step)

        val chosen = reduce(
            OnboardingState(step = OnboardingStep.ArchiveDrive, driveDeferred = true),
            OnboardingEvent.DriveChosen,
        )
        assertTrue(chosen.driveChosen)
        assertFalse(chosen.driveDeferred)
        assertEquals(OnboardingStep.PreviewQuality, reduce(chosen, OnboardingEvent.Next).step)
    }

    @Test
    fun presetChosen_keepsTheStepAndStoresThePreset() {
        val state = OnboardingState(step = OnboardingStep.PreviewQuality)
        val next = reduce(state, OnboardingEvent.PresetChosen(PreviewPreset.SHARPER))
        assertEquals(OnboardingStep.PreviewQuality, next.step)
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
