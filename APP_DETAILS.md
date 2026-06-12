# IPDial - Advanced SIP Client for Android

**IPDial** is a modern, open-source SIP client designed for Android. It provides a beautiful Material You interface, high-quality voice calling, and deep integration with the Android OS for an intuitive and native-feeling telephony experience.

## Minimum Requirements
- **OS Version:** Android 10 (API Level 29) or higher
- **Network:** Active internet connection (Wi-Fi, 4G, or 5G)
- **Permissions:** 
  - `RECORD_AUDIO` (For microphone access during calls)
  - `READ_CONTACTS` (To display caller ID and contacts list)
  - `MANAGE_OWN_CALLS` / `READ_PHONE_STATE` (For Android Telecom framework integration)
  - `POST_NOTIFICATIONS` (For displaying incoming calls and background service status)

## Key Features
- **Modern Material You Interface:** Built completely with Jetpack Compose for a fast, responsive, and adaptive user interface.
- **System Telecom Integration:** Uses Android's native TelecomManager to report incoming/outgoing calls to the system UI, pausing background music during calls, and routing audio effectively to Bluetooth or the earpiece.
- **Multiple SIP Accounts:** Easily add and manage multiple SIP profiles. Automatically selects the default account or falls back to the active one.
- **Native Incoming Call UI:** Displays native incoming call notifications (CallStyle) that look and behave exactly like standard cellular calls, including answer/decline actions directly from the lock screen.
- **Dark Mode Support:** Enforce system-wide dark mode via Settings or let it follow your OS preferences.
- **"Do Not Disturb" Mode:** A functional DND switch that silently rejects unwanted incoming SIP calls when enabled.
- **Call Recording:** Built-in call recording feature to capture important conversations.
- **Native Call History:** Detailed call logs integrated beautifully within the app, showing missed, incoming, and outgoing calls with durations.

## Build and Deployment Instructions

### Standard Deployment
You can build the debug or release APK directly from the source code.
```bash
# To build a debug APK
./gradlew assembleDebug

# To build a release APK
./gradlew assembleRelease
```
The resulting APKs will be located in the `app/build/outputs/apk/` directory.

### Signing the App
To distribute the app on the Google Play Store, ensure you sign your release build using your personal keystore.

1. Generate a Keystore file using Android Studio or `keytool`.
2. Configure the `signingConfigs` in `app/build.gradle`.
3. Run `./gradlew bundleRelease` to generate the `.aab` file for Google Play.
