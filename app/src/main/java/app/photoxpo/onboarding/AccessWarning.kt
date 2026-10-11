package app.photoxpo.onboarding

enum class MediaAccessState {
    Full,
    Partial,
    Denied,
}

enum class AccessAction {
    OpenSettings,
    TryAgain,
}

data class AccessWarning(
    val title: String,
    val message: String,
    val action: AccessAction,
)

fun accessWarning(state: MediaAccessState): AccessWarning? {
    return when (state) {
        MediaAccessState.Full -> null
        MediaAccessState.Partial -> AccessWarning(
            title = "Only some photos are visible",
            message = "Only some photos are visible, so the rest will not be Backed up.",
            action = AccessAction.OpenSettings,
        )
        MediaAccessState.Denied -> AccessWarning(
            title = "Cannot see photos",
            message = "Photoxpo cannot see your photos.",
            action = AccessAction.TryAgain,
        )
    }
}
