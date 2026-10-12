package app.photoxpo.history

import app.photoxpo.backup.LastArchiveDriveConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupHistoryRepositoryTest {
    @Test
    fun clear_dropsThePhoneBackupHistoryCache() = runBlocking {
        val dao = FakeBackupRunDao()
        dao.upsert(
            listOf(
                run("20261001T000000000Z.jsonl"),
                run("20261011T183000000Z.jsonl"),
            ),
        )
        val repository = BackupHistoryRepository(dao, Memory())

        repository.clear()

        assertTrue(repository.cached().isEmpty())
        assertEquals(0, dao.all().size)
    }

    private fun run(name: String): BackupRunEntity {
        return BackupRunEntity(
            runFile = name,
            startedAt = "2026-10-11T18:30:00.000Z",
            endedAt = "2026-10-11T18:32:00.000Z",
            fileCount = 2,
            bytes = 10,
            checked = true,
            elapsedMs = 120000,
            mbPerSecond = 1.25,
            interrupted = false,
        )
    }
}

private class Memory : LastArchiveDriveConnection {
    private var connectedAt: Long? = null

    override fun lastConnectedAtEpochMillis(): Long? = connectedAt

    override fun setLastConnectedAtEpochMillis(epochMillis: Long) {
        connectedAt = epochMillis
    }
}

private class FakeBackupRunDao : BackupRunDao {
    private val rows = linkedMapOf<String, BackupRunEntity>()

    override suspend fun all(): List<BackupRunEntity> = rows.values.toList()

    override suspend fun completedRunFiles(): List<String> {
        return rows.values.filter { !it.interrupted }.map { it.runFile }
    }

    override suspend fun upsert(runs: List<BackupRunEntity>) {
        runs.forEach { rows[it.runFile] = it }
    }

    override suspend fun clear() {
        rows.clear()
    }
}
