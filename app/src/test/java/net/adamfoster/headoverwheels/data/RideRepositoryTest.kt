package net.adamfoster.headoverwheels.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RideRepositoryTest {

    @Before
    fun setup() {
        RideRepository.resetAll()
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

    @Test
    fun `resetRide zeroes all ride fields`() = runTest {
        RideRepository.updateLocationMetrics(10f, 100.0, 5.0)
        RideRepository.updateDistance(500.0)
        RideRepository.updateElevationGainLoss(50.0, 20.0)
        RideRepository.updateElapsedTime(60000L)
        RideRepository.updateRecordingStatus(true)
        RideRepository.updateHeartRate(140)

        RideRepository.resetRide()

        assertEquals(0f, RideRepository.speed.first(), 0.01f)
        assertEquals(0.0, RideRepository.altitude.first(), 0.01)
        assertEquals(0.0, RideRepository.incline.first(), 0.01)
        assertEquals(0.0, RideRepository.distance.first(), 0.01)
        assertEquals(0.0, RideRepository.elevationGain.first(), 0.01)
        assertEquals(0.0, RideRepository.elevationLoss.first(), 0.01)
        assertEquals(0L, RideRepository.elapsedTime.first())
        assertEquals(false, RideRepository.isRecording.first())
        assertEquals(0, RideRepository.heartRate.first())
    }

    @Test
    fun `resetRide preserves sensor connection status`() = runTest {
        RideRepository.updateHrSensorStatus("active")
        RideRepository.updateRadarSensorStatus("active", true)

        RideRepository.resetRide()

        // Sensor statuses must survive a ride reset — they are connection state, not ride state
        assertEquals("active", RideRepository.hrSensorStatus.first())
        assertEquals("active", RideRepository.radarSensorStatus.first())
        assertEquals(true, RideRepository.isRadarConnected.first())
    }

    @Test
    fun `setHasPendingRecovery true is observable`() = runTest {
        RideRepository.setHasPendingRecovery(true)
        assertEquals(true, RideRepository.hasPendingRecovery.first())
    }

    @Test
    fun `resetRide resets hasPendingRecovery`() = runTest {
        RideRepository.setHasPendingRecovery(true)
        RideRepository.resetRide()
        assertEquals(false, RideRepository.hasPendingRecovery.first())
    }
}
