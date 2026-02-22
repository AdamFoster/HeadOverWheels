package net.adamfoster.headoverwheels.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.adamfoster.headoverwheels.MainDispatcherRule
import net.adamfoster.headoverwheels.data.RideRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@kotlinx.coroutines.ExperimentalCoroutinesApi
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        RideRepository.resetAll()
        viewModel = MainViewModel()
    }

    @Test
    fun `uiState initially shows zero values`() = runTest {
        val state = viewModel.uiState.first()
        
        assertEquals("0 km/h", state.speed)
        assertEquals("0 m", state.altitude)
        assertEquals("0.0 km", state.distance)
        assertEquals("0.0 %", state.incline)
        assertEquals("00:00:00", state.elapsedTime)
        assertEquals("---", state.heartRate)
        assertEquals("---", state.radarDistance)
    }

    @Test
    fun `uiState updates when repository updates`() = runTest {
        // Collect in background to keep the WhileSubscribed active
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        RideRepository.updateLocationMetrics(10f, 150.0, 2.5) // 10 m/s = 36 km/h
        RideRepository.updateDistance(1500.0)
        RideRepository.updateHeartRate(140)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        
        // Note: 10 m/s * 3.6 = 36 km/h
        assertEquals("36 km/h", state.speed)
        assertEquals("150 m", state.altitude)
        assertEquals("1.5 km", state.distance)
        assertEquals("2.5 %", state.incline)
        assertEquals("140 bpm", state.heartRate)
        
        job.cancel()
    }
}
