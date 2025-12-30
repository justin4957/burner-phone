package com.burnerphone.detector.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.burnerphone.detector.data.dao.AnomalyDetectionDao
import com.burnerphone.detector.data.dao.DeviceDetectionDao
import com.burnerphone.detector.data.models.AnomalyDetection
import com.burnerphone.detector.data.models.DeviceDetection

@Database(
    entities = [
        DeviceDetection::class,
        AnomalyDetection::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun deviceDetectionDao(): DeviceDetectionDao
    abstract fun anomalyDetectionDao(): AnomalyDetectionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "burner_phone_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
