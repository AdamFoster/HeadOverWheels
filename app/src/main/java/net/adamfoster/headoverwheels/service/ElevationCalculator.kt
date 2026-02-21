package net.adamfoster.headoverwheels.service

data class ElevationDelta(val gain: Double, val loss: Double)

class ElevationCalculator {

    companion object {
        const val BATCH_SIZE = 5
    }

    private val altitudeBuffer = mutableListOf<Double>()
    private var lastBatchAverage: Double? = null

    fun update(altitude: Double): ElevationDelta {
        altitudeBuffer.add(altitude)
        if (altitudeBuffer.size < BATCH_SIZE) {
            return ElevationDelta(0.0, 0.0)
        }

        val batchAvg = altitudeBuffer.average()
        altitudeBuffer.clear()

        val prev = lastBatchAverage
        lastBatchAverage = batchAvg

        if (prev == null) {
            return ElevationDelta(0.0, 0.0)
        }

        val delta = batchAvg - prev
        return if (delta > 0) {
            ElevationDelta(gain = delta, loss = 0.0)
        } else {
            ElevationDelta(gain = 0.0, loss = -delta)
        }
    }

    fun reset() {
        altitudeBuffer.clear()
        lastBatchAverage = null
    }
}
