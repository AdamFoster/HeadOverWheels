package net.adamfoster.headoverwheels.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RideRepositoryTest {

    @Before
    fun setup() {
        RideRepository.resetRide()
        RideRepository.updateGpsStatus("Acquiring...")
        RideRepository.updateHrSensorStatus("disconnected")
        RideRepository.updateRadarSensorStatus("disconnected", false)
    }

    @Test
    fun `updateLocationMetrics updates flows correctly`() = runTest {
        RideRepository.updateLocationMetrics(10f, 100.0, 5.0)

        assertEquals(10f, RideRepository.speed.first(), 0.01f)
        assertEquals(100.0, RideRepository.altitude.first(), 0.01)
        assertEquals(5.0, RideRepository.incline.first(), 0.01)
    }

    @Test
    fun `resetRide resets basic metrics`() = runTest {
        RideRepository.updateLocationMetrics(10f, 100.0, 5.0)
        RideRepository.updateDistance(500.0)
        
        RideRepository.resetRide()

        assertEquals(0f, RideRepository.speed.first(), 0.01f)
        assertEquals(0.0, RideRepository.altitude.first(), 0.01)
        assertEquals(0.0, RideRepository.distance.first(), 0.01)
    }
}
