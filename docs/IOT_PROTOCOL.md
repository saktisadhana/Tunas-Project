# Tunas device boundary — provisional protocol

## Status and limits

The Android client uses the real `BluetoothLeScanner` and `BluetoothGatt` APIs for nearby pots, and Eclipse Paho over TLS for a broker the user types in. There are no simulated devices, generated sensor readings, timer-driven connection successes, automatic actuator operations, reconnects, queued offline commands, or command retries.

**No Tunas hardware specification or physical peripheral was supplied. All vendor UUIDs, field units, limits, and message semantics below are proposed, not verified firmware compatibility.** A device advertising the name “Tunas” is not proof of identity. Do not deploy physical controls until the hardware checklist below is completed.

Only the `iot` Java package and its JVM tests are implemented here. The host application owns manifest declarations, runtime permission prompts, UI, persistence, foreground lifecycle, device selection, freshness display, and confirmation before a physical action.

## UI API

`com.example.tunas.iot.BleDeviceClient` is a public final class:

```java
BleDeviceClient(Context context, BleDeviceClient.Listener listener)
void startScan()
void stopScan()
void connect(String address)
void disconnect()
void close()
boolean isConnected()
void command(String action, int value)
```

The nested `Listener` supplies:

```java
void onStatus(String message)
void onDeviceFound(String address, String name, int rssi)
void onConnected(boolean connected)
void onTelemetry(Telemetry telemetry)
void onCommandResult(boolean success, String message)
```

Calls can originate on any thread; operations and **all callbacks run on the main looper**. Off-main calls are asynchronous. `isConnected()` is a volatile snapshot of setup completion, not an instantaneous guarantee of radio liveness or fresh measurements. Do not block or throw from listeners.

- Request `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` before scanning, and `BLUETOOTH_CONNECT` before connecting. The project has minSdk 31. The client checks grants and handles operation-time permission revocation but never opens a permission dialog. The app may use `neverForLocation` for scan permission when appropriate; this boundary neither requests nor uses location.
- A scan lasts at most 12 seconds, can be stopped early, and emits only actual advertisements. It intentionally does not filter on the provisional service UUID, because firmware advertising is unknown. Results may repeat as RSSI changes; the UI should deduplicate by address. An absent advertised name is labeled “Unnamed BLE device,” not inferred to be Tunas.
- `connect(address)` stops scanning, releases the previous GATT session, uses `autoConnect=false` and LE transport, and waits for real callbacks. Setup has a 20-second **failure-only** timeout.
- A radio link alone is not ready. `onConnected(true)` occurs only after successful service discovery, characteristic/property checks, local notification registration, and a successful CCCD write callback.
- On disconnect, setup failure after obtaining a GATT handle, close, write failure, or command timeout: clear native references and buffers, fail pending work, close the GATT handle, and ignore callbacks belonging to that old handle. There is no automatic reconnect.
- Treat every `onConnected(false)` as invalidating all prior measurements. Keep old readings only as visibly stale history, never live values. The client does not retain or republish a telemetry cache. Also expire data when no new telemetry arrives within an app-defined freshness window; a connected radio is not evidence of a healthy sensor stream. Missing fields are unknown **for that frame** and must not become zero or retain an old value with a new timestamp.
- Call `disconnect()` when leaving the foreground if monitoring should end, and `close()` when the owner is destroyed. Close is idempotent and terminal; create a new client for a new lifecycle. Close emits final disconnected/status/pending-command-failure callbacks; old asynchronous radio callbacks are subsequently ignored. Explicit calls made after close are rejected rather than reconnecting.

## Proposed GATT profile

These three vendor UUIDs are **PROVISIONAL** and exposed as `PROVISIONAL_*_UUID` constants:

| Role | UUID | Required behavior |
| --- | --- | --- |
| Service | `6f8a0001-7c2d-4e9f-a6b1-32d7c05e9140` | Primary Tunas service |
| Telemetry / acknowledgement | `6f8a0002-7c2d-4e9f-a6b1-32d7c05e9140` | Notify or indicate; notify preferred if both supported |
| Commands | `6f8a0003-7c2d-4e9f-a6b1-32d7c05e9140` | Write with response, not write-without-response |
| Standard CCCD | `00002902-0000-1000-8000-00805f9b34fb` | `01 00` for notify, or `02 00` for indicate |

