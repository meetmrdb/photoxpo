package app.photoxpo.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.photoxpo.core.MediaKind
import app.photoxpo.core.PreviewPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

@RunWith(AndroidJUnit4::class)
class AndroidPreviewMakerInstrumentedTest {
    @Test
    fun balancedStill_longEdgeIs720_decodable_smaller_andHasNoExif() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "px-src-${UUID.randomUUID()}.jpg")
        val previewFile = File(context.cacheDir, "px-preview-${UUID.randomUUID()}.jpg")
        try {
            writeSourceJpeg(source)
            val sourceLength = source.length()
            val preview = source.inputStream().use { stream ->
                AndroidPreviewMaker(context.cacheDir, PreviewPreset.BALANCED)
                    .makePreview(stream, MediaKind.PHOTO)
            }
            assertTrue(preview.size.toLong() < sourceLength)
            previewFile.writeBytes(preview)

            val bitmap = BitmapFactory.decodeFile(previewFile.absolutePath)
                ?: error("Preview is not a decodable JPEG")
            assertEquals(720, bitmap.width)
            assertEquals(540, bitmap.height)
            assertEquals(720, max(bitmap.width, bitmap.height))
            bitmap.recycle()

            val exif = ExifInterface(previewFile.absolutePath)
            assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME))
            assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        } finally {
            source.delete()
            previewFile.delete()
        }
    }

    private fun writeSourceJpeg(target: File) {
        val bitmap = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        val row = IntArray(4000)
        val random = java.util.Random(1)
        try {
            for (y in 0 until 3000) {
                for (x in 0 until 4000) {
                    row[x] = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
                }
                bitmap.setPixels(row, 0, 4000, 0, y, 4000, 1)
            }
            FileOutputStream(target).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                    error("Could not write the source JPEG")
                }
            }
        } finally {
            bitmap.recycle()
        }
    }
}
