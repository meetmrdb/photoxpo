package app.photoxpo.data

enum class DriveStatus {
    Absent,
    ReadOnly,
    Ready,
}

/**
 * Same strings as Environment.MEDIA_MOUNTED and Environment.MEDIA_MOUNTED_READ_ONLY.
 * Kept here so the mapping can be tested without the Android framework.
 */
internal const val VOLUME_MOUNTED = "mounted"

internal const val VOLUME_MOUNTED_READ_ONLY = "mounted_ro"

/** A read-only volume is not usable as an Archive Drive. */
fun isReadOnlyVolume(volumeState: String): Boolean = volumeState == VOLUME_MOUNTED_READ_ONLY

fun driveStatus(
    probeFound: Boolean,
    supportsWrite: Boolean,
    volumeState: String?,
): DriveStatus {
    if (volumeState != null && isReadOnlyVolume(volumeState)) {
        return DriveStatus.ReadOnly
    }
    if (!probeFound || (volumeState != null && volumeState != VOLUME_MOUNTED)) {
        return DriveStatus.Absent
    }
    if (!supportsWrite) {
        return DriveStatus.ReadOnly
    }
    return DriveStatus.Ready
}
