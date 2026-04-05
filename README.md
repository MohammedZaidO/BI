# Classroom Silent Mode App

A Flutter mobile application that automatically puts your phone on silent mode when connected to an ESP32 classroom device via Bluetooth.

## Features

- 🔵 **Bluetooth Auto-Connection**: Automatically connects to ESP32 device named "Classroom1"
- 🔇 **Silent Mode Management**: Automatically enables/disables silent mode based on Bluetooth connection
- 📞 **Emergency Contacts**: Allows only emergency contacts to ring when in silent mode
- 🎨 **Modern UI/UX**: Beautiful and intuitive user interface
- 🔄 **Auto-Reconnect**: Automatically reconnects when device is in range

## Requirements

- Flutter SDK (3.0.0 or higher)
- Android Studio or VS Code with Flutter extensions
- Android device with Bluetooth support
- ESP32 device configured with name "Classroom1" and password "12345678"

## Setup Instructions

### 1. Install Flutter

If you haven't installed Flutter yet:
- Download Flutter from [flutter.dev](https://flutter.dev)
- Extract and add to PATH
- Run `flutter doctor` to verify installation

### 2. Get Dependencies

```bash
flutter pub get
```

### 3. Connect Your Android Device

- Enable Developer Options on your Android device
- Enable USB Debugging
- Connect device via USB or use wireless debugging
- Run `flutter devices` to verify connection

### 4. Build APK

#### Debug APK (for testing):
```bash
flutter build apk --debug
```

The APK will be located at:
`build/app/outputs/flutter-apk/app-debug.apk`

#### Release APK (for distribution):
```bash
flutter build apk --release
```

The APK will be located at:
`build/app/outputs/flutter-apk/app-release.apk`

### 5. Install APK

Transfer the APK file to your Android device and install it. You may need to enable "Install from Unknown Sources" in your device settings.

## ESP32 Configuration

Your ESP32 device should be configured as:
- **Device Name**: `Classroom1`
- **Password/PIN**: `12345678`
- **Bluetooth Mode**: Classic Bluetooth (not BLE)

## Usage

1. **First Time Setup**:
   - Open the app
   - Grant all required permissions (Bluetooth, Contacts, Phone, Notifications)
   - Add emergency contacts from the contacts screen

2. **Pairing with ESP32**:
   - Make sure ESP32 is powered on and in pairing mode
   - Open Bluetooth settings on your phone
   - Pair with "Classroom1" (password: 12345678)
   - Return to the app

3. **Automatic Operation**:
   - The app will automatically scan for and connect to "Classroom1"
   - When connected, phone automatically goes to silent mode
   - Only emergency contacts can call you
   - When disconnected, silent mode is automatically disabled

## Permissions Required

- **Bluetooth**: To connect to ESP32 device
- **Contacts**: To manage emergency contacts
- **Phone**: To check phone state
- **Notifications**: To manage silent mode
- **Location**: Required for Bluetooth scanning on Android 6.0+

## Troubleshooting

### App won't connect to ESP32
- Ensure ESP32 is powered on and in pairing mode
- Check that device name is exactly "Classroom1"
- Try unpairing and re-pairing from Bluetooth settings
- Restart the app

### Silent mode not working
- Check that notification permission is granted
- Try manually toggling silent mode in the app
- Restart your phone

### Emergency contacts not working
- Ensure contacts permission is granted
- Verify emergency contacts are added correctly
- Check phone number format matches

## Project Structure

```
lib/
├── main.dart                 # App entry point
├── screens/
│   ├── home_screen.dart     # Main screen with connection status
│   └── emergency_contacts_screen.dart  # Emergency contacts management
└── services/
    ├── bluetooth_service.dart    # Bluetooth connectivity
    ├── phone_service.dart        # Silent mode management
    └── contacts_service.dart     # Emergency contacts storage
```

## Building for Production

For a production-ready APK:

1. Update version in `pubspec.yaml`
2. Build release APK:
   ```bash
   flutter build apk --release
   ```
3. Sign the APK (optional but recommended)
4. Distribute the APK

## License

This project is created for educational purposes.

## Support

For issues or questions, please check:
- Flutter documentation: https://flutter.dev/docs
- Bluetooth permissions: Android 12+ requires new permissions
