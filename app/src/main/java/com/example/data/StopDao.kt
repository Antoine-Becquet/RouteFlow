package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StopDao {
    @Query("SELECT * FROM delivery_stops ORDER BY orderIndex ASC")
    fun getAllStopsFlow(): Flow<List<StopEntity>>

    @Query("SELECT * FROM delivery_stops ORDER BY orderIndex ASC")
    suspend fun getAllStops(): List<StopEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStop(stop: StopEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<StopEntity>)

    @Transaction
    suspend fun replaceStops(stops: List<StopEntity>) {
        clearAllStops()
        insertStops(stops)
    }

    @Update
    suspend fun updateStop(stop: StopEntity)

    @Update
    suspend fun updateStops(stops: List<StopEntity>)

    @Delete
    suspend fun deleteStop(stop: StopEntity)

    @Query("DELETE FROM delivery_stops WHERE id = :id")
    suspend fun deleteStopById(id: Int)

    @Query("DELETE FROM delivery_stops")
    suspend fun clearAllStops()
}
