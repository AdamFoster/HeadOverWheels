# Head Over Wheels 🚴‍♂️💨

Head Over Wheels is a modern Android bicycle head unit application built with Kotlin and Jetpack Compose. It turns your smartphone into a powerful cycling computer, capable of tracking GPS metrics and connecting to external Bluetooth Low Energy (BLE) sensors like Heart Rate monitors and Garmin Varia radar units.

## ✨ Features

*   **Real-time Dashboard**: View live metrics including Speed, Altitude, Incline (%), Elapsed Time, and Total Distance.
*   **Sensor Connectivity**:
    *   ❤️ **Heart Rate Monitors**: Supports standard BLE heart rate straps.
    *   ⚠️ **Radar Integration**: Connects to Garmin Varia radars to detect approaching vehicles. Visual indicators (Yellow/Red) and audio alerts warn of cars approaching within 80 meters.
*   **Ride Recording**:
    *   Tracks and saves ride data locally using a Room database.
    *   Background recording support via Foreground Services.
*   **Live Charts**: Real-time visualization of Speed and Elevation profiles.
*   **Smart Battery Usage**: Services only run in high-power foreground mode when a ride is actively recording.

## 🛠 Tech Stack

*   **Language**: Kotlin
*   **UI**: Jetpack Compose (Material 3)
*   **Architecture**: MVVM (Model-View-ViewModel) with Unidirectional Data Flow.
*   **Asynchrony**: Kotlin Coroutines & Flows.
*   **Persistence**: Room Database.
*   **Location**: Google Fused Location Provider.
*   **Connectivity**: Android BLE API.

## 📂 Project Structure

The project follows a clean separation of concerns:

```text
net.adamfoster.headoverwheels
├── data/           # Data layer
│   ├── RideRepository.kt   # Single Source of Truth for app state
│   └── RideData.kt         # Database entity
├── db/             # Room Database configuration
├── service/        # Background Services
│   ├── LocationService.kt  # GPS tracking and ride recording
│   ├── BleService.kt       # Bluetooth sensor management
│   └── ble/                # Sensor-specific logic (Radar/HR)
├── ui/             # Presentation layer
│   ├── MainViewModel.kt    # State holder
│   ├── RideUiState.kt      # UI State definition
│   └── composables/        # Jetpack Compose UI components
└── MainActivity.kt # Entry point and permission handling
```

## 🚀 Getting Started

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/YOUR_USERNAME/headoverwheels.git
    ```
2.  **Open in Android Studio**: Ensure you have the latest version.
3.  **Build and Run**: Connect your Android device.
    *   *Note*: The app requires a physical device for Bluetooth and GPS features; emulators have limited support.
4.  **Permissions**: Grant Location and Bluetooth permissions when prompted to enable full functionality.

## 📱 Permissions Explained

*   **Location (Fine/Coarse)**: Required for speed calculation and route tracking.
*   **Bluetooth (Scan/Connect)**: Required to find and talk to sensors.
*   **Notifications**: Required to keep the tracking services running in the background (Foreground Service).

## 🤝 Contributing

Contributions are welcome! Please open an issue or submit a pull request for any bugs or feature enhancements.
