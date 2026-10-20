// Reference ESP32 sketch for the Tunas MQTT contract in docs/IOT_PROTOCOL.md.
// Not commissioned hardware. Analog maps are uncalibrated placeholders.
// Relays obey a 30s hard cap and turn off when MQTT drops.
// A moisture target is stored and does not start a pump by itself.
//
// Libraries: WiFi + WiFiClientSecure (ESP32 core), PubSubClient (Nick O'Leary).
// Fill in secrets.example.h locally. Do not commit real passwords.

#include <stdlib.h>
#include <string.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>

#include "secrets.example.h"
#include "isrg_root_x1.h"

static const int PIN_MOISTURE = 34;
static const int PIN_TDS = 35;
static const int PIN_PH = 32;
static const int PIN_NITROGEN = 33;
static const int PIN_PHOSPHORUS = 25;
static const int PIN_POTASSIUM = 26;
static const int PIN_WATER = 18;
static const int PIN_NUTRIENT = 19;
static const bool MOISTURE_INVERT = true;
static const unsigned long HARD_CAP_MS = 30000UL;
static const unsigned long TELEMETRY_MS = 5000UL;
static const int RELAY_ON = HIGH;
static const int RELAY_OFF = LOW;

static const char OFFLINE_STATUS[] = "{\"type\":\"status\",\"online\":false}";
static const char ONLINE_STATUS[] = "{\"type\":\"status\",\"online\":true}";

WiFiClientSecure wifiClient;
PubSubClient mqttClient(wifiClient);

char telemetryTopic[96];
char commandTopic[96];
char ackTopic[96];
char statusTopic[96];
char clientId[80];
char lastId[65];
bool lastOk = false;
bool haveLastId = false;
int moistureTarget = -1;
unsigned long lastTelemetry = 0;
unsigned long lastMqttAttempt = 0;
unsigned long lastWifiAttempt = 0;

struct Pump {
  int pin;
  bool running;
  unsigned long startedAt;
  unsigned long commandedMs;

  void begin() {
    pinMode(pin, OUTPUT);
    digitalWrite(pin, RELAY_OFF);
    running = false;
  }

  bool start(unsigned long now, long seconds) {
    if (seconds < 1 || seconds > 30) return false;
    commandedMs = (unsigned long)seconds * 1000UL;
    startedAt = now;
    running = true;
    digitalWrite(pin, RELAY_ON);
    return true;
  }

  void poll(unsigned long now, bool linkUp) {
    if (!running) return;
    unsigned long ran = now - startedAt;
    if (!linkUp || ran >= commandedMs || ran >= HARD_CAP_MS) {
      digitalWrite(pin, RELAY_OFF);
      running = false;
    }
  }
};

Pump waterPump = {PIN_WATER, false, 0, 0};
Pump nutrientPump = {PIN_NUTRIENT, false, 0, 0};

static bool configured() {
  return WIFI_SSID[0] != '\0' && MQTT_HOST[0] != '\0' && DEVICE_ID[0] != '\0' && MQTT_PORT > 0;
}

static bool deviceIdOk() {
  size_t n = strlen(DEVICE_ID);
  if (n < 1 || n > 64) return false;
  for (size_t i = 0; i < n; i++) {
    char c = DEVICE_ID[i];
    bool ok = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_' || c == '-';
    if (!ok || (i == 0 && (c == '_' || c == '-'))) return false;
  }
  return true;
}

static float clamp(float value, float lo, float hi) {
  if (value < lo) return lo;
  if (value > hi) return hi;
  return value;
}

static int readAdc(int pin) {
  int raw = analogRead(pin);
  if (raw < 0) return 0;
  if (raw > 4095) return 4095;
  return raw;
}

static bool extractString(const char* json, const char* key, char* out, size_t outLen) {
  char pattern[24];
  snprintf(pattern, sizeof(pattern), "\"%s\":\"", key);
  const char* found = strstr(json, pattern);
  if (!found) return false;
  found += strlen(pattern);
  size_t i = 0;
  while (found[i] != '\0' && found[i] != '"' && i + 1 < outLen) {
    if (found[i] == '\\') return false;
    out[i] = found[i];
    i++;
  }
  if (found[i] != '"') return false;
  out[i] = '\0';
  return i > 0 && i <= 64;
}

static bool extractInt(const char* json, const char* key, long* out) {
  char pattern[24];
  snprintf(pattern, sizeof(pattern), "\"%s\":", key);
  const char* found = strstr(json, pattern);
  if (!found) return false;
  found += strlen(pattern);
  while (*found == ' ') found++;
  char* end = nullptr;
  long value = strtol(found, &end, 10);
  if (end == found || value < 0 || value > 100000) return false;
  *out = value;
  return true;
}

static bool idOk(const char* id) {
  size_t n = strlen(id);
  if (n < 1 || n > 64) return false;
  for (size_t i = 0; i < n; i++) {
    char c = id[i];
    bool hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == '-';
    if (!hex) return false;
  }
  return true;
}

static void publishAck(const char* id, bool ok) {
  char body[128];
  snprintf(body, sizeof(body), "{\"type\":\"ack\",\"id\":\"%s\",\"ok\":%s}", id, ok ? "true" : "false");
  mqttClient.publish(ackTopic, body, false);
}

static void remember(const char* id, bool ok) {
  strncpy(lastId, id, sizeof(lastId) - 1);
  lastId[sizeof(lastId) - 1] = '\0';
  lastOk = ok;
  haveLastId = true;
}

