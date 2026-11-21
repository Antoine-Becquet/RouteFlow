package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedAddressDao {
    @Query("SELECT * FROM saved_addresses ORDER BY id DESC")
    fun getAllSavedAddressesFlow(): Flow<List<SavedAddressEntity>>

    @Query("SELECT * FROM saved_addresses ORDER BY id DESC")
    suspend fun getAllSavedAddresses(): List<SavedAddressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddress(address: SavedAddressEntity): Long

    @Delete
    suspend fun deleteAddress(address: SavedAddressEntity)

    @Query("DELETE FROM saved_addresses WHERE id = :id")
    suspend fun deleteAddressById(id: Int)

    @Query("DELETE FROM saved_addresses")
    suspend fun clearAll()
}
