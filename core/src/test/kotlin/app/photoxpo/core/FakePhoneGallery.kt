package app.photoxpo.core

import java.io.ByteArrayOutputStream
import java.io.OutputStream

class FakePhoneGallery : PhoneGallery {
    private val published = LinkedHashMap<String, ByteArray>()
    private val partials = LinkedHashMap<String, Handle>()
    private var nextId = 1

    fun publishedCount(): Int = published.size

    fun partialCount(): Int = partials.size

    fun bytes(uri: String): ByteArray = published.getValue(uri)

    override fun createLoadedFile(name: String, mimeHint: String?): LoadedFileHandle {
        val handle = Handle(nextId++, name)
        handle.rememberedMime = mimeHint
        partials[handle.key] = handle
        return handle
    }

    private inner class Handle(
        private val id: Int,
        private val name: String,
    ) : LoadedFileHandle {
        var rememberedMime: String? = null
        private val buffer = ByteArrayOutputStream()
        private var open = true
        val key: String = "partial-$id"

        override val output: OutputStream = buffer

        override fun commit(): String {
            check(open) { "Loaded file is already finished" }
            open = false
            partials.remove(key)
            val uri = "loaded://$id/$name"
            published[uri] = buffer.toByteArray()
            return uri
        }

        override fun abort() {
            open = false
            partials.remove(key)
            buffer.reset()
        }
    }
}
