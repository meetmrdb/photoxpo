package app.photoxpo.photos

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "0:07", "2:35" or "1:02:10". Null when the length is unknown. */
internal fun videoDurationLabel(durationMillis: Long?): String? {
    if (durationMillis == null || durationMillis <= 0) return null
    val seconds = (durationMillis + 500) / 1000
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
}

/**
 * The video's length as the phone's gallery knows it. Only a video still on the phone has one;
 * a video that lives on the Archive Drive alone keeps the plain "Video" chip.
 */
@Composable
internal fun rememberVideoDuration(mediaId: Long?): State<String?> {
    val context = LocalContext.current
    return produceState<String?>(initialValue = null, mediaId) {
        value = mediaId?.let { id -> withContext(Dispatchers.IO) { videoDurationLabel(queryDuration(context, id)) } }
    }
}

private fun queryDuration(context: Context, mediaId: Long): Long? = try {
    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId)
    context.contentResolver.query(uri, arrayOf(MediaStore.Video.Media.DURATION), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }
} catch (_: Exception) {
    null
}
