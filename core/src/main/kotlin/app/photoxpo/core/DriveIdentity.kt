package app.photoxpo.core

internal const val DRIVE_ID_PATH = "Photoxpo/.photoxpo/drive-id"

/**
 * Phone-side memory of which Archive Drive is in use.
 * [expected] is null until this phone has remembered an id.
 */
interface DriveIdentityStore {
    fun expected(): String?

    fun remember(id: String)
}

/**
 * Phone cache of media id to Original hash.
 * The Archive Drive wins: a Backup run re-derives Backed up from Manifests.
 */
interface BackedUpLedger {
    fun record(mediaId: String, hash: String)

    fun clear()
}

/** Preview kept on the phone. [originalNotOnThisDrive] means the Original is not on this Archive Drive. */
data class PreviewRecord(
    val mediaId: String,
    val originalNotOnThisDrive: Boolean = false,
)

interface PreviewRecordStore {
    fun markOriginalNotOnThisDrive()
}

class InMemoryDriveIdentityStore(
    remembered: String? = null,
) : DriveIdentityStore {
    private var rememberedId: String? = remembered

    override fun expected(): String? = rememberedId

    override fun remember(id: String) {
        rememberedId = id
    }
}

class InMemoryBackedUpLedger : BackedUpLedger {
    private val stored = linkedMapOf<String, String>()

    override fun record(mediaId: String, hash: String) {
        stored[mediaId] = hash
    }

    override fun clear() {
        stored.clear()
    }

    fun hashes(): Map<String, String> = stored.toMap()
}

class InMemoryPreviewRecordStore(
    records: List<PreviewRecord> = emptyList(),
) : PreviewRecordStore {
    private val stored = records.toMutableList()

    fun records(): List<PreviewRecord> = stored.toList()

    override fun markOriginalNotOnThisDrive() {
        for (index in stored.indices) {
            stored[index] = stored[index].copy(originalNotOnThisDrive = true)
        }
    }
}
