# Code Review Fixes Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Address all Critical and Important findings from the full project code review, plus actionable Minor issues.

**Architecture:** Fixes are grouped by logical area — BLE safety, BLE service structure, service lifecycle, ViewModel concurrency, repository test isolation, UI correctness, and code hygiene. Tasks 1–2 are TDD (unit-testable). Tasks 3–5 are code-only (Android services/ViewModels, not unit-testable in isolation). Tasks 6–9 are mixed.

**Tech Stack:** Kotlin, Android Services, BLE GATT, Jetpack Compose, Kotlin Coroutines, Mutex, JUnit 4, MockK

**Deferred (architectural scope):**
- I2 — timer loop wakes every second even when not recording (low impact; StateFlow deduplication prevents downstream noise)
- I8 — all ride state lost on process death (requires SharedPreferences persistence layer, separate body of work)

---

### Task 1: HeartRateManager — add packet bounds checks (I3)

**Files:**
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/service/ble/HeartRateManager.kt:66-79`
- Modify: `app/src/test/java/net/adamfoster/headoverwheels/service/ble/HeartRateManagerTest.kt`

**Background:** The current `onCharacteristicChanged` only checks `value.isNotEmpty()` before accessing `value[1]` and `value[2]`. A malformed BLE packet (e.g. only a flag byte, or a UINT16 flag with only 2 bytes) throws `ArrayIndexOutOfBoundsException`, crashing the service.

**Step 1: Add failing tests**

Add these two tests to `HeartRateManagerTest.kt` (after the existing three tests):

```kotlin
@Test
fun `onCharacteristicChanged ignores truncated UINT8 packet`() {
    val uuid = HeartRateManager.HEART_RATE_MEASUREMENT_CHAR_UUID
    // Only the flag byte present — no HR byte
    val value = byteArrayOf(0x00)

    manager.onCharacteristicChanged(uuid, value)

    verify(exactly = 0) { repository.updateHeartRate(any()) }
}

@Test
fun `onCharacteristicChanged ignores truncated UINT16 packet`() {
    val uuid = HeartRateManager.HEART_RATE_MEASUREMENT_CHAR_UUID
    // Flag says UINT16 but only 2 bytes present — missing the MSB
    val value = byteArrayOf(0x01, 0x2C)

    manager.onCharacteristicChanged(uuid, value)

    verify(exactly = 0) { repository.updateHeartRate(any()) }
}
```

**Step 2: Run tests to confirm they fail**

```bash
./gradlew test --tests "net.adamfoster.headoverwheels.service.ble.HeartRateManagerTest"
```

Expected: 2 failures (`ArrayIndexOutOfBoundsException` on the truncated packets).

**Step 3: Fix `onCharacteristicChanged` in HeartRateManager.kt**

Replace lines 66–79:

Old:
```kotlin
override fun onCharacteristicChanged(uuid: UUID, value: ByteArray) {
    if (uuid == HEART_RATE_MEASUREMENT_CHAR_UUID && value.isNotEmpty()) {
        val flags = value[0].toInt()
        val isHeartRateInUInt16 = (flags and 1) != 0
        val heartRate = if (isHeartRateInUInt16) {
            // UInt16: 2nd and 3rd bytes (Little Endian)
            ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
        } else {
            // UInt8: 2nd byte
            value[1].toInt() and 0xFF
        }
        repository.updateHeartRate(heartRate)
    }
}
```

New:
```kotlin
override fun onCharacteristicChanged(uuid: UUID, value: ByteArray) {
    if (uuid != HEART_RATE_MEASUREMENT_CHAR_UUID || value.isEmpty()) return
    val flags = value[0].toInt()
    val isHeartRateInUInt16 = (flags and 1) != 0
    if (isHeartRateInUInt16 && value.size < 3) return
    if (!isHeartRateInUInt16 && value.size < 2) return
    val heartRate = if (isHeartRateInUInt16) {
        // UInt16: 2nd and 3rd bytes (Little Endian)
        ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
    } else {
        // UInt8: 2nd byte
        value[1].toInt() and 0xFF
    }
    repository.updateHeartRate(heartRate)
}
```

**Step 4: Run tests to confirm all 5 pass**

```bash
./gradlew test --tests "net.adamfoster.headoverwheels.service.ble.HeartRateManagerTest"
```

Expected: `BUILD SUCCESSFUL`, 5 tests pass.

**Step 5: Run full suite**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

**Step 6: Commit**

```bash
git add app/src/main/java/net/adamfoster/headoverwheels/service/ble/HeartRateManager.kt \
        app/src/test/java/net/adamfoster/headoverwheels/service/ble/HeartRateManagerTest.kt
