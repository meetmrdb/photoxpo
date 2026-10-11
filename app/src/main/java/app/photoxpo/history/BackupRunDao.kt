package app.photoxpo.history

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface BackupRunDao {
    @Query("SELECT * FROM backup_runs")
    suspend fun all(): List<BackupRunEntity>

    /**
     * Run files that already have a footer. An interrupted run is left out so
     * the next refresh reads that file again.
     */
    @Query("SELECT runFile FROM backup_runs WHERE interrupted = 0")
    suspend fun completedRunFiles(): List<String>

    @Upsert
    suspend fun upsert(runs: List<BackupRunEntity>)
}
