# Tunas

Native Android plant-care app in Java. No WebView or HTML UI is used.

## Design source

The four supplied Figma frames define the visual system:

| Frame | Native screen |
| --- | --- |
| `13:348` | Welcome |
| `1:4` | Home / Your Ecosystem |
| `13:454` | Plants |
| `20:740` | Maintenance |

Cream/olive/sage colors, Merriweather Sans and Signika Negative, the wordmark, plant icons, dashed grid and leaf artwork come from Figma. The attached HTML was used only to identify missing flows, not for its Nunito/Nocturne tokens. Source references and font licenses are under `design/`. See `docs/DESIGN.md` for the visual contract and intentional adaptations.

## Working flows

- Create/open a **local profile**, edit it, sign out and reopen saved data.
- Browse starter species, search/filter, switch to My Plants and inspect care notes.
- Add/edit/delete plants; assign rooms and choose a 1–365 day care interval.
- Log manual watering with confirmation; inspect persisted care schedules.
- Add rooms, expand/collapse controls, assign a sensor and safely reject occupied-room deletion.
- Native Bluetooth permission request, scan, select a real advertisement, connect GATT, subscribe to telemetry and assign the connection to a room.
- Manual watering/nutrition/target commands require an assigned connection, fresh telemetry and explicit confirmation. Device acknowledgement is required; offline actions never display fake success.
- Form drafts survive Activity recreation. Adding a room from the plant editor preserves that editor's current inputs.

First launch has no saved plants, rooms or sensor readings. Library entries are reference material, not user records. Screenshots in `dist/` use a test profile/room and an offline sensor state.

## What is deliberately not connected

**Cloud account authentication and sync:** no backend was supplied. Email identifies a profile on this phone only; it is not verified authentication. There are no fake social login, password reset or remote push controls. Local data is app-private, with backup and device-transfer exclusions.

**Physical hardware compatibility:** BLE transport is implemented, but no actual sensor was available. UUIDs and the message schema are a **proposed firmware contract**, not a claim about an existing device. Align firmware with `docs/IOT_PROTOCOL.md` before commissioning. Do not connect actuators until device-side authentication, maximum-run watchdogs, electrical safety and stop-on-disconnect behavior have been verified. The app has no automatic command replay or unattended watering scheduler.

## Structure

- `MainActivity.java`: screen host, safe-area handling, navigation and BLE lifecycle.
- `GardenPages.java`: native plant, room, profile, schedule and pairing screens.
- `ui/`: shared Figma-based components, background artwork and starter catalog.
- `data/`: local AppStore, immutable models and Android-free domain logic.
- `iot/`: real GATT transport, protocol validation, stream framing and command acknowledgement policy.

## Build

Use the installed Android SDK from `local.properties` and the existing Gradle wrapper. The project's daemon toolchain is Java 21; source compatibility remains Java 11. Android 12/API 31 is the minimum.

Git Bash:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
```

Windows Command Prompt uses `gradlew.bat` with the same task arguments.

Output: `app/build/outputs/apk/debug/app-debug.apk`. A copy is packaged as `dist/Tunas-debug.apk`; this is a debug-signed development build, not a store release.

## Verification

The final baseline run passed **50 JVM tests** and **10 Android instrumentation tests** on an isolated Android 15/API 35 AOSP emulator. Eight instrumentation tests exercise the actual UI flows. Lint has no errors; non-blocking warnings remain for localization, starter resources, custom-view draw allocations and tooling/version recommendations. See `dist/verification.json` for exact counts and the APK hash.

Additional checks: local storage read after force-stop/new process; navigation and onboarding at approximately 325dp width with 140% system font scale. Physical sensor behavior, cloud auth, a full TalkBack/keyboard accessibility audit and release signing remain unverified/not supplied.

Tests erase the **selected test installation's** app preferences. Explicitly select an emulator; do not run them against a phone with valuable app data:

```bash
export ANDROID_SERIAL=emulator-5554  # replace with the intended test emulator
python3 tools/run_android_tests.py
```

The helper installs the built app and test APK, keeps raw evidence under `design/verification/`, and returns a failing exit code if instrumentation did not report success.

For the separate process-restart probe:

```bash
python3 tools/run_android_tests.py com.example.tunas.PersistenceProcessTest write
adb -s "$ANDROID_SERIAL" shell am force-stop com.example.tunas
python3 tools/run_android_tests.py com.example.tunas.PersistenceProcessTest read
```

Regenerate raster artwork from the original Figma SVGs with `uv run --no-project --with resvg-py python tools/render_assets.py`. Interactive text and controls are never rasterized. Package an already verified build and captured screenshots with `python3 tools/package_delivery.py`.