CCCD writes and command chunks are serialized. API 33+ uses value-taking GATT write APIs; API 31–32 uses the platform legacy setters and write calls. Both notification callback variants are implemented; mutable byte arrays are copied before posting to the main thread.

No negotiated MTU is assumed. Commands are UTF-8 bytes split into **at most 20-byte chunks** (the guaranteed MTU-23 payload), with the next chunk submitted only after the previous successful characteristic-write callback. The peripheral must support stream reassembly across writes and clear partial command frames on disconnect. A GATT write response never counts as application-level command success.

## Framing and validation

One UTF-8 JSON object per newline (`\n`); `\r\n` is also accepted. Notification boundaries need not coincide with JSON or UTF-8 character boundaries. Empty lines are ignored. The bounded decoder accepts at most 4096 bytes before a newline, including an optional CR. On overflow it discards through the next newline; invalid UTF-8 is rejected, not replacement-decoded. Disconnect replaces the decoder, so partial data cannot cross sessions.

Android's built-in `org.json` (`JSONTokener` / `JSONObject`) parses complete objects, with trailing non-whitespace rejected. Its platform JSON syntax leniency is not a custom strict RFC parser. The typed schema validator rejects numeric strings, booleans as measurements, non-finite values, out-of-range values, missing/incorrect envelope types, and incorrectly typed acknowledgements. Unknown keys are ignored for forward compatibility. Invalid frames produce a status message and never trigger a physical action or refresh telemetry. At least one numeric measurement must be present in a telemetry frame.

### Telemetry

Illustrative protocol fixture, **not an observed sensor reading**:

```json
{"type":"telemetry","moisture":52.5,"temperature":25.2,"humidity":61.0}
```

`Telemetry` is public, final and immutable. Its `Double` fields are nullable, with absence or JSON null represented by Java null. A present invalid measurement rejects the entire frame. The constructor also enforces these limits:

| Field | Proposed unit | Accepted range, inclusive |
| --- | --- | --- |
| `health` | Device-provided health score, percent | 0–100 |
| `moisture` | Calibrated soil moisture, percent | 0–100 |
| `nutrient` | Device-provided nutrient estimate, ppm | 0–5000 |
| `sunlight` | Normalized light level, percent, **not lux** | 0–100 |
| `temperature` | Degrees Celsius | -40–85 |
| `humidity` | Relative humidity, percent | 0–100 |

`timestampMillis` is a nonnegative local wall-clock receipt timestamp, not a sensor acquisition timestamp and not a device-provided value. Wall-clock changes are possible; app freshness decisions should also consider connection state and a monotonic receipt clock. Health is never computed or fabricated by the app. Units, calibration, nutrient interpretation, normalization and any health algorithm require firmware agreement.

### Commands and acknowledgements

Illustrative outbound fixture; the actual ID is a newly generated UUID per accepted command:

```json
{"type":"command","id":"f22860ef-4df3-4822-836e-d873be217fa4","action":"water","value":5}
```

Allowed action/value pairs only:

- `water`: integer duration **1–30 seconds**.
- `nutrition`: integer duration **1–30 seconds**.
- `target_moisture`: integer target **0–100 percent**.

Unknown actions, case variants, invalid bounds and offline requests fail immediately through `onCommandResult(false, ...)`; nothing is buffered for later transmission. There is only one in-flight command; another request is rejected without replacing it. The UI must obtain explicit user confirmation for water/nutrition. A moisture target update is not sent automatically at connect, screen entry, or state restoration. This boundary does not implement a watering schedule.

The peripheral responds on the telemetry characteristic:

```json
{"type":"ack","id":"f22860ef-4df3-4822-836e-d873be217fa4","ok":true}
```

`id` must be a nonempty string of at most 64 characters and must exactly match the pending UUID. `ok` must be a JSON boolean, not a string or number. `ok:false` is a device rejection. Unrelated, duplicate, and previous-session acknowledgements are ignored. An acknowledgement arriving before the last GATT write callback is held until all chunk writes succeed.

A matching `ok:true` received before the deadline reports **device acceptance**, not proof that water flowed or that a motor stopped. Do not render it as confirmed physical completion without a future completion/sensor contract.

Acknowledgement timeout is **10 seconds from command submission**, including chunk transmission, measured with Android's monotonic elapsed clock. Timeout, disconnect and transport failure mean **outcome unknown**: the device may already have acted. On timeout or write failure the client closes the connection and discards any partial frame. Never retry automatically, restore a pending command, or resubmit just because an acknowledgement was lost. The user must inspect the actual device before deciding to issue a new action.

