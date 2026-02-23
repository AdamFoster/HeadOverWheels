# Process Death Recovery & Timer Efficiency — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Eliminate the unconditional 1-second timer wakeup when not recording (I2), and restore in-progress ride state after OS process death with a Resume/Discard dialog (I8), while also persisting sensor pairing addresses for auto-reconnect.

**Architecture:** A new `RideStateStore` class wraps a named `SharedPreferences` file and is the sole persistence layer. `LocationService` reads from it on startup (restoring ride state) and writes to it on every state change. `BleService` reads/writes sensor addresses. `RideRepository` gains a `hasPendingRecovery` signal that flows up through `MainViewModel` to a non-dismissible `AlertDialog` in `MainActivity`.

**Tech Stack:** Kotlin, kotlinx.coroutines (`flatMapLatest`, `emptyFlow`, `flow`), Android `SharedPreferences`, Jetpack Compose `AlertDialog`, JUnit 4, kotlinx.coroutines.test.

---

### Task 1: Create RideStateStore

**Files:**
- Create: `app/src/main/java/net/adamfoster/headoverwheels/service/RideStateStore.kt`

> **Note:** `SharedPreferences` is an Android framework API — it cannot be exercised in JVM unit tests without Robolectric. The design doc explicitly excludes `RideStateStore` unit tests (they require instrumented tests on device). Skip the TDD loop for this task.

**Step 1: Create the file**

```kotlin
package net.adamfoster.headoverwheels.service

import android.content.Context

class RideStateStore(context: Context) {

    private val prefs = context.getSharedPreferences("ride_state", Context.MODE_PRIVATE)

    data class PendingRide(
        val distance: Double,
        val gain: Double,
        val loss: Double,
        val elapsedMs: Long
    )

    fun savePendingRide(distance: Double, gain: Double, loss: Double, elapsedMs: Long) {
        prefs.edit()
            .putBoolean("ride_pending", true)
            .putFloat("ride_distance", distance.toFloat())
            .putFloat("ride_elevation_gain", gain.toFloat())
            .putFloat("ride_elevation_loss", loss.toFloat())
            .putLong("ride_elapsed_offset", elapsedMs)
            .apply()
    }

    fun loadPendingRide(): PendingRide? {
        if (!prefs.getBoolean("ride_pending", false)) return null
        return PendingRide(
            distance = prefs.getFloat("ride_distance", 0f).toDouble(),
            gain = prefs.getFloat("ride_elevation_gain", 0f).toDouble(),
            loss = prefs.getFloat("ride_elevation_loss", 0f).toDouble(),
            elapsedMs = prefs.getLong("ride_elapsed_offset", 0L)
        )
    }

    fun clearPendingRide() {
        prefs.edit()
            .putBoolean("ride_pending", false)
            .remove("ride_distance")
            .remove("ride_elevation_gain")
            .remove("ride_elevation_loss")
            .remove("ride_elapsed_offset")
            .apply()
    }

    fun saveHrAddress(address: String?) {
        val editor = prefs.edit()
        if (address != null) editor.putString("sensor_hr_address", address)
        else editor.remove("sensor_hr_address")
        editor.apply()
    }

    fun loadHrAddress(): String? = prefs.getString("sensor_hr_address", null)

    fun saveRadarAddress(address: String?) {
        val editor = prefs.edit()
        if (address != null) editor.putString("sensor_radar_address", address)
        else editor.remove("sensor_radar_address")
        editor.apply()
    }

    fun loadRadarAddress(): String? = prefs.getString("sensor_radar_address", null)
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/net/adamfoster/headoverwheels/service/RideStateStore.kt
git commit -m "feat: add RideStateStore persistence wrapper for ride state and sensor addresses"
```

---

### Task 2: RideRepository — add hasPendingRecovery field (TDD)

**Files:**
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/data/RideRepository.kt`
- Test: `app/src/test/java/net/adamfoster/headoverwheels/data/RideRepositoryTest.kt`

**Step 1: Write the failing tests**

Add these two tests at the end of `RideRepositoryTest`, before the closing `}`:

```kotlin
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
```

**Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "net.adamfoster.headoverwheels.data.RideRepositoryTest"
```

Expected: FAIL with `Unresolved reference: hasPendingRecovery` and `Unresolved reference: setHasPendingRecovery`.

**Step 3: Implement in RideRepository**

Add the field and method after the `ThemeMode` block (after line 74 `val themeMode: StateFlow<ThemeMode>`):

```kotlin
private val _hasPendingRecovery = MutableStateFlow(false)
val hasPendingRecovery: StateFlow<Boolean> = _hasPendingRecovery.asStateFlow()
fun setHasPendingRecovery(value: Boolean) { _hasPendingRecovery.value = value }
```

