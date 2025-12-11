# Stremio Player - Android APK Project

## Overview

This is an Android video player application that integrates Stremio Core Kotlin with Media3 (ExoPlayer). The app is designed for:
- Android mobile devices
- Android TV
- Amazon Fire TV / Fire Stick

**Note**: This project cannot be run directly on Replit as it requires the Android SDK. Use GitHub Actions to build the APK.

## Architecture

### Key Components

- **StremioManager** (`app/src/main/java/com/tvplayer/app/stremio/StremioManager.kt`) - Wrapper around stremio-core-kotlin SDK
- **StremioStorage** (`app/src/main/java/com/tvplayer/app/stremio/StremioStorage.kt`) - Thread-safe SharedPreferences storage for Stremio Core
- **PlayerController** - Media3/ExoPlayer wrapper for video playback
- **MainActivity** - Main player UI with D-Pad support for TV remotes
- **SmartSkipManager** - Detects and handles intro/recap skipping

### Stremio Core Integration

The app uses the official stremio-core-kotlin SDK (v1.11.2) which wraps Rust-based Stremio Core:
- `Core.initialize(storage)` - Initialize with storage
- `Core.dispatch(action, field)` - Dispatch actions to the runtime
- `Core.getState<T>(field)` - Get current state
- `Core.addEventListener(listener)` - Listen for runtime events

### Dependencies

- `stremio-core-kotlin:1.11.2` from JitPack - Official Stremio SDK (Kotlin/Rust)
- `media3:1.2.1` - Google's Media3 library for playback
- OkHttp v4.12.0 + Gson v2.10.1 for networking

## Building

### Via GitHub Actions (Recommended)

1. Push code to `main` or `master` branch
2. Go to Actions tab in GitHub
3. Download APK artifacts from completed workflow

### Creating Releases

Tag with `v*` pattern (e.g., `v1.0.0`) to automatically create GitHub releases with APKs.

## File Structure

```
├── .github/workflows/build.yml  # GitHub Actions build workflow
├── app/
│   ├── build.gradle             # App dependencies and build config
│   ├── proguard-rules.pro       # ProGuard rules for release builds
│   └── src/main/
│       ├── AndroidManifest.xml  # Compatible with mobile + TV + FireStick
│       └── java/com/tvplayer/app/
│           ├── stremio/
│           │   ├── StremioManager.kt    # Stremio Core SDK wrapper
│           │   └── StremioStorage.kt    # Thread-safe storage
│           ├── StremioPlayerApp.kt      # Application class
│           ├── MainActivity.kt          # Player UI
│           ├── PlayerController.kt      # ExoPlayer wrapper
│           └── ...
├── build.gradle                 # Root build config with JitPack
├── settings.gradle              # Project settings
└── gradle.properties            # Gradle configuration
```

## Recent Changes

- **December 2025**: Fixed build errors for stremio-core-kotlin 1.11.2:
  - Updated JVM target from 17 to 21 (required by stremio-core-kotlin 1.11.2)
  - Updated GitHub Actions workflow to use JDK 21
  - Fixed StremioManager.kt to use correct pbandk sealed class API pattern
  - Fixed MainActivity.kt to properly handle MetaDetails sealed class wrappers
- Integrated Stremio Core Kotlin SDK v1.11.2 via JitPack
- Created StremioManager as proper wrapper around Core SDK
- Created thread-safe StremioStorage implementing Core's Storage interface
- Updated StremioPlayerApp to initialize Core on startup
- Updated AndroidManifest for mobile + TV + FireStick compatibility
- Created GitHub Actions workflow for automated APK builds
- Cleaned up unused source files (using JitPack prebuilt instead)
- Updated proguard-rules.pro for Stremio/pbandk/OkHttp

## SDK Integration Notes

The stremio-core-kotlin SDK uses Rust/JNI with protobuf serialization (pbandk). Key API patterns:
- Action types use sealed classes: `Action(type = Action.Type.Load(ActionLoad(...)))`
- ActionLoad uses sealed args: `ActionLoad(args = ActionLoad.Args.MetaDetails(selected))`
- RuntimeEvent uses sealed type: `when (event.type) { is RuntimeEvent.Type.CtxLoaded -> ... }`
- MetaDetails content uses sealed loadable wrappers
