package net.adamfoster.headoverwheels.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ElevationCalculatorTest {

    private val calc = ElevationCalculator()

    @Test
    fun `update returns zero for fewer than 5 readings`() {
        repeat(4) { n ->
            val delta = calc.update(100.0 + n)
            assertEquals(0.0, delta.gain, 0.001)
            assertEquals(0.0, delta.loss, 0.001)
        }
    }

    @Test
    fun `update returns zero on first complete batch - no previous average to compare`() {
        val calc2 = ElevationCalculator()
        repeat(4) { calc2.update(100.0) }
        val delta = calc2.update(100.0)
        assertEquals(0.0, delta.gain, 0.001)
        assertEquals(0.0, delta.loss, 0.001)
    }

    @Test
    fun `update returns gain when second batch average is higher`() {
        // Batch 1: avg 100m — sets baseline
        repeat(5) { calc.update(100.0) }
        // Batch 2: avg 110m — delta = +10m
        repeat(4) { calc.update(110.0) }
        val delta = calc.update(110.0)
        assertEquals(10.0, delta.gain, 0.001)
        assertEquals(0.0, delta.loss, 0.001)
    }

    @Test
    fun `update returns loss when second batch average is lower`() {
        // Batch 1: avg 100m
        repeat(5) { calc.update(100.0) }
        // Batch 2: avg 90m — delta = -10m
        repeat(4) { calc.update(90.0) }
        val delta = calc.update(90.0)
        assertEquals(0.0, delta.gain, 0.001)
        assertEquals(10.0, delta.loss, 0.001)
    }

    @Test
    fun `update averages readings within a batch correctly`() {
        // Batch 1: [98, 99, 100, 101, 102] → avg 100
        listOf(98.0, 99.0, 100.0, 101.0, 102.0).forEach { calc.update(it) }
        // Batch 2: [108, 109, 110, 111, 112] → avg 110 → gain = 10
        listOf(108.0, 109.0, 110.0, 111.0).forEach { calc.update(it) }
        val delta = calc.update(112.0)
        assertEquals(10.0, delta.gain, 0.001)
        assertEquals(0.0, delta.loss, 0.001)
    }

    @Test
    fun `update handles third batch correctly`() {
        // Batch 1: avg 100 → (0, 0)
        repeat(5) { calc.update(100.0) }
        // Batch 2: avg 110 → gain 10
        repeat(5) { calc.update(110.0) }
        // Batch 3: avg 105 → loss 5
        repeat(4) { calc.update(105.0) }
        val delta = calc.update(105.0)
        assertEquals(0.0, delta.gain, 0.001)
        assertEquals(5.0, delta.loss, 0.001)
    }

    @Test
    fun `reset discards partial buffer`() {
        // Add 3 readings (partial batch), then reset
        repeat(3) { calc.update(100.0) }
        calc.reset()
        // Now add a full batch — it should be treated as the first batch (no gain/loss)
        repeat(4) { calc.update(200.0) }
        val delta = calc.update(200.0)
        assertEquals(0.0, delta.gain, 0.001)
        assertEquals(0.0, delta.loss, 0.001)
    }

    @Test
    fun `reset discards lastBatchAverage`() {
        // Complete first batch (avg 100) — sets lastBatchAverage
        repeat(5) { calc.update(100.0) }
        calc.reset()
        // After reset, complete a new first batch (avg 200) — must produce no gain/loss
        repeat(4) { calc.update(200.0) }
        val firstPostReset = calc.update(200.0)
        assertEquals(0.0, firstPostReset.gain, 0.001)
        assertEquals(0.0, firstPostReset.loss, 0.001)
        // Second post-reset batch (avg 210) — gain = 10
        repeat(4) { calc.update(210.0) }
        val secondPostReset = calc.update(210.0)
        assertEquals(10.0, secondPostReset.gain, 0.001)
        assertEquals(0.0, secondPostReset.loss, 0.001)
    }
}
