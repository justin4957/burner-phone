# BurnerPhone Detector

An Android application for detecting potential surveillance through statistical analysis of WiFi, Bluetooth, and network device patterns.

## Overview

BurnerPhone runs in the background, continuously monitoring and recording:
- WiFi network MAC addresses and SSIDs
- Bluetooth device MAC addresses and names
- Network device information

The app analyzes this data for statistically anomalous temporal and geographic patterns that may indicate surveillance or tracking.

## Features

### Device Monitoring
- **Background Service**: Runs as a foreground service with minimal battery impact
- **WiFi Scanning**: Detects nearby WiFi networks with BSSID, SSID, signal strength, and frequency
- **Bluetooth Scanning**: Discovers Bluetooth devices with MAC address, name, and RSSI
- **Location Tracking**: Records GPS coordinates for geographic pattern analysis

### Anomaly Detection
The app uses statistical analysis to detect several types of anomalies:

1. **Temporal Clustering**: Same device appearing at unusual times or in rapid succession
2. **Geographic Tracking**: Devices following the user across different locations
3. **Frequency Anomaly**: Unusual appearance frequency of specific devices
4. **Correlation Pattern**: Multiple devices appearing together suspiciously
5. **Signal Strength Anomaly**: Unusual signal strength patterns
6. **New Device Cluster**: Sudden appearance of multiple new devices

### Severity Levels
Anomalies are classified by severity:
- **Low**: Minor statistical deviation
- **Medium**: Moderate concern
- **High**: Significant anomaly requiring attention
- **Critical**: Strong indication of potential surveillance

## Technical Architecture

### Technologies
- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Database**: Room (SQLite)
- **Background Processing**: Foreground Service + Coroutines
- **Location**: Google Play Services Location API
- **Statistical Analysis**: Apache Commons Math3

### Project Structure
```
app/src/main/kotlin/com/burnerphone/detector/
├── BurnerPhoneApplication.kt          # Application class
├── data/
│   ├── models/                        # Data models (DeviceDetection, AnomalyDetection)
│   ├── dao/                           # Room DAOs for database access
│   ├── converters/                    # Type converters for Room
│   └── AppDatabase.kt                 # Room database configuration
├── service/
│   └── DeviceMonitoringService.kt     # Background monitoring service
├── scanning/
│   ├── WiFiScanner.kt                 # WiFi network scanning
│   ├── BluetoothScanner.kt            # Bluetooth device scanning
│   └── LocationProvider.kt            # GPS location provider
├── analysis/
│   └── AnomalyAnalyzer.kt             # Statistical anomaly detection
└── ui/
    ├── MainActivity.kt                # Main UI with Compose
    └── theme/                         # Material 3 theme configuration
```

## Permissions Required

The app requires the following permissions:
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`: For WiFi/Bluetooth scanning (Android requirement)
- `ACCESS_BACKGROUND_LOCATION`: For continuous monitoring while app is in background
- `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`: For Bluetooth device discovery (Android 12+)
- `FOREGROUND_SERVICE`: For running background monitoring service
- `POST_NOTIFICATIONS`: For anomaly alerts (Android 13+)

## Building the App

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17
- Android SDK with API 34
- Gradle 8.2+

### Build Steps
```bash
# Clone the repository
git clone https://github.com/justin4957/burner-phone.git
cd burner-phone

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

## Usage

1. **Grant Permissions**: On first launch, grant all requested permissions
2. **Start Monitoring**: Tap "Start" to begin background monitoring
3. **View Anomalies**: Anomalies appear in the main screen as they're detected
4. **Review Detections**: Examine anomaly details including:
   - Device addresses involved
   - Detection timestamps
   - Geographic locations
   - Anomaly score and confidence level

## Privacy & Security

- **Local Storage**: All data is stored locally on device in encrypted SQLite database
- **No Network Access**: App does not transmit any data over the network
- **User Control**: User has full control over monitoring and can stop at any time
- **Data Retention**: Old detections can be automatically purged

## Statistical Analysis

The anomaly detection system uses several statistical methods:

- **Standard Deviation Analysis**: Identifies clusters of detections that deviate from normal patterns
- **Haversine Distance**: Calculates geographic distances between detection points
- **Frequency Analysis**: Monitors appearance rates over time windows
- **Confidence Scoring**: Provides confidence levels (0.0-1.0) for each anomaly

### Detection Thresholds
- Minimum detections for analysis: 3
- Anomaly score threshold: 0.5
- Significant distance: 500 meters
- Analysis time window: 7 days

## Limitations

- **Android Scan Throttling**: Android 9+ limits WiFi scans to 4 per 2 minutes
- **Bluetooth Discovery Time**: Full Bluetooth scan takes ~12 seconds
- **Battery Impact**: Continuous scanning impacts battery life
- **Location Accuracy**: GPS accuracy varies based on environment
- **False Positives**: Legitimate devices may trigger anomalies

## Future Enhancements

See GitHub Issues for planned features:
- Export anomaly data to CSV/JSON
- Machine learning-based pattern recognition
- Network device fingerprinting
- Correlation analysis between multiple devices
- Historical trend visualization
- Custom anomaly thresholds

## License

MIT License - See LICENSE file for details

## Disclaimer

This app is designed for personal security awareness and authorized security research only. Users are responsible for ensuring their use complies with local laws regarding wireless device scanning and monitoring.

## Contributing

Contributions are welcome! Please see GitHub Issues for current development priorities.
