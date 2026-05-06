package com.transcribecare.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.transcribecare.app.data.dao.SessionDao
import com.transcribecare.app.data.entity.SegmentEntity
import com.transcribecare.app.data.entity.SessionEntity

/**
 * Room database singleton for the TranscribeCare app.
 * Contains sessions and their associated transcript segments.
 */
@Database(
    entities = [SessionEntity::class, SegmentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "transcribecare.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
