# Process Death Recovery & Timer Efficiency — Design Doc

**Date:** 2026-02-22
**Scope:** I2 (timer loop efficiency) + I8 (ride state lost on process death) + sensor pairing persistence

---

## Background

Two issues were deferred from the 2026-02-21 code review fix plan as architectural in scope:

- **I2:** `LocationService` timer loop fires every second unconditionally via `while(true) { delay(1000) }`. When not recording, it repeatedly writes the same elapsed time value. `StateFlow` deduplication prevents downstream re-renders but the coroutine still wakes pointlessly.
- **I8:** All ride state (`totalDistance`, `totalElevationGain`, `totalElevationLoss`, `startTime`, `elapsedTimeOffset`, `isRecording`, `lastLocation`) lives in `LocationService` local variables. If the OS kills the process mid-ride (e.g. low memory) and restarts it via `START_STICKY`, all in-progress ride data is silently lost.

Sensor pairing (target HR monitor and radar device addresses) is addressed alongside I8 since it uses the same persistence layer.

---

## Goals

1. **I2:** Timer only fires while the ride is actively recording.
2. **I8:** Ride state survives process death; user is prompted to resume or discard on restart.
3. **Sensor pairing:** Last paired HR monitor and radar addresses are persisted and auto-reconnected on service restart.

---

## Architecture

### Persistence layer — `RideStateStore`

A new `class RideStateStore(context: Context)` backed by a single named `SharedPreferences` file (`"ride_state"`). All persistence reads and writes go through this class — nothing else touches SharedPreferences.

**Persisted keys:**

| Key | Type | Default | Purpose |
|---|---|---|---|
| `ride_pending` | Boolean | false | True if a ride was in progress when the process died |
| `ride_distance` | Float | 0f | Metres accumulated |
| `ride_elevation_gain` | Float | 0f | Metres gained |
| `ride_elevation_loss` | Float | 0f | Metres lost |
| `ride_elapsed_offset` | Long | 0L | Milliseconds of accumulated elapsed time |
| `sensor_hr_address` | String? | null | Last paired HR monitor BLE address |
| `sensor_radar_address` | String? | null | Last paired radar BLE address |

**Public API:**

```kotlin
data class PendingRide(
    val distance: Double,
    val gain: Double,
    val loss: Double,
    val elapsedMs: Long
)

fun savePendingRide(distance: Double, gain: Double, loss: Double, elapsedMs: Long)
fun loadPendingRide(): PendingRide?   // null if ride_pending == false
fun clearPendingRide()

fun saveHrAddress(address: String?)
fun loadHrAddress(): String?
fun saveRadarAddress(address: String?)
fun loadRadarAddress(): String?
```

Both `LocationService` and `BleService` create their own `RideStateStore(this)` instance. They share the same underlying SharedPreferences file by name, so writes from either service are immediately visible to the other.

---

### I2 — Timer loop fix (`LocationService`)

Replace the unconditional `while(true) { delay(1000) }` with a `flatMapLatest`-based ticker that suspends completely when not recording:

```kotlin
scope.launch(Dispatchers.Default) {
    repository.isRecording
        .flatMapLatest { recording ->
            if (recording) flow { while (true) { emit(Unit); delay(1000) } }
            else emptyFlow()
        }
        .collect { repository.updateElapsedTime(getElapsedTime()) }
}
```

When `isRecording` transitions to `false`, `flatMapLatest` cancels the inner flow and the coroutine suspends. No 1-second wakeups, no `StateFlow` deduplication needed.

---

### I8 — Ride recovery (`LocationService`)

**State saving:** `savePendingRide(...)` is called:
- In `startRide()` — sets `ride_pending = true` and saves initial state
- In `pauseRide()` — updates persisted elapsed offset
- In the GPS location callback while recording — keeps distance and elevation current (overwrites on each fix)

**State clearing:** `clearPendingRide()` is called in `resetRide()`.

**Recovery restore on startup:** In `onCreate`, after `RideStateStore` is created:

```kotlin
val pending = rideStateStore.loadPendingRide()
if (pending != null) {
    totalDistance = pending.distance
    totalElevationGain = pending.gain
    totalElevationLoss = pending.loss
    elapsedTimeOffset = pending.elapsedMs
    // isRecording stays false — always restore to paused state regardless of recording state at death
    repository.updateDistance(totalDistance)
    repository.updateElevationGainLoss(totalElevationGain, totalElevationLoss)
    repository.updateElapsedTime(elapsedTimeOffset)
    repository.setHasPendingRecovery(true)
}
```

---

### Sensor pairing persistence (`BleService`)

**Saving addresses:** In `connectToDevice`, alongside the existing `RideRepository.setTargetHrDevice(address)` / `setTargetRadarDevice(address)` calls, add matching `rideStateStore.saveHrAddress(address)` / `saveRadarAddress(address)`.

**Clearing addresses:** In `disconnectDevice`, alongside the existing null-address calls, add `rideStateStore.saveHrAddress(null)` / `saveRadarAddress(null)`.

**Auto-reconnect on startup:** At the end of `BleService.onCreate`, after managers are initialised and `startForeground` is called:

```kotlin
val hrAddress = rideStateStore.loadHrAddress()
val radarAddress = rideStateStore.loadRadarAddress()
if (hrAddress != null) RideRepository.setTargetHrDevice(hrAddress)
if (radarAddress != null) RideRepository.setTargetRadarDevice(radarAddress)
if (hrAddress != null || radarAddress != null) startScanning()
```

Reuses the existing scan → found → `connectToDevice` flow with no new connection logic.

---

### Repository, ViewModel, and UI — recovery dialog

**`RideRepository`:** New field:
```kotlin
private val _hasPendingRecovery = MutableStateFlow(false)
val hasPendingRecovery: StateFlow<Boolean> = _hasPendingRecovery.asStateFlow()
fun setHasPendingRecovery(value: Boolean) { _hasPendingRecovery.value = value }
```
`resetAll()` also resets it to `false`.

**`RideUiState`:** Add `val hasPendingRecovery: Boolean = false`.

**`MainViewModel`:** Include `hasPendingRecovery` in the status chunk of the `combine` call. Dialog text ("2.3 km, 00:14:32") uses the already-formatted `distance` and `elapsedTime` strings from `RideUiState` — no extra formatting needed.

**`MainActivity` dialog:** When `uiState.hasPendingRecovery` is true, show a non-dismissible `AlertDialog`:

```
A ride was in progress (2.3 km, 00:14:32).
[Resume]  [Discard]
```

- **Resume** → `RideRepository.setHasPendingRecovery(false)`. State is already restored in the service; user returns to main screen in paused state and presses record to continue.
- **Discard** → send `ACTION_RESET_RIDE` to `LocationService`, which calls `resetRide()` → `clearPendingRide()` and resets all repository state.

The dialog uses `onDismissRequest = {}` (no-op) so back-press and outside-tap are ignored — the user must make an explicit choice.

---

## Files affected

| File | Change |
|---|---|
| `service/RideStateStore.kt` | **New** — persistence wrapper |
| `service/LocationService.kt` | Timer fix, recovery restore, state saving |
| `service/BleService.kt` | Sensor pairing save/load/auto-reconnect |
| `data/RideRepository.kt` | `hasPendingRecovery` field + `setHasPendingRecovery` + `resetAll` update |
| `ui/RideUiState.kt` | `hasPendingRecovery` field |
| `ui/MainViewModel.kt` | Project `hasPendingRecovery` into `RideUiState` |
| `MainActivity.kt` | Recovery `AlertDialog` |

---

## Not in scope

- Persisting `lastLocation` (incline calculator warm-up state) — acceptable to restart incline from cold on recovery
- Persisting chart data (speed/elevation history) — chart restarts from empty on recovery
- Room database migration or ride history changes
- `RideStateStore` unit tests — SharedPreferences requires instrumented tests; covered by integration testing on device
