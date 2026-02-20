# Incline Smoothing — Design

**Date:** 2026-02-20

## Problem

The current `InclineCalculator` uses a 5-sample sliding window and computes incline by comparing the oldest and newest `Location` objects directly. This is sensitive to GPS altitude noise on individual readings.

## Goal

Reduce noise by averaging elevation (and position) across two groups of 5 samples each, so incline reflects a smoother trend rather than two potentially noisy point readings.

## Algorithm

### Window

`INCLINE_SMOOTHING_WINDOW = 10` — the `ArrayDeque<Location>` grows to at most 10 entries, evicting the oldest when a new sample arrives.

### Warm-up

Return `0.0` until the deque is full (exactly 10 samples). No partial result is produced.

### Calculation (when deque == 10)

Split the deque into two groups:

- `first` = `locations[0..4]` (5 oldest)
- `last` = `locations[5..9]` (5 newest)

For each group compute:

- `avgAlt` = mean of `.altitude` across the 5 locations
- `centroid` = synthetic `Location` with mean `.latitude` and mean `.longitude`

Then:

```
dist = centroidFirst.distanceTo(centroidLast)

if dist > MIN_DISTANCE_FOR_INCLINE (10.0 m):
    incline = (avgAltLast - avgAltFirst) / dist * 100.0
else:
    incline = 0.0
```

`MIN_DISTANCE_FOR_INCLINE` remains `10.0` metres.

## Why Option C (centroid distance)?

Using the distance between the average position of each group is consistent with using the average altitude of each group — both measurements represent the "centre of mass" of their respective 5-sample windows, not a single noisy GPS fix.

## Changes Required

| File | Change |
|---|---|
| `InclineCalculator.kt` | Update constant to 10; replace calculation block with group-average logic |
| `InclineCalculatorTest.kt` | Update existing tests for new window size; add tests for group-average and centroid distance behaviour |

`LocationService.kt` and all other files are unchanged — the public interface (`calculateIncline(location)`, `reset()`) is identical.