**A phone timeout or disconnect cannot safely stop hardware.** Firmware must independently reject invalid durations, implement a hard actuator cutoff of at most 30 seconds, clear partial frames at disconnect, and deduplicate command IDs before acting. The hard cutoff must survive a lost phone connection. Firmware behavior for a stored moisture target (including any autonomous control) is unknown and must be made explicit before enabling that command in production.

## MQTT transport

MQTT carries the same telemetry, command and ack objects as BLE. It is not a Tunas account. The phone connects with TLS to the broker the user entered (host, port, username, password, device id). There is no default broker. The password is kept in encrypted app prefs (`EncryptedSharedPreferences`), which are already excluded from backup.

The client disconnects when the app leaves the foreground, same as BLE. No auto-reconnect and no command replay. `MqttDeviceClient.isConnected()` means the pot has been seen (status or telemetry), not merely that the broker socket is up. Until then, water, nutrition and target commands are rejected.

### Topic scheme

`deviceId` is 1–64 characters, `[A-Za-z0-9][A-Za-z0-9_-]*`. It is one topic level. `/`, `+`, `#` and spaces are rejected so a device ID cannot become a wildcard.

| Topic | Publisher | Subscriber | Retain | QoS | Payload |
| --- | --- | --- | --- | --- | --- |
| `tunas/<deviceId>/telemetry` | Pot | Phone | no | 0 | One telemetry object |
| `tunas/<deviceId>/cmd` | Phone | Pot | **no** | **0** | One command object |
| `tunas/<deviceId>/ack` | Pot | Phone | no | 0 | One acknowledgement object |
| `tunas/<deviceId>/status` | Pot | Phone | **yes** | 1 | Presence object |

Commands are QoS 0 and not retained so a broker redelivery or a retained message cannot start a pump a second time. Firmware must still deduplicate command IDs before actuating. The phone never republishes a command after a timeout, a lost ack, or a reconnect.

### Last will

The **pot** sets a retained last will on `tunas/<deviceId>/status` before it connects:

```json
{"type":"status","online":false}
```

On a successful session it publishes the retained online object, then subscribes to `cmd`:

```json
{"type":"status","online":true}
```

`online` is a JSON boolean. The phone only subscribes. Publishing this topic, or setting it as the phone's last will, would mark the pot offline when the app sleeps. Retained `online:false` drops live readings and fails an in-flight command as outcome unknown. Telemetry or `online:true` marks the pot present. The UI still wants a reading under 30 seconds old, an assigned room, a confirmation, and a matching ack before it sends anything.

### Payloads

MQTT uses one JSON object per message. A trailing newline is tolerated; extra trailing data is rejected. Field rules are the BLE rules: `IncomingMessage` and `Telemetry` validate telemetry and acks, `CommandPolicy` and `CommandTracker` validate commands. Unknown keys are ignored. Invalid frames do not refresh readings and do not run a pump.

Optional soil-chemistry fields, all nullable, reject the whole frame when present but out of range:

| Field | Proposed unit | Accepted range, inclusive |
| --- | --- | --- |
| `ph` | Soil pH | 0–14 |
| `tds` | Total dissolved solids, ppm | 0–10000 |
| `nitrogen` | Nitrogen, mg/kg | 0–2000 |
| `phosphorus` | Phosphorus, mg/kg | 0–2000 |
| `potassium` | Potassium, mg/kg | 0–2000 |

`moisture` remains calibrated soil-moisture percent. `nutrient` remains the existing 0–5000 estimate; the maintenance screen still labels it µS/cm. A frame may send both `nutrient` and `tds`. Health, sunlight, temperature and humidity are unchanged. The reference sketch's analog maps are uncalibrated placeholders.

The phone publishes, for example:

```json
{"type":"command","id":"f22860ef-4df3-4822-836e-d873be217fa4","action":"water","value":5}
```

Allowed actions are still only `water` and `nutrition` (1–30 seconds) and `target_moisture` (0–100). The pot answers on `ack` with the same id. The phone holds an ack that arrives before the publish completes, times out after 10 seconds on the monotonic clock, and then drops the MQTT session. Timeout and disconnect mean outcome unknown. Do not retry automatically.

