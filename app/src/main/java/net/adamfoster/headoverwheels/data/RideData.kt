package net.adamfoster.headoverwheels.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideData(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val speed: Float,
    val altitude: Double,
    val distance: Double,
    val heartRate: Int
)
