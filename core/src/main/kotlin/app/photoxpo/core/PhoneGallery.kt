package app.photoxpo.core

import java.io.OutputStream

/**
 * The phone gallery, where a Loaded Original is published.
 * Photos are published in Pictures/Photoxpo Loaded.
 */
interface PhoneGallery {
    fun createLoadedFile(name: String, mimeHint: String?): LoadedFileHandle
}

/**
 * A Loaded Original that stays unpublished until [commit].
 * [abort] removes the partial file.
 */
interface LoadedFileHandle {
    val output: OutputStream

    /** Publishes the file and returns its uri. */
    fun commit(): String

    /** Removes the partial file. */
    fun abort()
}
