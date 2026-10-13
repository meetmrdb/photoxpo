package app.photoxpo.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.ExifInterface
import android.provider.MediaStore
import app.photoxpo.core.MediaFile
import app.photoxpo.core.MediaKind
import app.photoxpo.core.MediaSource
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * Originals in DCIM/Camera, photos and videos only, oldest first.
 * A partial photo selection is not full access.
 */
class MediaStoreSource(
    private val context: Context,
) : MediaSource {
    override fun listCameraFolder(): List<MediaFile> {
        val cursor = context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            PROJECTION,
            SELECTION,
            SELECTION_ARGS,
            SORT_ORDER,
        ) ?: throw IOException("MediaStore did not list the camera folder")
        val files = ArrayList<MediaFile>()
        cursor.use { rows ->
            val idCol = rows.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = rows.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = rows.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val modifiedCol = rows.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val typeCol = rows.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val takenCol = rows.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
            while (rows.moveToNext()) {
                val name = rows.getString(nameCol) ?: continue
                val kind = when (rows.getInt(typeCol)) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaKind.PHOTO
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaKind.VIDEO
                    else -> continue
                }
                val modifiedSeconds = if (rows.isNull(modifiedCol)) 0L else rows.getLong(modifiedCol)
                val id = rows.getLong(idCol)
                val dateTaken = if (rows.isNull(takenCol)) null else rows.getLong(takenCol)
                files += MediaFile(
                    id = id.toString(),
                    name = name,
                    sizeBytes = if (rows.isNull(sizeCol)) 0L else rows.getLong(sizeCol),
                    modifiedAtEpochMillis = modifiedSeconds * 1000L,
                    kind = kind,
                    // Photos: EXIF wall-clock first, then MediaStore date taken. Videos: MediaStore date
                    // taken is the container's UTC creation time; core treats a 1904 date as missing.
                    exifDateTimeOriginal = if (kind == MediaKind.PHOTO) readExifDateTimeOriginal(id) else null,
                    dateTakenEpochMillis = if (kind == MediaKind.PHOTO) dateTaken else null,
                    videoMetadataUtcEpochMillis = if (kind == MediaKind.VIDEO) dateTaken else null,
                )
            }
        }
        return files
    }

    /** EXIF DateTimeOriginal as written ("yyyy:MM:dd HH:mm:ss"), read from the file header only. */
    private fun readExifDateTimeOriginal(id: Long): String? {
        val uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            }
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    override fun openOriginal(id: String): InputStream {
        val base = ContentUris.withAppendedId(
            MediaStore.Files.getContentUri("external"),
            id.toLong(),
        )
        val original = MediaStore.setRequireOriginal(base)
        return context.contentResolver.openInputStream(original)
            ?: throw FileNotFoundException("Original $id")
    }

    private companion object {
        /**
         * DCIM/Camera/ only. DCIM/Camera/Foo/ is a nested folder and is excluded.
         * DATE_MODIFIED is seconds. [listCameraFolder] converts it to milliseconds.
         */
        const val CAMERA_FOLDER = "DCIM/Camera/%"
        const val NESTED_CAMERA_FOLDER = "DCIM/Camera/%/%"

        val PROJECTION = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )

        val SELECTION = buildString {
            append(MediaStore.Files.FileColumns.RELATIVE_PATH)
            append(" LIKE ? AND ")
            append(MediaStore.Files.FileColumns.RELATIVE_PATH)
            append(" NOT LIKE ? AND ")
            append(MediaStore.Files.FileColumns.MEDIA_TYPE)
            append(" IN (?, ?) AND (")
            append(MediaStore.Files.FileColumns.IS_PENDING)
            append(" IS NULL OR ")
            append(MediaStore.Files.FileColumns.IS_PENDING)
            append(" = 0)")
        }

        val SELECTION_ARGS = arrayOf(
            CAMERA_FOLDER,
            NESTED_CAMERA_FOLDER,
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )

        val SORT_ORDER =
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} ASC, ${MediaStore.Files.FileColumns._ID} ASC"
    }
}

/**
 * True only when both READ_MEDIA_IMAGES and READ_MEDIA_VIDEO are granted.
 * READ_MEDIA_VISUAL_USER_SELECTED alone is a partial selection and returns false.
 */
fun hasFullMediaAccess(context: Context): Boolean {
    return isGranted(context, Manifest.permission.READ_MEDIA_IMAGES) &&
        isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
}

fun hasAccessMediaLocation(context: Context): Boolean {
    return isGranted(context, Manifest.permission.ACCESS_MEDIA_LOCATION)
}

private fun isGranted(context: Context, permission: String): Boolean {
    return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
