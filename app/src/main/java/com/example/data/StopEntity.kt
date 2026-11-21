package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "delivery_stops")
data class StopEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val address: String,
    val contactName: String,
    val hasTimeSlot: Boolean,
    val timeSlotStart: String, // e.g., "09:00"
    val timeSlotEnd: String,   // e.g., "11:00"
    val latitude: Float,
    val longitude: Float,
    val orderIndex: Int,       // sequence ordering
    val isCompleted: Boolean = false,
    val estimatedArrivalTime: String? = null,
    val notes: String = "",
    val durationMinutes: Int = 20,
    val delayMinutes: Int = 0,  // delay in minutes due to traffic
    val trafficStatus: String = "Fluid", // "Fluid", "Dense", "Saturé"
    val priority: String = "Normal" // "High", "Normal", "Low"
)