git commit -m "fix: guard against truncated BLE heart rate packets"
```

---

### Task 2: RadarManager — fix incomplete triplet guard + add tests (I4)

**Files:**
- Create: `app/src/test/java/net/adamfoster/headoverwheels/service/ble/RadarManagerTest.kt`
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/service/ble/RadarManager.kt:77-86`

**Background:** The triplet loop checks `i + 1 < value.size` before reading `value[i + 1]`. A packet with a dangling 1- or 2-byte stub at the end passes this check with an ID byte misread as distance. The guard should be `i + 2 >= value.size` (full triplet present) to skip partial triplets.

**Step 1: Create the test file**

Create `app/src/test/java/net/adamfoster/headoverwheels/service/ble/RadarManagerTest.kt`:

```kotlin
package net.adamfoster.headoverwheels.service.ble

import io.mockk.mockk
import io.mockk.verify
import net.adamfoster.headoverwheels.data.RideRepository
import org.junit.Test

class RadarManagerTest {

    private val repository: RideRepository = mockk(relaxed = true)
    private val manager = RadarManager(repository)

    @Test
    fun `onCharacteristicChanged reports no threats for header-only packet`() {
        val uuid = RadarManager.RADAR_DATA_CHAR_UUID
        manager.onCharacteristicChanged(uuid, byteArrayOf(0x00))
        verify { repository.updateRadarDistance(-1) }
    }

    @Test
    fun `onCharacteristicChanged reports distance from a single complete triplet`() {
        val uuid = RadarManager.RADAR_DATA_CHAR_UUID
        // header=0x01, triplet=[ID=0x01, dist=50, speed=0x00]
        val value = byteArrayOf(0x01, 0x01, 50, 0x00)
        manager.onCharacteristicChanged(uuid, value)
        verify { repository.updateRadarDistance(50) }
    }

    @Test
    fun `onCharacteristicChanged ignores incomplete trailing triplet`() {
        val uuid = RadarManager.RADAR_DATA_CHAR_UUID
        // header + one complete triplet (dist=50) + 2-byte stub (incomplete)
        val value = byteArrayOf(0x01, 0x01, 50, 0x00, 0x02, 0x14)
        manager.onCharacteristicChanged(uuid, value)
        // Only the complete triplet is read — distance should be 50, not 0x02 (the stub's ID byte)
        verify { repository.updateRadarDistance(50) }
    }

    @Test
    fun `onCharacteristicChanged reports closest of multiple complete triplets`() {
        val uuid = RadarManager.RADAR_DATA_CHAR_UUID
        // header + triplet 1 (dist=100) + triplet 2 (dist=40)
        val value = byteArrayOf(0x01, 0x01, 100, 0x00, 0x02, 40, 0x00)
        manager.onCharacteristicChanged(uuid, value)
        verify { repository.updateRadarDistance(40) }
    }
}
```

**Step 2: Run tests to confirm they fail**

```bash
./gradlew test --tests "net.adamfoster.headoverwheels.service.ble.RadarManagerTest"
```

Expected: `ignores incomplete trailing triplet` fails — the current code reads the stub's ID byte as distance.

**Step 3: Fix the triplet loop in RadarManager.kt**

Replace lines 77–86 (the `for` loop body):

Old:
```kotlin
for (i in 1 until value.size step 3) {
    // Distance is likely the second byte of the triplet (index i+1)
    if (i + 1 < value.size) {
        val dist = (value[i + 1].toInt() and 0xFF)
        if (dist < closestDistance) {
            closestDistance = dist
        }
        hasThreats = true
    }
}
```

New:
```kotlin
for (i in 1 until value.size step 3) {
    if (i + 2 >= value.size) break  // skip incomplete triplet at end of packet
    val dist = (value[i + 1].toInt() and 0xFF)
    if (dist < closestDistance) {
        closestDistance = dist
    }
    hasThreats = true
}
```

**Step 4: Run tests to confirm all 4 pass**

```bash
./gradlew test --tests "net.adamfoster.headoverwheels.service.ble.RadarManagerTest"
```

Expected: `BUILD SUCCESSFUL`, 4 tests pass.

**Step 5: Run full suite**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

**Step 6: Commit**

```bash
git add app/src/main/java/net/adamfoster/headoverwheels/service/ble/RadarManager.kt \
        app/src/test/java/net/adamfoster/headoverwheels/service/ble/RadarManagerTest.kt
git commit -m "fix: skip incomplete radar triplets; add RadarManagerTest"
```

---

### Task 3: BleService — per-manager GATT callbacks (C2, C3, I6)

