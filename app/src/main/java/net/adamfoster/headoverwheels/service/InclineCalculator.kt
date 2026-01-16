package net.adamfoster.headoverwheels.service

import android.location.Location
import java.util.ArrayDeque

class InclineCalculator {

    companion object {
        const val INCLINE_SMOOTHING_WINDOW = 5
        const val MIN_DISTANCE_FOR_INCLINE = 10.0 // meters
    }

    private val recentLocations = ArrayDeque<Location>(INCLINE_SMOOTHING_WINDOW)

    fun calculateIncline(location: Location): Double {
        recentLocations.addLast(location)
        if (recentLocations.size > INCLINE_SMOOTHING_WINDOW) {
            recentLocations.removeFirst()
        }

        if (recentLocations.size < 2) {
            return 0.0
        }

        val oldest = recentLocations.first
        val newest = recentLocations.last
        val dist = oldest.distanceTo(newest)
        val altDiff = newest.altitude - oldest.altitude

        if (dist > MIN_DISTANCE_FOR_INCLINE) {
            return (altDiff / dist) * 100.0
        }
        
        return 0.0
    }
    
    fun reset() {
        recentLocations.clear()
    }
}
