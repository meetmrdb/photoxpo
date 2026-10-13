package app.photoxpo.core

interface PreviewStore {
    fun write(hash: String, bytes: ByteArray)

    fun read(hash: String): ByteArray?

    fun exists(hash: String): Boolean
}

fun previewFileName(hash: String): String = "$hash.jpg"
