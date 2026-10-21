package com.example.tunas.iot;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.example.tunas.iot.TransportReadings.Route.*;

public class TransportReadingsTest {
    private Telemetry reading(double moisture) { return new Telemetry(null, moisture, null, null, null, null, 0); }
    @Test public void prefersFreshMqttThenFallsBackToFreshBle() {
        TransportReadings r = new TransportReadings();
        r.mqtt(reading(10), 0); r.ble(reading(20), 20000);
        assertEquals(MQTT, r.route(true, true, 29999));
        assertEquals(BLE, r.route(true, true, 30000));
        assertEquals(20, r.value(r.route(true, true, 30000)).moisture, 0);
        r.mqtt(reading(30), 30001); assertEquals(MQTT, r.route(true, true, 30001));
    }
    @Test public void disconnectNeverRelabelsMqttDataAsBleData() {
        TransportReadings r = new TransportReadings(); r.mqtt(reading(10), 0);
        assertEquals(BLE, r.route(true, false, 1)); assertNull(r.value(BLE));
        r.ble(reading(20), 2); r.clearBle(); assertNull(r.value(BLE));
        r.clearMqtt(); assertNull(r.value(MQTT));
        assertEquals(OFFLINE, r.route(false, false, 3));
    }
    @Test public void staleAndDisconnectedBleCannotOverrideMqtt() {
        TransportReadings r = new TransportReadings(); r.ble(reading(20), 0);
        assertEquals(MQTT, r.route(true, true, 30000));
        assertEquals(MQTT, r.route(false, true, 1));
        assertEquals(BLE, r.route(true, true, 1));
    }
}
