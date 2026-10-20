# Tunas pot (ESP32)

Reference sketch for the MQTT topics in `docs/IOT_PROTOCOL.md`. Analog reads are placeholders. Relays stop at the commanded time, at 30 seconds, or if MQTT drops. `target_moisture` is stored and does not start a pump.

Libs: ESP32 `WiFiClientSecure`, `PubSubClient`.

## EMQX Cloud Serverless

1. In the EMQX Cloud console, create a Serverless deployment.
2. On the deployment Overview page, copy the host (`something.emqxsl.com`). The TLS port is **8883**. Ports 1883 and 8083 are not open.
3. Under Authentication, add a username and password. Serverless does not allow anonymous clients.
4. Copy `secrets.example.h` to `secrets.h` in this folder. `secrets.h` is gitignored. The sketch does not compile until that file exists.
5. Fill in Wi-Fi, `MQTT_HOST`, `MQTT_PORT` 8883, `MQTT_USER`, `MQTT_PASSWORD`, and `DEVICE_ID`. The device ID has to be the same string you enter in the app, or the phone and the pot will not see each other.
6. Leave `MQTT_CA_CERT` as `DIGICERT_GLOBAL_ROOT_CA` (`root_ca.h`). That is the root EMQX Serverless uses. If the Overview CA download is a different PEM, paste that into `MQTT_CA_CERT` instead.
7. Flash this folder. In the app, open Maintenance → Pair Sensor, enter the same host, port 8883, username, password, and device ID, then **Connect over MQTT**.

## HiveMQ Cloud

Same fields and port 8883. HiveMQ Cloud chains to Let's Encrypt, so set `MQTT_CA_CERT` to `ISRG_ROOT_X1` (`isrg_root_x1.h`) before flashing.
