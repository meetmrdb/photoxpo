package app.photoxpo.photos

sealed interface LoadUiState {
    data object Idle : LoadUiState

    data class Loading(val progress: Long) : LoadUiState

    data class Loaded(val uri: String) : LoadUiState

    data class Failed(val message: String, val canRetry: Boolean) : LoadUiState

    data class Disabled(val reason: String) : LoadUiState
}

const val LOAD_NEEDS_DRIVE = "Plug in your Archive Drive to load this photo"

const val LOAD_ON_PHONE_AND_DRIVE = "On this phone and your drive"

const val LOAD_DRIVE_UNPLUGGED = "Your drive was unplugged. Plug it back in and try again."

const val LOAD_NOT_ON_DRIVE = "This Original is not on your Archive Drive."

const val LOAD_CHECK_FAILED = "This Original could not be checked."