**Files:**
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/service/BleService.kt`

**Background (read carefully):**
- **C3:** A single `gattCallback` is shared for both HR and Radar GATT connections. `BluetoothGattCallback` methods fire on a Binder thread, so concurrent characteristic notifications from two connected devices are a data race on the manager fields.
- **C2:** The `onConnectionStateChange` check `hrManager.isConnected() && isGattForManager(gatt, hrManager)` is logically backwards in the disconnected path — `onDisconnected()` clears the gatt reference, so by the time the callback fires, `isConnected()` may already return false.
- **I6:** After any disconnect (including user-initiated via `disconnectDevice()`), the code unconditionally calls `startScanning()`, showing a confusing "Scanning..." notification even when the user deliberately disconnected.

**Fix:** Replace the one shared `gattCallback` with two dedicated callbacks — `hrGattCallback` and `radarGattCallback`. Each callback only calls its own manager, eliminating the data race, the inverted-check bug, and the need for `isGattForManager`. In each disconnected path, only restart scanning if the corresponding target address is still set (null means user disconnected).

**Step 1: Replace the entire `gattCallback` block (lines 217–284) with two dedicated callbacks**

Delete this entire block:
```kotlin
private val gattCallback = object : BluetoothGattCallback() {
    ...
    private fun isGattForManager(...): Boolean { ... }
    ...
}

// Quick fix helper comment block (lines 286-290)
```

Add in its place:

```kotlin
private val hrGattCallback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.i("BleService", "HR connected: ${gatt.device.address}")
            gatt.discoverServices()
            hrManager.onConnected(gatt)
            updateNotification("HR Connected")
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.i("BleService", "HR disconnected.")
            hrManager.onDisconnected()
            gatt.close()
            updateNotification("HR Disconnected")
            // Only auto-scan if this was not a user-initiated disconnect
            if (RideRepository.targetHrAddress.value != null) startScanning()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) hrManager.onServicesDiscovered(gatt)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        hrManager.onCharacteristicChanged(characteristic.uuid, value)
    }
}

private val radarGattCallback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.i("BleService", "Radar connected: ${gatt.device.address}")
            gatt.discoverServices()
            radarManager.onConnected(gatt)
            updateNotification("Radar Connected")
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.i("BleService", "Radar disconnected.")
            radarManager.onDisconnected()
            gatt.close()
            updateNotification("Radar Disconnected")
            if (RideRepository.targetRadarAddress.value != null) startScanning()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) radarManager.onServicesDiscovered(gatt)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        radarManager.onCharacteristicChanged(characteristic.uuid, value)
    }
}
```

**Step 2: Update `connectToDevice` to use the per-manager callbacks**

Replace the entire `connectToDevice` method (lines 142–160):

Old:
```kotlin
private fun connectToDevice(address: String, type: String) {
    val device = bluetoothAdapter?.getRemoteDevice(address)
    if (device != null) {
        Log.i("BleService", "Initiating connection to $type at $address")
        // Stop scanning to improve connection reliability
        if (isScanning) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
        }

        // Update target in repository so we remember it
        if (type == "HR") RideRepository.setTargetHrDevice(address)
        if (type == "RADAR") RideRepository.setTargetRadarDevice(address)

        val gatt = device.connectGatt(this, false, gattCallback)
        if (type == "HR") hrManager.setGatt(gatt)
        if (type == "RADAR") radarManager.setGatt(gatt)
    }
}
```

New:
```kotlin
private fun connectToDevice(address: String, type: String) {
    val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
    Log.i("BleService", "Initiating connection to $type at $address")
    if (isScanning) {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
    }
    if (type == "HR") {
        RideRepository.setTargetHrDevice(address)
        hrManager.setGatt(device.connectGatt(this, false, hrGattCallback))
    } else if (type == "RADAR") {
        RideRepository.setTargetRadarDevice(address)
        radarManager.setGatt(device.connectGatt(this, false, radarGattCallback))
    }
}
```

**Step 3: Run full test suite to check for regressions**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

**Step 4: Commit**

```bash
git add app/src/main/java/net/adamfoster/headoverwheels/service/BleService.kt
git commit -m "fix: give each BLE manager its own GattCallback

