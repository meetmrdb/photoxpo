package app.photoxpo.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

class PreviewPipelineTest {
    @Rule
    @JvmField
    val folders = TemporaryFolder()

    @Test
    fun previewPreset_exposesLongEdgeQualityAndStableWireName() {
        assertEquals(480, PreviewPreset.SMALLER.longEdgePx)
        assertEquals(60, PreviewPreset.SMALLER.jpegQuality)
        assertEquals("smaller", PreviewPreset.SMALLER.wireName())
        assertEquals(720, PreviewPreset.BALANCED.longEdgePx)
        assertEquals(70, PreviewPreset.BALANCED.jpegQuality)
        assertEquals("balanced", PreviewPreset.BALANCED.wireName())
        assertEquals(1080, PreviewPreset.SHARPER.longEdgePx)
        assertEquals(80, PreviewPreset.SHARPER.jpegQuality)
        assertEquals("sharper", PreviewPreset.SHARPER.wireName())
    }

    @Test
    fun previewMade_forEveryBackedUpOriginal() {
        val photo = backedUp("photo-1", MediaKind.PHOTO, byteArrayOf(1, 2, 3))
        val video = backedUp("video-1", MediaKind.VIDEO, byteArrayOf(4, 5, 6, 7))
        val maker = TrackingPreviewMaker()
        val store = FakePreviewStore()
        val result = pipeline(listOf(photo, video), maker, store).run(listOf(photo.original, video.original))

        assertEquals(listOf(photo.original.hash, video.original.hash), result.made)
        assertTrue(result.skipped.isEmpty())
        assertTrue(result.failed.isEmpty())
        assertArrayEquals(photo.bytes, store.read(photo.original.hash))
        assertArrayEquals(video.bytes, store.read(video.original.hash))
        assertEquals(listOf(MediaKind.PHOTO, MediaKind.VIDEO), maker.seenKinds)
    }

    @Test
    fun previewsAreMade_oneAtATime() {
        val first = backedUp("a", MediaKind.PHOTO, byteArrayOf(1))
        val second = backedUp("b", MediaKind.PHOTO, byteArrayOf(2))
        val maker = TrackingPreviewMaker(delayMillis = 50)
        val result = pipeline(listOf(first, second), maker, FakePreviewStore())
            .run(listOf(first.original, second.original))

        assertEquals(listOf(first.original.hash, second.original.hash), result.made)
        assertEquals(1, maker.maxConcurrency)
        assertEquals(2, maker.calls)
    }

    @Test
    fun secondRun_makesNone() {
        val first = backedUp("a", MediaKind.PHOTO, byteArrayOf(1, 1))
        val second = backedUp("b", MediaKind.VIDEO, byteArrayOf(2, 2))
        val originals = listOf(first, second)
        val maker = TrackingPreviewMaker()
        val store = FakePreviewStore()
        val pipeline = pipeline(originals, maker, store)
        val backedUp = originals.map { it.original }

        val firstRun = pipeline.run(backedUp)
        val callsAfterFirst = maker.calls
        val secondRun = pipeline.run(backedUp)

        assertEquals(listOf(first.original.hash, second.original.hash), firstRun.made)
        assertTrue(secondRun.made.isEmpty())
        assertEquals(listOf(first.original.hash, second.original.hash), secondRun.skipped)
        assertTrue(secondRun.failed.isEmpty())
        assertEquals(callsAfterFirst, maker.calls)
    }

