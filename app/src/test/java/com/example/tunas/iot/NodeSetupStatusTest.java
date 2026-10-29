package com.example.tunas.iot;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class NodeSetupStatusTest {
    private Map<String, Object> frame() {
        Map<String, Object> f = new HashMap<>();
        f.put("type", "setup"); f.put("deviceId", "tunas-123"); f.put("id", "transaction");
        f.put("stage", "ready"); f.put("setupOpen", false); f.put("mqttReady", true); return f;
    }
    @Test public void onlyCommittedMatchingTransactionCanPromoteSettings() {
        NodeSetupStatus status = NodeSetupStatus.decode(frame());
        assertTrue(status.confirms("transaction", "tunas-123"));
        assertFalse(status.confirms("old-transaction", "tunas-123"));
        assertFalse(status.confirms("transaction", "different-node"));
        Map<String, Object> f = frame(); f.put("stage", "mqtt_connecting");
        assertFalse(NodeSetupStatus.decode(f).confirms("transaction", "tunas-123"));
        f.put("stage", "ready"); f.put("mqttReady", false);
        assertFalse(NodeSetupStatus.decode(f).confirms("transaction", "tunas-123"));
        f.put("mqttReady", true); f.put("id", "");
        assertFalse(NodeSetupStatus.decode(f).confirms("", "tunas-123"));
    }
    @Test public void rejectsMalformedIdentityFlagsAndUnknownStages() {
        for (Object[] bad : new Object[][]{{"deviceId", "node/#"}, {"deviceId", ""}, {"id", null},
                {"id", "x".repeat(65)}, {"stage", "fake-success"}, {"setupOpen", "true"},
                {"mqttReady", 1}, {"type", "telemetry"}}) {
            Map<String, Object> f = frame(); f.put((String) bad[0], bad[1]);
            assertThrows(IllegalArgumentException.class, () -> NodeSetupStatus.decode(f));
        }
    }
    @Test public void failedNetworkSetupIsNotFailureOfBleAuthentication() {
        Map<String, Object> f = frame(); f.put("stage", "wifi_failed"); f.put("mqttReady", false); f.put("setupOpen", true);
        NodeSetupStatus status = NodeSetupStatus.decode(f);
        assertTrue(status.setupOpen); assertFalse(status.mqttReady); assertEquals("tunas-123", status.deviceId);
    }
}
