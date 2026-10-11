package app.photoxpo.core

class FakePreviewStore : PreviewStore {
    private val files = LinkedHashMap<String, ByteArray>()

    override fun write(hash: String, bytes: ByteArray) {
        files[hash] = bytes.copyOf()
    }

    override fun read(hash: String): ByteArray? = files[hash]?.copyOf()

    override fun exists(hash: String): Boolean = hash in files
}
