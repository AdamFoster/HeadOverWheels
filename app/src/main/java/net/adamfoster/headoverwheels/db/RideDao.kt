package net.adamfoster.headoverwheels.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import net.adamfoster.headoverwheels.data.RideData
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Insert
    suspend fun insert(rideData: RideData)

    @Query("SELECT * FROM rides ORDER BY timestamp DESC")
    fun getAllRides(): Flow<List<RideData>>
}
