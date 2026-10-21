# Pairing and transport contract (classic ESP32)

This extends `IOT_PROTOCOL.md`; telemetry, command bounds, acknowledgements, room assignment and app data are unchanged. New firmware requires authenticated BLE and the identity characteristic below; selecting an advertisement is never authentication. The Android app supports one active node, like the existing app—not a new multi-node backend.

## BLE authentication

- ESP32 Arduino 2.0.17 / Bluedroid: **LE Secure Connections, MITM, bonding, 16-byte encryption keys**, display-only IO policy with a random per-node six-digit static passkey recorded on a private sticker. Android supplies the passkey through its system pairing UI. No QR scanner is implemented; codes are typed manually.
- BLE setup is open for five minutes on an unconfigured boot or after holding GPIO27's Setup button for five seconds. Known bonds can reconnect outside setup; new passkey exchanges are rejected outside it. Five authentication failures introduce a 60-second pairing cooldown (in RAM, reset by power cycling).
- Commands and identity require encrypted/MITM GATT access. Notifications are sent only after authenticated encryption; their CCCD requires MITM encryption too. Android waits for bonding, subscription and a valid node identity before exposing a usable BLE connection.
- MAC, name and UUID are discoverability hints. The chip-derived logical device ID is read over the authenticated channel and bound to MQTT's topic identity. Switching to an unrelated BLE node disconnects the old MQTT transport.
- The physical Setup button stops pumps and reopens provisioning; it is **not a factory reset**. Bonds, passkey and current configuration survive. A power switch only controls power.
- For local sticker recovery, first boot and an explicit five-second physical Setup press print the code on trusted USB serial. Ordinary reboot and radio pairing callbacks do not print it. Treat physical serial access as privileged; this reference firmware does not protect against someone with physical flash/USB access.

## GATT and frames

Keep service `6f8a0001-7c2d-4e9f-a6b1-32d7c05e9140`, notify `...0002...` and write-with-response `...0003...`. Add **read-only identity/status** `6f8a0004-7c2d-4e9f-a6b1-32d7c05e9140`. Read it after subscribing; it contains one JSON status object, without a newline. Notifications/commands use UTF-8 JSON followed by a newline and serialized chunks of at most 20 bytes. Firmware accepts at most 2048 bytes per incoming BLE frame and clears partial frames on disconnect. Android's existing inbound 4096-byte limit stays unchanged.

Illustrative provisioning request (never log real credentials):

```json
{"type":"provision","id":"transaction-uuid","deviceId":"tunas-012345abcdef","ssid":"my-2.4g-network","wifiPassword":"example-only","host":"broker.example.com","port":8883,"username":"node-user","password":"example-only","ca":"digicert"}
```

SSID: 1–32 UTF-8 bytes, unchanged whitespace. Wi-Fi password: empty for an open network, otherwise 8–63 bytes. Host: DNS name or IPv4, at most 253 bytes. Port: integer 1–65535, always TLS regardless of port. Broker username/password: at most 128/256 UTF-8 bytes. CA must be `digicert` or `isrg`, selecting compiled public CA certificates; arbitrary certificates and insecure TLS are not supported. `deviceId` must match the immutable node ID. Phone Wi-Fi credentials are not persisted; pending and verified MQTT settings are encrypted in the existing excluded-from-backup preferences.

Status notification / identity read:

```json
{"type":"setup","deviceId":"tunas-012345abcdef","id":"transaction-uuid","stage":"ready","setupOpen":false,"mqttReady":true}
```

Stages: `setup`, `wifi_connecting`, `time_sync`, `tls_connecting`, `mqtt_connecting`, `ready`, `ble_only`, `wifi_failed`, `time_failed`, `tls_failed`, `mqtt_failed`, `storage_failed`, `setup_closed`, `invalid_config`. BLE setup/radio timeout is 90 seconds to allow entry of the passkey; configuration transfer/result deadline is 120 seconds. Network errors do not disconnect an already authenticated BLE client. Status is periodically refreshed; no status represents a fabricated sensor reading. A first-install connection needs NTP for certificate date validation; time sync failure leaves BLE available. TLS/network failures and MQTT login failures are reported without credentials.

