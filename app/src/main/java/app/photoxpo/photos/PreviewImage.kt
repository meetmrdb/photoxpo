package app.photoxpo.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PreviewImage(
    hash: String,
    contentDescription: String?,
    contentScale: ContentScale,
    cache: PreviewBitmapCache,
    modifier: Modifier = Modifier,
    maxEdge: Int = 2048,
) {
    val filesDir = LocalContext.current.filesDir
    var bitmap by remember(hash, maxEdge) { mutableStateOf(cache.get(cacheKey(hash, maxEdge))) }
    LaunchedEffect(hash, maxEdge) {
        val cached = cache.get(cacheKey(hash, maxEdge))
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            decodePreview(previewJpeg(filesDir, hash), maxEdge)
        }
        if (loaded != null) {
            cache.put(cacheKey(hash, maxEdge), loaded)
            bitmap = loaded
        }
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}

private fun cacheKey(hash: String, maxEdge: Int): String = "$hash@$maxEdge"

private fun decodePreview(file: File, maxEdge: Int): Bitmap? {
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
    }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
    var sample = 1
    var edgeW = width
    var edgeH = height
    while (edgeW / 2 >= maxEdge || edgeH / 2 >= maxEdge) {
        edgeW /= 2
        edgeH /= 2
        sample *= 2
    }
    return sample
}
