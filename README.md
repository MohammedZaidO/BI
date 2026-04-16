# Smart Silent Device (Dialer Architecture)

A high-authority Android application designed to selectively manage call alerts during specific contexts (Classroom Mode). This project utilizes the **Android Telecom Framework** by functioning as the **Default Phone App** to ensure reliable silence for non-priority callers while guaranteeing alerts for emergency contacts.

## 🚀 Key Features

- **Default Dialer Authority**: Built on `InCallService`, getting full control over the phone's alerting hardware (speaker and vibration motor).
- **Logical Classroom Mode**: A non-intrusive silence model that avoids using system-level DND, allowing the app to select per-call alerts without hardware conflicts.
- **High-Precision Emergency Matching**: 
  - Uses a **10-Digit Suffix Match** fallback to handle formatting variances (e.g., +1 vs 011).
  - Utilizes Android's native `PhoneNumberUtils` for exact matching.
- **ESP32 Connectivity**: Seamlessly toggles Classroom Mode based on Bluetooth LE connectivity status.

## 🛠️ Technical Architecture

### 1. The Dialer Role (`ROLE_DIALER`)
The app requests and maintains the Default Phone App role. This is the only officially supported Android method for an app to "decide" whether a call should ring before the hardware starts.

### 2. Decision Logic Layer (`AppInCallService`)
Every incoming call is evaluated through a three-path decision engine:
- **Normal Path**: If Classroom Mode is OFF, alerts trigger normally.
- **Emergency Path**: If Classroom Mode is ON AND the caller matches a priority contact, a manual priority alert is triggered.
- **Silent Path**: If Classroom Mode is ON and no match is found, the dialer choosingly bypasses the alert path.

### 3. Native Integration
- **AlertEngine**: Direct ownership of the `Ringtone` and `Vibrator` services.
- **EmergencyMatcher**: Robust normalization logic to handle international and local number formats.

## 📦 Getting Started

1. **Build**: Run `flutter build apk --release`.
2. **Setup**:
   - Open the app and grant the **Default Phone App** role.
   - Designate your "Emergency Contacts" within the app UI.
3. **Usage**:
   - The app automatically enters **Classroom Mode** when it detects the linked ESP32 device.
   - Non-emergency callers will remain silent; priority contacts will break through.

---
*Developed for professional deployment. Modular, high-authority Android call management.*