Only `stage=ready`, `mqttReady=true`, matching transaction ID **and** matching node ID promote the phone's pending broker settings. Merely connecting to a broker, or reaching `mqtt_connecting`, does not commit a new configuration. `{"type":"setup_close"}` over authenticated BLE closes setup and permits normal controls; it does not claim successful network setup or cancel a network test already in progress.

## Preferences transaction

Namespace `tunas` stores the passkey and independent `active` / `candidate` JSON blobs. A candidate is written and read back before testing. The prior active blob is never overwritten during testing. On success, save/read back the active blob, then remove the candidate. A failure leaves the prior active configuration intact and reconnects it; with no active blob, retain and retry the candidate on boot. After a failed replacement, the user can reopen setup and submit a corrected candidate. On boot a valid active blob takes precedence over an unverified replacement candidate. NVS writes are single-blob writes, not a bespoke multi-key transaction.

## Runtime transport selection

- Wi-Fi → clock → verified TLS → authenticated MQTT → command subscription/presence. The device uses asynchronous ESP-MQTT so blocking network work never runs on the BLE/control loop.
- Android keeps the same node's authenticated BLE link available alongside MQTT. Prefer MQTT with telemetry younger than 30 seconds; otherwise use fresh BLE telemetry when nearby. With neither link, show Offline; with a link but no fresh telemetry, controls remain disabled. Never relabel cached measurements from one transport as fresh measurements from the other.
- Foreground-only recovery attempts are bounded/spaced by 30 seconds on the phone. It reconnects the securely remembered BLE address without scanning arbitrary devices. Permission denial/radio-off never bypasses Android's permission flow. Explicit Disconnect disables recovery until another explicit connection (or a new Activity instance). Both links disconnect when the app stops. The ESP32 retries networking independently.
- One global in-flight physical command, pinned to its originating transport. No queue, retry, replay, or takeover when switching transport. A lost acknowledgement remains outcome unknown. New commands still require an assigned room, a fresh reading, bounds checks, confirmation and matching device acknowledgement.
- Node pump safety runs in its own high-priority task. Setup or loss of the originating link stops the actuator. Duration bounds and the 30-second hard cap remain. The shared 32-ID cache deduplicates BLE/MQTT requests within a boot; broker authorization and no retained commands remain mandatory. MQTT retained commands are explicitly dropped by the firmware.

## Physical acceptance checklist — not yet performed

Use disconnected pumps or a safe test load first:

1. First boot: record the private sticker code, pair correctly, reject wrong/cancelled codes and legacy/Just Works pairing. Test cooldown, unknown phones outside setup, and bonded reconnection after reboot.
2. Hold Setup for five seconds; verify an active relay turns off. Verify timeout, manual close and old credentials/bonds surviving reconfiguration. Check GPIO27/power switch wiring and relay active polarity.
3. Test Wi-Fi failure, Wi-Fi with no WAN, NTP blocked, DNS failure, wrong CA/hostname, expired certificate, wrong MQTT credentials and subscription denied. BLE telemetry/control must work after closing setup. Verify the old active blob is recovered after a failed replacement and a first-install candidate survives power loss.
4. Verify MQTT handover, WAN loss and recovery, moving outside BLE range, permissions revoked and app background/process restart. No old readings may authorize another transport; no physical command may be resent.
5. Test fragmented/oversized/trailing JSON, retained/duplicate commands, two-transport contention and lost acknowledgements. Independently measure pump cutoff under blocked TLS/DNS, link loss and setup entry. Verify an ack is not rendered as measured fluid delivery.
6. Calibrate/replace the analog placeholders; resolve classic ESP32 ADC2/Wi-Fi conflicts on GPIO25/26. Review flash/NVS encryption, secure boot, sticker handling, broker ACLs and unique broker credentials before production.
