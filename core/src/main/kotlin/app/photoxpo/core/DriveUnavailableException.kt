package app.photoxpo.core

import java.io.IOException

class DriveUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
