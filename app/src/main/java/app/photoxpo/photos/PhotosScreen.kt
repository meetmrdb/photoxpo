package app.photoxpo.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import app.photoxpo.ui.theme.PhotoOverlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        Text(
            text = "Photos",
            modifier = Modifier
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 20.dp)
                .wrapContentHeight(Alignment.CenterVertically)
                .semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        if (photos.isEmpty()) {
            EmptyPhotos()
        } else {
            GroupedGrid(photos = photos, previewCache = previewCache, onOpenPhoto = onOpenPhoto)
        }
    }
}

@Composable
private fun GroupedGrid(
    photos: List<PhotoEntity>,
    previewCache: PreviewBitmapCache,
    onOpenPhoto: (String) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val locale = Locale.getDefault()
    val groups = remember(photos, zone, locale) { groupByMonth(photos, zone, locale) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    LazyVerticalGrid(
        columns = if (wide) GridCells.Adaptive(minSize = 128.dp) else GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
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
                        .padding(start = 13.dp, end = 13.dp, top = 20.dp, bottom = 8.dp)
                        .semantics { heading() },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        item(
            key = "legend",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "legend",
        ) {
            Legend()
        }
    }
}

@Composable
private fun Legend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 13.dp, end = 13.dp, top = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DriveBadge(contentDescription = null)
        Text(
            text = "This mark means the Original is on your Archive Drive.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyPhotos() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No photos yet",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Previews of your photos will show here.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DriveBadge(contentDescription: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .background(PhotoOverlay.Scrim, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_archive_drive),
            contentDescription = contentDescription,
            tint = PhotoOverlay.OnScrim,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun PhotoTile(
    photo: PhotoEntity,
    previewCache: PreviewBitmapCache,
    onClick: () -> Unit,
) {
    val isVideo = photo.kind.equals("video", ignoreCase = true)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics {
                val where = if (photo.originalOnThisDrive) ". On your Archive Drive" else ""
                contentDescription = (if (isVideo) "Video " else "") + photo.name + where
            }
            .clickable(onClick = onClick),
    ) {
        PreviewImage(
            hash = photo.hash,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            cache = previewCache,
            modifier = Modifier.fillMaxSize(),
            maxEdge = 512,
        )
        if (isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .height(22.dp)
                    .background(PhotoOverlay.Scrim, RoundedCornerShape(11.dp))
                    .padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = PhotoOverlay.OnScrim,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = "Video",
                    style = MaterialTheme.typography.labelMedium,
                    color = PhotoOverlay.OnScrim,
                )
            }
        }
        if (photo.originalOnThisDrive) {
            DriveBadge(
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp),
            )
        }
    }
}
