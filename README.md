<div align="center">

# 🎙️ Pekora Voice for Android

**Voice chat over a UDP relay: Opus 48 kHz, multi-user mixer, floating overlay.**

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?logo=jetpackcompose&logoColor=white)

</div>

Android port of the Pekora voice module. Fetches a token, connects to a UDP relay, encodes the mic to Opus and mixes everyone's voices in the room.

## ✨ Features

- 🔊 Opus 48 kHz mono, 20 ms frames (pure-Java [Concentus](https://github.com/lostromb/concentus))
- 👥 Per-sender jitter buffer and mixer
- 🫧 Draggable overlay bubble (mute / disconnect) over other apps
- 🌙 Dark Compose UI with mic level meter
- 🔗 `pekora-player://` and `pekora-studio://` deep links

## 🔧 Build

**Requires:** JDK 17, Android SDK (platform 37), Gradle 9.8.1.

```bash
git clone https://github.com/<user>/pekora-voice-android.git
cd pekora-voice-android
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

gradlew.bat assembleDebug     # Windows
gradle assembleDebug          # Linux / macOS
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Or open the folder in **Android Studio** and press Run.

> AGP 9 has built-in Kotlin — don't apply `org.jetbrains.kotlin.android`.

## ▶️ Run

1. Start a relay server (separate backend, not included).
2. In the app enter **Server**, **Login**, **Password**, **Room**.
3. **Register** (once) → **Token** → **Connect**; grant mic and overlay permissions.

## 🛠️ Modify

| To change | Edit |
|---|---|
| Token URL / protocol | `VoiceClient.kt`, `RelayProtocol.kt` |
| Opus settings | `OpusCodec.kt` |
| Mixing / jitter buffer | `AudioPlayback.kt`, `Mixer.kt` |
| UI / theme | `MainActivity.kt`, `RobloxTheme.kt` |
| Package name / app name | `app/build.gradle`, `AndroidManifest.xml`, `strings.xml` |

Sources live in `app/src/main/java/pro/pekora/voice/`. Reverse-engineering notes are in [`docs/`](docs/).

---

<sub>Unofficial project, not affiliated with Pekora or Roblox.</sub>
