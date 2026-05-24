package com.example.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository pattern implementation to abstract Route database access from ViewModel.
 */
class RouteRepository(private val routeDao: RouteDao) {
    val allRoutes: Flow<List<SavedRoute>> = routeDao.getAllRoutes()

    suspend fun insertRoute(route: SavedRoute) {
        routeDao.insertRoute(route)
    }

    suspend fun deleteRouteById(id: Int) {
        routeDao.deleteRouteById(id)
    }
}
