package app.photoxpo.onboarding

import app.photoxpo.formatByteCount
import app.photoxpo.core.MediaFile
import app.photoxpo.core.MediaKind
import app.photoxpo.core.PreviewPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingCopyTest {
    @Test
    fun classifyMediaAccess_usesFullAccessThenPartialGrants() {
        assertEquals(MediaAccessState.Full, classifyMediaAccess(true, false, false, false))
        assertEquals(MediaAccessState.Partial, classifyMediaAccess(false, true, false, false))
        assertEquals(MediaAccessState.Partial, classifyMediaAccess(false, false, true, false))
        assertEquals(MediaAccessState.Partial, classifyMediaAccess(false, false, false, true))
        assertEquals(MediaAccessState.Partial, classifyMediaAccess(false, true, true, false))
        assertEquals(MediaAccessState.Denied, classifyMediaAccess(false, false, false, false))
    }

    @Test
    fun showAccessWarning_waitsForADeniedAnswer() {
        assertFalse(showAccessWarning(null, requested = false))
        assertFalse(showAccessWarning(MediaAccessState.Full, requested = true))
        assertFalse(showAccessWarning(MediaAccessState.Denied, requested = false))
        assertTrue(showAccessWarning(MediaAccessState.Denied, requested = true))
        assertTrue(showAccessWarning(MediaAccessState.Partial, requested = false))
    }

    @Test
    fun cameraPhotos_keepOldestFirstOrder() {
        val files = listOf(photo("old"), video("clip"), photo("new"))
        assertEquals(listOf("old", "new"), cameraPhotos(files).map { it.id })
        assertEquals("new", newestCameraPhoto(cameraPhotos(files))?.id)
        assertNull(newestCameraPhoto(emptyList()))
    }

    @Test
    fun anotherCameraPhoto_skipsTheCurrentPhoto() {
        val photos = listOf(photo("a"), photo("b"), photo("c"))
        assertEquals("b", anotherCameraPhoto(photos, "a", 0)?.id)
        assertEquals("c", anotherCameraPhoto(photos, "a", 1)?.id)
        assertEquals("c", anotherCameraPhoto(photos, "a", -1)?.id)
        assertEquals("a", anotherCameraPhoto(listOf(photo("a")), "a", 3)?.id)
        assertNull(anotherCameraPhoto(emptyList(), "a", 0))
    }

    @Test
    fun formatByteCount_usesPlainUnits() {
        assertEquals("0 bytes", formatByteCount(0))
        assertEquals("1 byte", formatByteCount(1))
        assertEquals("0 bytes", formatByteCount(-4))
        assertEquals("512 bytes", formatByteCount(512))
        assertEquals("1 KB", formatByteCount(1024))
        assertEquals("1.5 KB", formatByteCount(1536))
        assertEquals("112 KB", formatByteCount(112L * 1024))
        assertEquals("1 MB", formatByteCount(1024L * 1024))
        assertEquals("1.5 MB", formatByteCount((1.5 * 1024 * 1024).toLong()))
        assertEquals("5 GB", formatByteCount(5L * 1024 * 1024 * 1024))
    }

    @Test
    fun qualityCardModels_labelSizeAndEstimate() {
        val models = qualityCardModels(
            selected = PreviewPreset.BALANCED,
            inputs = mapOf(
                PreviewPreset.SMALLER to QualityCardInput(
                    previewBytes = 112L * 1024,
                    projectedBytes = 5L * 1024 * 1024 * 1024,
                ),
                PreviewPreset.SHARPER to QualityCardInput(failed = true),
            ),
            libraryCount = 10,
        )
        assertEquals(
            listOf(PreviewPreset.SMALLER, PreviewPreset.BALANCED, PreviewPreset.SHARPER),
            models.map { it.preset },
        )
        assertEquals("Preview 112 KB", models[0].previewSize)
        assertEquals("5 GB estimate", models[0].estimate)
        assertEquals("Smaller", models[0].name)
        assertFalse(models[0].selected)
        assertEquals("Preview is still working", models[1].previewSize)
        assertEquals("Estimate is still working", models[1].estimate)
        assertTrue(models[1].recommended)
        assertTrue(models[1].selected)
        assertEquals("Balanced", models[1].name)
        assertEquals("Preview could not be made", models[2].previewSize)
        assertEquals("Estimate unavailable", models[2].estimate)
        assertEquals(
            "Nothing to estimate yet",
            estimateLabel(QualityCardInput(previewBytes = 10, projectedBytes = 10), 0),
        )
    }

    @Test
    fun readyLines_namesTheChoiceAndTheFirstBackup() {
        val withDrive = readyLines(
            ReadySummary(driveChosen = true, preset = PreviewPreset.SHARPER),
        )
        assertEquals("Chosen", withDrive.driveValue)
        assertEquals("Sharper", withDrive.previewsValue)
        assertEquals("The first backup takes hours and can be paused. You can unplug your drive any time; backup carries on when you plug it back in.", withDrive.note)
        assertEquals("Start backup", withDrive.button)

        val withoutDrive = readyLines(
            ReadySummary(
                driveChosen = false,
                preset = PreviewPreset.BALANCED,
                firstBackupTakesHoursAndCanBePaused = false,
            ),
        )
        assertEquals("Not chosen yet", withoutDrive.driveValue)
        assertEquals("Balanced · Recommended", withoutDrive.previewsValue)
        assertNull(withoutDrive.note)
        assertEquals("Open the app", withoutDrive.button)
        assertEquals("Use Smaller", usePresetLabel(PreviewPreset.SMALLER))
        assertEquals("Original 2 MB", originalSizeLabel(2L * 1024 * 1024))
    }

    @Test
    fun driveMessages_stayInPlainWords() {
        val readOnly = driveReadOnlyMessage()
        assertTrue(readOnly.contains("Archive Drive"))
        assertFalse(readOnly.contains("HDD"))
        assertFalse(readOnly.contains("cloud"))
        assertEquals("Archive Drive not found.", driveFailureMessage("Archive Drive not found."))
        assertEquals(
            "Could not keep access to the Archive Drive.",
            driveFailureMessage("Could not keep access to the Archive Drive.\njava.lang.SecurityException"),
        )
        assertEquals("Photoxpo could not use that Archive Drive.", driveFailureMessage("  "))
        assertEquals("Photoxpo could not use that Archive Drive.", driveFailureMessage("x".repeat(180)))
    }

    private fun photo(id: String): MediaFile {
        return MediaFile(
            id = id,
            name = "$id.jpg",
            sizeBytes = 100,
            modifiedAtEpochMillis = 1,
            kind = MediaKind.PHOTO,
        )
    }

    private fun video(id: String): MediaFile {
        return MediaFile(
            id = id,
            name = "$id.mp4",
            sizeBytes = 100,
            modifiedAtEpochMillis = 1,
            kind = MediaKind.VIDEO,
        )
    }
}
