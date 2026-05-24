package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Android Room Entity for storing completed routes locally in SQLite.
 */
@Entity(tableName = "saved_routes")
data class SavedRoute(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val pointsCsv: String, // RoutePoint.serializeList is stored here
    val distanceKm: Double,
    val durationSec: Long,
    val averageSpeedKmh: Double
) {
    // Helper to easily get the deserialized points list
    fun getPoints(): List<RoutePoint> {
        return RoutePoint.deserializeList(pointsCsv)
    }
}
