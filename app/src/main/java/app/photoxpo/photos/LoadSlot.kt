package app.photoxpo.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key

@Composable
fun LoadSlot(photo: PhotoEntity) {
    // Filled by ticket #32.
    key(photo.hash) {}
}
