package com.example.tunas.iot;

/** Separate freshness per transport prevents old MQTT readings authorizing a BLE command. */
public final class TransportReadings {
    public enum Route { MQTT, BLE, OFFLINE }
    private Telemetry ble, mqtt;
    private long bleAt, mqttAt;
    public void ble(Telemetry value, long now) { ble = value; bleAt = now; }
    public void mqtt(Telemetry value, long now) { mqtt = value; mqttAt = now; }
    public void clearBle() { ble = null; }
    public void clearMqtt() { mqtt = null; }
    private boolean fresh(Telemetry value, long at, long now) { return value != null && now >= at && now - at < 30_000; }
    public Route route(boolean bleUp, boolean mqttUp, long now) {
        if (mqttUp && fresh(mqtt, mqttAt, now)) return Route.MQTT;
        if (bleUp && fresh(ble, bleAt, now)) return Route.BLE;
        return mqttUp ? Route.MQTT : bleUp ? Route.BLE : Route.OFFLINE;
    }
    public Telemetry value(Route route) { return route == Route.MQTT ? mqtt : route == Route.BLE ? ble : null; }
    public long receivedAt(Route route) { return route == Route.MQTT ? mqttAt : bleAt; }
}