Eliminates shared-callback data race (C3), inverted connection-state
check (C2), and auto-scan after user-initiated disconnect (I6)."
```

---

### Task 4: Service lifecycle fixes (C1, C5)

**Files:**
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/service/LocationService.kt`
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/service/BleService.kt`

**Background:**
- **C1:** `LocationService.onStartCommand` unconditionally calls `startLocationUpdates()` after every intent, including PAUSE and RESET. A `START_STICKY` restart with a null intent falls through the `when` block and calls `startLocationUpdates()` with no ride state. GPS should be started once in `onCreate`.
- **C5:** `BleService` only calls `startForeground()` when `ACTION_START_RIDE` is received. On Android 8+, a service doing BLE scanning in the background can be killed within minutes without a foreground notification. It must call `startForeground()` as soon as it starts.

**Step 1: Fix LocationService — move `startLocationUpdates()` to `onCreate`**

In `LocationService.kt`:

Remove the last line `startLocationUpdates()` from `onStartCommand` (it appears after the `when` block, around line 173).

Add `startLocationUpdates()` at the end of `onCreate()`, after the timer loop launch (around line 128):

Old `onCreate` ending:
```kotlin
        // Timer Loop
        scope.launch(Dispatchers.Default) {
            while (true) {
                repository.updateElapsedTime(getElapsedTime())
                delay(1000)
            }
        }

    }
```

New:
```kotlin
        // Timer Loop
        scope.launch(Dispatchers.Default) {
            while (true) {
                repository.updateElapsedTime(getElapsedTime())
                delay(1000)
            }
        }

        startLocationUpdates()
    }
```

Old `onStartCommand` ending:
```kotlin
        startLocationUpdates()
        return START_STICKY
    }
```

New:
```kotlin
        return START_STICKY
    }
```

**Step 2: Fix BleService — call `startForeground` in `onCreate`**

In `BleService.kt`, at the end of `onCreate()`, after the managers are initialised (after line 68), add:

```kotlin
        startForeground(NOTIFICATION_ID, createNotification("Sensors Active"))
```

Then in `onStartCommand`, change the `ACTION_START_RIDE` case from:
```kotlin
ACTION_START_RIDE -> {
    startForeground(NOTIFICATION_ID, createNotification("Sensors Connected (Ride Active)"))
}
```
To:
```kotlin
ACTION_START_RIDE -> {
    updateNotification("Sensors Connected (Ride Active)")
}
```

And change the `ACTION_RESET_RIDE` case from:
```kotlin
ACTION_RESET_RIDE -> {
    stopForeground(STOP_FOREGROUND_REMOVE)
}
```
To:
```kotlin
ACTION_RESET_RIDE -> {
    updateNotification("Sensors Active")
}
```

**Step 3: Run full test suite**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

**Step 4: Commit**

```bash
git add app/src/main/java/net/adamfoster/headoverwheels/service/LocationService.kt \
        app/src/main/java/net/adamfoster/headoverwheels/service/BleService.kt
git commit -m "fix: start GPS/BLE in onCreate, not conditionally in onStartCommand

Prevents GPS starting on PAUSE/RESET intents (C1) and ensures
BleService always has a foreground notification on Android 8+ (C5)."
```

---

### Task 5: MainViewModel — fix chart data races (C4, I5)

**Files:**
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/ui/MainViewModel.kt`

**Background:**
- **I5:** The speed collector reads `repository.altitude.value` as a snapshot while in the speed flow. Since `updateLocationMetrics()` sets speed and altitude in two separate `MutableStateFlow` assignments, the snapshot can read the previous altitude value. Fix: use `combine(repository.speed, repository.altitude)` so both are emitted together.
- **C4:** Two `viewModelScope.launch` coroutines both mutate `_speedData`, `_elevationData`, `_startingElevation`, and `dataPointIndex`. If one is suspended mid-update and the other runs, state is partially corrupted. Fix: protect all chart mutations with a `Mutex`.

**Step 1: Add Mutex imports to MainViewModel.kt**

At the top of the file, alongside the existing imports, add:

```kotlin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
```

**Step 2: Add the `chartMutex` field**

After the existing `private var dataPointIndex = 0f` line, add:

```kotlin
private val chartMutex = Mutex()
```

**Step 3: Replace the entire `init` block**

Old:
```kotlin
init {
    viewModelScope.launch {
         repository.speed.collect { speed ->
             val altitude = repository.altitude.value
             val speedKmh = speed * 3.6f

             if (_startingElevation.value == null && altitude != 0.0) {
                 _startingElevation.value = altitude.toFloat()
             }

             val currentSpeedList = _speedData.value.toMutableList()
             val currentElevList = _elevationData.value.toMutableList()

             currentSpeedList.add(Entry(dataPointIndex, speedKmh))
             currentElevList.add(Entry(dataPointIndex, altitude.toFloat()))

             if (currentSpeedList.size > 300) {
                 currentSpeedList.removeAt(0)
                 currentElevList.removeAt(0)
             }

             _speedData.value = currentSpeedList
             _elevationData.value = currentElevList
             dataPointIndex += 1f
         }
    }

    viewModelScope.launch {
        repository.distance.collect { distance ->
             if (distance == 0.0) {
                 _speedData.value = emptyList()
                 _elevationData.value = emptyList()
                 _startingElevation.value = null
                 dataPointIndex = 0f
             }
        }
    }
}
```

