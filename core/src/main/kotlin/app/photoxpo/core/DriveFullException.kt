package app.photoxpo.core

import java.io.IOException

/** The Archive Drive has no room left for the Original being copied. */
class DriveFullException(cause: Throwable? = null) : IOException("Archive Drive is full", cause)
