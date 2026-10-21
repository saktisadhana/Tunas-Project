#include <WiFi.h>
#include <Preferences.h>
#include <ArduinoJson.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include <BLESecurity.h>
#include <esp_gap_ble_api.h>
#include <mqtt_client.h>
#include <time.h>
#include "root_ca.h"
#include "isrg_root_x1.h"

static const int PIN_SETUP = 27; // Normally open button to GND; not the power switch.
static const int PIN_MOISTURE = 34, PIN_TDS = 35, PIN_PH = 32;
static const int PIN_NITROGEN = 33, PIN_PHOSPHORUS = 25, PIN_POTASSIUM = 26;
static const int PIN_WATER = 18, PIN_NUTRIENT = 19;
static const int RELAY_ON = HIGH, RELAY_OFF = LOW;
static const uint32_t HARD_CAP_MS = 30000, TELEMETRY_MS = 5000, SETUP_MS = 300000;
static const char* SERVICE_UUID = "6f8a0001-7c2d-4e9f-a6b1-32d7c05e9140";
static const char* RX_UUID = "6f8a0002-7c2d-4e9f-a6b1-32d7c05e9140";
static const char* TX_UUID = "6f8a0003-7c2d-4e9f-a6b1-32d7c05e9140";
static const char* ID_UUID = "6f8a0004-7c2d-4e9f-a6b1-32d7c05e9140";
static const char* OFFLINE = "{\"type\":\"status\",\"online\":false}";
static const char* ONLINE = "{\"type\":\"status\",\"online\":true}";

struct Config {
  char id[65], ssid[33], wifiPassword[64], host[254], username[129], password[257], ca[10];
  uint16_t port;
};
struct Frame { char text[2049]; uint32_t epoch; bool ble; };
struct Publish { char text[512]; bool telemetry; };
struct Progress { char stage[24], id[65]; bool ready, closeSetup; };
QueueHandle_t incoming, outgoing, configurations, progress;
struct PumpEvent { int pin; bool running, fromBle; uint32_t milliseconds; };
QueueHandle_t pumpEvents;
SemaphoreHandle_t pumpLock;
SemaphoreHandle_t statusLock;
Preferences prefs;
BLEServer* bleServer;
BLECharacteristic *rxCharacteristic, *identityCharacteristic;
esp_mqtt_client_handle_t mqtt = nullptr;
volatile bool bleConnected = false, bleAuthenticated = false, mqttLink = false;
volatile bool setupOpen = false, peerKnown = false, mqttFailed = false;
volatile bool mqttTransportFailure = false;
volatile uint32_t bleEpoch = 0, mqttEpoch = 0;
uint32_t setupStarted, connectedAt, pairingBlockedAt = 0;
uint8_t pairingFailures = 0;
uint16_t bleConnectionId;
String deviceId, stage = "setup", resultId;
String statusSnapshot;
uint8_t peerAddress[6] = {};
char telemetryTopic[96], commandTopic[96], ackTopic[96], statusTopic[96], clientId[80];
char networkRequestId[65];
int commandSubscription = -1;
uint32_t lastTelemetry = 0, lastStatus = 0, pressedAt = 0;
bool buttonHandled = false, rxOverflow = false;
std::string bleBuffer;

struct Pump {
  int pin; bool running; bool fromBle; uint32_t startedAt, duration, linkEpoch;
  void begin() { pinMode(pin, OUTPUT); digitalWrite(pin, RELAY_OFF); running = false; }
  void stop() {
    digitalWrite(pin, RELAY_OFF);
    if (!running) return;
    running = false;
    PumpEvent event = {pin, false, fromBle, millis() - startedAt};
    xQueueSend(pumpEvents, &event, 0);
  }
  void start(uint32_t seconds, bool source) {
    fromBle = source; linkEpoch = source ? bleEpoch : mqttEpoch; startedAt = millis(); duration = seconds * 1000;
    running = true; digitalWrite(pin, RELAY_ON);
    PumpEvent event = {pin, true, fromBle, duration};
    // shortcut: logs may drop if full; enlarge the queue if diagnostics need every transition.
    xQueueSend(pumpEvents, &event, 0); // Never block the pump safety task on serial output.
  }
  void poll(uint32_t now) {
    bool link = fromBle ? bleAuthenticated && linkEpoch == bleEpoch
        : mqttLink && linkEpoch == mqttEpoch && WiFi.status() == WL_CONNECTED;
    if (running && (!link || setupOpen || now - startedAt >= duration || now - startedAt >= HARD_CAP_MS)) stop();
  }
};
Pump water = {PIN_WATER}, nutrient = {PIN_NUTRIENT};

