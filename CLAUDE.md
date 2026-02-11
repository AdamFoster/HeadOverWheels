# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run all unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "net.adamfoster.headoverwheels.ui.MainViewModelTest"

# Run instrumentation tests (requires physical device or emulator)
./gradlew connectedAndroidTest

# Clean
./gradlew clean
```

A physical Android device (API 24+) is needed for full testing — GPS, BLE sensors, and foreground services have limited emulator support.

## Architecture

**MVVM with Unidirectional Data Flow** — all state flows from `RideRepository` through ViewModels to Compose UI via `StateFlow`.

### Data flow

```
Services (Location/BLE) → RideRepository (StateFlow) → ViewModels (combine/transform) → Compose UI
```

- **`RideRepository`** is a singleton and the single source of truth for all app state (metrics, sensor data, connection status, settings). Services write to it; ViewModels read from it.
- **`MainViewModel`** combines repository flows into a single `RideUiState` using chunked `combine()` calls (MetricsChunk, SensorChunk, StatusChunk, ChartDataChunk). It converts raw values to display units (m/s→km/h, m→km, seconds→HH:MM:SS).
- **`SettingsViewModel`** exposes BLE scan results and sensor connection state, dispatches scan/connect/disconnect actions to `BleService`.

### Services (Foreground)

- **`LocationService`** — GPS tracking via Fused Location Provider (1-second intervals). Handles ride recording lifecycle (start/pause/reset), calculates speed/distance/elevation, persists data to Room.
- **`BleService`** — BLE scanning and GATT connections for heart rate monitors (standard UUID `180D`) and Garmin Varia radar (proprietary UUID `6A4E3200-667B-11E3-949A-0800200C9A66`). Plays audio alerts when radar detects vehicles <80m.

### BLE Sensor Managers

`BleSensorManager` is the interface. `HeartRateManager` and `RadarManager` each handle their respective GATT service/characteristic discovery, notification setup, and data parsing. The radar protocol uses header byte + threat triplets (3 bytes each, distance in second byte).

### Database

Room database (`RideDatabase`, singleton) with `RideDao` for insert (suspend) and `getAllRides()` (Flow). Entity is `RideData` with timestamp, speed, altitude, distance, heart rate.

### UI

Jetpack Compose with Material 3. Two navigation routes: `main` (MainScreen — metric grid + charts) and `settings` (SettingsScreen — sensor management + theme). Charts use MPAndroidChart with a 300-point rolling window. Theme supports SYSTEM/LIGHT/DARK modes.

### Incline Calculation

`InclineCalculator` uses a sliding window of 5 locations. Computes `(altitude_delta / distance) * 100` between oldest and newest points, requiring a minimum 10m distance.

## Key Technical Details

- **Namespace:** `net.adamfoster.headoverwheels`
- **SDK:** minSdk 24, targetSdk/compileSdk 36, Java 17
- **Kotlin 2.3.0**, Compose BOM 2026.01.00, Room 2.8.4
- **Permissions:** Fine Location, Bluetooth Scan/Connect (API 31+), Post Notifications (API 33+)
- **Test stack:** JUnit 4, Mockk 1.14.7, Coroutines Test — uses `MainDispatcherRule` for test dispatcher injection and `unitTests.isReturnDefaultValues = true`
