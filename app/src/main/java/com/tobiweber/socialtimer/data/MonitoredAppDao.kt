package com.tobiweber.socialtimer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {

    @Query("SELECT * FROM monitored_apps ORDER BY appName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MonitoredApp>>

    @Query("SELECT * FROM monitored_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): MonitoredApp?

    @Query("SELECT * FROM monitored_apps WHERE state != 'IDLE'")
    suspend fun getAllWithActiveCycle(): List<MonitoredApp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: MonitoredApp)

    @Update
    suspend fun update(app: MonitoredApp)

    @Delete
    suspend fun delete(app: MonitoredApp)
}
