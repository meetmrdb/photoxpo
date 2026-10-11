package app.photoxpo.photos

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One photo in the on-phone cache.
 *
 * [chosenDate] is the Manifest date. It is an ISO-8601 local date (`yyyy-MM-dd`),
 * the same text the Archive Drive stores. A decimal string of epoch milliseconds
 * is also accepted when a caller only has an instant. Month grouping treats a
 * local date as that calendar day, and treats milliseconds as an instant in the
 * caller's time zone.
 */
@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val hash: String,
    val mediaId: Long?,
    val name: String,
    val chosenDate: String?,
    val sourceTag: String?,
    val kind: String?,
    val sizeBytes: Long,
    val drivePath: String?,
    val backedUp: Boolean,
    val previewFile: String?,
    val originalOnThisDrive: Boolean,
    val originalOnPhone: Boolean,
)
