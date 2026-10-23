# OyaVolt

Native Android battery telemetry and logging app, built for the Poco X3 NFC with Kotlin, Jetpack Compose, Room, and native Bluetooth LE.

OyaVolt records battery readings through a foreground monitoring service, displays live telemetry and history, and calculates charge/discharge energy with explicit gaps when readings are missing. Its BLE protocol API permits telemetry queries only.

## Build

Requires JDK 17 and an Android SDK with API 36. Configure your SDK through `ANDROID_HOME` or a local, untracked `local.properties` file containing `sdk.dir`.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug --console=plain
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Protocol reference

The upstream web implementation is [warwar13/gentai](https://github.com/warwar13/gentai). For protocol work, clone it locally:

```powershell
git clone https://github.com/warwar13/gentai.git reference-web
```

The reference clone, local device databases, root-level captures/exports, signing material, SDK paths, and build outputs are excluded from Git. The CSV under `app/src/main/assets/` is a bundled import asset and remains versioned.

See [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) for completed work, validation results, and remaining device checks, and [AGENTS.md](AGENTS.md) for development and BLE safety rules. Android/OEM process restrictions can interrupt monitoring; missing periods remain visible as data gaps.
