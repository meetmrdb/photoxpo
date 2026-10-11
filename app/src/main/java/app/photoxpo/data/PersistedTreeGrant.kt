package app.photoxpo.data

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

private const val TREE_GRANT_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

fun takePersistableTreeGrant(contentResolver: ContentResolver, treeUri: Uri) {
    contentResolver.takePersistableUriPermission(treeUri, TREE_GRANT_FLAGS)
}

fun persistedTreeGrants(contentResolver: ContentResolver): List<Uri> {
    return contentResolver.persistedUriPermissions.map { permission -> permission.uri }
}

/**
 * True when [stored] still has a read and write grant.
 * [ContentResolver.persistedUriPermissions] is the list that survives process death and reboot.
 */
fun isPersistedTreeGrantHeld(contentResolver: ContentResolver, stored: Uri): Boolean {
    return contentResolver.persistedUriPermissions.any { permission ->
        permission.isReadPermission &&
            permission.isWritePermission &&
            sameTree(permission.uri, stored)
    }
}

private fun sameTree(granted: Uri, stored: Uri): Boolean {
    if (granted == stored) return true
    return try {
        granted.authority == stored.authority &&
            DocumentsContract.getTreeDocumentId(granted) == DocumentsContract.getTreeDocumentId(stored)
    } catch (_: IllegalArgumentException) {
        false
    }
}
