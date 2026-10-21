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
- Native Bluetooth permission request, scan, authenticate with the node's unique sticker passkey in Android's pairing dialog, read its identity and assign it to a room.
- Provision Wi-Fi and TLS MQTT settings over authenticated BLE; ESP32 saves a candidate in Preferences and tests it before replacing the active configuration. A physical Setup button reopens setup. See `docs/PAIRING.md`.
- Reach the same pot over MQTT from outside the LAN. BLE stays available nearby for telemetry, control and acknowledgements without Wi-Fi/internet. Foreground connection recovery never replays actuator commands. The Maintenance header shows **BLE**, **MQTT** or **Offline** based on the selected transport and its own readings.
- Manual watering/nutrition/target commands require an assigned connection, fresh telemetry and explicit confirmation on either transport. Device acknowledgement is required; a broker login without the pot online, and any offline action, never display fake success.
- Form drafts survive Activity recreation. Adding a room from the plant editor preserves that editor's current inputs.

First launch has no saved plants, rooms or sensor readings. Library entries are reference material, not user records. Screenshots in `dist/` use a test profile/room and an offline sensor state.

## What is deliberately not connected

**Cloud account authentication and sync:** no backend was supplied. Email identifies a profile on this phone only; it is not verified authentication. There are no fake social login, password reset or remote push controls. Local data is app-private, with backup and device-transfer exclusions.

**Physical hardware compatibility:** BLE and MQTT transports are implemented, but no actual sensor was commissioned. UUIDs, topics and the message schema are a **proposed firmware contract**, not a claim about an existing device. Align firmware with `docs/IOT_PROTOCOL.md` and `docs/PAIRING.md`. `firmware/tunas_pot/` is a reference sketch for a classic ESP32, with authenticated BLE, Preferences, asynchronous TLS MQTT and an independent 30-second pump cutoff task. Sensor reads are uncalibrated placeholders; this is not a flashed or electrically verified board. Do not connect actuators until authentication, cutoff timing, electrical safety and stop-on-disconnect behavior have been verified. The app has no automatic command replay or unattended watering scheduler. MQTT reaches a broker you choose; it is not a Tunas account or sync service.

## Structure

- `MainActivity.java`: screen host, safe-area handling, navigation, and BLE/MQTT lifecycle.
- `GardenPages.java`: native plant, room, profile, schedule, pairing and broker screens.
- `ui/`: shared Figma-based components, background artwork and starter catalog.
- `data/`: local AppStore, immutable models and Android-free domain logic.
- `iot/`: GATT transport, TLS MQTT transport, protocol validation, stream framing and command acknowledgement policy.
- `firmware/tunas_pot/`: classic ESP32 reference sketch for BLE provisioning, offline BLE control and TLS MQTT.

## Remote MQTT

BLE still pairs a pot that is next to the phone. MQTT is how the same pot is reached from another network.

1. Create a broker. EMQX Cloud Serverless is the sketch default: the deployment Overview page shows a host like `xxxx.emqxsl.com` and TLS port **8883**. Add a username and password under Authentication. HiveMQ Cloud works on the same port; see `firmware/tunas_pot/README.md` for the CA switch. Do not put a pump on a public unauthenticated broker.
2. Flash `firmware/tunas_pot/` using the pinned PlatformIO configuration. Record the serial output's device ID and random six-digit passkey on a private node sticker. If you miss first-boot output, hold the physical Setup button for five seconds while monitoring USB serial to recover it. Connect that normally-open button from GPIO27 to GND. No `secrets.h` is required by the new sketch.
3. In Maintenance → Pair Sensor, scan and select the node. Enter its sticker code in Android's Bluetooth pairing dialog. Enter the 2.4 GHz Wi-Fi network and MQTT broker settings in **Node setup**, choose the broker's CA and tap **Send setup over Bluetooth**. The immutable device ID comes from the node. Progress is reported over BLE; only a committed `ready` result promotes the pending broker settings on the phone. Assign the node to a room. To reconfigure, hold Setup for five seconds. To operate without internet immediately, tap **Use Bluetooth now / close setup** (the window otherwise closes after five minutes).
4. Water, nutrients and the moisture target still ask for confirmation, still require a reading from the last 30 seconds, and still wait for the pot's ack. A broker login while the pot is offline does not report success.

Broker settings are encrypted on the phone; Wi-Fi passwords are not persisted there. Ordinary ESP32 Preferences/NVS is not encrypted by default. The app has no built-in host or password. The existing manual MQTT form remains available for remote/legacy access, but provisioning new firmware should start with authenticated BLE.

## Build

Use the installed Android SDK from `local.properties` and the existing Gradle wrapper. The project's daemon toolchain is Java 21; source compatibility remains Java 11. Android 12/API 31 is the minimum.

Git Bash:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
```

Windows Command Prompt uses `gradlew.bat` with the same task arguments.

Output: `app/build/outputs/apk/debug/app-debug.apk`. A copy is packaged as `dist/Tunas-debug.apk`; this is a debug-signed development build, not a store release.

## Verification

`:app:testDebugUnitTest` passes **78** JVM tests, including MQTT topics, TLS broker settings, the shared actuator gate, the scripted MQTT session, provisioning validation/transaction correlation and per-transport freshness/fallback. The Android debug APK, instrumentation APK and lint tasks build successfully (no lint errors; non-blocking warnings remain). The pinned ESP32 PlatformIO target also compiles. An earlier baseline passed **10** Android instrumentation tests on an isolated Android 15/API 35 AOSP emulator; eight exercise UI flows. Instrumentation and physical BLE/Wi-Fi/broker/actuator tests were **not** run for this pairing update. Offline UI strings remain (`No sensor connected`, `0 Sensors Online`, `Pair Sensor`, `Scan nearby devices`). `dist/verification.json` describes the older packaged delivery, not this new APK.

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
