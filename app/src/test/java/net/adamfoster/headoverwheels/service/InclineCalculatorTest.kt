package net.adamfoster.headoverwheels.service

import android.location.Location
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class InclineCalculatorTest {

    private val calculator = InclineCalculator()

    @Test
    fun `calculateIncline returns 0 when insufficient data`() {
        val loc1 = mockLocation(altitude = 100.0)
        
        val incline = calculator.calculateIncline(loc1)
        
        assertEquals(0.0, incline, 0.01)
    }

    @Test
    fun `calculateIncline returns 0 when distance is too small`() {
        val loc1 = mockLocation(altitude = 100.0)
        val loc2 = mockLocation(altitude = 101.0)
        
        // Mock distanceTo to return small distance
        every { loc1.distanceTo(loc2) } returns 5.0f
        
        calculator.calculateIncline(loc1)
        val incline = calculator.calculateIncline(loc2)
        
        assertEquals(0.0, incline, 0.01)
    }

    @Test
    fun `calculateIncline calculates correct positive incline`() {
        // 10% incline: Rise 10m over Run 100m
        val loc1 = mockLocation(altitude = 100.0)
        val loc2 = mockLocation(altitude = 110.0)
        
        every { loc1.distanceTo(loc2) } returns 100.0f
        
        calculator.calculateIncline(loc1)
        val incline = calculator.calculateIncline(loc2)
        
        assertEquals(10.0, incline, 0.01)
    }

    @Test
    fun `calculateIncline calculates correct negative incline`() {
        // -5% incline: Drop 5m over Run 100m
        val loc1 = mockLocation(altitude = 100.0)
        val loc2 = mockLocation(altitude = 95.0)
        
        every { loc1.distanceTo(loc2) } returns 100.0f
        
        calculator.calculateIncline(loc1)
        val incline = calculator.calculateIncline(loc2)
        
        assertEquals(-5.0, incline, 0.01)
    }

    @Test
    fun `calculateIncline uses sliding window correctly`() {
        // Window size is 5.
        // We will add 6 locations.
        // Logic compares oldest (index 0 initially, then index 1) vs newest.
        
        val loc1 = mockLocation(altitude = 100.0)
        val loc2 = mockLocation(altitude = 100.0)
        val loc3 = mockLocation(altitude = 100.0)
        val loc4 = mockLocation(altitude = 100.0)
        val loc5 = mockLocation(altitude = 100.0)
        
        // 6th location is much higher. 
        // Oldest should now be loc2 (100.0). Newest loc6 (150.0).
        // If it was still using loc1, result would be same, so let's make loc1 different?
        // Let's make loc1 very high, so if it was included, incline would be negative/lower.
        // Actually, simplest check:
        // loc1: 100m
        // loc2: 100m
        // ...
        // loc6: 110m.
        
        // If window works, it compares loc2 (100m) to loc6 (110m).
        // Let's verify standard behavior first.
        
        // Setup distance mocks. Since logic takes oldest.distanceTo(newest),
        // we need to mock that specific interaction.
        // Because the calculator stores objects, we need to ensure the mocks persist/work.
        
        val loc6 = mockLocation(altitude = 110.0)
        
        // We need to mock distance from the current oldest in the queue to the newest.
        
        // Step 1: Add 5 locations. Queue: [l1, l2, l3, l4, l5]
        every { loc1.distanceTo(any()) } returns 10.0f // irrelevant
        
        calculator.calculateIncline(loc1)
        calculator.calculateIncline(loc2)
        calculator.calculateIncline(loc3)
        calculator.calculateIncline(loc4)
        calculator.calculateIncline(loc5)
        
        // Step 2: Add 6th location. Queue becomes: [l2, l3, l4, l5, l6]
        // Oldest is l2. Newest is l6.
        // We expect call l2.distanceTo(l6)
        
        every { loc2.distanceTo(loc6) } returns 100.0f
        
        val incline = calculator.calculateIncline(loc6)
        
        // Rise: 110 - 100 = 10. Run: 100. Incline: 10%
        assertEquals(10.0, incline, 0.01)
    }

    private fun mockLocation(altitude: Double): Location {
        val loc = mockk<Location>(relaxed = true)
        every { loc.altitude } returns altitude
        return loc
    }
}