New:
```kotlin
init {
    viewModelScope.launch {
        combine(repository.speed, repository.altitude) { speed, altitude ->
            Pair(speed, altitude)
        }.collect { (speed, altitude) ->
            chartMutex.withLock {
                val speedKmh = speed * 3.6f

                if (_startingElevation.value == null && altitude != 0.0) {
                    _startingElevation.value = altitude.toFloat()
                }

                val currentSpeedList = _speedData.value.toMutableList()
                val currentElevList = _elevationData.value.toMutableList()

                currentSpeedList.add(Entry(dataPointIndex, speedKmh))
                currentElevList.add(Entry(dataPointIndex, altitude.toFloat()))

                if (currentSpeedList.size > 300) {
                    currentSpeedList.removeAt(0)
                    currentElevList.removeAt(0)
                }

                _speedData.value = currentSpeedList
                _elevationData.value = currentElevList
                dataPointIndex += 1f
            }
        }
    }

    viewModelScope.launch {
        repository.distance.collect { distance ->
            if (distance == 0.0) {
                chartMutex.withLock {
                    _speedData.value = emptyList()
                    _elevationData.value = emptyList()
                    _startingElevation.value = null
                    dataPointIndex = 0f
                }
            }
        }
    }
}
```

**Step 4: Run full test suite**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

**Step 5: Commit**

```bash
git add app/src/main/java/net/adamfoster/headoverwheels/ui/MainViewModel.kt
git commit -m "fix: eliminate chart data races in MainViewModel

Use combine(speed, altitude) to get both values atomically (I5).
Protect chart mutations with a Mutex to prevent reset/update races (C4)."
```

---

### Task 6: RideRepository — add `resetAll()` and expand test coverage (I1, M9)

**Files:**
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/data/RideRepository.kt`
- Modify: `app/src/test/java/net/adamfoster/headoverwheels/data/RideRepositoryTest.kt`
- Modify: `app/src/test/java/net/adamfoster/headoverwheels/ui/MainViewModelTest.kt`

**Background (I1):** `RideRepository` is a Kotlin `object` (JVM singleton). Tests that set sensor status, theme mode, or target addresses bleed into subsequent tests. The `@Before` methods in both test classes manually reset some but not all fields. A `resetAll()` method on the repository — and using it in every `@Before` — prevents cross-test pollution.

**Background (M9):** `RideRepositoryTest.resetRide resets basic metrics` only asserts 3 of 8 fields that `resetRide()` clears, and does not verify that sensor status fields are preserved across a reset.

**Step 1: Add `resetAll()` to `RideRepository.kt`**

After the existing `resetRide()` function (line 151), add:

```kotlin
fun resetAll() {
    resetRide()
    _gpsStatus.value = "Acquiring..."
    _hrSensorStatus.value = "disconnected"
    _radarSensorStatus.value = "disconnected"
    _isRadarConnected.value = false
    _scannedDevices.value = emptyList()
    _targetHrAddress.value = null
    _targetRadarAddress.value = null
    _themeMode.value = ThemeMode.SYSTEM
}
```

**Step 2: Update `@Before` in `RideRepositoryTest.kt`**

Replace the `setup()` method:

Old:
```kotlin
@Before
fun setup() {
    RideRepository.resetRide()
    RideRepository.updateGpsStatus("Acquiring...")
    RideRepository.updateHrSensorStatus("disconnected")
    RideRepository.updateRadarSensorStatus("disconnected", false)
}
```

New:
```kotlin
@Before
fun setup() {
    RideRepository.resetAll()
}
```

**Step 3: Update `@Before` in `MainViewModelTest.kt`**

Replace the `setup()` method:

Old:
```kotlin
@Before
fun setup() {
    RideRepository.resetRide()
    RideRepository.updateGpsStatus("Acquiring...")
    RideRepository.updateHrSensorStatus("disconnected")
    RideRepository.updateRadarSensorStatus("disconnected", false)
    viewModel = MainViewModel()
}
```

New:
```kotlin
@Before
fun setup() {
    RideRepository.resetAll()
    viewModel = MainViewModel()
}
```

**Step 4: Expand `RideRepositoryTest.kt` with two new tests**

Add these after the existing two tests:

```kotlin
@Test
fun `resetRide zeroes all ride fields`() = runTest {
    RideRepository.updateLocationMetrics(10f, 100.0, 5.0)
    RideRepository.updateDistance(500.0)
    RideRepository.updateElevationGainLoss(50.0, 20.0)
    RideRepository.updateElapsedTime(60000L)
    RideRepository.updateRecordingStatus(true)

    RideRepository.resetRide()

    assertEquals(0f, RideRepository.speed.first(), 0.01f)
    assertEquals(0.0, RideRepository.altitude.first(), 0.01)
    assertEquals(0.0, RideRepository.incline.first(), 0.01)
    assertEquals(0.0, RideRepository.distance.first(), 0.01)
    assertEquals(0.0, RideRepository.elevationGain.first(), 0.01)
    assertEquals(0.0, RideRepository.elevationLoss.first(), 0.01)
    assertEquals(0L, RideRepository.elapsedTime.first())
    assertEquals(false, RideRepository.isRecording.first())
}

