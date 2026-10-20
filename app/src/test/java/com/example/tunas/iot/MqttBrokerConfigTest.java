package com.example.tunas.iot;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MqttBrokerConfigTest {
    private static final String SECRET = "s3cret-value-should-not-leak";

    @Test public void requiresAUserSuppliedTlsBroker() {
        assertEquals(8883, MqttBrokerConfig.DEFAULT_PORT);
        MqttBrokerConfig config = MqttBrokerConfig.create(" Broker.Example.COM ", 8883, " gardener ", SECRET, " pot_1 ");
        assertEquals("broker.example.com", config.host);
        assertEquals(8883, config.port);
        assertEquals("gardener", config.username);
        assertEquals(SECRET, config.password);
        assertEquals("pot_1", config.deviceId);
        assertEquals("ssl://broker.example.com:8883", config.serverUri());
        assertTrue(config.serverUri().startsWith("ssl://"));
        assertFalse(config.serverUri().startsWith("tcp://"));
        assertFalse(config.redacted().contains(SECRET));
        assertFalse(config.toString().contains(SECRET));
        assertTrue(config.toString().contains("password=redacted"));
    }

    @Test public void rejectsMissingHostCleartextTricksAndBadPorts() {
        assertThrows(IllegalArgumentException.class, () -> MqttBrokerConfig.create(null, 8883, "", "", "pot1"));
        assertThrows(IllegalArgumentException.class, () -> MqttBrokerConfig.create("   ", 8883, "", "", "pot1"));
        assertThrows(IllegalArgumentException.class, () -> MqttBrokerConfig.create("tcp://broker.example.com", 8883, "", "", "pot1"));
        assertThrows(IllegalArgumentException.class, () -> MqttBrokerConfig.create("broker.example.com/path", 8883, "", "", "pot1"));
        assertThrows(IllegalArgumentException.class, () -> MqttBrokerConfig.create("user@broker.example.com", 8883, "", "", "pot1"));
        assertThrows(IllegalArgumentException.class, () -> MqttBrokerConfig.create("localhost", 0, "", "", "pot1"));
        assertThrows(IllegalArgumentException.class, () -> MqttBrokerConfig.create("localhost", 65536, "", "", "pot1"));
        assertThrows(IllegalArgumentException.class, () -> MqttBrokerConfig.create("ok.example", 8883, "user", "bad\npassword", "pot1"));
        MqttBrokerConfig local = MqttBrokerConfig.create("127.0.0.1", 8884, "", "", "pot1");
        assertEquals("ssl://127.0.0.1:8884", local.serverUri());
    }

    @Test public void staticTextDoesNotEmbedABrokerOrSecret() throws Exception {
        for (Class<?> type : new Class<?>[]{MqttBrokerConfig.class, MqttTopics.class, MqttSettingsFields.class}) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != String.class || !Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                String value = (String) field.get(null);
                if (value == null) continue;
                String lower = value.toLowerCase(Locale.ROOT);
                assertFalse(field.getName(), lower.contains("hivemq") || lower.contains("example.com") || lower.contains(SECRET));
            }
        }
    }

    @Test public void settingsRoundTripKeepsThePasswordOutOfTheSummary() {
        MqttBrokerConfig config = MqttBrokerConfig.create("mqtt.example.com", 8883, "ada", SECRET, "kitchen");
        Map<String, String> stored = MqttSettingsFields.write(config);
        assertEquals(SECRET, stored.get(MqttSettingsFields.PASSWORD));
        MqttBrokerConfig loaded = MqttSettingsFields.read(stored);
        assertEquals(config.host, loaded.host);
        assertEquals(config.port, loaded.port);
        assertEquals(SECRET, loaded.password);
        assertFalse(loaded.redacted().contains(SECRET));
        assertNull(MqttSettingsFields.read(null));
        stored.put(MqttSettingsFields.PORT, "nope");
        assertNull(MqttSettingsFields.read(stored));
        stored.put(MqttSettingsFields.HOST, "");
        assertNull(MqttSettingsFields.read(stored));
    }
}
