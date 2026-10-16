package com.example.tunas.iot;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class IncomingMessageTest {
    @Test public void ackRequiresExactBooleanAndAnIdWithoutCoercion() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("type", "ack");
        fields.put("id", "a");
        fields.put("ok", true);
        IncomingMessage ack = IncomingMessage.decode(fields, 100);
        assertEquals("a", ack.commandId);
        assertTrue(ack.success);
        assertNull(ack.telemetry);
        fields.put("ok", false);
        assertFalse(IncomingMessage.decode(fields, 100).success);
        for (Object bad : new Object[]{null, "true", 1}) {
            fields.put("ok", bad);
            assertThrows(IllegalArgumentException.class, () -> IncomingMessage.decode(fields, 100));
        }
        fields.put("ok", true);
        for (Object bad : new Object[]{null, "", 5, new String(new char[65])}) {
            fields.put("id", bad);
            assertThrows(IllegalArgumentException.class, () -> IncomingMessage.decode(fields, 100));
        }
        fields.put("type", "command");
        assertThrows(IllegalArgumentException.class, () -> IncomingMessage.decode(fields, 100));
    }

    @Test public void onlyTypedTelemetryWithAtLeastOneMeasurementCanRefreshData() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("type", "telemetry");
        assertThrows(IllegalArgumentException.class, () -> IncomingMessage.decode(fields, 100));
        fields.put("moisture", "50");
        assertThrows(IllegalArgumentException.class, () -> IncomingMessage.decode(fields, 100));
        fields.put("moisture", 50);
        fields.put("health", null);
        IncomingMessage message = IncomingMessage.decode(fields, 100);
        assertEquals(50.0, message.telemetry.moisture, 0.0);
        assertNull(message.telemetry.health);
        assertEquals(100L, message.telemetry.timestampMillis);
        assertNull(message.commandId);
        fields.put("humidity", 101);
        assertThrows(IllegalArgumentException.class, () -> IncomingMessage.decode(fields, 100));
    }
}
