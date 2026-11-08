# Islandroid — a Dynamic Island for Android

Wraps your front camera, auto-detects it (Android `DisplayCutout` API), and shows iOS-style Live Activities and
system alerts in a pill that morphs between **compact → expanded**, with a detached **bubble** when two things
are happening at once.

Two themes (Settings → Theme, switches live):
* **iOS** – pure black, white type, snappy spring, blur-in crossfade, capsule scrubber, green/red call buttons.
* **Material 3 Expressive** – Material You wallpaper colours, 32 dp shapes, bouncier spring, wavy progress with a
  handle, play/pause button that morphs circle ↔ squircle, tonal badges.

## Setup
1. Build (below), install.
2. Sideloaded on Android 13+: App info → ⋮ → **Allow restricted settings**.
3. Settings → Accessibility → Islandroid → On  (draws the overlay – it reads nothing from your screen).
4. Settings → Notification access → Islandroid → On  (music, calls, timers, navigation, notifications).
5. Optional: grant Bluetooth permission in the app (earbud connect cards).
6. Use the **Try it** buttons in the app to preview every state.

## Build
* **Android Studio**: open this folder, let it sync (it creates the Gradle wrapper), Run.
* **GitHub**: push to a repo – `.github/workflows/build.yml` builds `app-debug.apk` as a downloadable artifact.
* CLI: `gradle assembleDebug` (Gradle 8.9+, JDK 17, Android SDK 35).

## iOS feature → Islandroid
| iOS | Islandroid |
|---|---|
| Compact leading/trailing around the camera | ✅ pill sized from the real cutout rect |
| Expanded on long-press | ✅ long-press, or auto for alerts |
| Tap to open app | ✅ |
| Minimal + detached bubble for 2 activities | ✅ (primary shrinks, 2nd becomes a bubble; tap = swap, long-press = expand) |
| Swipe sideways to background an activity | ✅ (returns on next update) |
| Now Playing (art, waveform, scrubber, controls) | ✅ via MediaSession |
| Incoming / ongoing call | ✅ via call notification (answer/decline/hang-up use the dialer’s own actions) |
| Timer, stopwatch, maps, rides, downloads (Live Activities) | ✅ any ongoing notification with chronometer/progress/navigation |
| Charging, low battery | ✅ |
| AirPods-style connect card + battery | ✅ Bluetooth audio devices (battery only if the phone exposes it) |
| Silent/ring switch, Focus, Airplane mode | ✅ |
| Face ID / unlock animation | ✅ lock → unlock on unlock |
| Camera/mic privacy dot | ✅ camera reliable; mic best-effort |
| Hotspot, screen recording | ⚠️ best-effort (depends on OEM broadcasts/notifications) |
| Apple Pay, AirDrop, NFC, CarKey, Find My, SharePlay | ❌ no Android equivalent exposed to third-party apps |
| Notification banners | ➕ extra (iOS doesn’t do this); toggle off if unwanted |

## Honest limitations
* It is an overlay: it can’t draw *inside* the camera hole, and a few OEMs hide overlays on the lock screen.
* Shown in portrait only (hidden in landscape).
* Never compiled or run by the author’s tooling (no Android SDK available) – expect to fix a typo or two on first build.
* Accessibility apps are restricted on Google Play; this is intended for sideloading.
