package app.photoxpo.photos

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoInfoTest {
    @Test
    fun originalOnArchiveDrive() {
        val info = PhotoInfo.from(
            sample(originalOnThisDrive = true, originalOnPhone = false, backedUp = true),
            previewSizeBytes = 2048,
        )
        assertEquals("On your Archive Drive", info.whereTheOriginalIs)
        assertEquals("Yes", info.backedUp)
        assertEquals("2 KB", info.previewSize)
    }

    @Test
    fun originalOnPhoneAndDrive() {
        val info = PhotoInfo.from(
            sample(originalOnThisDrive = true, originalOnPhone = true, backedUp = true),
            previewSizeBytes = 1024,
        )
        assertEquals("On this phone and your drive", info.whereTheOriginalIs)
    }

    @Test
    fun originalOnlyOnThisPhone() {
        val info = PhotoInfo.from(
            sample(
                originalOnThisDrive = false,
                originalOnPhone = true,
                backedUp = false,
                drivePath = null,
            ),
            previewSizeBytes = 512,
        )
        assertEquals("Only on this phone", info.whereTheOriginalIs)
        assertEquals("No", info.backedUp)
        assertEquals("Not on your Archive Drive", info.driveFolder)
    }

    @Test
    fun backedUpYesAndNo() {
        val yes = PhotoInfo.from(sample(backedUp = true), previewSizeBytes = 10)
        val no = PhotoInfo.from(sample(backedUp = false), previewSizeBytes = 10)
        assertEquals("Yes", yes.backedUp)
        assertEquals("No", no.backedUp)
    }

    @Test
    fun missingPreview() {
        val info = PhotoInfo.from(
            sample(sizeBytes = 1536, name = "IMG_1000.jpg", chosenDate = "2026-10-12"),
            previewSizeBytes = null,
        )
        assertEquals("No Preview", info.previewSize)
        assertEquals("1.5 KB", info.originalSize)
        assertEquals("IMG_1000.jpg", info.fileName)
        assertEquals("12 October 2026", info.date)
    }

    @Test
    fun driveFolderIsTheParentPath() {
        val info = PhotoInfo.from(
            sample(drivePath = "Photoxpo/2026/10/IMG_1000.jpg"),
            previewSizeBytes = 100,
        )
        assertEquals("Photoxpo/2026/10", info.driveFolder)
    }

    @Test
    fun missingDate() {
        val info = PhotoInfo.from(sample(chosenDate = null), previewSizeBytes = 100)
        assertEquals("No date", info.date)
    }
}

private fun sample(
    originalOnThisDrive: Boolean = false,
    originalOnPhone: Boolean = false,
    backedUp: Boolean = false,
    sizeBytes: Long = 2048,
    name: String = "IMG.jpg",
    chosenDate: String? = "2026-10-01",
    drivePath: String? = "Photoxpo/2026/10/IMG.jpg",
): PhotoEntity {
    return PhotoEntity(
        hash = "abc",
        mediaId = null,
        name = name,
        chosenDate = chosenDate,
        sourceTag = "exif",
        kind = "photo",
        sizeBytes = sizeBytes,
        drivePath = drivePath,
        backedUp = backedUp,
        previewFile = null,
        originalOnThisDrive = originalOnThisDrive,
        originalOnPhone = originalOnPhone,
    )
}