static void stopPumps() {
  xSemaphoreTake(pumpLock, portMAX_DELAY); water.stop(); nutrient.stop(); xSemaphoreGive(pumpLock);
}
static void safetyTask(void*) {
  for (;;) {
    xSemaphoreTake(pumpLock, portMAX_DELAY);
    water.poll(millis()); nutrient.poll(millis());
    xSemaphoreGive(pumpLock);
    vTaskDelay(pdMS_TO_TICKS(10));
  }
}

static void report(const char* value, const char* id, bool ready = false, bool closeSetup = false) {
  Progress event = {};
  strlcpy(event.stage, value, sizeof(event.stage)); strlcpy(event.id, id, sizeof(event.id));
  event.ready = ready; event.closeSetup = closeSetup;
  // Keep the latest state, never block a TLS/MQTT callback on the UI.
  if (xQueueSend(progress, &event, 0) != pdTRUE) { Progress old; xQueueReceive(progress, &old, 0); xQueueSend(progress, &event, 0); }
}
static String statusJson() {
  JsonDocument doc;
  doc["type"] = "setup"; doc["deviceId"] = deviceId; doc["id"] = resultId;
  doc["stage"] = stage; doc["setupOpen"] = setupOpen; doc["mqttReady"] = mqttLink;
  String text; serializeJson(doc, text); return text;
}
static void notifyBle(const String& text) {
  if (!bleAuthenticated) return;
  uint32_t epoch = bleEpoch;
  String line = text + "\n";
  for (size_t at = 0; at < line.length() && bleAuthenticated && epoch == bleEpoch; at += 20) {
    size_t count = min((size_t)20, line.length() - at);
    rxCharacteristic->setValue((uint8_t*)line.c_str() + at, count);
    rxCharacteristic->notify();
    delay(8); // MTU-23 safe; safety task is independent of notification/network delays.
  }
}
static void sendStatus() {
  String text = statusJson();
  xSemaphoreTake(statusLock, portMAX_DELAY); statusSnapshot = text; xSemaphoreGive(statusLock);
  notifyBle(text); lastStatus = millis();
}
static bool bonded(const uint8_t* address) {
  int count = esp_ble_get_bond_device_num();
  if (count <= 0 || count > 32) return false;
  esp_ble_bond_dev_t* list = (esp_ble_bond_dev_t*)calloc(count, sizeof(esp_ble_bond_dev_t));
  if (!list) return false;
  bool found = false;
  if (esp_ble_get_bond_device_list(&count, list) == ESP_OK)
    for (int i = 0; i < count; i++) if (!memcmp(address, list[i].bd_addr, 6)) found = true;
  free(list); return found;
}
class Security : public BLESecurityCallbacks {
  uint32_t onPassKeyRequest() override {
    bleServer->disconnect(bleConnectionId); return 0; // PIN pairing is not supported.
  }
  void onPassKeyNotify(uint32_t) override {
    bleServer->disconnect(bleConnectionId);
  }
  bool onConfirmPIN(uint32_t) override { return false; } // Numeric comparison is not supported.
  bool onSecurityRequest() override {
    return peerKnown || (setupOpen && (pairingFailures < 5 || millis() - pairingBlockedAt >= 60000));
  }
  void onAuthenticationComplete(esp_ble_auth_cmpl_t auth) override {
    if (!bleConnected || memcmp(auth.bd_addr, peerAddress, 6)) return;
    bool sc = (auth.auth_mode & ESP_LE_AUTH_REQ_SC_ONLY) != 0;
    bleAuthenticated = auth.success && sc && (peerKnown || setupOpen);
    if (!bleAuthenticated) {
      if (pairingFailures < 5) pairingFailures++;
      pairingBlockedAt = millis(); bleServer->disconnect(bleConnectionId);
    } else { pairingFailures = 0; peerKnown = true; }
  }
};
class ServerEvents : public BLEServerCallbacks {
  void onConnect(BLEServer*, esp_ble_gatts_cb_param_t* param) override {
    bleConnected = true; bleAuthenticated = false; bleEpoch++; connectedAt = millis();
    bleConnectionId = param->connect.conn_id; peerKnown = bonded(param->connect.remote_bda);
    memcpy(peerAddress, param->connect.remote_bda, 6);
    if (!setupOpen && !peerKnown) { bleServer->disconnect(bleConnectionId); return; }
    // Android explicitly initiates bonding after discovering services.
    if (peerKnown) esp_ble_set_encryption(param->connect.remote_bda, ESP_BLE_SEC_ENCRYPT_NO_MITM);
  }
  void onDisconnect(BLEServer*) override {
    bleAuthenticated = false; bleConnected = false; peerKnown = false; bleEpoch++;
    bleBuffer.clear(); rxOverflow = false;
    BLEDevice::startAdvertising();
  }
};
class IdentityEvents : public BLECharacteristicCallbacks {
  void onRead(BLECharacteristic* characteristic) override {
    // Only this callback changes the characteristic value, avoiding cross-task String access.
    String snapshot;
    xSemaphoreTake(statusLock, portMAX_DELAY); snapshot = statusSnapshot; xSemaphoreGive(statusLock);
    characteristic->setValue(bleAuthenticated ? snapshot.c_str() : "");
  }
};
class CommandEvents : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* characteristic) override {
    if (!bleAuthenticated) return;
    std::string chunk = characteristic->getValue();
    for (char c : chunk) {
      if (c == '\n') {
        if (!rxOverflow && !bleBuffer.empty()) {
          Frame frame = {}; frame.ble = true; frame.epoch = bleEpoch;
          memcpy(frame.text, bleBuffer.data(), bleBuffer.size());
          xQueueSend(incoming, &frame, 0);
        }
        bleBuffer.clear(); rxOverflow = false;
      } else if (!rxOverflow) {
        if (bleBuffer.size() >= 2048 || c == '\0') { bleBuffer.clear(); rxOverflow = true; }
        else bleBuffer.push_back(c);
      }
    }
  }
};

