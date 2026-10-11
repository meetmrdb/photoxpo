package app.photoxpo.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.photoxpo.core.BackupRunner
import app.photoxpo.core.CreateResult
import app.photoxpo.core.DriveUnavailableException
import app.photoxpo.core.ItemState
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlin.random.Random

/**
 * Instrumented tests for SafArchiveDrive, probeArchiveDrive, and one Backup run.
 *
 * The Archive Drive is [TestArchiveDocumentsProvider], authority app.photoxpo.test.archive.
 * Its files live in the test app cache directory archive-drive.
 * Setup grants that tree Uri with grantUriPermission and calls adoptShellPermissionIdentity
 * for android.permission.MANAGE_DOCUMENTS. The provider is exported and sets grantUriPermissions.
 * Resolver calls from this process still succeed when the shell identity does not hold
 * MANAGE_DOCUMENTS, because that permission is signature-level and the provider does not require it.
 * A tree Uri for com.android.externalstorage.documents needs a document picker grant, so these
 * tests build a tree Uri for the test provider.
 *
 * oneOriginal_isBackedUp_withMatchingHashAndOneFileLine inserts one Original under DCIM/Camera.
 * That folder must contain no other photos or videos, or the Manifest would contain more than
 * one file line. Tests delete that MediaStore row and the documents they created.
 */
