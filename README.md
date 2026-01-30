# Monitor Alert App

A cross-platform Android/HarmonyOS compatible app that receives push alerts from HMS Core and plays loud audio alerts.

## Features

- **HMS Push Integration**: On-demand alerts via Huawei Push Kit
- **Background Service**: Runs continuously even when app is minimized
- **Wake Lock**: Prevents CPU from sleeping during monitoring
- **Loud Audio Alert**: Plays alarm at configured volume with selected ringtone
- **Vibration**: Optional vibration alert
- **Silence Feature**: 30-second silence option during alerts
- **Auto-Recovery**: Service auto-restarts after interruption
- **HarmonyOS Compatible**: Works on HarmonyOS and Android devices

## Requirements

- Android API 26+ (Android 8.0 Oreo)
- Kotlin 1.9.0+
- Gradle 8.0+
- HMS Core (Huawei Mobile Services)

## Setup

1. Open project in Android Studio / DevEco Studio
2. Wait for Gradle sync to complete
3. Add your `agconnect-services.json` from AppGallery Connect
4. Update HMS App ID in `AndroidManifest.xml`
5. Build and run

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build (signed)
./gradlew assembleRelease

# App Bundle for Play Store
./gradlew bundleRelease
```

## Release Artifacts

```
app/build/outputs/apk/release/app-release.apk    # Signed APK (~3.3MB)
app/build/outputs/bundle/release/app-release.aab # App Bundle (~4.5MB)
```

## HMS Push Alert Format

Send push with this data to trigger alert:

```json
{
  "alert": "true",
  "message": "Your alert message here"
}
```

## Configuration

- **Remote URL**: Optional HTTP endpoint to poll
- **Check Interval**: Poll frequency in seconds
- **Target Data**: String to detect in HTTP response
- **Alert Volume**: 50-100% volume level
- **Vibrate**: Enable/disable vibration
- **Ringtone**: Select alarm sound from device

## Architecture

- `MonitoringService` - Foreground service for alerts
- `HmsMessagingService` - HMS Push message handler
- `AudioPlayer` - Manages alarm playback
- `Repository` - Persistent configuration storage
- `MainActivity` - Jetpack Compose UI

## Required Permissions

- `INTERNET` - Network access
- `FOREGROUND_SERVICE` - Background operation
- `POST_NOTIFICATIONS` - Service notifications
- `WAKE_LOCK` - Prevent sleeping
- `VIBRATE` - Vibration alerts
- `RECEIVE_BOOT_COMPLETED` - Auto-start on boot

## HMS Setup in AppGallery Connect

1. Create project in AppGallery Connect
2. Enable Push Service
3. Download `agconnect-services.json`
4. Place in `app/` directory
5. Update `android:name="com.huawei.hms.client.appid"` with your app ID

## HarmonyOS Notes

This app uses standard Android APIs compatible with HarmonyOS:
- HMS Core Push Kit works on HarmonyOS
- Foreground services are supported
- MediaPlayer for audio playback
- Works alongside native HarmonyOS apps

## License

MIT
