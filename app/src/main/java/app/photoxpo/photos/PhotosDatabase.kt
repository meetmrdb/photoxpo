package app.photoxpo.photos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PhotoEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PhotosDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile
        private var instance: PhotosDatabase? = null

        fun get(context: Context): PhotosDatabase {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PhotosDatabase::class.java,
                    "photos.db",
                ).build().also { instance = it }
            }
        }
    }
}
