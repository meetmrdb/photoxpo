package app.photoxpo

import android.app.Application
import android.content.Context
import app.photoxpo.backup.BackupSettingsStore
import app.photoxpo.core.ArchiveDrive
import app.photoxpo.core.DriveUnavailableException
import app.photoxpo.core.ManifestReader
import app.photoxpo.core.PhoneGallery
import app.photoxpo.data.DriveStatus
import app.photoxpo.data.MediaStorePhoneGallery
import app.photoxpo.data.SafArchiveDrive
import app.photoxpo.data.probeArchiveDrive
import app.photoxpo.photos.PhotosDatabase
import app.photoxpo.photos.PhotosRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

class PhotoxpoApp : Application() {
    lateinit var services: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        services = ServiceLocator(this)
    }
}

/**
 * Holds the on-phone photo cache and the phone gallery.
 * [refreshDriveOnline] reads the Archive Drive chosen for Backup runs.
 */
class ServiceLocator(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val driveOnline = MutableStateFlow(false)
    private val backupSettings = BackupSettingsStore(appContext)

    val photosRepository: PhotosRepository = PhotosRepository(
        dao = PhotosDatabase.get(appContext).photoDao(),
        manifestReader = ManifestReader(),
        driveOnlineSource = driveOnline,
        scope = appScope,
    )

    val phoneGallery: PhoneGallery = MediaStorePhoneGallery(appContext)

    fun archiveDrive(): ArchiveDrive {
        val uri = backupSettings.treeUri()
            ?: throw DriveUnavailableException("Archive Drive is absent")
        return SafArchiveDrive(appContext, uri)
    }

    fun refreshDriveOnline() {
        val uri = backupSettings.treeUri()
        driveOnline.value = try {
            uri != null && probeArchiveDrive(appContext, uri) == DriveStatus.Ready
        } catch (_: Exception) {
            false
        }
    }
}
