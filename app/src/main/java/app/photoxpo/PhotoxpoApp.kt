package app.photoxpo

import android.app.Application
import android.content.Context
import app.photoxpo.core.ManifestReader
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
 * Holds the on-phone photo cache.
 * [driveOnline] starts false. Nothing in this build marks an Archive Drive as attached.
 */
class ServiceLocator(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val driveOnline = MutableStateFlow(false)

    val photosRepository: PhotosRepository = PhotosRepository(
        dao = PhotosDatabase.get(appContext).photoDao(),
        manifestReader = ManifestReader(),
        driveOnlineSource = driveOnline,
        scope = appScope,
    )
}
