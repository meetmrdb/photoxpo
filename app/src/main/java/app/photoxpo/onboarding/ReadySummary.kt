package app.photoxpo.onboarding

import app.photoxpo.core.PreviewPreset

data class ReadySummary(
    val driveChosen: Boolean,
    val preset: PreviewPreset,
    val firstBackupTakesHoursAndCanBePaused: Boolean = true,
)

fun readySummary(state: OnboardingState): ReadySummary {
    return ReadySummary(
        driveChosen = state.driveChosen,
        preset = state.preset,
    )
}
