package com.example.tunas.iot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class MqttTopicsTest {
    @Test public void topicsAreOneLevelPerPot() {
        assertEquals("pot-1", MqttTopics.requireDeviceId(" pot-1 "));
        assertEquals("tunas/pot-1/telemetry", MqttTopics.telemetry("pot-1"));
        assertEquals("tunas/pot-1/cmd", MqttTopics.command("pot-1"));
        assertEquals("tunas/pot-1/ack", MqttTopics.acknowledgement("pot-1"));
        assertEquals("tunas/pot-1/status", MqttTopics.status("pot-1"));
        assertEquals("{\"type\":\"status\",\"online\":false}", MqttTopics.OFFLINE_STATUS);
        assertEquals("{\"type\":\"status\",\"online\":true}", MqttTopics.ONLINE_STATUS);
    }

    @Test public void deviceIdsRejectTopicWildcardsAndSeparators() {
        for (String bad : new String[]{null, "", " ", "a/b", "a+b", "#", "pot#1", "room/pot", "$share", "has space"}) {
            assertThrows(IllegalArgumentException.class, () -> MqttTopics.telemetry(bad));
        }
        assertThrows(IllegalArgumentException.class, () -> MqttTopics.requireDeviceId(new String(new char[65]).replace('\0', 'a')));
        assertEquals(64, MqttTopics.requireDeviceId(new String(new char[64]).replace('\0', 'a')).length());
    }

    @Test public void statusPayloadsAreNotCredentials() {
        assertFalse(MqttTopics.OFFLINE_STATUS.contains("password"));
        assertFalse(MqttTopics.ONLINE_STATUS.contains("://"));
    }
}
