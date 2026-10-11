package app.photoxpo.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.photoxpo.PhotoxpoApp
import app.photoxpo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
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
        else -> PhotoViewer(
            photo = photo,
            repository = repository,
            previewCache = previewCache,
            onBack = onBack,
        )
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
    repository: PhotosRepository,
    previewCache: PreviewBitmapCache,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val services = (context.applicationContext as PhotoxpoApp).services
    val controller = remember(photo.hash) {
        LoadController(
            archiveDrive = services::archiveDrive,
            gallery = services.phoneGallery,
            repository = repository,
            scope = scope,
        )
    }
    LaunchedEffect(photo.hash) {
        withContext(Dispatchers.IO) {
            services.refreshDriveOnline()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch(Dispatchers.IO) {
                    services.refreshDriveOnline()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val filesDir = context.filesDir
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
    val loadState by produceState(initialValue = controller.state.value, controller) {
        controller.state.collect { value = it }
    }
    val loadedUri = (loadState as? LoadUiState.Loaded)?.uri

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        PreviewImage(
            hash = photo.hash,
            contentDescription = if (loadedUri.isNullOrBlank()) photo.name else null,
            contentScale = ContentScale.Fit,
            cache = previewCache,
            modifier = Modifier.fillMaxSize(),
        )
        if (!loadedUri.isNullOrBlank()) {
            LoadedOriginalImage(
                uri = loadedUri,
                contentDescription = photo.name,
                modifier = Modifier.fillMaxSize(),
            )
        }
        PhotoTopBar(
            onBack = onBack,
            onInfo = { showInfo = true },
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            LoadSlot(photo = photo, repository = repository, controller = controller)
        }
    }

    if (showInfo) {
        PhotoInfoSheet(
            info = info,
            onDismiss = { showInfo = false },
        )
    }
}

@Composable
private fun LoadedOriginalImage(
    uri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        val parsed = Uri.parse(uri)
        bitmap = withContext(Dispatchers.IO) {
            decodeLoadedOriginal(context, parsed)
        }
    }
    val current = bitmap ?: return
    Image(
        bitmap = current.asImageBitmap(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

private fun decodeLoadedOriginal(context: Context, uri: Uri): Bitmap? {
    val mime = context.contentResolver.getType(uri)
    if (mime != null && mime.startsWith("video/")) {
        return videoFrame(context, uri)
    }
    return try {
        decodeImage(context, uri)
    } catch (_: IOException) {
        videoFrame(context, uri)
    }
}

private fun decodeImage(context: Context, uri: Uri): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setTargetSampleSize(sampleSize(info.size.width, info.size.height))
    }
}

private fun videoFrame(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        return retriever.getScaledFrameAtTime(
            0,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            DISPLAY_EDGE,
            DISPLAY_EDGE,
        )
    } catch (_: RuntimeException) {
        return null
    } finally {
        retriever.release()
    }
}

private fun sampleSize(width: Int, height: Int): Int {
    var sample = 1
    var edgeW = width
    var edgeH = height
    while (edgeW / 2 >= DISPLAY_EDGE || edgeH / 2 >= DISPLAY_EDGE) {
        edgeW /= 2
        edgeH /= 2
        sample *= 2
    }
    return sample
}

private const val DISPLAY_EDGE = 2048

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