static bool copyField(JsonObjectConst doc, const char* key, char* destination, size_t capacity, bool nonempty = false) {
  if (!doc[key].is<const char*>()) return false;
  JsonString value = doc[key].as<JsonString>();
  if (value.size() >= capacity || (nonempty && value.size() == 0) || strlen(value.c_str()) != value.size()) return false;
  memcpy(destination, value.c_str(), value.size() + 1); return true;
}
static bool validHost(const char* host) {
  size_t n = strlen(host), label = 0;
  if (!n || n > 253) return false;
  for (size_t i = 0; i < n; i++) {
    char c = host[i];
    if (c == '.') { if (!label || host[i - 1] == '-') return false; label = 0; continue; }
    if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || (c == '-' && label))) return false;
    if (++label > 63) return false;
  }
  return label && host[n - 1] != '-';
}
static bool readConfig(JsonObjectConst doc, Config& cfg) {
  if (!copyField(doc, "id", cfg.id, sizeof(cfg.id), true)
      || !copyField(doc, "ssid", cfg.ssid, sizeof(cfg.ssid), true)
      || !copyField(doc, "wifiPassword", cfg.wifiPassword, sizeof(cfg.wifiPassword))
      || !copyField(doc, "host", cfg.host, sizeof(cfg.host), true)
      || !copyField(doc, "username", cfg.username, sizeof(cfg.username))
      || !copyField(doc, "password", cfg.password, sizeof(cfg.password))
      || !copyField(doc, "ca", cfg.ca, sizeof(cfg.ca), true) || !doc["port"].is<unsigned int>()) return false;
  unsigned int port = doc["port"].as<unsigned int>();
  size_t passLen = strlen(cfg.wifiPassword);
  if (!port || port > 65535 || (passLen && passLen < 8) || !validHost(cfg.host)
      || (strcmp(cfg.ca, "digicert") && strcmp(cfg.ca, "isrg"))) return false;
  cfg.port = port; return true;
}
static String configJson(const Config& cfg) {
  JsonDocument doc;
  doc["id"] = cfg.id; doc["ssid"] = cfg.ssid; doc["wifiPassword"] = cfg.wifiPassword;
  doc["host"] = cfg.host; doc["port"] = cfg.port; doc["username"] = cfg.username;
  doc["password"] = cfg.password; doc["ca"] = cfg.ca;
  String value; serializeJson(doc, value); return value;
}
static bool loadConfig(const char* key, Config& cfg) {
  String value = prefs.getString(key, ""); JsonDocument doc;
  return !value.isEmpty() && !deserializeJson(doc, value) && readConfig(doc.as<JsonObjectConst>(), cfg);
}
static bool saveConfig(const char* key, const Config& cfg) {
  String value = configJson(cfg);
  if (prefs.putString(key, value) != value.length()) return false;
  return prefs.getString(key, "") == value; // One NVS blob; never overwrite active during testing.
}

