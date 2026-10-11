package app.photoxpo.backup

import android.content.Context
import android.net.Uri
import app.photoxpo.core.BackedUpLedger
import app.photoxpo.core.DriveIdentityStore
import app.photoxpo.core.PreviewPreset

/**
 * Phone-side memory for the Archive Drive: expected drive id, Backed up hashes,
 * last Backed up time, the last time the Archive Drive was connected, chosen tree,
 * file cap, onboarding, and Preview preset.
 */
class BackupSettingsStore(
    context: Context,
) : DriveIdentityStore, BackedUpLedger {
    private val settings = context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    private val ledger = context.applicationContext.getSharedPreferences(LEDGER_PREFS, Context.MODE_PRIVATE)

    fun fileCap(): Int {
        val stored = settings.getInt(KEY_FILE_CAP, DEFAULT_FILE_CAP)
        return if (stored < 1) DEFAULT_FILE_CAP else stored
    }

    fun setFileCap(fileCap: Int) {
        val stored = if (fileCap < 1) DEFAULT_FILE_CAP else fileCap
        settings.edit().putInt(KEY_FILE_CAP, stored).commit()
    }

    fun onboardingDone(): Boolean = settings.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(done: Boolean) {
        settings.edit().putBoolean(KEY_ONBOARDING_DONE, done).commit()
    }

    fun previewPreset(): PreviewPreset {
        val stored = settings.getString(KEY_PREVIEW_PRESET, null) ?: return PreviewPreset.BALANCED
        return try {
            PreviewPreset.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            PreviewPreset.BALANCED
        }
    }

    fun setPreviewPreset(preset: PreviewPreset) {
        settings.edit().putString(KEY_PREVIEW_PRESET, preset.name).commit()
    }

    fun treeUri(): Uri? {
        val raw = settings.getString(KEY_TREE_URI, null) ?: return null
        return Uri.parse(raw)
    }

    fun setTreeUri(uri: Uri) {
        settings.edit().putString(KEY_TREE_URI, uri.toString()).commit()
    }

    fun lastBackedUpAtEpochMillis(): Long? {
        if (!settings.contains(KEY_LAST_BACKED_UP_AT)) return null
        return settings.getLong(KEY_LAST_BACKED_UP_AT, 0L)
    }

    fun setLastBackedUpAtEpochMillis(epochMillis: Long) {
        settings.edit().putLong(KEY_LAST_BACKED_UP_AT, epochMillis).commit()
    }

    fun lastConnectedAtEpochMillis(): Long? {
        if (!settings.contains(KEY_LAST_CONNECTED_AT)) return null
        return settings.getLong(KEY_LAST_CONNECTED_AT, 0L)
    }

    fun setLastConnectedAtEpochMillis(epochMillis: Long) {
        settings.edit().putLong(KEY_LAST_CONNECTED_AT, epochMillis).commit()
    }

    override fun expected(): String? {
        val id = settings.getString(KEY_DRIVE_ID, null)?.trim()
        return id?.ifEmpty { null }
    }

    override fun remember(id: String) {
        settings.edit().putString(KEY_DRIVE_ID, id.trim()).commit()
    }

    override fun record(mediaId: String, hash: String) {
        ledger.edit().putString(mediaId, hash).commit()
    }

    override fun clear() {
        ledger.edit().clear().commit()
    }

    /** Backed up Originals this phone knows about: MediaStore id to SHA-256. The drive wins; this is a cache. */
    fun ledgerEntries(): Map<String, String> {
        return ledger.all.mapNotNull { (mediaId, hash) -> (hash as? String)?.let { mediaId to it } }.toMap()
    }

    private companion object {
        const val SETTINGS_PREFS = "photoxpo.backup"
        const val LEDGER_PREFS = "photoxpo.backed-up"
        const val KEY_LAST_BACKED_UP_AT = "lastBackedUpAtEpochMillis"
        const val KEY_LAST_CONNECTED_AT = "lastConnectedAtEpochMillis"
        const val KEY_DRIVE_ID = "expectedDriveId"
        const val KEY_TREE_URI = "treeUri"
        const val KEY_FILE_CAP = "fileCap"
        const val KEY_ONBOARDING_DONE = "onboardingDone"
        const val KEY_PREVIEW_PRESET = "previewPreset"
    }
}
