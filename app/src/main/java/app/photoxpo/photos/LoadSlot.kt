package app.photoxpo.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key

@Composable
fun LoadSlot(
    photo: PhotoEntity,
    repository: PhotosRepository,
    controller: LoadController,
) {
    // Filled by the photo view in this ticket.
    key(photo.hash, repository, controller) {}
}
