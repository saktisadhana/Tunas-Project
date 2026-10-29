# Verification and anti-slop review

## BLE provisioning / fallback update

- `:app:testDebugUnitTest`: **78 tests, 0 failures, 0 errors, 0 skipped**. New coverage validates provisioning field/UTF-8 limits, CA selection, transaction/device correlation and independent BLE/MQTT freshness/fallback.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest` and `:app:lintDebug`: successful; **0 errors/fatal issues, 35 warnings**. These builds do not execute Android instrumentation or open a BLE radio/broker socket.
- The ESP32 reference target compiles with `firmware/tunas_pot/platformio.ini` (espressif32 6.9.0 / Arduino-ESP32 2.0.17). This is compilation evidence only, not a successful flash, pairing, TLS session or physical actuator test.
- Current APK: `app/build/outputs/apk/debug/app-debug.apk`. Existing `dist/` metadata/screenshots and the baseline instrumentation counts below describe the older delivery; they were not regenerated for this update.
- Physical acceptance remains outstanding: `PAIRING.md` lists wrong-passkey rejection, Setup-button operation, NVS rollback/power-loss recovery, no-internet BLE controls, transport handover, malformed/retained commands and pump cutoff under network failure.

The following original checks are retained as historical evidence, not re-executed results for this pairing update.

## Executed checks

- Final debug build and test APK compiled successfully with the existing Gradle wrapper.
- JVM JUnit XML: **50 tests, 0 failures, 0 errors, 0 skipped**.
- Final full Android instrumentation invocation: **10 passed**. This comprises eight UI-flow tests, a local persistence adapter test and the original package-context test.
- Separate two-phase persistence probe: write succeeded, app was force-stopped, read succeeded with an assertion that the Android process ID changed.
- Native UI flows: profile creation, four-tab navigation, room creation, plant creation/assignment/edit/deletion, manual watering log, no-result search/reset, offline actuator rejection, Activity recreation and editor draft retention while adding a room.
- Additional small-viewport/large-text checks: approximately 325dp width, system font scale 1.4, native navigation and profile onboarding.
- Android lint: **0 errors, 0 fatal issues, 28 warnings**. Warnings concern English-only strings, draw allocations, starter/duplicate launcher resources, view-tooling constructors, drawable loading and an existing Gradle plugin version recommendation. No error was hidden with a baseline.

Raw instrumentation results: `design/verification/instrumentation-suite.txt` and the separately named per-flow/process-probe files. Screenshots actually captured from the APK are under `design/verification/delivery/`; the delivered contact sheet uses those images, not Figma exports. The profile and room in test screenshots are fixtures. A new installation has no saved records.

## Anti-slop delivery gates

| Block | Result | Evidence / boundary |
| --- | --- | --- |
| Hard gate | PASS within exercised scope | Real native controls and saved data; explicit empty/offline states; no fake sensor percentages, fake login success, or timer-based pairing. Form/navigation/offline safety tests passed. Physical hardware, a full TalkBack audit and exhaustive physical-keyboard traversal are UNTESTED. |
| Purpose gate | PASS | Palette, type, wordmark, leaf/grid motif and metric/card composition trace to the supplied Figma frames. HTML contributed only the flow inventory. Added profile, room, schedule and pairing screens support actual user actions. |
| Liveliness | PASS | ENERGY 2 / RHYTHM 2 / MOTION 1 retained in the real renders. Authored botanical artwork and pill navigation remain; there are no unrequested pulsing indicators, theme toggles or decorative dashboards. |
| Craftsmanship / quality | PASS with noted warnings | 50 JVM + 10 instrumentation checks, cross-process storage probe, safe-area fixes and draft-recovery regressions. Lint is error-free but not warning-free. This is a debug build, not a signed store release. |

## Measured opaque text contrast

Ratios were calculated from the actual resource/control colors, using unrounded values for threshold comparisons:

| Foreground / background | Ratio | Normal text (4.5:1) |
| --- | ---: | --- |
| `#454D39` / `#EFEECF` | 7.4993 | Pass |
| `#3B5142` / `#ECEBD7` | 7.1392 | Pass |
| `#59664F` / `#ECEBD7` | 5.0698 | Pass |
| `#2B3B30` / `#A2B595` | 5.4093 | Pass |
| `#EFEECF` / `#454D39` | 7.4993 | Pass |

This is not a claim of complete WCAG conformance. Logo artwork and decorative background gradients were not treated as ordinary text pairs. The sage-input ink was darkened slightly for readable control text rather than changing the Figma palette globally.

## Not verified / not supplied

- Real BLE radio/peripheral behavior, firmware UUID/schema compatibility, authentication and physical actuator safety. See `IOT_PROTOCOL.md` before commissioning.
- Cloud account backend, email verification, account recovery, sync and remote push notifications. Profile access is local convenience, not authentication.
- All Android OEMs/API versions, full screen-reader operation, release signing and store distribution.

Only an isolated test emulator was installed or changed. A physical phone appeared during verification and was not used; subsequent operations explicitly targeted the test emulator.
