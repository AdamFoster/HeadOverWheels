# Elevation Gain/Loss Smoothing — Design

**Date:** 2026-02-21

## Problem

The current elevation gain/loss calculation compares raw consecutive GPS altitude readings at 1 Hz. GPS altitude is noisy; small fluctuations accumulate into inflated totals over a ride.

## Goal

Reduce noise by batching every 5 altitude readings into an average, then computing gain/loss between consecutive batch averages.

## Algorithm

### Batching

Readings are collected into a buffer of exactly 5. When the buffer fills:

1. `batchAvg = buffer.average()`
2. If a previous batch average exists: `delta = batchAvg - lastBatchAvg`
   - `delta > 0` → `gain += delta`
   - `delta < 0` → `loss += abs(delta)`
3. Store `lastBatchAvg = batchAvg`
4. Clear the buffer

### Warm-up

The first complete batch sets `lastBatchAvg` but produces no gain/loss (no previous average to compare against). Gain/loss begins accumulating from the second complete batch onward.

### Partial batches on pause/reset

Partial batches (< 5 readings) are discarded — the buffer and `lastBatchAvg` are both cleared on `reset()`. On `pauseRide()`, the buffer is also discarded so post-resume readings start a fresh batch.

### Recording guard

`ElevationCalculator.update()` is only called while `isRecording` is true, so paused GPS readings do not silently fill the buffer.

## Data structure

```kotlin
data class ElevationDelta(val gain: Double, val loss: Double)

class ElevationCalculator {
    fun update(altitude: Double): ElevationDelta
    fun reset()
}
```

## Changes required

| File | Change |
|---|---|
| `ElevationCalculator.kt` | New file — calculator class + `ElevationDelta` |
| `ElevationCalculatorTest.kt` | New file — unit tests |
| `LocationService.kt` | Replace inline elevation block; add `elevationCalculator`; call `reset()` on pause and reset |

No other files need to change.
