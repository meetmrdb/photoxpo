package app.photoxpo.data

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import java.io.FileNotFoundException

fun probeArchiveDrive(context: Context, treeUri: Uri): DriveStatus {
    val volumeState = volumeStateForTree(context, treeUri)
    val probed = queryTree(context, treeUri)
    return driveStatus(
        probeFound = probed.found,
        supportsWrite = probed.supportsWrite,
        volumeState = volumeState,
    )
}

private data class TreeProbe(
    val found: Boolean,
    val supportsWrite: Boolean,
)

private fun volumeStateForTree(context: Context, treeUri: Uri): String? {
    val rootId = treeRootId(treeUri) ?: return null
    val manager = context.getSystemService(StorageManager::class.java) ?: return null
    val volume = manager.storageVolumes.firstOrNull { candidate ->
        if (rootId == "primary") candidate.isPrimary else candidate.uuid == rootId
    } ?: return null
    return volume.state
}

private fun treeRootId(treeUri: Uri): String? {
    return try {
        DocumentsContract.getTreeDocumentId(treeUri).substringBefore(':')
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun queryTree(context: Context, treeUri: Uri): TreeProbe {
    val documentId = try {
        DocumentsContract.getTreeDocumentId(treeUri)
    } catch (_: IllegalArgumentException) {
        return TreeProbe(found = false, supportsWrite = false)
    }
    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    val cursor = try {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
            null,
            null,
            null,
        )
    } catch (_: FileNotFoundException) {
        return TreeProbe(found = false, supportsWrite = false)
    } catch (_: IllegalArgumentException) {
        return TreeProbe(found = false, supportsWrite = false)
    }
    if (cursor == null) {
        return TreeProbe(found = false, supportsWrite = false)
    }
    cursor.use { rows ->
        if (!rows.moveToFirst()) {
            return TreeProbe(found = false, supportsWrite = false)
        }
        val flags = if (rows.isNull(0)) 0 else rows.getInt(0)
        val write = flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0
        val create = flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
        return TreeProbe(found = true, supportsWrite = write || create)
    }
}

/** Room left on the Archive Drive's volume, or null when the volume is not readable. */
fun archiveDriveFreeBytes(context: Context, treeUri: Uri): Long? {
    val rootId = treeRootId(treeUri) ?: return null
    val manager = context.getSystemService(StorageManager::class.java) ?: return null
    val volume = manager.storageVolumes.firstOrNull { candidate ->
        if (rootId == "primary") candidate.isPrimary else candidate.uuid == rootId
    } ?: return null
    val directory = volume.directory ?: return null
    return try {
        StatFs(directory.path).availableBytes
    } catch (_: Exception) {
        null
    }
}