// One deduplication cache shared by BLE and MQTT. Duplicate IDs never actuate twice.
struct Seen { char id[65]; bool ok; };
Seen seen[32] = {}; size_t seenAt = 0;
static void acknowledge(const char* id, bool ok, bool ble) {
  JsonDocument doc; doc["type"] = "ack"; doc["id"] = id; doc["ok"] = ok;
  String text; serializeJson(doc, text);
  if (ble) notifyBle(text);
  else if (mqttLink) { Publish message = {}; strlcpy(message.text, text.c_str(), sizeof(message.text)); xQueueSend(outgoing, &message, 0); }
}
struct JsonInput {
  const char* text; size_t at;
  explicit JsonInput(const char* source) : text(source), at(0) { }
  int read() { return text[at] ? (unsigned char)text[at++] : -1; }
  size_t readBytes(char* destination, size_t length) {
    size_t count = 0;
    while (count < length && text[at]) destination[count++] = text[at++];
    return count;
  }
};
static void handleFrame(const Frame& frame) {
  if (frame.ble ? (!bleAuthenticated || frame.epoch != bleEpoch) : (!mqttLink || frame.epoch != mqttEpoch)) return;
  JsonDocument doc;
  JsonInput input(frame.text);
  if (deserializeJson(doc, input) || !doc.is<JsonObject>()) return;
  int tail;
  while ((tail = input.read()) >= 0) if (tail != ' ' && tail != '\r' && tail != '\t' && tail != '\n') return;
  JsonObjectConst object = doc.as<JsonObjectConst>();
  if (frame.ble && object["type"] == "setup_close") {
    setupOpen = false; stage = "setup_closed"; sendStatus(); return;
  }
  if (object["type"] == "provision") {
    Config cfg = {};
    if (!frame.ble || !setupOpen) return;
    if (object["deviceId"].as<String>() != deviceId || !readConfig(object, cfg)) { resultId = cfg.id; stage = "invalid_config"; sendStatus(); return; }
    stopPumps();
    if (xQueueSend(configurations, &cfg, 0) != pdTRUE) { stage = "invalid_config"; sendStatus(); }
    return;
  }
  if (object["type"] != "command" || !object["id"].is<const char*>()
      || !object["action"].is<const char*>() || !object["value"].is<int>()) return;
  JsonString identifier = object["id"].as<JsonString>();
  const char* id = identifier.c_str();
  if (!identifier.size() || identifier.size() > 64 || strlen(id) != identifier.size()) return;
  for (size_t i = 0; i < identifier.size(); i++) if (!(isalnum((unsigned char)id[i]) || id[i] == '-')) return;
  for (const Seen& entry : seen) if (!strcmp(entry.id, id)) { acknowledge(id, entry.ok, frame.ble); return; }
  JsonString actionText = object["action"].as<JsonString>();
  const char* action = actionText.c_str(); int value = object["value"];
  if (strlen(action) != actionText.size()) return;
  bool ok = false;
  xSemaphoreTake(pumpLock, portMAX_DELAY);
  if (!setupOpen && !water.running && !nutrient.running && value >= 1 && value <= 30) {
    if (!strcmp(action, "water")) { water.start(value, frame.ble); ok = true; }
    else if (!strcmp(action, "nutrition")) { nutrient.start(value, frame.ble); ok = true; }
  }
  // A target only changes a setting; it never starts an autonomous watering schedule.
  if (!setupOpen && !strcmp(action, "target_moisture") && value >= 0 && value <= 100) ok = true;
  xSemaphoreGive(pumpLock);
  // Preserve the old target behavior in RAM, outside the safety mutex.
  static int moistureTarget = -1;
  if (ok && !strcmp(action, "target_moisture")) moistureTarget = value;
  (void)moistureTarget;
  strlcpy(seen[seenAt].id, id, sizeof(seen[seenAt].id)); seen[seenAt].ok = ok; seenAt = (seenAt + 1) % 32;
  acknowledge(id, ok, frame.ble);
}

