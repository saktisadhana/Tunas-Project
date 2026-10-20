package com.example.tunas.iot;

import java.util.Locale;
import java.util.regex.Pattern;

/** Broker settings from the user. No default host. */
public final class MqttBrokerConfig {
    public static final int DEFAULT_PORT = 8883;
    private static final Pattern HOST = Pattern.compile(
            "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*");

    public final String host;
    public final int port;
    public final String username;
    public final String password;
    public final String deviceId;

    private MqttBrokerConfig(String host, int port, String username, String password, String deviceId) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.deviceId = deviceId;
    }

    public static MqttBrokerConfig create(String host, int port, String username, String password, String deviceId) {
        String cleanHost = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (cleanHost.length() > 253 || !HOST.matcher(cleanHost).matches()) {
            throw new IllegalArgumentException("Enter the broker host name. Tunas doesn't come with a built-in broker.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Enter a broker port from 1 to 65535. TLS usually uses 8883.");
        }
        String user = username == null ? "" : username.trim();
        if (user.length() > 256 || hasControl(user)) {
            throw new IllegalArgumentException("Enter a broker username up to 256 characters.");
        }
        String pass = password == null ? "" : password;
        if (pass.length() > 256 || hasControl(pass)) {
            throw new IllegalArgumentException("Enter a broker password up to 256 characters.");
        }
        return new MqttBrokerConfig(cleanHost, port, user, pass, MqttTopics.requireDeviceId(deviceId));
    }

    /** Host, port and device id. Password left out. */
    public String redacted() {
        return host + ":" + port + " · " + deviceId;
    }

    /** ssl://host:port */
    public String serverUri() {
        return "ssl://" + host + ":" + port;
    }

    @Override public String toString() {
        return "MqttBrokerConfig{" + redacted() + ", password=redacted}";
    }

    private static boolean hasControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
