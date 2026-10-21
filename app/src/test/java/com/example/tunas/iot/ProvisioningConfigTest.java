package com.example.tunas.iot;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProvisioningConfigTest {
    private MqttBrokerConfig broker() { return MqttBrokerConfig.create("broker.example.com", 8883, "user", "secret", "tunas-123"); }

    @Test public void validatesWifiByteLengthsWithoutTrimmingSsidOrPassword() {
        ProvisioningConfig cfg = new ProvisioningConfig(" network ", " pass123 ", "digicert", broker());
        assertEquals(" network ", cfg.ssid); assertEquals(" pass123 ", cfg.wifiPassword);
        new ProvisioningConfig("open", "", "isrg", broker());
        new ProvisioningConfig("é".repeat(16), "x".repeat(63), "digicert", broker());
        assertThrows(IllegalArgumentException.class, () -> new ProvisioningConfig("é".repeat(17), "", "isrg", broker()));
        assertThrows(IllegalArgumentException.class, () -> new ProvisioningConfig("", "", "isrg", broker()));
        assertThrows(IllegalArgumentException.class, () -> new ProvisioningConfig("a\0b", "", "isrg", broker()));
        assertThrows(IllegalArgumentException.class, () -> new ProvisioningConfig("net", "short", "isrg", broker()));
        assertThrows(IllegalArgumentException.class, () -> new ProvisioningConfig("net", "x".repeat(64), "isrg", broker()));
    }
    @Test public void onlyAllowsCompiledTrustedCasAndBoundedBrokerFields() {
        assertThrows(IllegalArgumentException.class, () -> new ProvisioningConfig("net", "", "insecure", broker()));
        MqttBrokerConfig longUser = MqttBrokerConfig.create("broker.example.com", 8883, "é".repeat(65), "", "node");
        assertThrows(IllegalArgumentException.class, () -> new ProvisioningConfig("net", "", "isrg", longUser));
        assertThrows(IllegalArgumentException.class, () -> new ProvisioningConfig("net", "", "isrg", null));
    }
    @Test public void transactionsHaveDifferentIdsAndSecretsAreRedacted() {
        ProvisioningConfig a = new ProvisioningConfig("network", "wifi-secret", "digicert", broker());
        ProvisioningConfig b = new ProvisioningConfig("network", "wifi-secret", "digicert", broker());
        assertNotEquals(a.requestId, b.requestId);
        assertFalse(a.toString().contains("wifi-secret")); assertFalse(a.toString().contains("user"));
    }
}