static void mqttEvent(void*, esp_event_base_t, int32_t eventId, void* data) {
  esp_mqtt_event_handle_t event = (esp_mqtt_event_handle_t)data;
  if (event->client != mqtt) return;
  static Frame frame; static size_t used = 0; static bool accept = false;
  switch ((esp_mqtt_event_id_t)eventId) {
    case MQTT_EVENT_CONNECTED:
      report("mqtt_connecting", networkRequestId);
      commandSubscription = esp_mqtt_client_subscribe(mqtt, commandTopic, 0);
      if (commandSubscription < 0) mqttFailed = true;
      break;
    case MQTT_EVENT_SUBSCRIBED:
      if (event->msg_id != commandSubscription || event->data_len != 1 || event->data == nullptr
          || (uint8_t)event->data[0] != 0) { mqttFailed = true; break; }
      if (esp_mqtt_client_publish(mqtt, statusTopic, ONLINE, 0, 1, true) >= 0) { mqttEpoch++; mqttLink = true; }
      else mqttFailed = true;
      break;
    case MQTT_EVENT_DISCONNECTED: mqttLink = false; mqttEpoch++; break;
    case MQTT_EVENT_ERROR:
      mqttTransportFailure = event->error_handle && event->error_handle->error_type == MQTT_ERROR_TYPE_TCP_TRANSPORT;
      mqttLink = false; mqttFailed = true;
      break;
    case MQTT_EVENT_DATA:
      if (event->current_data_offset == 0) {
        used = 0; frame = {}; frame.epoch = mqttEpoch;
        accept = mqttLink && !event->retain && event->qos == 0 && event->total_data_len > 0
            && event->total_data_len < (int)sizeof(frame.text)
            && event->topic_len == (int)strlen(commandTopic) && !memcmp(event->topic, commandTopic, event->topic_len);
      }
      if (!accept || event->current_data_offset != (int)used || used + event->data_len >= sizeof(frame.text)) { accept = false; break; }
      if (memchr(event->data, 0, event->data_len)) { accept = false; break; }
      memcpy(frame.text + used, event->data, event->data_len); used += event->data_len;
      if (used == (size_t)event->total_data_len) { frame.text[used] = 0; xQueueSend(incoming, &frame, 0); accept = false; }
      break;
    default: break;
  }
}
static void closeMqtt() {
  mqttLink = false; mqttEpoch++;
  esp_mqtt_client_handle_t previous = mqtt; mqtt = nullptr;
  if (previous) {
    esp_mqtt_client_publish(previous, statusTopic, OFFLINE, 0, 1, true);
    esp_mqtt_client_stop(previous); esp_mqtt_client_destroy(previous);
  }
  xQueueReset(outgoing); // No acknowledgements/telemetry cross network sessions.
}
static bool tryNetwork(const Config& cfg) {
  closeMqtt(); report("wifi_connecting", cfg.id);
  WiFi.disconnect(); WiFi.begin(cfg.ssid, cfg.wifiPassword);
  uint32_t at = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - at < 25000) vTaskDelay(pdMS_TO_TICKS(100));
  if (WiFi.status() != WL_CONNECTED) { report("wifi_failed", cfg.id); return false; }
  // Validate certificate dates. A router association alone is not internet/MQTT readiness.
  report("time_sync", cfg.id); configTime(0, 0, "pool.ntp.org", "time.google.com"); at = millis();
  while (time(nullptr) < 1700000000 && millis() - at < 15000) vTaskDelay(pdMS_TO_TICKS(100));
  if (time(nullptr) < 1700000000) { report("time_failed", cfg.id); return false; }
  report("tls_connecting", cfg.id);
  strlcpy(networkRequestId, cfg.id, sizeof(networkRequestId)); commandSubscription = -1;
  esp_mqtt_client_config_t options = {};
  options.host = cfg.host; options.port = cfg.port; options.transport = MQTT_TRANSPORT_OVER_SSL;
  options.cert_pem = !strcmp(cfg.ca, "isrg") ? ISRG_ROOT_X1 : DIGICERT_GLOBAL_ROOT_CA;
  options.client_id = clientId; options.username = cfg.username[0] ? cfg.username : nullptr;
  options.password = cfg.password[0] ? cfg.password : nullptr;
  options.lwt_topic = statusTopic; options.lwt_msg = OFFLINE; options.lwt_qos = 1; options.lwt_retain = true;
  options.keepalive = 15; options.network_timeout_ms = 5000; options.disable_auto_reconnect = true;
  // Clean sessions and QoS-0 non-retained commands prevent an offline command queue.
  options.disable_clean_session = false; options.buffer_size = 2048;
  options.protocol_ver = MQTT_PROTOCOL_V_3_1_1;
  mqttFailed = false; mqttTransportFailure = false; mqtt = esp_mqtt_client_init(&options);
  if (!mqtt) { report("tls_failed", cfg.id); return false; }
  esp_mqtt_client_register_event(mqtt, MQTT_EVENT_ANY, mqttEvent, nullptr);
  if (esp_mqtt_client_start(mqtt) != ESP_OK) { closeMqtt(); report("tls_failed", cfg.id); return false; }
  at = millis();
  while (!mqttLink && !mqttFailed && millis() - at < 20000) vTaskDelay(pdMS_TO_TICKS(50));
  if (!mqttLink) { bool tls = mqttTransportFailure; closeMqtt(); report(tls ? "tls_failed" : "mqtt_failed", cfg.id); return false; }
  return true;
}
static void networkTask(void*) {
  Config active = {}, candidate = {}, next = {};
  bool haveActive = loadConfig("active", active), haveCandidate = loadConfig("candidate", candidate);
  uint32_t nextAttempt = 0;
  for (;;) {
    bool received = xQueueReceive(configurations, &next, 0) == pdTRUE;
    if (received) {
      if (!saveConfig("candidate", next)) { report("storage_failed", next.id); continue; }
      candidate = next; haveCandidate = true;
      if (tryNetwork(candidate)) {
        if (saveConfig("active", candidate)) {
          active = candidate; haveActive = true; haveCandidate = false; prefs.remove("candidate");
          report("ready", active.id, true, true);
        } else { closeMqtt(); report("storage_failed", candidate.id); }
      }
      // When replacement fails, keep the prior active blob and restore its connection.
      nextAttempt = millis() + (haveActive ? 3000 : 30000);
    }
    if (mqttLink && WiFi.status() != WL_CONNECTED) closeMqtt();
    if (!mqttLink && (haveActive || haveCandidate) && (int32_t)(millis() - nextAttempt) >= 0) {
      const Config& cfg = haveActive ? active : candidate;
      if (tryNetwork(cfg)) {
        bool completingFirstSetup = !haveActive;
        if (!haveActive) {
          if (!saveConfig("active", cfg)) { closeMqtt(); report("storage_failed", cfg.id); nextAttempt = millis() + 30000; continue; }
          active = cfg; haveActive = true; haveCandidate = false; prefs.remove("candidate");
        }
        report("ready", active.id, true, completingFirstSetup);
      }
      nextAttempt = millis() + 30000;
    }
    Publish message;
    while (xQueueReceive(outgoing, &message, 0) == pdTRUE) {
      if (mqttLink && mqtt) esp_mqtt_client_publish(mqtt, message.telemetry ? telemetryTopic : ackTopic, message.text, 0, 0, false);
    }
    vTaskDelay(pdMS_TO_TICKS(20));
  }
}

