package com.example.data

import kotlinx.coroutines.flow.Flow

class StopRepository(private val stopDao: StopDao) {
    val allStopsFlow: Flow<List<StopEntity>> = stopDao.getAllStopsFlow()

    suspend fun getAllStops(): List<StopEntity> {
        return stopDao.getAllStops()
    }

    suspend fun insertStop(stop: StopEntity): Long {
        return stopDao.insertStop(stop)
    }

    suspend fun insertStops(stops: List<StopEntity>) {
        stopDao.insertStops(stops)
    }

    suspend fun replaceStops(stops: List<StopEntity>) {
        stopDao.replaceStops(stops)
    }

    suspend fun updateStop(stop: StopEntity) {
        stopDao.updateStop(stop)
    }

    suspend fun updateStops(stops: List<StopEntity>) {
        stopDao.updateStops(stops)
    }

    suspend fun deleteStop(stop: StopEntity) {
        stopDao.deleteStop(stop)
    }

    suspend fun deleteStopById(id: Int) {
        stopDao.deleteStopById(id)
    }

    suspend fun clearAllStops() {
        stopDao.clearAllStops()
    }
}
