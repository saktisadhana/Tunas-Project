package com.example.tunas.iot;

import java.util.regex.Pattern;

/** Topic names: tunas/<deviceId>/telemetry|cmd|ack|status. */
public final class MqttTopics {
    public static final String OFFLINE_STATUS = "{\"type\":\"status\",\"online\":false}";
    public static final String ONLINE_STATUS = "{\"type\":\"status\",\"online\":true}";
    private static final Pattern DEVICE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_\\-]{0,63}");

    private MqttTopics() { }

    public static String requireDeviceId(String raw) {
        String id = raw == null ? "" : raw.trim();
        if (!DEVICE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Enter a device ID using letters, numbers, hyphens or underscores (1–64 characters).");
        }
        return id;
    }

    public static String telemetry(String deviceId) { return topic(deviceId, "telemetry"); }
    public static String command(String deviceId) { return topic(deviceId, "cmd"); }
    public static String acknowledgement(String deviceId) { return topic(deviceId, "ack"); }
    public static String status(String deviceId) { return topic(deviceId, "status"); }

    private static String topic(String deviceId, String leaf) {
        return "tunas/" + requireDeviceId(deviceId) + "/" + leaf;
    }
}
