package app.photoxpo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveStatusTest {
    @Test
    fun readOnlyVolume_isNotUsable() {
        assertTrue(isReadOnlyVolume(VOLUME_MOUNTED_READ_ONLY))
        assertFalse(isReadOnlyVolume(VOLUME_MOUNTED))
        assertEquals(
            DriveStatus.ReadOnly,
            driveStatus(probeFound = true, supportsWrite = true, volumeState = VOLUME_MOUNTED_READ_ONLY),
        )
        assertEquals(
            DriveStatus.ReadOnly,
            driveStatus(probeFound = false, supportsWrite = false, volumeState = VOLUME_MOUNTED_READ_ONLY),
        )
    }

    @Test
    fun missingProbe_isAbsent() {
        assertEquals(
            DriveStatus.Absent,
            driveStatus(probeFound = false, supportsWrite = false, volumeState = null),
        )
        assertEquals(
            DriveStatus.Absent,
            driveStatus(probeFound = false, supportsWrite = true, volumeState = VOLUME_MOUNTED),
        )
        assertEquals(
            DriveStatus.Absent,
            driveStatus(probeFound = true, supportsWrite = true, volumeState = "unmounted"),
        )
        assertEquals(
            DriveStatus.Absent,
            driveStatus(probeFound = true, supportsWrite = true, volumeState = "removed"),
        )
    }

    @Test
    fun mountedVolumeThatCannotCreate_isReadOnly() {
        assertEquals(
            DriveStatus.ReadOnly,
            driveStatus(probeFound = true, supportsWrite = false, volumeState = VOLUME_MOUNTED),
        )
    }

    @Test
    fun mountedWritableProbe_isReady() {
        assertEquals(
            DriveStatus.Ready,
            driveStatus(probeFound = true, supportsWrite = true, volumeState = VOLUME_MOUNTED),
        )
        assertEquals(
            DriveStatus.Ready,
            driveStatus(probeFound = true, supportsWrite = true, volumeState = null),
        )
    }
}
