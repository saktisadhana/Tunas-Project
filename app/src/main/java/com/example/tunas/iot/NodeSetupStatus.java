package com.example.tunas.iot;

import java.util.Arrays;
import java.util.Map;

/** Authenticated BLE identity and provisioning progress, never sensor data. */
public final class NodeSetupStatus {
    static final String[] KEYS = {"type", "deviceId", "id", "stage", "setupOpen", "mqttReady"};
    public final String deviceId, requestId, stage;
    public final boolean setupOpen, mqttReady;

    private NodeSetupStatus(String deviceId, String requestId, String stage, boolean open, boolean ready) {
        this.deviceId = deviceId; this.requestId = requestId; this.stage = stage;
        setupOpen = open; mqttReady = ready;
    }

    public boolean confirms(String pendingId, String pendingDeviceId) {
        return mqttReady && "ready".equals(stage) && !requestId.isEmpty()
                && requestId.equals(pendingId) && deviceId.equals(pendingDeviceId);
    }

    public static NodeSetupStatus decode(Map<String, ?> fields) {
        Object device = fields.get("deviceId"), id = fields.get("id"), stage = fields.get("stage");
        if (!"setup".equals(fields.get("type")) || !(device instanceof String)
                || !((String) device).matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
                || !(id instanceof String) || ((String) id).length() > 64
                || !(stage instanceof String) || !Arrays.asList("setup", "wifi_connecting", "time_sync",
                "tls_connecting", "mqtt_connecting", "ready", "ble_only", "wifi_failed", "time_failed",
                "tls_failed", "mqtt_failed", "storage_failed", "setup_closed", "invalid_config").contains(stage)
                || !(fields.get("setupOpen") instanceof Boolean) || !(fields.get("mqttReady") instanceof Boolean)) {
            throw new IllegalArgumentException("Invalid setup status");
        }
        return new NodeSetupStatus((String) device, (String) id, (String) stage,
                (Boolean) fields.get("setupOpen"), (Boolean) fields.get("mqttReady"));
    }
}
