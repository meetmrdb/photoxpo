package app.photoxpo.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import app.photoxpo.core.previewFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

/**
 * Inserts 40 fake photos and writes a small Preview JPEG for each.
 * Release builds do not include this. It is not called on a normal launch.
 *
 * From a computer, with the debug app installed:
 * adb shell am start -n app.photoxpo/.MainActivity --ez app.photoxpo.seedPhotos true
 *
 * The extra name is [EXTRA]. [DebugSeedHooks] calls [seed] when that extra is true.
 * [seed] blocks the caller while it writes, so call it off the main thread.
 */
object DebugSeed {
    const val EXTRA = "app.photoxpo.seedPhotos"

    fun seed(context: Context) {
        synchronized(lock) {
            val appContext = context.applicationContext
            val photos = buildSeedPhotos()
            check(photos.size == 40) { "Expected 40 fake photos, found ${photos.size}" }
            runBlocking(Dispatchers.IO) {
                val directory = File(appContext.filesDir, "previews")
                if (!directory.isDirectory && !directory.mkdirs()) {
                    error("Could not create the Preview directory")
                }
                PhotosDatabase.get(appContext).photoDao().upsert(photos)
                photos.forEachIndexed { index, photo ->
                    writePreview(previewJpeg(appContext.filesDir, photo.hash), index)
                }
            }
        }
    }

    private val lock = Any()
}

private val seedMonths = listOf(
    "2026-10" to 10,
    "2026-09" to 8,
    "2026-08" to 8,
    "2026-06" to 7,
    "2025-12" to 7,
)

internal fun buildSeedPhotos(): List<PhotoEntity> {
    val photos = ArrayList<PhotoEntity>(40)
    var index = 0
    for ((yearMonth, count) in seedMonths) {
        for (day in 1..count) {
            val onDrive = index % 3 != 0
            val onPhone = index % 3 != 1
            val video = index % 7 == 0
            val extension = if (video) "mp4" else "jpg"
            val name = "IMG_${2000 + index}.$extension"
            val hash = "seed" + index.toString().padStart(4, '0')
            val date = "$yearMonth-" + day.toString().padStart(2, '0')
            photos += PhotoEntity(
                hash = hash,
                mediaId = if (onPhone) 5_000L + index else null,
                name = name,
                chosenDate = date,
                sourceTag = "debug",
                kind = if (video) "video" else "photo",
                sizeBytes = 1_800_000L + index * 1024L,
                drivePath = if (onDrive) "Photoxpo/$yearMonth/$name" else null,
                backedUp = onDrive,
                previewFile = "previews/${previewFileName(hash)}",
                originalOnThisDrive = onDrive,
                originalOnPhone = onPhone,
            )
            index++
        }
    }
    return photos
}

private fun writePreview(file: File, index: Int) {
    val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
    try {
        val red = 40 + (index * 37) % 180
        val green = 60 + (index * 19) % 140
        val blue = 80 + (index * 23) % 150
        bitmap.eraseColor(Color.rgb(red, green, blue))
        FileOutputStream(file).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)) {
                error("Could not write Preview ${file.name}")
            }
        }
    } finally {
        bitmap.recycle()
    }
}
