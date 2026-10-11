package app.photoxpo.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.photoxpo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.Locale

@Composable
fun PhotoRoute(
    hash: String,
    repository: PhotosRepository,
    previewCache: PreviewBitmapCache,
    onBack: () -> Unit,
) {
    if (hash.isBlank()) {
        PhotoMessage(message = "This photo is not on this phone.", onBack = onBack)
        return
    }
    val loaded by produceState<LoadedPhoto?>(initialValue = null, hash, repository) {
        repository.photo(hash).collect { value = LoadedPhoto(it) }
    }
    val photo = loaded?.photo
    when {
        loaded == null -> PhotoMessage(message = "", onBack = onBack, showMessage = false)
        photo == null -> PhotoMessage(message = "This photo is not on this phone.", onBack = onBack)
        else -> PhotoViewer(photo = photo, previewCache = previewCache, onBack = onBack)
    }
}

private data class LoadedPhoto(val photo: PhotoEntity?)

@Composable
private fun PhotoMessage(
    message: String,
    onBack: () -> Unit,
    showMessage: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (showMessage) {
            Text(
                text = message,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }
        PhotoTopBar(
            onBack = onBack,
            onInfo = null,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun PhotoViewer(
    photo: PhotoEntity,
    previewCache: PreviewBitmapCache,
    onBack: () -> Unit,
) {
    val filesDir = LocalContext.current.filesDir
    val previewSizeBytes by produceState<Long?>(initialValue = null, photo.hash) {
        value = withContext(Dispatchers.IO) {
            val file = previewJpeg(filesDir, photo.hash)
            if (file.isFile) file.length() else null
        }
    }
    val info = remember(photo, previewSizeBytes) {
        PhotoInfo.from(
            photo = photo,
            previewSizeBytes = previewSizeBytes,
            zone = ZoneId.systemDefault(),
            locale = Locale.getDefault(),
        )
    }
    var showInfo by remember(photo.hash) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        PreviewImage(
            hash = photo.hash,
            contentDescription = photo.name,
            contentScale = ContentScale.Fit,
            cache = previewCache,
            modifier = Modifier.fillMaxSize(),
        )
        PhotoTopBar(
            onBack = onBack,
            onInfo = { showInfo = true },
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            LoadSlot(photo)
        }
    }

    if (showInfo) {
        PhotoInfoSheet(
            info = info,
            onDismiss = { showInfo = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoTopBar(
    onBack: () -> Unit,
    onInfo: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier.fillMaxWidth(),
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "Back",
                )
            }
        },
        actions = {
            if (onInfo != null) {
                IconButton(onClick = onInfo) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = "Info",
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoInfoSheet(
    info: PhotoInfo,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Info",
                style = MaterialTheme.typography.headlineSmall,
            )
            InfoRow(label = "Date", value = info.date)
            InfoRow(label = "File name", value = info.fileName)
            InfoRow(
                label = "Sizes",
                value = "Original ${info.originalSize}\nPreview ${info.previewSize}",
            )
            InfoRow(label = "Where the Original is", value = info.whereTheOriginalIs)
            InfoRow(label = "Backed up", value = info.backedUp)
            InfoRow(label = "Drive folder", value = info.driveFolder)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
