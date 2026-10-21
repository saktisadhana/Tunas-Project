package com.example.tunas.iot;

/** Line under the MQTT form while the pot is not online yet. */
public final class MqttPairStatus {
    private MqttPairStatus() {}

    public static String line(String connectionStatus, String deviceId) {
        String status = connectionStatus == null ? "" : connectionStatus;
        if ("Connecting to the MQTT broker…".equals(status)) return "Connecting…";
        if ("Broker connected. Waiting for the pot.".equals(status)) {
            String id = deviceId == null ? "" : deviceId.trim();
            if (id.isEmpty()) return "Connected to broker, waiting for the pot.";
            return "Connected to broker, waiting for pot " + id + ".";
        }
        if (isMqttNotice(status)) return status;
        return "";
    }

    private static boolean isMqttNotice(String status) {
        return status.startsWith("Could not connect")
                || status.startsWith("Could not subscribe")
                || status.startsWith("Broker disconnected")
                || status.startsWith("The pot is offline")
                || status.startsWith("MQTT publish")
                || status.startsWith("Command acknowledgement")
                || status.startsWith("Discarded malformed MQTT")
                || status.startsWith("Disconnected.");
    }
}
