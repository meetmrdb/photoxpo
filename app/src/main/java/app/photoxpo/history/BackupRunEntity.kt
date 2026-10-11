package app.photoxpo.history

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One Backup run in the on-phone cache, keyed by the run file name.
 */
@Entity(tableName = "backup_runs")
data class BackupRunEntity(
    @PrimaryKey val runFile: String,
    val startedAt: String,
    val endedAt: String?,
    val fileCount: Long,
    val bytes: Long,
    val checked: Boolean,
    val elapsedMs: Long?,
    val mbPerSecond: Double?,
    val interrupted: Boolean,
)
