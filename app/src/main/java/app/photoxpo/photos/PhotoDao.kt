package app.photoxpo.photos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query(
        """
        SELECT * FROM photos
        ORDER BY CASE WHEN chosenDate IS NULL OR chosenDate = '' THEN 1 ELSE 0 END,
                 chosenDate DESC,
                 hash ASC
        """,
    )
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE hash = :hash LIMIT 1")
    fun observeByHash(hash: String): Flow<PhotoEntity?>

    @Query("SELECT * FROM photos WHERE hash = :hash LIMIT 1")
    suspend fun get(hash: String): PhotoEntity?

    @Query("SELECT * FROM photos WHERE hash IN (:hashes)")
    suspend fun getByHashes(hashes: List<String>): List<PhotoEntity>

    @Query("UPDATE photos SET mediaId = :mediaId, previewFile = :previewFile WHERE hash = :hash")
    suspend fun linkPhone(hash: String, mediaId: Long?, previewFile: String?)

    @Upsert
    suspend fun upsert(photos: List<PhotoEntity>)
}
