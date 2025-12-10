# Stremio Player

A Media3-based Android video player with Stremio Core Kotlin integration, compatible with:
- Android mobile devices
- Android TV
- Amazon Fire TV / Fire Stick

## Features

- **Stremio Core Kotlin** integration for addon management, catalogs, and streams
- **Media3 (ExoPlayer)** for high-quality video playback
- Smart skip detection for intros and recaps
- D-Pad navigation for TV remotes
- Support for HLS, DASH, and SmoothStreaming

## Building

### Prerequisites

- JDK 17
- Android SDK 34

### Local Build

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

### GitHub Actions

Push to `main` or create a tag starting with `v` to trigger automated builds:

1. Push commits to trigger debug/release builds
2. Create a tag like `v1.0.0` to create a GitHub Release with APKs

## Project Structure

```
app/
├── src/main/
│   ├── java/com/tvplayer/app/
│   │   ├── stremio/              # Stremio Core integration
│   │   │   ├── StremioManager.kt # Core SDK wrapper
│   │   │   └── StremioStorage.kt # Persistent storage implementation
│   │   ├── models/               # Data models
│   │   ├── skipdetection/        # Smart skip functionality
│   │   ├── StremioPlayerApp.kt   # Application class
│   │   ├── MainActivity.kt       # Main player activity
│   │   ├── PlayerController.kt   # Media3 player wrapper
│   │   └── ...
│   ├── res/                      # Android resources
│   └── AndroidManifest.xml
├── build.gradle                  # App build config
└── proguard-rules.pro
```

## Stremio Core Integration

The app uses the official [stremio-core-kotlin](https://github.com/Stremio/stremio-core-kotlin) SDK v1.11.2 which wraps the Rust-based Stremio Core. This provides:

- **Addon Management**: Install/uninstall Stremio addons
- **Catalog Browsing**: Browse movie and series catalogs from addons
- **Stream Discovery**: Get available streams for content
- **User Context**: Profile and settings management

### Usage Example

```kotlin
val stremioManager = StremioPlayerApp.instance.stremioManager

// Check if ready
if (stremioManager.isReady()) {
    // Load a catalog
    stremioManager.loadCatalog("movie", "top")
    
    // Load meta details
    stremioManager.loadMetaDetails("movie", "tt1254207")
    
    // Decode stream data
    val stream = stremioManager.decodeStreamData(streamData)
}
```

### SDK Integration Notes

The stremio-core-kotlin SDK uses Rust/JNI with protobuf serialization. The exact protobuf field structures may require adjustment based on runtime testing. Key integration points:

1. `Core.initialize(storage)` - Initialize with SharedPreferences-backed storage
2. `Core.dispatch(action, field)` - Dispatch actions to the runtime
3. `Core.getState<T>(field)` - Get current state
4. `Core.addEventListener(listener)` - Listen for runtime events

## Dependencies

- [Stremio Core Kotlin](https://github.com/Stremio/stremio-core-kotlin) v1.11.2 - Official Stremio SDK
- [Media3](https://developer.android.com/media/media3) v1.2.1 - Video playback
- [OkHttp](https://square.github.io/okhttp/) v4.12.0 - HTTP client
- [Gson](https://github.com/google/gson) v2.10.1 - JSON parsing
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) v1.7.3 - Async programming

## License

MIT
