package com.example.tunas.iot;

import java.util.Map;

/** Typed schema validation shared by the Android JSON adapter and JVM tests. */
final class IncomingMessage {
    final Telemetry telemetry;
    final String commandId;
    final boolean success;

    private IncomingMessage(Telemetry telemetry, String commandId, boolean success) {
        this.telemetry = telemetry;
        this.commandId = commandId;
        this.success = success;
    }

    static IncomingMessage decode(Map<String, ?> fields, long receivedAt) {
        if ("ack".equals(fields.get("type"))) {
            Object id = fields.get("id");
            Object ok = fields.get("ok");
            if (!(id instanceof String) || ((String) id).isEmpty() || ((String) id).length() > 64
                    || !(ok instanceof Boolean)) throw new IllegalArgumentException("Invalid acknowledgement");
            return new IncomingMessage(null, (String) id, (Boolean) ok);
        }
        if (!"telemetry".equals(fields.get("type"))) throw new IllegalArgumentException("Unknown frame type");
        Telemetry t = new Telemetry(number(fields, "health"), number(fields, "moisture"),
                number(fields, "nutrient"), number(fields, "sunlight"),
                number(fields, "temperature"), number(fields, "humidity"), receivedAt);
        if (t.health == null && t.moisture == null && t.nutrient == null && t.sunlight == null
                && t.temperature == null && t.humidity == null) {
            throw new IllegalArgumentException("Empty telemetry");
        }
        return new IncomingMessage(t, null, false);
    }

    private static Double number(Map<String, ?> fields, String name) {
        Object value = fields.get(name);
        if (value == null) return null;
        if (!(value instanceof Number)) throw new IllegalArgumentException("Non-numeric " + name);
        return ((Number) value).doubleValue();
    }
}
