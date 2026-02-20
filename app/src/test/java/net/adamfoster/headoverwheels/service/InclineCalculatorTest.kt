package net.adamfoster.headoverwheels.service

import android.location.Location
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class InclineCalculatorTest {

    private fun makeCalculator(distance: Float = 100.0f) =
        InclineCalculator(distanceFn = { _, _ -> distance })

    private fun mockLocation(altitude: Double): Location {
        val loc = mockk<Location>(relaxed = true)
        every { loc.altitude } returns altitude
        every { loc.latitude } returns 0.0
        every { loc.longitude } returns 0.0
        return loc
    }

    @Test
    fun `calculateIncline returns 0 when fewer than 10 samples`() {
        val calculator = makeCalculator()
        repeat(9) { n ->
            val result = calculator.calculateIncline(mockLocation(100.0 + n))
            assertEquals(0.0, result, 0.01)
        }
    }

    @Test
    fun `calculateIncline returns 0 when distance is too small`() {
        val calculator = makeCalculator(distance = 5.0f)  // below 10m minimum
        repeat(5) { calculator.calculateIncline(mockLocation(100.0)) }
        repeat(4) { calculator.calculateIncline(mockLocation(110.0)) }
        val result = calculator.calculateIncline(mockLocation(110.0))
        assertEquals(0.0, result, 0.01)
    }

    @Test
    fun `calculateIncline calculates correct positive incline`() {
        // first-5 avg alt = 100m, last-5 avg alt = 110m, dist = 100m → 10%
        val calculator = makeCalculator(distance = 100.0f)
        repeat(5) { calculator.calculateIncline(mockLocation(100.0)) }
        repeat(4) { calculator.calculateIncline(mockLocation(110.0)) }
        val result = calculator.calculateIncline(mockLocation(110.0))
        assertEquals(10.0, result, 0.01)
    }

    @Test
    fun `calculateIncline calculates correct negative incline`() {
        // first-5 avg alt = 100m, last-5 avg alt = 95m, dist = 100m → -5%
        val calculator = makeCalculator(distance = 100.0f)
        repeat(5) { calculator.calculateIncline(mockLocation(100.0)) }
        repeat(4) { calculator.calculateIncline(mockLocation(95.0)) }
        val result = calculator.calculateIncline(mockLocation(95.0))
        assertEquals(-5.0, result, 0.01)
    }

    @Test
    fun `calculateIncline averages altitudes within each group`() {
        // first-5: [98, 99, 100, 101, 102] → avg 100
        // last-5:  [108, 109, 110, 111, 112] → avg 110
        // incline = (110 - 100) / 100 * 100 = 10%
        val calculator = makeCalculator(distance = 100.0f)
        listOf(98.0, 99.0, 100.0, 101.0, 102.0).forEach {
            calculator.calculateIncline(mockLocation(it))
        }
        listOf(108.0, 109.0, 110.0, 111.0).forEach {
            calculator.calculateIncline(mockLocation(it))
        }
        val result = calculator.calculateIncline(mockLocation(112.0))
        assertEquals(10.0, result, 0.01)
    }

    @Test
    fun `calculateIncline uses sliding window - evicts oldest sample`() {
        // loc1 (alt 200m) is added first and should be evicted after the 11th sample.
        // After eviction:
        //   first group (loc2-loc6): avg alt = 100m
        //   last  group (loc7-loc11): avg alt = 110m
        //   expected incline = 10%
        // If loc1 were still present:
        //   first group (loc1-loc5): avg alt = (200+100+100+100+100)/5 = 120m
        //   last  group (loc6-loc10): avg alt = (100+110+110+110+110)/5 = 108m
        //   incline would be -12%, not 10%
        val calculator = makeCalculator(distance = 100.0f)
        calculator.calculateIncline(mockLocation(200.0))   // loc1 — will be evicted
        repeat(5) { calculator.calculateIncline(mockLocation(100.0)) }  // loc2-6
        repeat(4) { calculator.calculateIncline(mockLocation(110.0)) }  // loc7-10
        val result = calculator.calculateIncline(mockLocation(110.0))   // loc11
        assertEquals(10.0, result, 0.01)
    }
}