In `resetRide()`, add `_hasPendingRecovery.value = false` before the `// Don't reset sensor connection statuses` comment:

```kotlin
fun resetRide() {
    _speed.value = 0f
    _altitude.value = 0.0
    _incline.value = 0.0
    _distance.value = 0.0
    _elevationGain.value = 0.0
    _elevationLoss.value = 0.0
    _elapsedTime.value = 0L
    _heartRate.value = 0
    _radarDistance.value = -1
    _isRecording.value = false
    _hasPendingRecovery.value = false
    // Don't reset sensor connection statuses
}
```

In `resetAll()`, add `_hasPendingRecovery.value = false` after `resetRide()`:

```kotlin
fun resetAll() {
    resetRide()
    _hasPendingRecovery.value = false
    _gpsStatus.value = "Acquiring..."
    ...
```

Wait — since `resetRide()` already sets `_hasPendingRecovery.value = false`, the explicit line in `resetAll()` is redundant but harmless. Omit the duplicate from `resetAll()` — `resetRide()` handles it.

**Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "net.adamfoster.headoverwheels.data.RideRepositoryTest"
```

Expected: all tests PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/net/adamfoster/headoverwheels/data/RideRepository.kt
git add app/src/test/java/net/adamfoster/headoverwheels/data/RideRepositoryTest.kt
git commit -m "feat: add hasPendingRecovery StateFlow to RideRepository; reset in resetRide()"
```

---

### Task 3: RideUiState + MainViewModel — wire hasPendingRecovery (TDD)

**Files:**
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/ui/RideUiState.kt`
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/ui/MainViewModel.kt`
- Test: `app/src/test/java/net/adamfoster/headoverwheels/ui/MainViewModelTest.kt`

**Step 1: Add the field to RideUiState**

Add `val hasPendingRecovery: Boolean = false,` after the `isRadarConnected` line:

```kotlin
val isRecording: Boolean = false,
val isRadarConnected: Boolean = false,
val hasPendingRecovery: Boolean = false,
```

**Step 2: Write the failing test**

Add this test at the end of `MainViewModelTest`, before the closing `}`:

```kotlin
@Test
fun `uiState hasPendingRecovery reflects repository state`() = runTest {
    val job = launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.uiState.collect {}
    }

    RideRepository.setHasPendingRecovery(true)
    advanceUntilIdle()

    assertEquals(true, viewModel.uiState.value.hasPendingRecovery)
    job.cancel()
}
```

**Step 3: Run test to verify it fails**

```bash
./gradlew test --tests "net.adamfoster.headoverwheels.ui.MainViewModelTest"
```

Expected: FAIL — the test compiles but `hasPendingRecovery` is `false` because `MainViewModel` does not yet wire the field.

**Step 4: Implement in MainViewModel**

There are three changes to `MainViewModel.kt`:

**4a. Chain `hasPendingRecovery` onto `statusFlow`** — the existing 5-flow `combine` is already at the typed overload limit; add a second `.combine()` call. Replace the entire `statusFlow` block:

```kotlin
// Chunk 3: System Status
private val statusFlow = combine(
    repository.gpsStatus,
    repository.isRecording,
    repository.isRadarConnected,
    repository.elevationGain,
    repository.elevationLoss
) { gps, recording, radarConn, elevGain, elevLoss ->
    StatusChunk(gps, recording, radarConn, elevGain, elevLoss)
}.combine(repository.hasPendingRecovery) { chunk, pending ->
    chunk.copy(hasPendingRecovery = pending)
}
```

**4b. Add `hasPendingRecovery` to `StatusChunk`** — update the private data class at the bottom of `MainViewModel.kt`:

```kotlin
private data class StatusChunk(
    val gpsStatus: String,
    val isRecording: Boolean,
    val isRadarConnected: Boolean,
    val elevationGain: Double,
    val elevationLoss: Double,
    val hasPendingRecovery: Boolean = false
)
```

**4c. Map `hasPendingRecovery` into `RideUiState`** — inside the outer `combine` lambda, add one line to the `RideUiState(...)` constructor call:

```kotlin
isRadarConnected = status.isRadarConnected,
hasPendingRecovery = status.hasPendingRecovery,
speedData = chartData.speedData,
```

**4d. Add `dismissRecovery()` method** — add after the closing `}` of the `init` block and before `private fun formatElapsedTime`:

```kotlin
fun dismissRecovery() {
    repository.setHasPendingRecovery(false)
}
```

**Step 5: Run tests to verify they pass**

```bash
./gradlew test --tests "net.adamfoster.headoverwheels.ui.MainViewModelTest"
```

Expected: all tests PASS.

**Step 6: Run the full test suite**

```bash
./gradlew test
```

Expected: all tests PASS.

**Step 7: Commit**

