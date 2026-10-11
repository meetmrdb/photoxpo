package app.photoxpo.onboarding

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import app.photoxpo.backup.BackupController
import app.photoxpo.backup.BackupSettingsStore
import app.photoxpo.core.MediaFile
import app.photoxpo.core.MediaKind
import app.photoxpo.core.MediaSource
import app.photoxpo.core.PreviewPreset
import app.photoxpo.data.AndroidPreviewMaker
import app.photoxpo.data.MediaStoreSource
import app.photoxpo.data.hasFullMediaAccess
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PreviewJpeg(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        return other is PreviewJpeg && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()
}

data class QualityCardState(
    val preview: PreviewJpeg? = null,
    val failed: Boolean = false,
    val projectedBytes: Long? = null,
)

data class OnboardingUiState(
    val onboarding: OnboardingState = OnboardingState(),
    val access: MediaAccessState? = null,
    val accessRequested: Boolean = false,
    val driveMessage: String? = null,
    val usingSample: Boolean = false,
    val photoName: String? = null,
    val originalBytes: Long? = null,
    val canTryAnother: Boolean = false,
    val libraryCount: Int = 0,
    val cards: Map<PreviewPreset, QualityCardState> = emptyMap(),
)

class OnboardingViewModel(
    private val settings: BackupSettingsStore,
    private val backup: BackupController,
    private val source: MediaSource,
    private val openOriginal: (String) -> InputStream,
    private val hasFullAccess: () -> Boolean,
    private val isGranted: (String) -> Boolean,
    private val openSample: () -> InputStream,
    private val makePreview: (PreviewPreset, InputStream) -> ByteArray,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val onePreviewAtATime = Mutex()
    private val _ui = MutableStateFlow(OnboardingUiState())
    val ui: StateFlow<OnboardingUiState> = _ui.asStateFlow()

    private var previewStarted = false
    private var photos: List<MediaFile> = emptyList()
    private var librarySize: Int = 0
    private var currentPhotoId: String? = null
    private var previewJob: kotlinx.coroutines.Job? = null

    @Volatile
    private var generation: Int = 0

    init {
        refreshAccess()
    }

    fun refreshAccess() {
        val access = classifyMediaAccess(
            fullAccess = hasFullAccess(),
            imagesGranted = isGranted(Manifest.permission.READ_MEDIA_IMAGES),
            videosGranted = isGranted(Manifest.permission.READ_MEDIA_VIDEO),
            selectedGranted = isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
        )
        _ui.update { it.copy(access = access) }
    }

    fun onPermissionResult() {
        _ui.update { it.copy(accessRequested = true) }
        refreshAccess()
    }

    fun onEvent(event: OnboardingEvent) {
        _ui.update { it.copy(onboarding = reduce(it.onboarding, event)) }
    }

    fun onDriveChosen() {
        _ui.update {
            it.copy(
                onboarding = reduce(it.onboarding, OnboardingEvent.DriveChosen),
                driveMessage = null,
            )
        }
    }

    fun onDriveMessage(message: String) {
        _ui.update { it.copy(driveMessage = message) }
    }

    fun deferDrive() {
        _ui.update {
            val deferred = reduce(it.onboarding, OnboardingEvent.DriveDeferred)
            it.copy(
                onboarding = reduce(deferred, OnboardingEvent.Next),
                driveMessage = null,
            )
        }
    }

    fun ensurePreviewPhoto() {
        if (previewStarted) return
        previewStarted = true
        scope.launch {
            val files = try {
                withContext(Dispatchers.IO) { source.listCameraFolder() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            librarySize = files.size
            photos = cameraPhotos(files)
            _ui.update { it.copy(libraryCount = files.size) }
            val newest = newestCameraPhoto(photos)
            if (newest == null) {
                showSample()
            } else {
                showCameraPhoto(newest)
            }
        }
    }

    fun tryAnotherPhoto() {
        if (_ui.value.usingSample) return
        val current = currentPhotoId ?: return
        val next = anotherCameraPhoto(photos, current, Random.nextInt()) ?: return
        if (next.id == current) return
        scope.launch { showCameraPhoto(next) }
    }

    fun finish() {
        val state = _ui.value.onboarding
        settings.setPreviewPreset(state.preset)
        settings.setOnboardingDone(true)
        if (state.driveChosen) {
            backup.start()
        }
    }

    override fun onCleared() {
        scope.cancel()
    }

    private suspend fun showCameraPhoto(file: MediaFile) {
        val bytes = try {
            withContext(Dispatchers.IO) {
                openOriginal(file.id).use { it.readBytes() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (bytes == null) {
            showSample()
            return
        }
        currentPhotoId = file.id
        render(
            bytes = bytes,
            originalSize = file.sizeBytes.coerceAtLeast(0L),
            usingSample = false,
            photoName = file.name,
        )
    }

    private suspend fun showSample() {
        val bytes = try {
            withContext(Dispatchers.IO) {
                openSample().use { it.readBytes() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        currentPhotoId = null
        if (bytes == null) {
            _ui.update {
                it.copy(
                    usingSample = true,
                    photoName = null,
                    originalBytes = null,
                    canTryAnother = false,
                    cards = PreviewPreset.entries.associateWith { QualityCardState(failed = true) },
                )
            }
            return
        }
        render(
            bytes = bytes,
            originalSize = bytes.size.toLong(),
            usingSample = true,
            photoName = null,
        )
    }

    private fun render(
        bytes: ByteArray,
        originalSize: Long,
        usingSample: Boolean,
        photoName: String?,
    ) {
        val gen = ++generation
        previewJob?.cancel()
        _ui.update {
            it.copy(
                usingSample = usingSample,
                photoName = photoName,
                originalBytes = originalSize,
                canTryAnother = !usingSample && photos.size > 1,
                cards = emptyMap(),
            )
        }
        previewJob = scope.launch {
            val built = linkedMapOf<PreviewPreset, QualityCardState>()
            for (preset in PreviewPreset.entries) {
                ensureActive()
                val card = try {
                    val jpeg = onePreviewAtATime.withLock {
                        withContext(Dispatchers.IO) {
                            ByteArrayInputStream(bytes).use { stream ->
                                makePreview(preset, stream)
                            }
                        }
                    }
                    QualityCardState(
                        preview = PreviewJpeg(jpeg),
                        projectedBytes = projectedTotalBytes(
                            sampleOriginalBytes = originalSize,
                            samplePreviewBytes = jpeg.size.toLong(),
                            libraryCount = librarySize,
                        ),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    QualityCardState(failed = true)
                }
                if (gen != generation) return@launch
                built[preset] = card
                val snapshot = built.toMap()
                _ui.update { state -> state.copy(cards = snapshot) }
            }
        }
    }

    companion object {
        const val SAMPLE_ASSET = "sample_photo.jpg"

        private fun openCameraPhoto(context: Context, media: MediaSource, id: String): InputStream {
            try {
                return media.openOriginal(id)
            } catch (_: Exception) {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Files.getContentUri("external"),
                    id.toLong(),
                )
                return context.contentResolver.openInputStream(uri)
                    ?: throw FileNotFoundException(id)
            }
        }

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            val media = MediaStoreSource(appContext)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return OnboardingViewModel(
                        settings = BackupSettingsStore(appContext),
                        backup = BackupController.get(appContext),
                        source = media,
                        openOriginal = { id -> openCameraPhoto(appContext, media, id) },
                        hasFullAccess = { hasFullMediaAccess(appContext) },
                        isGranted = { permission ->
                            appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                        },
                        openSample = { appContext.assets.open(SAMPLE_ASSET) },
                        makePreview = { preset, input ->
                            AndroidPreviewMaker(appContext, preset).makePreview(input, MediaKind.PHOTO)
                        },
                    ) as T
                }
            }
        }
    }
}