@Test
fun `resetRide preserves sensor connection status`() = runTest {
    RideRepository.updateHrSensorStatus("active")
    RideRepository.updateRadarSensorStatus("active", true)

    RideRepository.resetRide()

    // Sensor statuses must survive a ride reset — they are connection state, not ride state
    assertEquals("active", RideRepository.hrSensorStatus.first())
    assertEquals("active", RideRepository.radarSensorStatus.first())
    assertEquals(true, RideRepository.isRadarConnected.first())
}
```

**Step 5: Run tests**

```bash
./gradlew test --tests "net.adamfoster.headoverwheels.data.RideRepositoryTest"
```

Expected: `BUILD SUCCESSFUL`, 4 tests pass.

**Step 6: Run full suite**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

**Step 7: Commit**

```bash
git add app/src/main/java/net/adamfoster/headoverwheels/data/RideRepository.kt \
        app/src/test/java/net/adamfoster/headoverwheels/data/RideRepositoryTest.kt \
        app/src/test/java/net/adamfoster/headoverwheels/ui/MainViewModelTest.kt
git commit -m "fix: add resetAll() to prevent test pollution; expand resetRide tests"
```

---

### Task 7: MainViewModelTest — fix flow timing dependency (M10)

**Files:**
- Modify: `app/src/test/java/net/adamfoster/headoverwheels/ui/MainViewModelTest.kt`

**Background:** The test reads `viewModel.uiState.value` immediately after mutating the repository without calling `advanceUntilIdle()`. This only works because `UnconfinedTestDispatcher` happens to run coroutines synchronously. If the dispatcher changes, the test will intermittently read stale state. Adding `advanceUntilIdle()` makes the dependency explicit and robust.

**Step 1: Add `advanceUntilIdle()` before the state read**

In `MainViewModelTest.kt`, in the `uiState updates when repository updates` test, add `advanceUntilIdle()` after the repository mutations and before the `.value` read:

Old:
```kotlin
RideRepository.updateLocationMetrics(10f, 150.0, 2.5) // 10 m/s = 36 km/h
RideRepository.updateDistance(1500.0)
RideRepository.updateHeartRate(140)

// Yield to allow flow to process
// In UnconfinedTestDispatcher this usually happens immediately, but let's be safe

val state = viewModel.uiState.value
```

New:
```kotlin
RideRepository.updateLocationMetrics(10f, 150.0, 2.5) // 10 m/s = 36 km/h
RideRepository.updateDistance(1500.0)
RideRepository.updateHeartRate(140)

advanceUntilIdle()

