package com.example.tunas.iot;

import java.util.Map;

/** {"type":"status","online":true|false} from the pot. */
final class StatusFrame {
    final boolean online;

    private StatusFrame(boolean online) { this.online = online; }

    static StatusFrame decode(Map<String, ?> fields) {
        if (!"status".equals(fields.get("type"))) throw new IllegalArgumentException("Unknown frame type");
        Object online = fields.get("online");
        if (!(online instanceof Boolean)) throw new IllegalArgumentException("Invalid status");
        return new StatusFrame((Boolean) online);
    }
}
