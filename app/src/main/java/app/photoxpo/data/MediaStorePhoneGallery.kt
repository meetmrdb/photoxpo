package app.photoxpo.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import app.photoxpo.core.LoadedFileHandle
import app.photoxpo.core.PhoneGallery
import java.io.IOException
import java.io.OutputStream

/**
 * Publishes a Loaded Original into the phone gallery.
 * Photos use Pictures/Photoxpo Loaded. Videos use Movies/Photoxpo Loaded
 * because MediaStore requires videos to live under Movies/.
 */
class MediaStorePhoneGallery(
    context: Context,
) : PhoneGallery {
    private val resolver = context.applicationContext.contentResolver

    override fun createLoadedFile(name: String, mimeHint: String?): LoadedFileHandle {
        val video = isVideo(mimeHint, name)
        val collection = if (video) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val relativePath = if (video) LOADED_VIDEOS else LOADED_PHOTOS
        val mime = when {
            !mimeHint.isNullOrBlank() -> mimeHint
            video -> "video/mp4"
            else -> "image/jpeg"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(
                MediaStore.MediaColumns.DATE_EXPIRES,
                System.currentTimeMillis() / 1000 + PENDING_TTL_SECONDS,
            )
        }
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Could not create a Loaded Original")
        val output = try {
            resolver.openOutputStream(uri)
                ?: throw IOException("Could not write a Loaded Original")
        } catch (error: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            throw error
        }
        return PendingLoadedFile(resolver, uri, output)
    }

    private companion object {
        const val LOADED_PHOTOS = "Pictures/Photoxpo Loaded"
        const val LOADED_VIDEOS = "Movies/Photoxpo Loaded"
        const val PENDING_TTL_SECONDS = 24 * 60 * 60L
    }
}

private class PendingLoadedFile(
    private val resolver: ContentResolver,
    private val uri: Uri,
    override val output: OutputStream,
) : LoadedFileHandle {
    private val gate = Any()
    private var finished = false

    override fun commit(): String {
        synchronized(gate) {
            check(!finished) { "Loaded file is already finished" }
            try {
                output.close()
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                    putNull(MediaStore.MediaColumns.DATE_EXPIRES)
                }
                if (resolver.update(uri, values, null, null) != 1) {
                    throw IOException("Could not publish the Loaded Original")
                }
                finished = true
                return uri.toString()
            } catch (error: Exception) {
                finished = true
                closeQuietly()
                deletePending()
                throw error
            }
        }
    }

    override fun abort() {
        synchronized(gate) {
            if (finished) return
            finished = true
            closeQuietly()
            deletePending()
        }
    }

    private fun deletePending() {
        try {
            resolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
    }

    private fun closeQuietly() {
        try {
            output.close()
        } catch (_: IOException) {
        }
    }
}

private fun isVideo(mimeHint: String?, name: String): Boolean {
    val mime = mimeHint?.substringBefore(';')?.trim()?.lowercase()
    if (mime != null) {
        if (mime.startsWith("video/")) return true
        if (mime.startsWith("image/")) return false
    }
    val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return extension in VIDEO_EXTENSIONS
}

private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "mkv", "webm", "3gp")
