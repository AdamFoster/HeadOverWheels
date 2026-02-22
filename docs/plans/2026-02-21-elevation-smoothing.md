# Elevation Gain/Loss Smoothing Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the raw consecutive-reading elevation gain/loss calculation with a batched approach that averages every 5 altitude readings and computes gain/loss between consecutive batch averages.

**Architecture:** A new `ElevationCalculator` class owns a 5-sample altitude buffer and a `lastBatchAverage`. When the buffer fills, it computes a batch average, compares it to the previous batch average to produce an `ElevationDelta`, then clears the buffer. `LocationService` replaces its inline elevation block with a single `elevationCalculator.update()` call, and calls `reset()` on both pause and ride reset.

**Tech Stack:** Kotlin, JUnit 4, no mocking needed (pure arithmetic)

---

### Task 1: Create ElevationCalculator with tests (TDD)

**Files:**
- Create: `app/src/main/java/net/adamfoster/headoverwheels/service/ElevationCalculator.kt`
- Create: `app/src/test/java/net/adamfoster/headoverwheels/service/ElevationCalculatorTest.kt`

---

**Step 1: Create the test file**

```kotlin
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
        repeat(5) { calc.update(100.0) }
        // The 5th reading completes the first batch. lastBatchAverage is now set
        // but there was no prior average, so gain/loss must be zero.
        // We verify by checking the 5th call's return value.
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
```

**Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "net.adamfoster.headoverwheels.service.ElevationCalculatorTest"
```

Expected: compilation error — `ElevationCalculator` does not exist yet.

---

**Step 3: Create the implementation**

```kotlin
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
```

**Step 4: Run tests to verify all pass**

```bash
./gradlew test --tests "net.adamfoster.headoverwheels.service.ElevationCalculatorTest"
```

Expected: `BUILD SUCCESSFUL`, all 8 tests pass.

**Step 5: Run full suite for regressions**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

**Step 6: Commit**

```bash
git add \
  app/src/main/java/net/adamfoster/headoverwheels/service/ElevationCalculator.kt \
  app/src/test/java/net/adamfoster/headoverwheels/service/ElevationCalculatorTest.kt
git commit -m "feat: add ElevationCalculator with 5-sample batch averaging"
```

---

### Task 2: Wire ElevationCalculator into LocationService

**Files:**
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/service/LocationService.kt`

**Context:** `LocationService` is a foreground Android service — no unit tests exist for it and none are added here. Verify correctness by reading the diff carefully.

The current elevation block (lines 83–92) looks like this:

```kotlin
// Distance and Elevation Gain/Loss Calculation (only if recording)
if (isRecording && lastLocation != null) {
    totalDistance += lastLocation!!.distanceTo(location)
    val elevDelta = location.altitude - lastLocation!!.altitude
    if (elevDelta > 0) {
        totalElevationGain += elevDelta
    } else {
        totalElevationLoss -= elevDelta
    }
}
```

**Step 1: Add the `elevationCalculator` field**

After the existing `private val inclineCalculator = InclineCalculator()` line (line 38), add:

```kotlin
private val elevationCalculator = ElevationCalculator()
```

**Step 2: Replace the inline elevation block**

Replace the 4 elevation lines inside the recording block:

Old:
```kotlin
val elevDelta = location.altitude - lastLocation!!.altitude
if (elevDelta > 0) {
    totalElevationGain += elevDelta
} else {
    totalElevationLoss -= elevDelta
}
```

New:
```kotlin
val elevDelta = elevationCalculator.update(location.altitude)
totalElevationGain += elevDelta.gain
totalElevationLoss += elevDelta.loss
```

**Step 3: Reset the calculator on pause**

In `pauseRide()`, after `repository.updateRecordingStatus(false)`, add:

```kotlin
elevationCalculator.reset()
```

**Step 4: Reset the calculator on ride reset**

In `resetRide()`, after `inclineCalculator.reset()`, add:

```kotlin
elevationCalculator.reset()
```

**Step 5: Run the full test suite**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL` — no regressions.

**Step 6: Commit**

```bash
git add app/src/main/java/net/adamfoster/headoverwheels/service/LocationService.kt
git commit -m "feat: wire ElevationCalculator into LocationService

Replaces raw consecutive-reading elevation delta with 5-sample
batch averaging. Partial batches are discarded on pause and reset."
```