val state = viewModel.uiState.value
```

Also remove the now-stale comment block between the mutations and the state read.

**Step 2: Run tests**

```bash
./gradlew test --tests "net.adamfoster.headoverwheels.ui.MainViewModelTest"
```

Expected: `BUILD SUCCESSFUL`, both tests pass.

**Step 3: Commit**

```bash
git add app/src/test/java/net/adamfoster/headoverwheels/ui/MainViewModelTest.kt
git commit -m "fix: use advanceUntilIdle() in MainViewModelTest for robust flow timing"
```

---

### Task 8: Minor UI fixes (M1, M2, M3, M4)

**Files:**
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/ui/RideUiState.kt`
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/ui/composables/MainScreen.kt`
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/ui/composables/RideChart.kt`
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/ui/composables/SettingsScreen.kt`

**Step 1: M1 — Fix speed default in `RideUiState.kt`**

The `stateIn` initial value uses `RideUiState()` whose default speed is `"0.0 km/h"`, but the combined flow always emits `"${speedKmh.roundToInt()} km/h"` (e.g. `"0 km/h"`). Align the default with the runtime format.

In `RideUiState.kt` line 8, change:
```kotlin
val speed: String = "0.0 km/h",
```
To:
```kotlin
val speed: String = "0 km/h",
```

Also update `MainViewModelTest` line 35 (it already expects `"0 km/h"` — confirm it is still correct after the change; no edit needed there).

**Step 2: M2 — Fix gain/loss colours in `MainScreen.kt`**

Elevation **gain** (climbing) should be green (positive). Elevation **loss** (descending) should be red (negative). The current code has them swapped.

In `MainScreen.kt` around lines 349 and 358:

Old:
```kotlin
withStyle(SpanStyle(color = Color.Red)) { append("+$elevationGain") }
...
withStyle(SpanStyle(color = Color.Green)) { append("-$elevationLoss") }
```

New:
```kotlin
withStyle(SpanStyle(color = Color.Green)) { append("+$elevationGain") }
...
withStyle(SpanStyle(color = Color.Red)) { append("-$elevationLoss") }
```

**Step 3: M3 — Fix chart text colours for dark mode in `RideChart.kt`**

Add an `isDarkTheme: Boolean = false` parameter and use theme-aware text colour in both the `factory` and `update` blocks.

Replace the entire `RideChart` function:

```kotlin
@Composable
fun RideChart(
    speedData: List<Entry>,
    elevationData: List<Entry>,
    startingElevation: Float? = null,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    val speedColor = AndroidColor.GREEN
    val elevationColor = if (isDarkTheme) AndroidColor.LTGRAY else AndroidColor.DKGRAY
    val textColor = if (isDarkTheme) AndroidColor.LTGRAY else AndroidColor.DKGRAY

    val decimalFormatter = object : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return String.format(Locale.getDefault(), "%.1f", value)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            LineChart(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                description.isEnabled = false
                setTouchEnabled(false)
                isDragEnabled = false
                setScaleEnabled(false)
                setPinchZoom(false)
                setDrawGridBackground(false)
                legend.isEnabled = true
                legend.textColor = textColor

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    this.textColor = textColor
                    setDrawLabels(false)
                }

                axisLeft.apply {
                    this.textColor = speedColor
                    setDrawGridLines(true)
                    valueFormatter = decimalFormatter
                    axisMinimum = 0f
                }

                axisRight.apply {
                    this.textColor = elevationColor
                    setDrawGridLines(false)
                    valueFormatter = decimalFormatter
                }
            }
        },
        update = { chart ->
            // Update colours on recomposition (e.g. theme switch)
            chart.legend.textColor = textColor
            chart.xAxis.textColor = textColor
            chart.axisRight.textColor = elevationColor

            if (speedData.isEmpty() && elevationData.isEmpty()) {
                chart.clear()
                return@AndroidView
            }

            val speedDataSet = LineDataSet(speedData, "Speed (km/h)").apply {
                axisDependency = YAxis.AxisDependency.LEFT
                color = speedColor
                setDrawCircles(false)
                lineWidth = 2f
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

            val elevationDataSet = LineDataSet(elevationData, "Elev (m)").apply {
                axisDependency = YAxis.AxisDependency.RIGHT
                color = elevationColor
                setDrawCircles(false)
                lineWidth = 2f
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

            val lineData = LineData(speedDataSet, elevationDataSet)
            chart.data = lineData

            chart.axisRight.removeAllLimitLines()
            if (startingElevation != null) {
                val limitLine = LimitLine(startingElevation, "Start").apply {
                    lineColor = elevationColor
                    lineWidth = 1f
                    enableDashedLine(10f, 10f, 0f)
                    this.textColor = elevationColor
                    textSize = 10f
                }
                chart.axisRight.addLimitLine(limitLine)
            }

            chart.setVisibleXRangeMaximum(100f)
            chart.moveViewToX(speedData.lastOrNull()?.x ?: 0f)

            chart.invalidate()
        }
    )
}
```

Then in `MainScreen.kt`, find the `RideChart(...)` call and add the `isDarkTheme` parameter. The theme is already available via `MaterialTheme` — add this import at the top of `MainScreen.kt` if not present:

```kotlin
import androidx.compose.foundation.isSystemInDarkTheme
```

And pass it to `RideChart`:
```kotlin
RideChart(
    speedData = speedData,
    elevationData = elevationData,
    startingElevation = startingElevation,
    isDarkTheme = isSystemInDarkTheme(),
    modifier = Modifier.fillMaxWidth().height(180.dp)
)
```

**Step 4: M4 — Fix settings spinner shown when not scanning**

In `SettingsScreen.kt` around lines 136–144, replace the always-on spinner with a static "no devices" message:

Old:
```kotlin
if (scannedDevices.isEmpty()) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(8.dp))
        Text("Scanning...")
    }
}
```

New:
```kotlin
if (scannedDevices.isEmpty()) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No devices found. Tap the scan button to search.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}
```

Add `import androidx.compose.ui.text.style.TextAlign` at the top of `SettingsScreen.kt` if not already present.

**Step 5: Run full test suite**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

**Step 6: Commit**

```bash
git add app/src/main/java/net/adamfoster/headoverwheels/ui/RideUiState.kt \
        app/src/main/java/net/adamfoster/headoverwheels/ui/composables/MainScreen.kt \
        app/src/main/java/net/adamfoster/headoverwheels/ui/composables/RideChart.kt \
        app/src/main/java/net/adamfoster/headoverwheels/ui/composables/SettingsScreen.kt
