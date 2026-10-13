package app.photoxpo.photos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.photoxpo.history.BackupRunDao
import app.photoxpo.history.BackupRunEntity

@Database(
    entities = [PhotoEntity::class, BackupRunEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class PhotosDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao

    abstract fun backupRunDao(): BackupRunDao

    companion object {
        @Volatile
        private var instance: PhotosDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `backup_runs` (
                        `runFile` TEXT NOT NULL,
                        `startedAt` TEXT NOT NULL,
                        `endedAt` TEXT,
                        `fileCount` INTEGER NOT NULL,
                        `bytes` INTEGER NOT NULL,
                        `checked` INTEGER NOT NULL,
                        `elapsedMs` INTEGER,
                        `mbPerSecond` REAL,
                        `interrupted` INTEGER NOT NULL,
                        PRIMARY KEY(`runFile`)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `backup_runs` ADD COLUMN `runEnd` TEXT")
            }
        }

        fun get(context: Context): PhotosDatabase {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PhotosDatabase::class.java,
                    "photos.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