    @Test
    fun failingPreview_leavesTheOriginalBackedUp_andRetriesNextTime() {
        val bytes = byteArrayOf(0x00, 0x01, 0xFF.toByte(), 0x0A, 0x41)
        val file = MediaFile(
            id = "media-1",
            name = "IMG_0001.jpg",
            sizeBytes = bytes.size.toLong(),
            modifiedAtEpochMillis = Instant.parse("2020-01-31T20:00:00Z").toEpochMilli(),
            kind = MediaKind.PHOTO,
        )
        val drive = FakeArchiveDrive(folders.newFolder("drive").toPath())
        val media = FakeMediaSource(listOf(file to bytes))
        val backup = startBackupRun(media, drive, clock = fixedClock)
        val hash = "c4deec4e2222596ddb146449a39db308d94413ff0f538d7faa3db02100aeaece"
        val original = BackedUpOriginal(hash = hash, kind = MediaKind.PHOTO, mediaId = file.id)
        val maker = TrackingPreviewMaker(fail = true)
        val store = FakePreviewStore()
        val pipeline = PreviewPipeline(media, maker, store)

        val failed = pipeline.run(listOf(original))

        assertEquals(listOf(hash), failed.failed)
        assertTrue(failed.made.isEmpty())
        assertFalse(store.exists(hash))
        assertEquals(ItemState.COMMITTED, backup.items.single().state)
        assertArrayEquals(bytes, drive.read(FINAL_PATH).use { it.readAllBytes() })
        val fileLine = drive.readText(backup.manifestPath).lines().first { it.contains("\"type\":\"file\"") }
        assertTrue(fileLine.contains("\"previewFileName\":\"$hash.jpg\""))

        maker.fail = false
        val retried = pipeline.run(listOf(original))

        assertEquals(listOf(hash), retried.made)
        assertArrayEquals(bytes, store.read(hash))
        assertEquals(ItemState.COMMITTED, backup.items.single().state)
        assertArrayEquals(bytes, drive.read(FINAL_PATH).use { it.readAllBytes() })
    }

    @Test
    fun onePreviewFailure_doesNotStopTheRest() {
        val first = backedUp("a", MediaKind.PHOTO, byteArrayOf(9))
        val second = backedUp("b", MediaKind.PHOTO, byteArrayOf(8))
        val maker = TrackingPreviewMaker(failBytes = first.bytes)
        val store = FakePreviewStore()
        val result = pipeline(listOf(first, second), maker, store)
            .run(listOf(first.original, second.original))

        assertEquals(listOf(first.original.hash), result.failed)
        assertEquals(listOf(second.original.hash), result.made)
        assertFalse(store.exists(first.original.hash))
        assertArrayEquals(second.bytes, store.read(second.original.hash))
    }

    private fun pipeline(
        originals: List<Sample>,
        maker: PreviewMaker,
        store: PreviewStore,
    ): PreviewPipeline {
        val files = originals.map { sample ->
            MediaFile(
                id = sample.original.mediaId,
                name = "${sample.original.mediaId}.jpg",
                sizeBytes = sample.bytes.size.toLong(),
                modifiedAtEpochMillis = 0L,
                kind = sample.original.kind,
            ) to sample.bytes
        }
        return PreviewPipeline(FakeMediaSource(files), maker, store)
    }

    private fun backedUp(id: String, kind: MediaKind, bytes: ByteArray): Sample {
        return Sample(
            original = BackedUpOriginal(hash = "hash-$id", kind = kind, mediaId = id),
            bytes = bytes,
        )
    }

    private data class Sample(
        val original: BackedUpOriginal,
        val bytes: ByteArray,
    )

    private class TrackingPreviewMaker(
        private val delayMillis: Long = 0,
        var fail: Boolean = false,
        private val failBytes: ByteArray? = null,
    ) : PreviewMaker {
        private val inFlight = AtomicInteger(0)

        var maxConcurrency: Int = 0
            private set

        var calls: Int = 0
            private set

        val seenKinds: List<MediaKind>
            get() = kinds.toList()

        private val kinds = mutableListOf<MediaKind>()

        override fun makePreview(original: InputStream, kind: MediaKind): ByteArray {
            val bytes = original.readAllBytes()
            val now = inFlight.incrementAndGet()
            try {
                synchronized(this) {
                    calls += 1
                    kinds += kind
                    if (now > maxConcurrency) maxConcurrency = now
                }
                if (fail || (failBytes != null && bytes.contentEquals(failBytes))) {
                    throw IOException("Preview failed")
                }
                if (delayMillis > 0) Thread.sleep(delayMillis)
                return bytes
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

    companion object {
        private const val FINAL_PATH = "Photoxpo/Originals/2020/2020-01/IMG_0001.jpg"
        private val fixedClock: Clock =
            Clock.fixed(Instant.parse("2026-10-11T18:30:00Z"), ZoneOffset.UTC)
    }
}
