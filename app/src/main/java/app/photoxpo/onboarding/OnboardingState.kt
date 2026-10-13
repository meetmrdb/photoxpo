package app.photoxpo.onboarding

import app.photoxpo.core.PreviewPreset

enum class OnboardingStep {
    Welcome,
    PhotosAccess,
    ArchiveDrive,
    Ready,
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val driveChosen: Boolean = false,
    val driveDeferred: Boolean = false,
    val preset: PreviewPreset = PreviewPreset.BALANCED,
    /** The Preview quality sheet is open over the Ready screen. Balanced stands unless the person changes it. */
    val choosingPreviews: Boolean = false,
)

sealed interface OnboardingEvent {
    data object Next : OnboardingEvent

    data object Back : OnboardingEvent

    data object DriveChosen : OnboardingEvent

    /** "Do this later" on the Archive Drive step. */
    data object DriveDeferred : OnboardingEvent

    data class PresetChosen(val preset: PreviewPreset) : OnboardingEvent

    /** "Change" on the Ready screen. */
    data object OpenPreviews : OnboardingEvent

    data object ClosePreviews : OnboardingEvent
}

fun reduce(state: OnboardingState, event: OnboardingEvent): OnboardingState {
    return when (event) {
        OnboardingEvent.Next -> {
            if (state.step == OnboardingStep.ArchiveDrive && !state.driveChosen && !state.driveDeferred) {
                state
            } else {
                state.copy(step = state.step.shift(1))
            }
        }
        OnboardingEvent.Back ->
            if (state.choosingPreviews) state.copy(choosingPreviews = false) else state.copy(step = state.step.shift(-1))
        OnboardingEvent.DriveChosen -> state.copy(driveChosen = true, driveDeferred = false)
        OnboardingEvent.DriveDeferred -> state.copy(driveDeferred = true, driveChosen = false)
        is OnboardingEvent.PresetChosen -> state.copy(preset = event.preset)
        OnboardingEvent.OpenPreviews -> state.copy(choosingPreviews = true)
        OnboardingEvent.ClosePreviews -> state.copy(choosingPreviews = false)
    }
}

private fun OnboardingStep.shift(delta: Int): OnboardingStep {
    val steps = OnboardingStep.entries
    val index = (ordinal + delta).coerceIn(0, steps.lastIndex)
    return steps[index]
}
