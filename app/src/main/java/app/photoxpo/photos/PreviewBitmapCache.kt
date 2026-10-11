package app.photoxpo.photos

import android.graphics.Bitmap
import android.util.LruCache

/** In-memory Preview bitmaps, keyed by hash. Sized in bytes. */
class PreviewBitmapCache(
    maxBytes: Int = defaultMaxBytes(),
) {
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.allocationByteCount.coerceAtLeast(1)
        }
    }

    fun get(hash: String): Bitmap? = cache.get(hash)

    fun put(hash: String, bitmap: Bitmap) {
        cache.put(hash, bitmap)
    }
}

private fun defaultMaxBytes(): Int {
    val eighth = (Runtime.getRuntime().maxMemory() / 8L).coerceAtMost(Int.MAX_VALUE.toLong())
    return eighth.toInt().coerceAtLeast(4 * 1024 * 1024)
}