git commit -m "fix: UI corrections — speed default, gain/loss colours, chart dark mode, settings spinner"
```

---

### Task 9: Code hygiene (M5, M6, M7)

**Files:**
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/service/ble/BleSensorManager.kt`
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/service/ble/HeartRateManager.kt`
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/service/ble/RadarManager.kt`
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/MainActivity.kt`
- Modify: `app/src/main/java/net/adamfoster/headoverwheels/db/RideDatabase.kt`

**Step 1: M5 — Remove dead `onDeviceFound` from interface and implementations**

`BleSensorManager.onDeviceFound` is never called anywhere in the codebase. Its implementations are empty stubs.

In `BleSensorManager.kt`, remove line 10:
```kotlin
fun onDeviceFound(device: BluetoothDevice, context: Context, callback: (BluetoothGatt?) -> Unit)
```

After removal, also remove the unused imports `BluetoothDevice` and `Context` if no other method uses them. Check the remaining interface — only `BluetoothGatt` and `UUID` are used. Remove:
```kotlin
import android.bluetooth.BluetoothDevice
import android.content.Context
```

In `HeartRateManager.kt`, remove the `onDeviceFound` override (lines 25–34):
```kotlin
override fun onDeviceFound(device: BluetoothDevice, context: Context, callback: (BluetoothGatt?) -> Unit) {
    if (gatt == null) {
        Log.i("HeartRateManager", "Connecting to HR Sensor: ${device.address}")
        // Note: ...
    }
}
```

Also remove unused imports from `HeartRateManager.kt`:
```kotlin
import android.bluetooth.BluetoothDevice
import android.content.Context
```

In `RadarManager.kt`, remove the `onDeviceFound` override (lines 28–30):
```kotlin
override fun onDeviceFound(device: BluetoothDevice, context: Context, callback: (BluetoothGatt?) -> Unit) {
     // Logic handled by service to initiate connection
}
```

Also remove unused imports from `RadarManager.kt`:
```kotlin
import android.bluetooth.BluetoothDevice
import android.content.Context
```

**Step 2: M6 — Add explanatory comment for BLE permission defaults in `MainActivity.kt`**

In `MainActivity.kt` around lines 33–34, the `?: true` defaults silently grant BLE permissions on API < 31 (where those permissions don't exist and are absent from the request map). Add a clarifying comment:

Old:
```kotlin
val bluetoothScanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: true
val bluetoothConnectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: true
```

New:
```kotlin
// BLUETOOTH_SCAN and BLUETOOTH_CONNECT are only requested on API 31+.
// On older APIs they are absent from the result map, so ?: true correctly
// treats their absence as "granted" (the permissions did not exist pre-31).
val bluetoothScanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: true
val bluetoothConnectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: true
```

**Step 3: M7 — Add `fallbackToDestructiveMigration` to `RideDatabase.kt`**

Without a migration strategy, any future schema change will crash existing users with `IllegalStateException`. `fallbackToDestructiveMigration()` documents the current intent (wipe and rebuild) until proper migrations are written.

In `RideDatabase.kt`, update the builder (lines 20–24):

Old:
```kotlin
val instance = Room.databaseBuilder(
    context.applicationContext,
    RideDatabase::class.java,
    "ride_database"
).build()
```

New:
```kotlin
val instance = Room.databaseBuilder(
    context.applicationContext,
    RideDatabase::class.java,
    "ride_database"
).fallbackToDestructiveMigration()
    .build()
```

**Step 4: Run full test suite**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

**Step 5: Commit**

```bash
git add app/src/main/java/net/adamfoster/headoverwheels/service/ble/BleSensorManager.kt \
        app/src/main/java/net/adamfoster/headoverwheels/service/ble/HeartRateManager.kt \
        app/src/main/java/net/adamfoster/headoverwheels/service/ble/RadarManager.kt \
        app/src/main/java/net/adamfoster/headoverwheels/MainActivity.kt \
        app/src/main/java/net/adamfoster/headoverwheels/db/RideDatabase.kt
git commit -m "chore: remove dead onDeviceFound interface method, add DB migration strategy, clarify BLE permission defaults"
```
