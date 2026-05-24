package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SavedRoute::class], version = 1, exportSchema = false)
abstract class RouteDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao

    companion object {
        @Volatile
        private var INSTANCE: RouteDatabase? = null

        fun getDatabase(context: Context): RouteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RouteDatabase::class.java,
                    "route_tracker_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
