# Incline Smoothing Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the 5-sample point-to-point incline algorithm with a 10-sample group-average algorithm that computes incline between the mean altitude/position of the first-5 and last-5 samples.

**Architecture:** `InclineCalculator` keeps an `ArrayDeque<Location>` capped at 10 entries. When full, it splits the window into two groups of 5, averages their altitudes and lat/lng centroids, and calculates incline between those group averages. A `distanceFn` lambda is injected so unit tests can control the centroid distance without depending on the Android SDK stub.

**Tech Stack:** Kotlin, Android Location API, JUnit 4, MockK

---

### Task 1: Rewrite InclineCalculatorTest

**Files:**
- Modify: `app/src/test/java/net/adamfoster/headoverwheels/service/InclineCalculatorTest.kt`

**Context:** The current tests use 2-sample windows and mock `distanceTo` on specific Location objects. With the new algorithm, `distanceTo` is called on internally-created centroid `Location` objects which cannot be pre-mocked. The fix is to inject a `distanceFn` lambda into `InclineCalculator` (added in Task 2). Tests create the calculator with `distanceFn = { _, _ -> <value>f }` to control distance independently of the altitude-averaging logic.

**Step 1: Replace the entire test file with the following**

```kotlin
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
```

**Step 2: Run the tests to verify they fail**

```bash
./gradlew test --tests "net.adamfoster.headoverwheels.service.InclineCalculatorTest"
```

Expected: compilation error or test failures because `InclineCalculator` does not yet accept a `distanceFn` parameter and still has window size 5.

---

### Task 2: Implement the new InclineCalculator

**Files:**
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/service/InclineCalculator.kt`

**Step 1: Replace the entire implementation with the following**

```kotlin
package net.adamfoster.headoverwheels.service

import android.location.Location
import java.util.ArrayDeque

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

        val locations = recentLocations.toList()
        val first = locations.subList(0, 5)
        val last = locations.subList(5, 10)

        val avgAltFirst = first.map { it.altitude }.average()
        val avgAltLast = last.map { it.altitude }.average()

        val centroidFirst = Location("").apply {
            latitude = first.map { it.latitude }.average()
            longitude = first.map { it.longitude }.average()
        }
        val centroidLast = Location("").apply {
            latitude = last.map { it.latitude }.average()
            longitude = last.map { it.longitude }.average()
        }

        val dist = distanceFn(centroidFirst, centroidLast)

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
```

**Step 2: Run the tests and verify they all pass**

```bash
./gradlew test --tests "net.adamfoster.headoverwheels.service.InclineCalculatorTest"
```

Expected: `BUILD SUCCESSFUL`, all 6 tests pass.

**Step 3: Run the full unit test suite to check for regressions**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 3: Commit

```bash
git add app/src/main/java/net/adamfoster/headoverwheels/service/InclineCalculator.kt \
        app/src/test/java/net/adamfoster/headoverwheels/service/InclineCalculatorTest.kt
git commit -m "feat: smooth incline via 10-sample group-average algorithm

Replaces 5-sample oldest-to-newest comparison with a 10-sample
window split into first-5 and last-5 groups. Incline is calculated
between the average altitude and centroid position of each group,
reducing sensitivity to GPS altitude noise on individual readings.
Returns 0.0 until the window is full."
```
