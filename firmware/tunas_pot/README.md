# Classic ESP32: BLE setup + offline BLE + TLS MQTT

Reference firmware, **not commissioned hardware**. Sensor conversion formulas remain uncalibrated placeholders. Never connect pumps until the physical safety checklist in `../../docs/PAIRING.md` passes.

## Build and first boot

Use PlatformIO Core: `pio run -d firmware/tunas_pot` from the repository root. `platformio.ini` pins espressif32 6.9.0 / Arduino-ESP32 2.0.17 and ArduinoJson 7.2.1. Wi-Fi, BLE, Preferences and asynchronous ESP-MQTT come with that framework. The target is `esp32dev` with a **4 MB flash / huge_app partition** (no OTA); verify your board before flashing. Arduino IDE also needs ArduinoJson 7.2.1 and a partition with enough app space.

1. With actuators disconnected, wire a normally-open **Setup button between GPIO27 and GND**. It uses `INPUT_PULLUP`; this is separate from the power switch. Existing relay pins remain GPIO18/19, active HIGH: verify this against your actual driver.
2. Upload: `pio run -d firmware/tunas_pot -t upload --upload-port <port>`; monitor at 115200 baud.
3. At first boot, the serial monitor prints the random **six-digit passkey** and chip-derived `tunas-<MAC>` identity. Record them on a private sticker attached to the node. If upload/reset made you miss the initial message, open the USB serial monitor and **hold the physical Setup button for five seconds** to print the sticker details again. There is no display or universal passkey. Serial is a trusted, local commissioning/recovery channel; do not publish its output. The code is not printed on ordinary reboot or pairing callbacks and is never advertised over BLE. Losing the sticker and phone bond requires physical access (USB serial + Setup); do not erase NVS casually, since that also destroys configuration/bonds.
4. In the Android app: Maintenance → Pair Sensor → Scan nearby devices → Connect. Type the sticker code into **Android's Bluetooth pairing dialog**, not an ordinary application password field. Keep Android Nearby devices permissions enabled.
5. Enter a **2.4 GHz** Wi-Fi SSID/password and TLS MQTT host, port, username/password and CA. Select DigiCert Global Root CA for brokers whose chain uses it (often EMQX); ISRG Root X1 for chains using it (often HiveMQ). Confirm the actual broker certificate chain—provider names alone are not a guarantee. Port is usually **8883**. TLS hostname/date verification is never disabled.
6. Watch setup progress over BLE. Preferences saves a candidate before network testing; active settings are replaced only after Wi-Fi, clock synchronization, TLS, MQTT authentication and command subscription work. The phone promotes pending credentials only on a matching committed `ready` result.

`secrets.example.h` is retained as a legacy example; the new sketch does not include `secrets.h`, nor import old compile-time credentials. No passwords need to be placed in source files.

## Setup button and offline operation

- Fresh node: opens setup for five minutes. Once bonded, the phone can always use encrypted BLE nearby, without internet or Wi-Fi.
- Hold Setup for **five seconds**: immediately stop pumps and open a new five-minute setup window. It does **not** erase active settings, bonds, passkey, or any phone data. New bonds and changes to Wi-Fi/MQTT require this window.
- Existing configuration: boot attempts it automatically; bonded phones can reconnect without entering the code again.
- Network/setup failure: report the failing stage over BLE. Keep/recover prior active settings when available. A first-install candidate survives reboot and retries even if no active configuration exists.
- Tap **Use Bluetooth now / close setup**, or wait for the window to expire, to enable normal controls. Pump/target commands are rejected while setup is open.
- Normal operation: telemetry and acknowledgements work on BLE; MQTT publishes/subscribes to the existing `tunas/<deviceId>/...` topics. Connection retries never replay commands.

## Safety and hardware limits

Pumps stop at the requested 1–30 seconds, at the hard cap, when their **originating** connection drops, or when setup opens. Another surviving transport does not take over a running pump. A separate high-priority safety task runs independently of Wi-Fi/TLS waits. A command acknowledgement means accepted/rejected, not that fluid flow was physically measured.

MQTT uses a clean session, subscribes at QoS 0, and rejects retained commands. Both transports share a bounded 32-ID duplicate cache for the current boot; this is not persistent, unlimited replay protection. There is no automatic command replay and no autonomous watering from the moisture target. Broker ACLs must restrict the node and phone to this node's topics. Do not use a public/unauthenticated broker for actuators.

On classic ESP32 **GPIO25/26 are ADC2**, which conflicts with Wi-Fi. Phosphorus/potassium are omitted while Wi-Fi is enabled, rather than reported as zero. Preserve your real sensor drivers/calibration and use an appropriate external ADC or digital sensor interface before production. Moisture/TDS/pH/N on GPIO34/35/32/33 remain illustrative analog mappings.

Ordinary Preferences/NVS and bonding keys are **not automatically protected against physical flash extraction**. Production devices need a considered secure-boot/flash-encryption/NVS-encryption and device credential policy. A sticker passkey authenticates possession, not a cloud account or ownership backend. GPIO27, relay polarity, watchdog/cutoff timing, Bluetooth interoperability and Wi-Fi/TLS coexistence require real-board verification.
