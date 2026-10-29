package com.example.tunas.iot;

import java.util.LinkedHashMap;
import java.util.Map;

/** Keys for the saved broker record. */
final class MqttSettingsFields {
    static final String HOST = "host";
    static final String PORT = "port";
    static final String USERNAME = "username";
    static final String PASSWORD = "password";
    static final String DEVICE_ID = "device_id";
    static final String[] KEYS = {HOST, PORT, USERNAME, PASSWORD, DEVICE_ID};

    private MqttSettingsFields() { }

    static Map<String, String> write(MqttBrokerConfig config) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(HOST, config.host);
        fields.put(PORT, Integer.toString(config.port));
        fields.put(USERNAME, config.username);
        fields.put(PASSWORD, config.password);
        fields.put(DEVICE_ID, config.deviceId);
        return fields;
    }

    /** Null if the saved record is missing or invalid. */
    static MqttBrokerConfig read(Map<String, String> fields) {
        if (fields == null) return null;
        String host = fields.get(HOST);
        if (host == null || host.isEmpty()) return null;
        String portText = fields.get(PORT);
        int port;
        try {
            if (portText == null) return null;
            port = Integer.parseInt(portText);
        } catch (RuntimeException invalid) {
            return null;
        }
        try {
            return MqttBrokerConfig.create(host, port, fields.get(USERNAME), fields.get(PASSWORD), fields.get(DEVICE_ID));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
