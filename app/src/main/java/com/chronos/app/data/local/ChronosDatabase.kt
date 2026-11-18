package com.chronos.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CustomTimerEntity::class,
        PerformanceTimerEntity::class,
        DailyNeuralStatEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ChronosDatabase : RoomDatabase() {
    abstract fun customTimerDao(): CustomTimerDao
    abstract fun performanceTimerDao(): PerformanceTimerDao
    abstract fun dailyNeuralStatDao(): DailyNeuralStatDao

    companion object {
        @Volatile
        private var INSTANCE: ChronosDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_neural_stats` (
                        `dateKey` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `focusSeconds` INTEGER NOT NULL,
                        `sessionsCount` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`dateKey`, `categoryName`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): ChronosDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChronosDatabase::class.java,
                    "chronos_system.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.let { seedPerformanceTimers(it.performanceTimerDao()) }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun seedPerformanceTimers(dao: PerformanceTimerDao) {
            if (dao.getAllSnapshot().isEmpty()) {
                val initial = PerformanceCategory.entries.map { cat ->
                    val secs = cat.defaultDurationMinutes * 60L
                    PerformanceTimerEntity(
                        categoryName = cat.name,
                        totalSeconds = secs,
                        remainingSeconds = secs,
                        isRunning = false
                    )
                }
                dao.insertAll(initial)
            }
        }
    }
}