static void telemetry() {
  if (millis() - lastTelemetry < TELEMETRY_MS || (!bleAuthenticated && !mqttLink)) return;
  lastTelemetry = millis();
  JsonDocument doc; doc["type"] = "telemetry";
  doc["moisture"] = 100.0f - analogRead(PIN_MOISTURE) * (100.0f / 4095);
  doc["nutrient"] = analogRead(PIN_TDS) * (5000.0f / 4095);
  doc["tds"] = analogRead(PIN_TDS) * (10000.0f / 4095);
  doc["ph"] = analogRead(PIN_PH) * (14.0f / 4095);
  doc["nitrogen"] = analogRead(PIN_NITROGEN) * (2000.0f / 4095);

  Serial.printf("Telemetry: moisture=%.1f%%, nutrient=%.1f ppm, tds=%.1f ppm, ph=%.2f, nitrogen=%.1f ppm\n",
                doc["moisture"].as<float>(), doc["nutrient"].as<float>(), doc["tds"].as<float>(),
                doc["ph"].as<float>(), doc["nitrogen"].as<float>());
  // GPIO25/26 are ADC2 and cannot be sampled while Wi-Fi is running on classic ESP32.
  // Omit these fields rather than publish fabricated zeros. See hardware notes.
  if (WiFi.getMode() == WIFI_OFF) {
    doc["phosphorus"] = analogRead(PIN_PHOSPHORUS) * (2000.0f / 4095);
    doc["potassium"] = analogRead(PIN_POTASSIUM) * (2000.0f / 4095);
  }
  String text; serializeJson(doc, text); notifyBle(text);
  if (mqttLink) { Publish message = {}; message.telemetry = true; strlcpy(message.text, text.c_str(), sizeof(message.text)); xQueueSend(outgoing, &message, 0); }
}
void setup() {
  Serial.begin(115200); pinMode(PIN_SETUP, INPUT_PULLUP);
  pumpLock = xSemaphoreCreateMutex(); statusLock = xSemaphoreCreateMutex();
  if (!pumpLock || !statusLock) { pinMode(PIN_WATER, OUTPUT); pinMode(PIN_NUTRIENT, OUTPUT); digitalWrite(PIN_WATER, RELAY_OFF); digitalWrite(PIN_NUTRIENT, RELAY_OFF); for (;;) delay(1000); }
  water.begin(); nutrient.begin();
  incoming = xQueueCreate(6, sizeof(Frame)); outgoing = xQueueCreate(8, sizeof(Publish));
  configurations = xQueueCreate(1, sizeof(Config)); progress = xQueueCreate(8, sizeof(Progress));
  pumpEvents = xQueueCreate(8, sizeof(PumpEvent));
  if (!incoming || !outgoing || !configurations || !progress || !pumpEvents || !prefs.begin("tunas", false)) { Serial.println("Storage/queue initialization failed. Relays remain off."); for (;;) delay(1000); }
  Serial.println("[PUMP] water OFF; nutrient OFF (startup)");
  if (xTaskCreatePinnedToCore(safetyTask, "pump-safety", 2048, nullptr, 3, nullptr, 1) != pdPASS) { Serial.println("Safety task failed. Relays remain off."); for (;;) delay(1000); }
  analogReadResolution(12); analogSetAttenuation(ADC_11db);
  char suffix[13]; snprintf(suffix, sizeof(suffix), "%012llx", (unsigned long long)ESP.getEfuseMac()); deviceId = String("tunas-") + suffix;
  snprintf(telemetryTopic, sizeof(telemetryTopic), "tunas/%s/telemetry", deviceId.c_str());
  snprintf(commandTopic, sizeof(commandTopic), "tunas/%s/cmd", deviceId.c_str());
  snprintf(ackTopic, sizeof(ackTopic), "tunas/%s/ack", deviceId.c_str());
  snprintf(statusTopic, sizeof(statusTopic), "tunas/%s/status", deviceId.c_str());
  snprintf(clientId, sizeof(clientId), "tunas-pot-%s", deviceId.c_str());
  Config stored = {}; setupOpen = !loadConfig("active", stored); setupStarted = millis();
  stage = setupOpen ? "setup" : "ble_only";
  BLEDevice::init(deviceId.c_str());
  BLEDevice::setEncryptionLevel(ESP_BLE_SEC_ENCRYPT_NO_MITM);
  BLEDevice::setSecurityCallbacks(new Security());
  BLESecurity* security = new BLESecurity();
  // Just Works: encrypted pairing without PIN, but no MITM protection during pairing.
  security->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_BOND); security->setCapability(ESP_IO_CAP_NONE);
  security->setKeySize(16);
  uint8_t strictAuth = ESP_BLE_ONLY_ACCEPT_SPECIFIED_AUTH_ENABLE;
  esp_ble_gap_set_security_param(ESP_BLE_SM_ONLY_ACCEPT_SPECIFIED_SEC_AUTH, &strictAuth, sizeof(strictAuth));
  security->setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);
  security->setRespEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);
  bleServer = BLEDevice::createServer(); bleServer->setCallbacks(new ServerEvents());
  BLEService* service = bleServer->createService(SERVICE_UUID);
  rxCharacteristic = service->createCharacteristic(RX_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  BLE2902* cccd = new BLE2902();
  cccd->setAccessPermissions(ESP_GATT_PERM_READ_ENCRYPTED | ESP_GATT_PERM_WRITE_ENCRYPTED); rxCharacteristic->addDescriptor(cccd);
  BLECharacteristic* tx = service->createCharacteristic(TX_UUID, BLECharacteristic::PROPERTY_WRITE);
  tx->setAccessPermissions(ESP_GATT_PERM_WRITE_ENCRYPTED); tx->setCallbacks(new CommandEvents());
  identityCharacteristic = service->createCharacteristic(ID_UUID, BLECharacteristic::PROPERTY_READ);
  identityCharacteristic->setAccessPermissions(ESP_GATT_PERM_READ_ENCRYPTED); identityCharacteristic->setCallbacks(new IdentityEvents());
  statusSnapshot = statusJson(); identityCharacteristic->setValue(statusSnapshot.c_str()); service->start();
  BLEAdvertising* advertising = BLEDevice::getAdvertising(); advertising->addServiceUUID(SERVICE_UUID); advertising->setScanResponse(true); advertising->start();
  WiFi.persistent(false); WiFi.setAutoReconnect(false); WiFi.mode(WIFI_STA); // Credentials belong only in our transactional NVS blobs.
  if (xTaskCreatePinnedToCore(networkTask, "network", 12288, nullptr, 1, nullptr, 0) != pdPASS) { stage = "ble_only"; Serial.println("Network task failed; Bluetooth remains available."); }
}
void loop() {
  PumpEvent pumpEvent;
  while (xQueueReceive(pumpEvents, &pumpEvent, 0) == pdTRUE) {
    Serial.printf("[PUMP] %s GPIO%d %s source=%s %s=%lu ms\n",
        pumpEvent.pin == PIN_WATER ? "water" : "nutrient", pumpEvent.pin,
        pumpEvent.running ? "ON" : "OFF", pumpEvent.fromBle ? "BLE" : "MQTT",
        pumpEvent.running ? "duration" : "elapsed", (unsigned long)pumpEvent.milliseconds);
  }
  uint32_t now = millis();
  if (digitalRead(PIN_SETUP) == LOW) {
    if (!pressedAt) pressedAt = now;
    if (!buttonHandled && now - pressedAt >= 5000) {
      buttonHandled = true; setupOpen = true; setupStarted = now; stopPumps();
      Serial.printf("LOCAL SETUP — node=%s, BLE pairing without PIN\n", deviceId.c_str());
      stage = "setup"; sendStatus();
    }
  } else { pressedAt = 0; buttonHandled = false; }
  if (setupOpen && now - setupStarted >= SETUP_MS) { setupOpen = false; stage = "setup_closed"; sendStatus(); }
  if (bleConnected && !bleAuthenticated && now - connectedAt >= 90000) bleServer->disconnect(bleConnectionId);
  Progress event;
  while (xQueueReceive(progress, &event, 0) == pdTRUE) {
    stage = event.stage; resultId = event.id;
    if (event.closeSetup) setupOpen = false;
    sendStatus();
  }
  Frame frame;
  if (xQueueReceive(incoming, &frame, 0) == pdTRUE) handleFrame(frame);
  telemetry();
  if (millis() - lastStatus >= 3000) sendStatus();
  delay(5);
}
