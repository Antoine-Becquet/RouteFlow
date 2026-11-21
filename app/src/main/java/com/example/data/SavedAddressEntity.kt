package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_addresses")
data class SavedAddressEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,      // e.g. "Dépôt Principal", "Bureau", "Chez Client Martin"
    val address: String,    // e.g. "12 Rue de Rivoli, Paris"
    val latitude: Float,
    val longitude: Float
)