```bash
git add app/src/main/java/net/adamfoster/headoverwheels/ui/RideUiState.kt
git add app/src/main/java/net/adamfoster/headoverwheels/ui/MainViewModel.kt
git add app/src/test/java/net/adamfoster/headoverwheels/ui/MainViewModelTest.kt
git commit -m "feat: wire hasPendingRecovery through StatusChunk, RideUiState, MainViewModel"
```

---

### Task 4: LocationService — timer fix + recovery restore + state saving

**Files:**
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/service/LocationService.kt`

> **Note:** `LocationService` is a `Service` requiring the Android runtime. No JVM unit tests are written for this task. Verify by building and testing on device.

**Step 1: Add flow imports**

The existing imports stop at `import kotlinx.coroutines.launch`. Add three lines after it:

```kotlin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
```

**Step 2: Add rideStateStore field**

Add after `private val repository = RideRepository`:

```kotlin
private lateinit var rideStateStore: RideStateStore
```

**Step 3: Replace the timer loop in `onCreate`**

The current timer loop (lines 120–126) is:

```kotlin
// Timer Loop
scope.launch(Dispatchers.Default) {
    while (true) {
        repository.updateElapsedTime(getElapsedTime())
        delay(1000)
    }
}
```

Replace it with:

```kotlin
// Timer Loop — suspends completely when not recording via flatMapLatest
scope.launch(Dispatchers.Default) {
    repository.isRecording
        .flatMapLatest { recording ->
            if (recording) flow { while (true) { emit(Unit); delay(1000) } }
            else emptyFlow()
        }
        .collect { repository.updateElapsedTime(getElapsedTime()) }
}
```

**Step 4: Initialize rideStateStore and restore pending ride in `onCreate`**

In `onCreate`, after `db = RideDatabase.getDatabase(this)` and before the `locationCallback = object : LocationCallback()` block, add:

```kotlin
rideStateStore = RideStateStore(this)

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

**Step 5: Save state in `startRide()`**

After `startTime = System.currentTimeMillis()`, add:

```kotlin
rideStateStore.savePendingRide(totalDistance, totalElevationGain, totalElevationLoss, elapsedTimeOffset)
```

The complete `startRide()` method becomes:

```kotlin
private fun startRide() {
    if (!isRecording) {
        isRecording = true
        startTime = System.currentTimeMillis()
        rideStateStore.savePendingRide(totalDistance, totalElevationGain, totalElevationLoss, elapsedTimeOffset)
        repository.updateRecordingStatus(true)
    }
}
```

**Step 6: Save state in `pauseRide()`**

After `elapsedTimeOffset += System.currentTimeMillis() - startTime`, add:

```kotlin
rideStateStore.savePendingRide(totalDistance, totalElevationGain, totalElevationLoss, elapsedTimeOffset)
```

The complete `pauseRide()` method becomes:

```kotlin
private fun pauseRide() {
    if (isRecording) {
        isRecording = false
        elapsedTimeOffset += System.currentTimeMillis() - startTime
        rideStateStore.savePendingRide(totalDistance, totalElevationGain, totalElevationLoss, elapsedTimeOffset)
        repository.updateRecordingStatus(false)
        elevationCalculator.reset()
    }
}
```

**Step 7: Save state on each GPS fix while recording**

Inside the `if (isRecording && lastLocation != null)` block in `onLocationResult`, add one line after `totalElevationLoss += elevDelta.loss`:

```kotlin
if (isRecording && lastLocation != null) {
    totalDistance += lastLocation!!.distanceTo(location)
    val elevDelta = elevationCalculator.update(location.altitude)
    totalElevationGain += elevDelta.gain
    totalElevationLoss += elevDelta.loss
    rideStateStore.savePendingRide(totalDistance, totalElevationGain, totalElevationLoss, elapsedTimeOffset)
}
```

**Step 8: Clear state in `resetRide()`**

Add `rideStateStore.clearPendingRide()` after the existing reset assignments:

```kotlin
private fun resetRide() {
    isRecording = false
    startTime = 0L
    elapsedTimeOffset = 0L
    totalDistance = 0.0
    totalElevationGain = 0.0
    totalElevationLoss = 0.0
    inclineCalculator.reset()
    elevationCalculator.reset()
    rideStateStore.clearPendingRide()
    repository.resetRide()
}
```

**Step 9: Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

**Step 10: Commit**

```bash
git add app/src/main/java/net/adamfoster/headoverwheels/service/LocationService.kt
git commit -m "feat: fix timer loop with flatMapLatest; add ride state persistence and recovery restore"
```

---

### Task 5: BleService — sensor pairing persistence

