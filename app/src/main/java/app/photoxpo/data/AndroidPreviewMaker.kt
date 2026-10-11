package app.photoxpo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.os.Build
import app.photoxpo.core.MediaKind
import app.photoxpo.core.PreviewMaker
import app.photoxpo.core.PreviewPreset
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Builds a JPEG Preview in [cacheDir]. Photos, including motion photos, use the
 * primary still. Videos take one frame, and only one video runs at a time.
 */
class AndroidPreviewMaker(
    private val cacheDir: File,
    private val preset: PreviewPreset,
) : PreviewMaker {
    constructor(context: Context, preset: PreviewPreset) : this(context.cacheDir, preset)

    override fun makePreview(original: InputStream, kind: MediaKind): ByteArray {
        val temp = copyToTemp(original)
        try {
            return if (kind == MediaKind.VIDEO) {
                synchronized(videoLock) { encode(temp, kind) }
            } else {
                encode(temp, kind)
            }
        } finally {
            temp.delete()
        }
    }

    private fun encode(file: File, kind: MediaKind): ByteArray {
        val decoded = when (kind) {
            MediaKind.PHOTO -> decodeStill(file)
            MediaKind.VIDEO -> videoFrame(file)
        }
        val bitmap = scaleDown(decoded, preset.longEdgePx)
        try {
            dropGainmap(bitmap)
            return jpegBytes(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun copyToTemp(original: InputStream): File {
        if (!cacheDir.isDirectory && !cacheDir.mkdirs()) {
            throw IOException("Preview cache directory is missing")
        }
        val temp = File.createTempFile("px-preview-", ".bin", cacheDir)
        try {
            FileOutputStream(temp).use { output -> original.copyTo(output) }
            return temp
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }

    private fun decodeStill(file: File): Bitmap {
        val source = ImageDecoder.createSource(file)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            val fitted = fit(info.size.width, info.size.height, preset.longEdgePx) ?: return@decodeBitmap
            decoder.setTargetSize(fitted.first, fitted.second)
        }
    }

    private fun videoFrame(file: File): Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            return frame(retriever)
        } finally {
            retriever.release()
        }
    }

    private fun frame(retriever: MediaMetadataRetriever): Bitmap {
        val oriented = orientedSize(retriever)
        val sampled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && oriented != null) {
            val (width, height) = fit(oriented.first, oriented.second, preset.longEdgePx) ?: oriented
            retriever.getScaledFrameAtTime(
                -1,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                width,
                height,
            )
        } else {
            null
        }
        val bitmap = sampled ?: retriever.getFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        return bitmap ?: throw IOException("Video has no frame for a Preview")
    }

    private fun orientedSize(retriever: MediaMetadataRetriever): Pair<Int, Int>? {
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
            ?: return null
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()
            ?: return null
        if (width <= 0 || height <= 0) return null
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()
            ?: 0
        return if (rotation == 90 || rotation == 270) height to width else width to height
    }

    private fun jpegBytes(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, preset.jpegQuality, output)) {
            throw IOException("Could not encode Preview")
        }
        return output.toByteArray()
    }

    private companion object {
        val videoLock = Any()

        fun fit(width: Int, height: Int, longEdge: Int): Pair<Int, Int>? {
            val longest = max(width, height)
            if (longest <= longEdge || longest <= 0) return null
            val scale = longEdge.toDouble() / longest.toDouble()
            val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
            val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
            return targetWidth to targetHeight
        }

        fun scaleDown(bitmap: Bitmap, longEdge: Int): Bitmap {
            val fitted = fit(bitmap.width, bitmap.height, longEdge) ?: return bitmap
            val scaled = Bitmap.createScaledBitmap(bitmap, fitted.first, fitted.second, true)
            if (scaled !== bitmap) bitmap.recycle()
            return scaled
        }

        fun dropGainmap(bitmap: Bitmap) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && bitmap.hasGainmap()) {
                bitmap.setGainmap(null)
            }
        }
    }
}
