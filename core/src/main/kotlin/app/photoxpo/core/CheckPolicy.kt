package app.photoxpo.core

import java.time.Duration
import java.time.Instant

/**
 * How long a Backed up Original counts as "checked recently". Inside this window a Backup run does not
 * read the Original again. Tune after hardware tests.
 */
val RECENTLY_CHECKED_WINDOW: Duration = Duration.ofDays(14)

/**
 * What the phone remembers about the last time an Original was checked identical to its copy on the
 * Archive Drive. [checkedAt] is null for older records that never stored a check time; those count as
 * not checked recently. [sizeBytes] and [modifiedAtEpochMillis] are the Original's values at that check,
 * so an Original edited on the phone since is never skipped.
 */
data class CheckedOriginal(
    val hash: String,
    val sizeBytes: Long,
    val modifiedAtEpochMillis: Long,
    val checkedAt: Instant?,
) {
    companion object {
        /** A Backed up Original with no stored check details; never counts as checked recently. */
        fun unknown(hash: String) = CheckedOriginal(hash, -1L, -1L, null)
    }
}

/**
 * Decides whether an Original may be trusted as checked without reading it again.
 * A Backup run uses [skipRecentlyChecked]. "Verify everything" uses [verifyEverything].
 * Free up space can ask [isRecentlyChecked] with [verifyEverything] to force a re-check
 * of just the Originals it is about to remove.
 */
class CheckPolicy private constructor(private val window: Duration?) {
    fun isRecentlyChecked(record: CheckedOriginal?, file: MediaFile, now: Instant): Boolean {
        val limit = window ?: return false
        if (record == null) return false
        val checkedAt = record.checkedAt ?: return false
        if (checkedAt.isAfter(now)) return false
        if (record.sizeBytes != file.sizeBytes) return false
        if (record.modifiedAtEpochMillis != file.modifiedAtEpochMillis) return false
        return Duration.between(checkedAt, now) < limit
    }

    companion object {
        fun skipRecentlyChecked(window: Duration = RECENTLY_CHECKED_WINDOW): CheckPolicy = CheckPolicy(window)

        val verifyEverything: CheckPolicy = CheckPolicy(null)
    }
}
