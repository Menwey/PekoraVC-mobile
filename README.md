<div align="center">

# Pekora Voice chat for Android

**Unofficially Pekora voice-chat port to android.**

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?logo=jetpackcompose&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSdk-26-blue)

</div>

## ✨ Features

- 🔊 **Opus** 48 kHz mono, 20 ms frames (pure-Java [Concentus](https://github.com/lostromb/concentus), no NDK needed)
- 👥 **Multi-user mixer** with a per-speaker jitter buffer
- 🫧 **Floating overlay** with mute and disconnect, drawn over other apps
- 🌙 **Dark Compose UI** with a live mic level meter
- 🔗 **Deep links:** `pekora-player://` and `pekora-studio://`

## 📋 Requirements

| Tool | Version |
|---|---|
| JDK | 17 |
| Android SDK | platform 37 |
| Gradle | 9.8.1 (wrapper included) |
| Android Studio | optional |

## 🔧 Build

```bash
git clone https://github.com/Menwey/PekoraVC-mobile.git
cd PekoraVC-mobile
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

gradlew.bat assembleDebug      # Windows
gradle assembleDebug           # Linux / macOS
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or just open the folder in **Android Studio** and press **Run**.

> **Note:** AGP 9 ships with built-in Kotlin. Do not apply `org.jetbrains.kotlin.android`.

## 🔐 Permissions

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | microphone capture |
| `INTERNET` | network connection |
| `SYSTEM_ALERT_WINDOW` | floating overlay |
| `FOREGROUND_SERVICE_MICROPHONE` | keeps voice alive in the background |
| `POST_NOTIFICATIONS` | foreground service notification |

## 🗂️ Project structure

```
app/src/main/java/pro/pekora/voice/
├── MainActivity.kt          UI (Compose)
├── VoiceClient.kt           connection lifecycle
├── RelayProtocol.kt         packet formats
├── MicCapture.kt            microphone input
├── AudioPlayback.kt         output and mixing
├── OpusCodec.kt             Opus encode / decode
├── OverlayService.kt        floating overlay
└── RobloxTheme.kt           theme and components
```

## 🛠️ Modify

| To change | Edit |
|---|---|
| Endpoint / protocol | `VoiceClient.kt`, `RelayProtocol.kt` |
| Opus settings | `OpusCodec.kt` |
| Mixing / jitter buffer | `AudioPlayback.kt`, `Mixer.kt` |
| UI / theme | `MainActivity.kt`, `RobloxTheme.kt` |
| Package / app name | `app/build.gradle`, `AndroidManifest.xml`, `strings.xml` |

Reverse-engineering notes are in [`docs/`](docs/).

---

<sub>Unofficial project, not affiliated with Pekora or Roblox.</sub>
