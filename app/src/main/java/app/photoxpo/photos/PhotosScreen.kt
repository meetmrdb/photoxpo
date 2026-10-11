package app.photoxpo.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.photoxpo.R
import java.time.ZoneId
import java.util.Locale

@Composable
fun PhotosRoute(
    repository: PhotosRepository,
    previewCache: PreviewBitmapCache,
    onOpenPhoto: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val photos by produceState<List<PhotoEntity>?>(initialValue = null, repository) {
        repository.photos().collect { value = it }
    }
    val ready = photos
    if (ready == null) {
        Box(modifier = modifier.fillMaxSize())
        return
    }
    PhotosGrid(
        photos = ready,
        previewCache = previewCache,
        onOpenPhoto = onOpenPhoto,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
fun PhotosGrid(
    photos: List<PhotoEntity>,
    previewCache: PreviewBitmapCache,
    onOpenPhoto: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    if (photos.isEmpty()) {
        EmptyPhotos(modifier = modifier, contentPadding = contentPadding)
        return
    }
    val zone = remember { ZoneId.systemDefault() }
    val locale = Locale.getDefault()
    val groups = remember(photos, zone, locale) { groupByMonth(photos, zone, locale) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (group in groups) {
            item(
                key = "header-${group.label}",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "header",
            ) {
                Text(
                    text = group.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(
                items = group.photos,
                key = { photo -> photo.hash },
                contentType = { "photo" },
            ) { photo ->
                PhotoTile(
                    photo = photo,
                    previewCache = previewCache,
                    onClick = { onOpenPhoto(photo.hash) },
                )
            }
        }
    }
}

@Composable
private fun EmptyPhotos(
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No photos yet",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Previews of your photos will show here.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PhotoTile(
    photo: PhotoEntity,
    previewCache: PreviewBitmapCache,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics {
                contentDescription = if (photo.originalOnThisDrive) {
                    "${photo.name}. On your Archive Drive"
                } else {
                    photo.name
                }
            }
            .clickable(onClick = onClick),
    ) {
        PreviewImage(
            hash = photo.hash,
            contentDescription = photo.name,
            contentScale = ContentScale.Crop,
            cache = previewCache,
            modifier = Modifier.fillMaxSize(),
            maxEdge = 512,
        )
        if (photo.originalOnThisDrive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.72f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_archive_drive),
                    contentDescription = "On your Archive Drive",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