@RunWith(AndroidJUnit4::class)
class SafArchiveDriveInstrumentedTest {
    @Rule
    @JvmField
    val mediaPermission: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )

    private var inserted: Uri? = null

    @Before
    fun setUp() {
        prepareDriveAccess()
        deleteLeftoverOriginals()
        wipeDrive()
    }

    @After
    fun tearDown() {
        val uri = inserted
        inserted = null
        if (uri != null) {
            try {
                targetContext().contentResolver.delete(uri, null, null)
            } catch (_: SecurityException) {
                // The next run deletes rows whose display name starts with px-e2e-.
            }
        }
        try {
            wipeDrive()
        } catch (_: IOException) {
            // Leave the test failure as the result. setUp wipes archive-drive on the next run.
        }
    }

    @Test
    fun oneOriginal_isBackedUp_withMatchingHashAndOneFileLine() {
        val target = targetContext()
        val source = MediaStoreSource(target)
        val already = source.listCameraFolder()
        check(already.isEmpty()) {
            "DCIM/Camera already holds ${already.size} Originals. This test needs that folder empty."
        }

        val bytes = jpegLikeBytes()
        val name = "px-e2e-${UUID.randomUUID()}.jpg"
        inserted = insertCameraOriginal(target, name, bytes)
        val listed = waitForCameraFile(source, name)
        val sourceBytes = source.openOriginal(listed.id).use { input -> input.readBytes() }
        assertArrayEquals(bytes, sourceBytes)

        val drive = SafArchiveDrive(driveContext(), TestArchiveDocumentsProvider.treeUri())
        val result = BackupRunner(source, drive).start()

        val item = result.items.single()
        assertEquals(listed.id, item.id)
        assertEquals(name, item.name)
        assertEquals(ItemState.COMMITTED, item.state)

        val lines = drive.readText(result.manifestPath).lineSequence().filter { it.isNotBlank() }.toList()
        val fileLines = lines.filter { it.contains("\"type\":\"file\"") }
        assertEquals(1, fileLines.size)
        val fileLine = fileLines.single()
        val drivePath = stringField(fileLine, "drivePath")
        val driveBytes = drive.read(drivePath).use { input -> input.readBytes() }
        val hash = sha256(sourceBytes)
        assertEquals(hash, sha256(driveBytes))
        assertEquals(hash, stringField(fileLine, "hash"))
    }

    @Test
    fun collision_keepsTheRequestedName() {
        val drive = SafArchiveDrive(driveContext(), TestArchiveDocumentsProvider.treeUri())
        val path = "Photoxpo/Originals/IMG.jpg"
        assertEquals(CreateResult.Created, drive.create(path))
        assertEquals(CreateResult.Collision(path), drive.create(path))
        assertEquals(listOf("IMG.jpg"), drive.list("Photoxpo/Originals"))
    }

    @Test
    fun write_replacesLongerFileWithShorterContent() {
        val drive = SafArchiveDrive(driveContext(), TestArchiveDocumentsProvider.treeUri())
        val path = "Photoxpo/note.txt"
        assertEquals(CreateResult.Created, drive.create(path))
        val longer = ByteArray(64) { 'A'.code.toByte() }
        drive.write(path).use { output -> output.write(longer) }
        val shorter = byteArrayOf('B'.code.toByte(), 'C'.code.toByte(), 'D'.code.toByte())
        drive.write(path).use { output -> output.write(shorter) }
        val read = drive.read(path).use { input -> input.readBytes() }
        assertArrayEquals(shorter, read)
        assertEquals(shorter.size.toLong(), drive.byteCount(path))
    }

    @Test
    fun missingPath_throwsFileNotFound() {
        val drive = SafArchiveDrive(driveContext(), TestArchiveDocumentsProvider.treeUri())
        assertThrows(FileNotFoundException::class.java) {
            drive.read("Photoxpo/missing.jpg")
        }
        assertFalse(drive.exists("Photoxpo/missing.jpg"))
    }

    @Test
    fun absentTree_throwsDriveUnavailable_andReadOnlyMaps() {
        val context = driveContext()
        assertEquals(
            DriveStatus.Absent,
            probeArchiveDrive(context, TestArchiveDocumentsProvider.absentTreeUri()),
        )
        assertEquals(
            DriveStatus.Ready,
            probeArchiveDrive(context, TestArchiveDocumentsProvider.treeUri()),
        )
        assertEquals(
            DriveStatus.ReadOnly,
            driveStatus(
                probeFound = true,
                supportsWrite = false,
                volumeState = Environment.MEDIA_MOUNTED,
            ),
        )
        assertEquals(
            DriveStatus.ReadOnly,
            driveStatus(
                probeFound = true,
                supportsWrite = true,
                volumeState = Environment.MEDIA_MOUNTED_READ_ONLY,
            ),
        )

        val drive = SafArchiveDrive(context, TestArchiveDocumentsProvider.absentTreeUri())
        assertThrows(DriveUnavailableException::class.java) { drive.list("") }
        assertThrows(DriveUnavailableException::class.java) { drive.exists("Photoxpo/a.jpg") }
        assertThrows(DriveUnavailableException::class.java) { drive.create("Photoxpo/a.jpg") }
        assertThrows(DriveUnavailableException::class.java) { drive.read("Photoxpo/a.jpg") }
    }

    private fun insertCameraOriginal(context: Context, name: String, bytes: ByteArray): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, CAMERA_FOLDER)
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.DATE_EXPIRES, System.currentTimeMillis() / 1000 + 60 * 60)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore did not accept the Original")
        inserted = uri
        resolver.openOutputStream(uri)?.use { stream -> stream.write(bytes) }
            ?: error("MediaStore did not open the Original")
        val ready = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
            put(MediaStore.Images.Media.SIZE, bytes.size)
        }
        check(resolver.update(uri, ready, null, null) == 1) { "MediaStore did not publish the Original" }
        return uri
    }

    private fun waitForCameraFile(source: MediaStoreSource, name: String): app.photoxpo.core.MediaFile {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (true) {
            val files = source.listCameraFolder()
            val match = files.filter { file -> file.name == name }
            if (match.size == 1 && files.size == 1) return match.single()
            check(SystemClock.elapsedRealtime() < deadline) {
                "Original $name did not appear alone in DCIM/Camera"
            }
            SystemClock.sleep(50)
        }
    }

    private fun deleteLeftoverOriginals() {
        val resolver = targetContext().contentResolver
        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(CAMERA_FOLDER, "px-e2e-%"),
            )
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        }
        val cursor = resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            args,
            null,
        ) ?: return
        cursor.use { rows ->
            val idCol = rows.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (rows.moveToNext()) {
                val item = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rows.getLong(idCol))
                resolver.delete(item, null, null)
            }
        }
    }

    private fun wipeDrive() {
        val drive = SafArchiveDrive(driveContext(), TestArchiveDocumentsProvider.treeUri())
        for (name in drive.list("")) {
            drive.delete(name)
        }
    }

    private fun prepareDriveAccess() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        try {
            automation.dropShellPermissionIdentity()
        } catch (_: RuntimeException) {
            // No shell identity was adopted on this thread.
        }
        var adopted = false
        try {
            automation.adoptShellPermissionIdentity(Manifest.permission.MANAGE_DOCUMENTS)
            adopted = true
        } catch (_: RuntimeException) {
            // Shell does not hold MANAGE_DOCUMENTS on a normal device image.
        }
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            val tree = TestArchiveDocumentsProvider.treeUri()
            val context = instrumentation.context
            try {
                context.grantUriPermission(context.packageName, tree, flags)
                context.grantUriPermission(instrumentation.targetContext.packageName, tree, flags)
            } catch (_: SecurityException) {
                // The provider is exported, so the test resolver can call it without this grant.
            }
        } finally {
            if (adopted) automation.dropShellPermissionIdentity()
        }
    }

    private fun stringField(line: String, key: String): String {
        val marker = "\"$key\":\""
        val start = line.indexOf(marker)
        assertTrue("missing $key in $line", start >= 0)
        val from = start + marker.length
        val end = line.indexOf('"', from)
        assertTrue("unterminated $key in $line", end > from)
        return line.substring(from, end)
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val digits = "0123456789abcdef"
        val chars = CharArray(digest.size * 2)
        digest.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            chars[index * 2] = digits[value ushr 4]
            chars[index * 2 + 1] = digits[value and 0x0f]
        }
        return String(chars)
    }

    private fun jpegLikeBytes(): ByteArray {
        val bytes = ByteArray(4 * 1024)
        Random(0xC0FFEE).nextBytes(bytes)
        bytes[0] = 0xFF.toByte()
        bytes[1] = 0xD8.toByte()
        bytes[2] = 0xFF.toByte()
        return bytes
    }

    private fun driveContext(): Context = InstrumentationRegistry.getInstrumentation().context

    private fun targetContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        const val CAMERA_FOLDER = "DCIM/Camera/"
    }
}
