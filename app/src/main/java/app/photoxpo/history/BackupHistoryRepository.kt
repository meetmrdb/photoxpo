package app.photoxpo.history

import app.photoxpo.backup.LastArchiveDriveConnection
import app.photoxpo.core.ArchiveDrive
import app.photoxpo.core.BackupHistoryReader
import app.photoxpo.core.BackupRunSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-phone cache of Backup history, keyed by run file.
 *
 * [refresh] reads the Archive Drive. [cached] is what the phone last stored,
 * including when the Archive Drive is not connected.
 */
class BackupHistoryRepository(
    private val dao: BackupRunDao,
    private val settings: LastArchiveDriveConnection,
    private val reader: BackupHistoryReader = BackupHistoryReader(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun lastConnectedAtEpochMillis(): Long? = settings.lastConnectedAtEpochMillis()

    suspend fun cached(): List<BackupRunSummary> {
        return dao.all().map { it.toSummary() }
    }

    /**
     * Reads run files on [drive] that are not already cached as finished Backup runs.
     * An interrupted run is omitted from that set, so it is read again.
     * Stores the rows and the time the Archive Drive was connected.
     * Returns the whole cache after the refresh.
     */
    suspend fun refresh(drive: ArchiveDrive): List<BackupRunSummary> {
        val known = dao.completedRunFiles().toSet()
        val read = withContext(Dispatchers.IO) { reader.read(drive, known) }
        if (read.isNotEmpty()) {
            dao.upsert(read.map { it.toEntity() })
        }
        settings.setLastConnectedAtEpochMillis(clock())
        return dao.all().map { it.toSummary() }
    }

    /** Drops cached Backup runs so a new Archive Drive does not keep the previous history. */
    suspend fun clear() {
        dao.clear()
    }
}

private fun BackupRunSummary.toEntity(): BackupRunEntity {
    return BackupRunEntity(
        runFile = runFile,
        startedAt = startedAt,
        endedAt = endedAt,
        fileCount = fileCount,
        bytes = bytes,
        checked = checked,
        elapsedMs = elapsedMs,
        mbPerSecond = mbPerSecond,
        interrupted = interrupted,
    )
}

private fun BackupRunEntity.toSummary(): BackupRunSummary {
    return BackupRunSummary(
        runFile = runFile,
        startedAt = startedAt,
        endedAt = endedAt,
        fileCount = fileCount,
        bytes = bytes,
        checked = checked,
        elapsedMs = elapsedMs,
        mbPerSecond = mbPerSecond,
        interrupted = interrupted,
    )
}