The phone always connects with an `ssl://` URI (EMQX Cloud Serverless and HiveMQ Cloud use port **8883**). Port 1883 is not a cleartext option; the client does not open `tcp://`. Hostname verification uses the platform default verifier and the system trust store. Automatic MQTT reconnect is off, and the session is clean, so subscriptions are not resumed with queued commands.

### Reference sketch

`firmware/tunas_pot/` is an ESP32 Arduino sketch (WiFiClientSecure + PubSubClient) that publishes these topics, applies a 30-second pump cap, stops relays when MQTT drops, and deduplicates the last command id. It stores a moisture target and does **not** run a pump on its own when the target changes. NPK/TDS/pH/moisture reads are uncalibrated analog placeholders, not a claim of sensor-library compatibility. Copy `secrets.example.h` to `secrets.h` (gitignored) and fill in Wi-Fi, host, port 8883, username, password, and device ID. The device ID must match the one entered in the app. `MQTT_CA_CERT` defaults to DigiCert Global Root CA for EMQX Cloud Serverless (`root_ca.h`). HiveMQ Cloud needs `ISRG_ROOT_X1` instead. Do not commit Wi-Fi or broker passwords. Setup steps are in `firmware/tunas_pot/README.md`.

## Security and commissioning

BLE names, addresses and service UUIDs are discoverability information, not authentication. This implementation does not invent bonding credentials or a device-authentication protocol. The peripheral must require an appropriate authenticated/encrypted link and authorize actuator writes, or a documented application-level authentication protocol must be designed with the firmware. Do not mistake selecting an advertisement for secure pairing.

Before physical use, verify on real target hardware:

1. Confirm vendor UUIDs, advertise behavior, write/notify properties, CCCD behavior, units and calibration against firmware documentation.
2. Verify API 31–32 and API 33+ scanning, grant/deny/revoke flows, Bluetooth-off behavior, connection failures, service failures and CCCD rejection.
3. Confirm fragmented telemetry, multi-frame notifications, malformed/oversized input, final-write/ack ordering, stale callbacks after close and reconnect, and absence of synthetic readings.
4. With actuators electrically disconnected or a safe test load, verify invalid/offline rejection, matching positive and negative acknowledgements, lost acknowledgement, partial-write failure and no replay after reconnect/process restart.
5. Independently measure firmware's maximum actuator duration and lost-link cutoff. Verify command-ID deduplication, incomplete-line discard, authentication and authorization, and what a target update actually enables.
6. Only then attach pumps/valves and perform supervised, explicitly confirmed short-duration tests.

## Test evidence and remaining gap

```sh
./gradlew :app:testDebugUnitTest --console=plain
```

JVM tests cover real pure-Java command policy, single-command correlation/deadline/cancellation state, setup readiness ordering, bounded byte framing and strict UTF-8 decoding, typed incoming-field validation and immutable telemetry ranges/nullability. MQTT tests cover topic names, TLS URI and credential redaction, the shared actuator gate, and a scripted session: broker connectivity is not pot presence, commands are QoS 0 and not retained, acks match the BLE tracker, and timeout, last will and publish failure do not publish a second command. API-shape tests check the BLE and MQTT client signatures; they do not open a radio or a broker socket.

The MQTT client adds Eclipse Paho `org.eclipse.paho.client.mqttv3` and `androidx.security:security-crypto` for encrypted broker settings. Those dependencies are not a stand-in for a live HiveMQ Cloud session.

Implementation used successive red/green tracer bullets: bounded physical commands; target validation; fragmented frames; malformed UTF-8; overflow recovery; optional telemetry; telemetry ranges; offline rejection; matching acknowledgement and busy rejection; deadline rejection; disconnect/timeout cleanup; GATT readiness ordering; telemetry schema; acknowledgement schema; native API; missing-correlation rejection. Missing-feature compile failures were followed by green executions; behavioral regressions (target, UTF-8, overflow, telemetry ranges, pending state, deadline, acknowledgement schema, native API and missing ID) were also observed failing before their implementation.

**Hardware transport and the Android `org.json` runtime adapter have not been exercised on a device in this task.** Unit tests do not establish electrical safety, RF reliability, firmware compatibility, authenticated pairing, actual actuator operation, or main-looper behavior on a physical Android runtime. Those are explicit outstanding commissioning tests, not simulated successes.