static void handleCommand(const char* json) {
  char id[65];
  char action[24];
  long value = -1;
  if (!extractString(json, "id", id, sizeof(id)) || !idOk(id) || !extractString(json, "action", action, sizeof(action)) || !extractInt(json, "value", &value)) {
    return;
  }
  if (haveLastId && strcmp(lastId, id) == 0) {
    publishAck(id, lastOk);
    return;
  }
  bool pumpsBusy = waterPump.running || nutrientPump.running;
  bool ok = false;
  if (!pumpsBusy && strcmp(action, "water") == 0 && value >= 1 && value <= 30) {
    ok = waterPump.start(millis(), value);
  } else if (!pumpsBusy && strcmp(action, "nutrition") == 0 && value >= 1 && value <= 30) {
    ok = nutrientPump.start(millis(), value);
  } else if (strcmp(action, "target_moisture") == 0 && value >= 0 && value <= 100) {
    moistureTarget = (int)value;
    ok = true;
  }
  remember(id, ok);
  publishAck(id, ok);
}

static void onMessage(char* topic, byte* payload, unsigned int length) {
  if (topic == nullptr || strcmp(topic, commandTopic) != 0 || length == 0 || length > 255) return;
  char json[256];
  memcpy(json, payload, length);
  json[length] = '\0';
  handleCommand(json);
}

static void connectWifi() {
  if (WiFi.status() == WL_CONNECTED) return;
  unsigned long now = millis();
  if (lastWifiAttempt != 0 && now - lastWifiAttempt < 10000UL) return;
  lastWifiAttempt = now;
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.println("Wi-Fi connecting");
}

static void connectMqtt() {
  unsigned long now = millis();
  if (now - lastMqttAttempt < 5000UL) return;
  lastMqttAttempt = now;
  wifiClient.setCACert(ISRG_ROOT_X1);
  mqttClient.setServer(MQTT_HOST, MQTT_PORT);
  mqttClient.setCallback(onMessage);
  mqttClient.setBufferSize(512);
  bool up;
  if (MQTT_USER[0] == '\0') {
    up = mqttClient.connect(clientId, statusTopic, 1, true, OFFLINE_STATUS);
  } else {
    up = mqttClient.connect(clientId, MQTT_USER, MQTT_PASSWORD, statusTopic, 1, true, OFFLINE_STATUS);
  }
  if (!up) {
    Serial.println("MQTT connect failed");
    return;
  }
  mqttClient.publish(statusTopic, ONLINE_STATUS, true);
  mqttClient.subscribe(commandTopic);
  Serial.println("MQTT online");
}

static void publishTelemetry() {
  if (!mqttClient.connected() || millis() - lastTelemetry < TELEMETRY_MS) return;
  lastTelemetry = millis();
  float moisture = (readAdc(PIN_MOISTURE) / 4095.0f) * 100.0f;
  if (MOISTURE_INVERT) moisture = 100.0f - moisture;
  float nutrient = (readAdc(PIN_TDS) / 4095.0f) * 5000.0f;
  float tds = (readAdc(PIN_TDS) / 4095.0f) * 10000.0f;
  float ph = (readAdc(PIN_PH) / 4095.0f) * 14.0f;
  float nitrogen = (readAdc(PIN_NITROGEN) / 4095.0f) * 2000.0f;
  float phosphorus = (readAdc(PIN_PHOSPHORUS) / 4095.0f) * 2000.0f;
  float potassium = (readAdc(PIN_POTASSIUM) / 4095.0f) * 2000.0f;
  char body[320];
  snprintf(body, sizeof(body),
           "{\"type\":\"telemetry\",\"moisture\":%.1f,\"nutrient\":%.1f,\"ph\":%.2f,\"tds\":%.0f,\"nitrogen\":%.0f,\"phosphorus\":%.0f,\"potassium\":%.0f}",
           clamp(moisture, 0, 100), clamp(nutrient, 0, 5000), clamp(ph, 0, 14), clamp(tds, 0, 10000),
           clamp(nitrogen, 0, 2000), clamp(phosphorus, 0, 2000), clamp(potassium, 0, 2000));
  mqttClient.publish(telemetryTopic, body, false);
}

void setup() {
  Serial.begin(115200);
  analogReadResolution(12);
  analogSetAttenuation(ADC_11db);
  waterPump.begin();
  nutrientPump.begin();
  if (!configured() || !deviceIdOk()) {
    Serial.println("Fill in secrets.example.h before connecting. Relays stay off.");
    return;
  }
  snprintf(telemetryTopic, sizeof(telemetryTopic), "tunas/%s/telemetry", DEVICE_ID);
  snprintf(commandTopic, sizeof(commandTopic), "tunas/%s/cmd", DEVICE_ID);
  snprintf(ackTopic, sizeof(ackTopic), "tunas/%s/ack", DEVICE_ID);
  snprintf(statusTopic, sizeof(statusTopic), "tunas/%s/status", DEVICE_ID);
  snprintf(clientId, sizeof(clientId), "tunas-pot-%s", DEVICE_ID);
  Serial.println(telemetryTopic);
  connectWifi();
}

void loop() {
  bool link = mqttClient.connected();
  unsigned long now = millis();
  waterPump.poll(now, link);
  nutrientPump.poll(now, link);
  if (!configured() || !deviceIdOk()) return;
  if (WiFi.status() != WL_CONNECTED) {
    connectWifi();
    return;
  }
  if (!link) connectMqtt();
  else mqttClient.loop();
  publishTelemetry();
}
