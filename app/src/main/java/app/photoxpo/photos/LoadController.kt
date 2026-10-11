package app.photoxpo.photos

import app.photoxpo.core.ArchiveDrive
import app.photoxpo.core.DriveUnavailableException
import app.photoxpo.core.LoadResult
import app.photoxpo.core.OriginalLoader
import app.photoxpo.core.PhoneGallery
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads one Original onto the phone and publishes [LoadUiState].
 * [cancel] stops the copy and leaves nothing partial in the gallery.
 */
class LoadController(
    private val archiveDrive: () -> ArchiveDrive,
    private val gallery: PhoneGallery,
    private val repository: PhotosRepository,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow<LoadUiState>(LoadUiState.Idle)
    val state: StateFlow<LoadUiState> = _state.asStateFlow()

    val loadedUri: String?
        get() = (state.value as? LoadUiState.Loaded)?.uri

    private val cancelFlag = AtomicBoolean(false)
    private var job: Job? = null

    fun load(photo: PhotoEntity) {
        if (job?.isActive == true) return
        if (photo.originalOnPhone || state.value is LoadUiState.Loaded) return
        if (!repository.driveOnline.value) {
            _state.value = LoadUiState.Disabled(LOAD_NEEDS_DRIVE)
            return
        }
        val drivePath = photo.drivePath
        if (drivePath.isNullOrBlank()) {
            _state.value = LoadUiState.Failed(LOAD_NOT_ON_DRIVE, canRetry = false)
            return
        }
        cancelFlag.set(false)
        _state.value = LoadUiState.Loading(progress = 0)
        val hash = photo.hash
        val expectedSize = photo.sizeBytes
        val name = photo.name
        val mimeHint = mimeHintFor(photo.kind, photo.name)
        job = scope.launch {
            val result = try {
                withContext(io) {
                    val current = coroutineContext[Job]
                    val drive = try {
                        archiveDrive()
                    } catch (_: DriveUnavailableException) {
                        return@withContext LoadResult.DriveGone
                    }
                    OriginalLoader(drive, gallery).load(
                        hash = hash,
                        drivePath = drivePath,
                        expectedSize = expectedSize,
                        name = name,
                        mimeHint = mimeHint,
                        isCancelled = { cancelFlag.get() || current?.isActive == false },
                        onProgress = { copied ->
                            _state.value = LoadUiState.Loading(copied)
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: DriveUnavailableException) {
                LoadResult.DriveGone
            } catch (_: Exception) {
                LoadResult.Failed
            }
            when (result) {
                is LoadResult.Loaded -> finishLoaded(hash, result.uri)
                LoadResult.NotOnDrive -> {
                    _state.value = LoadUiState.Failed(LOAD_NOT_ON_DRIVE, canRetry = true)
                }
                LoadResult.DriveGone -> {
                    _state.value = LoadUiState.Failed(LOAD_DRIVE_UNPLUGGED, canRetry = true)
                }
                LoadResult.Cancelled -> _state.value = LoadUiState.Idle
                LoadResult.Failed -> {
                    _state.value = LoadUiState.Failed(LOAD_CHECK_FAILED, canRetry = true)
                }
            }
        }
    }

    fun cancel() {
        cancelFlag.set(true)
    }

    private suspend fun finishLoaded(hash: String, uri: String) {
        withContext(NonCancellable) {
            try {
                repository.markOriginalOnPhone(hash)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The Original is already in the phone gallery.
            }
            _state.value = LoadUiState.Loaded(uri)
        }
    }
}

internal fun mimeHintFor(kind: String?, name: String): String? {
    val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    val fromName = when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "heic", "heif" -> "image/heic"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "dng" -> "image/x-adobe-dng"
        "mp4", "m4v" -> "video/mp4"
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "3gp" -> "video/3gpp"
        else -> null
    }
    if (fromName != null) return fromName
    return when (kind) {
        "video" -> "video/mp4"
        "photo" -> "image/jpeg"
        else -> null
    }
}