**Files:**
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/service/BleService.kt`

> **Note:** BleService is a Service. No JVM unit tests are written for this task.

**Step 1: Add rideStateStore field**

After the existing field declarations (`private var isScanning = false`), add:

```kotlin
private lateinit var rideStateStore: RideStateStore
```

**Step 2: Initialize rideStateStore and auto-reconnect in `onCreate`**

At the end of `onCreate`, after `startForeground(NOTIFICATION_ID, createNotification("Sensors Active"))`, add:

```kotlin
rideStateStore = RideStateStore(this)

val hrAddress = rideStateStore.loadHrAddress()
val radarAddress = rideStateStore.loadRadarAddress()
if (hrAddress != null) RideRepository.setTargetHrDevice(hrAddress)
if (radarAddress != null) RideRepository.setTargetRadarDevice(radarAddress)
if (hrAddress != null || radarAddress != null) startScanning()
```

**Step 3: Save addresses in `connectToDevice`**

In the `if (type == "HR")` branch, add `rideStateStore.saveHrAddress(address)` after `RideRepository.setTargetHrDevice(address)`:

```kotlin
if (type == "HR") {
    RideRepository.setTargetHrDevice(address)
    rideStateStore.saveHrAddress(address)
    hrManager.setGatt(device.connectGatt(this, false, hrGattCallback))
} else if (type == "RADAR") {
    RideRepository.setTargetRadarDevice(address)
    rideStateStore.saveRadarAddress(address)
    radarManager.setGatt(device.connectGatt(this, false, radarGattCallback))
}
```

**Step 4: Clear addresses in `disconnectDevice`**

Add the store clear calls alongside the repository null-address calls:

```kotlin
private fun disconnectDevice(type: String) {
    if (type == "HR") {
        RideRepository.setTargetHrDevice(null)
        rideStateStore.saveHrAddress(null)
        hrManager.getGatt()?.disconnect()
        // Cleanup happens in callback
    }
    if (type == "RADAR") {
        RideRepository.setTargetRadarDevice(null)
        rideStateStore.saveRadarAddress(null)
        radarManager.getGatt()?.disconnect()
        // Cleanup happens in callback
    }
}
```

**Step 5: Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

**Step 6: Commit**

```bash
git add app/src/main/java/net/adamfoster/headoverwheels/service/BleService.kt
git commit -m "feat: persist sensor addresses in RideStateStore; auto-reconnect on BleService start"
```

---

### Task 6: MainActivity — recovery AlertDialog

**Files:**
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/MainActivity.kt`

> **Note:** Compose UI dialogs require instrumented tests. No JVM unit tests are written for this task.

**Step 1: Add imports**

Add these imports after the existing `androidx.compose.runtime.getValue` import:

```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
```

**Step 2: Add the recovery dialog inside `setContent`**

The dialog must appear at the `HeadOverWheelsTheme { ... }` scope, before the `NavHost` block. The dialog is non-dismissible (`onDismissRequest = {}`).

Inside the `HeadOverWheelsTheme { ... }` block, add the dialog before the `NavHost(...)` call:

```kotlin
net.adamfoster.headoverwheels.ui.theme.HeadOverWheelsTheme(
    themeMode = mainUiState.themeMode
) {
    if (mainUiState.hasPendingRecovery) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Ride Recovery") },
            text = {
                Text("A ride was in progress (${mainUiState.distance}, ${mainUiState.elapsedTime}).")
            },
            confirmButton = {
                TextButton(onClick = { mainViewModel.dismissRecovery() }) {
                    Text("Resume")
                }
            },
            dismissButton = {
                TextButton(onClick = { resetRide() }) {
                    Text("Discard")
                }
            }
        )
    }

    NavHost(navController = navController, startDestination = "main") {
        ...
    }
}
```

**Step 3: Build to verify**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

**Step 4: Run the full test suite**

```bash
./gradlew test
```

Expected: all tests PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/net/adamfoster/headoverwheels/MainActivity.kt
git commit -m "feat: add non-dismissible recovery AlertDialog for in-progress ride after process death"
```

---

## Verification checklist (on device)

After all tasks are committed, test the following flows on a physical device:

1. **Normal ride cycle** — Start → Pause → Resume → Reset. No dialog appears, timer stops when paused, no spurious wakeups in logcat.
2. **Process death recovery** — Start a ride, force-kill the app from developer settings, relaunch. Confirm the dialog shows the correct distance and elapsed time. Tap Resume → ride continues from paused state.
3. **Discard after recovery** — Same setup, tap Discard → all metrics reset to zero, dialog disappears.
4. **Sensor pairing persistence** — Pair HR monitor and radar, force-kill the app, relaunch. Confirm sensors auto-reconnect without manual pairing.
5. **Sensor unpairing is not persisted** — Disconnect a sensor from the Settings screen, force-kill, relaunch. Confirm the sensor does NOT auto-reconnect.
