package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Room queries. Matches mandates (suspend write/delete, Flow-based read).
 */
@Dao
interface RouteDao {
    @Query("SELECT * FROM saved_routes ORDER BY timestamp DESC")
    fun getAllRoutes(): Flow<List<SavedRoute>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: SavedRoute)

    @Query("DELETE FROM saved_routes WHERE id = :id")
    suspend fun deleteRouteById(id: Int)
}
