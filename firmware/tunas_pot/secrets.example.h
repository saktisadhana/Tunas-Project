#pragma once

// Copy this file to secrets.h in the same folder and fill it in.
// The sketch includes secrets.h and will not compile until that copy exists.
// secrets.h is gitignored. Don't commit Wi-Fi or broker passwords.

#include "root_ca.h"

static const char WIFI_SSID[] = "";
static const char WIFI_PASSWORD[] = "";
static const char MQTT_HOST[] = "";  // e.g. xxxx.emqxsl.com
static const int MQTT_PORT = 8883;
static const char MQTT_USER[] = "";
static const char MQTT_PASSWORD[] = "";
static const char DEVICE_ID[] = "";  // same id you enter in the app

// EMQX Cloud Serverless. For HiveMQ Cloud, use ISRG_ROOT_X1 instead.
static const char *MQTT_CA_CERT = DIGICERT_GLOBAL_ROOT_CA;
