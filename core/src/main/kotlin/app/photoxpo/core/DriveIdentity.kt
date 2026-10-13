package app.photoxpo.core

import java.time.Instant

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

    /**
     * Records that [file] was copied and checked identical at [checkedAt].
     * Ledgers that cannot keep a check time fall back to [record]; the Original then counts as not checked recently.
     */
    fun recordChecked(file: MediaFile, hash: String, checkedAt: Instant) = record(file.id, hash)

    /** When [mediaId] was last checked, or null when unknown. A record with a null check time is "not checked recently". */
    fun checkedOriginal(mediaId: String): CheckedOriginal? = null

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
    private val checks = mutableMapOf<String, CheckedOriginal>()

    override fun record(mediaId: String, hash: String) {
        stored[mediaId] = hash
        if (checks[mediaId]?.hash != hash) checks.remove(mediaId)
    }

    override fun recordChecked(file: MediaFile, hash: String, checkedAt: Instant) {
        stored[file.id] = hash
        checks[file.id] = CheckedOriginal(hash, file.sizeBytes, file.modifiedAtEpochMillis, checkedAt)
    }

    override fun checkedOriginal(mediaId: String): CheckedOriginal? {
        val hash = stored[mediaId] ?: return null
        return checks[mediaId] ?: CheckedOriginal(hash, -1L, -1L, null)
    }

    override fun clear() {
        stored.clear()
        checks.clear()
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
