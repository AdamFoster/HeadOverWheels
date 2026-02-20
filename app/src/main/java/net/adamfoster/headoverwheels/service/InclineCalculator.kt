package net.adamfoster.headoverwheels.service

import android.location.Location

class InclineCalculator(
    private val distanceFn: (Location, Location) -> Float = { a, b -> a.distanceTo(b) }
) {

    companion object {
        const val INCLINE_SMOOTHING_WINDOW = 10
        const val MIN_DISTANCE_FOR_INCLINE = 10.0 // metres
    }

    private val recentLocations = ArrayDeque<Location>(INCLINE_SMOOTHING_WINDOW)

    fun calculateIncline(location: Location): Double {
        recentLocations.addLast(location)
        if (recentLocations.size > INCLINE_SMOOTHING_WINDOW) {
            recentLocations.removeFirst()
        }

        if (recentLocations.size < INCLINE_SMOOTHING_WINDOW) {
            return 0.0
        }

        val half = INCLINE_SMOOTHING_WINDOW / 2
        var sumAltFirst = 0.0
        var sumAltLast = 0.0
        var sumLatFirst = 0.0
        var sumLatLast = 0.0
        var sumLonFirst = 0.0
        var sumLonLast = 0.0

        for (i in 0 until half) {
            sumAltFirst += recentLocations[i].altitude
            sumLatFirst += recentLocations[i].latitude
            sumLonFirst += recentLocations[i].longitude
        }
        for (i in half until INCLINE_SMOOTHING_WINDOW) {
            sumAltLast += recentLocations[i].altitude
            sumLatLast += recentLocations[i].latitude
            sumLonLast += recentLocations[i].longitude
        }

        val centroidFirst = Location("").apply {
            latitude = sumLatFirst / half
            longitude = sumLonFirst / half
        }
        val centroidLast = Location("").apply {
            latitude = sumLatLast / half
            longitude = sumLonLast / half
        }

        val dist = distanceFn(centroidFirst, centroidLast).toDouble()

        val avgAltFirst = sumAltFirst / half
        val avgAltLast = sumAltLast / half

        return if (dist > MIN_DISTANCE_FOR_INCLINE) {
            (avgAltLast - avgAltFirst) / dist * 100.0
        } else {
            0.0
        }
    }

    fun reset() {
        recentLocations.clear()
    }
}
