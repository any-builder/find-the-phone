# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Monitor Alert (找手机) - An Android app that receives HMS Push alerts and plays loud audio alarms. Compatible with Huawei HMS Core and HarmonyOS.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (signed)
./gradlew assembleRelease

# Install
adb install -r /path/to/release/apk

# App Bundle for Play Store
./gradlew bundleRelease

# Lint checks
./gradlew lint

# Unit tests
./gradlew test
```

**Release signing**: Keystore is `monitor-alert-keystore.jks`. Set `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` environment variables.

## Architecture

**Component Flow**:
```
HMS Push → HmsMessagingService → MonitoringService → AudioPlayer → Alarm
                 ↓
            MainActivity (UI)
                 ↓
            MainViewModel → Repository → SharedPreferences
```

**Core Components**:
- `MonitoringService` - Foreground service with WakeLock; holds partial wake lock for 10 minutes during monitoring
- `HmsMessagingService` - Receives HMS Push messages; triggers alert when payload contains `"alert":"true"`
- `AudioPlayer` - Manages MediaPlayer and vibration for alarm playback
- `MainViewModel` - State management with StateFlow; calls activation API on startup
- `Repository` - SharedPreferences storage via Gson

**Key Patterns**:
- Push message must be < 5 seconds old to trigger alert
- Alarm auto-stops after 15 seconds
- 30-second silence option after manual stop
- No dependency injection framework (manual construction)
- StateFlow for reactive UI state

## Critical Files

- `app/build.gradle.kts:18-33` - Kotlin/Compose versions, HMS dependencies
- `AndroidManifest.xml` - Permissions and service declarations
- `gradle.properties` - Build configuration (JVM args, AndroidX)

## HMS Push Setup

Place `agconnect-services.json` from AppGallery Connect in `app/` directory. Push payload format:
```json
{
  "alert": "true",
  "message": "Your alert message"
}
```

## Cloud Functions

Aliyun FunctionCompute Python functions in `cloud_functions/`:
- `hms_push_sender/` - Sends push notifications
- `oss_event_store/` - Activation endpoint: `https://device-activate-mcvfbtcuhc.ap-southeast-1.fcapp.run`
