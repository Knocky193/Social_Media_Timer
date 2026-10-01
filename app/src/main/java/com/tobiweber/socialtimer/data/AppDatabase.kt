package com.tobiweber.socialtimer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MonitoredApp::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun monitoredAppDao(): MonitoredAppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2: Wall-Clock-Timer (timerEndAtMillis) wird durch aufsummierte Nutzungszeit ersetzt.
         * Ein gerade laufender Timer lässt sich nicht umrechnen und wird zurückgesetzt; ein laufender
         * Cooldown bleibt erhalten. Die eingestellten Apps bleiben erhalten.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE monitored_apps ADD COLUMN usedMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE monitored_apps ADD COLUMN usageStartedAtMillis INTEGER")
                db.execSQL("ALTER TABLE monitored_apps ADD COLUMN lastUsageEndedAtMillis INTEGER")
                db.execSQL("UPDATE monitored_apps SET state = 'IDLE' WHERE state = 'TIMER_RUNNING'")
                // DROP COLUMN benötigt SQLite 3.35+, ab Android 14 (minSdk) vorhanden.
                db.execSQL("ALTER TABLE monitored_apps DROP COLUMN timerEndAtMillis")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "social_timer.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
        }
    }
}
