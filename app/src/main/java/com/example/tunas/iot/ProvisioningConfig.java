package com.example.tunas.iot;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Validation is independent of Android; secrets must never be logged. */
public final class ProvisioningConfig {
    public final String requestId, ssid, wifiPassword, ca;
    public final MqttBrokerConfig broker;

    public ProvisioningConfig(String ssid, String wifiPassword, String ca, MqttBrokerConfig broker) {
        if (ssid == null || ssid.isEmpty() || bytes(ssid) > 32 || ssid.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Wi-Fi SSID must contain 1–32 UTF-8 bytes.");
        if (wifiPassword == null || wifiPassword.indexOf('\0') >= 0 || bytes(wifiPassword) > 63
                || (!wifiPassword.isEmpty() && bytes(wifiPassword) < 8))
            throw new IllegalArgumentException("Wi-Fi password must be empty (open network) or 8–63 bytes.");
        if (!"digicert".equals(ca) && !"isrg".equals(ca)) throw new IllegalArgumentException("Choose a trusted CA.");
        if (broker == null || bytes(broker.host) > 253 || bytes(broker.username) > 128 || bytes(broker.password) > 256)
            throw new IllegalArgumentException("Broker settings exceed the node limits.");
        this.ssid = ssid; this.wifiPassword = wifiPassword; this.ca = ca; this.broker = broker;
        requestId = UUID.randomUUID().toString();
    }

    private static int bytes(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }

    @Override public String toString() { return "ProvisioningConfig[credentials redacted]"; }
}
