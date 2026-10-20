package com.example.tunas.iot;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MqttSessionTest {
    @Test public void brokerSocketIsNotPotPresenceAndCommandsFailClosed() {
        Fixture fixture = new Fixture();
        MqttSession.Plan plan = fixture.session.start(config());
        assertEquals("tunas/pot-1/telemetry", plan.telemetryTopic);
        assertEquals("tunas/pot-1/cmd", plan.commandTopic);
        assertEquals("tunas/pot-1/ack", plan.acknowledgementTopic);
        assertEquals("tunas/pot-1/status", plan.statusTopic);
        assertEquals(MqttTopics.OFFLINE_STATUS, plan.deviceOfflinePayload);
        fixture.session.brokerConnected();
        assertFalse(fixture.session.isDeviceOnline());
        assertNull(fixture.session.command("water", 5, 1_000));
        assertEquals("Device is offline", fixture.results.get(fixture.results.size() - 1));
        assertTrue(fixture.payloads.isEmpty());
        fixture.session.message(plan.statusTopic, status(false), 10, 1_000);
        assertFalse(fixture.session.isDeviceOnline());
        assertTrue(fixture.connected.isEmpty());
    }

    @Test public void telemetryAndMatchingAckFollowTheSharedPolicy() {
        Fixture fixture = openOnline();
        Map<String, Object> reading = new HashMap<>();
        reading.put("type", "telemetry");
        reading.put("moisture", 41);
        reading.put("ph", 6.4);
        reading.put("tds", 320);
        reading.put("nitrogen", 80);
        fixture.session.message(fixture.session.plan().telemetryTopic, reading, 50, 2_000);
        assertEquals(1, fixture.telemetry.size());
        assertEquals(41.0, fixture.telemetry.get(0).moisture, 0.0);
        assertEquals(6.4, fixture.telemetry.get(0).ph, 0.0);
        assertNull(fixture.telemetry.get(0).potassium);
        MqttSession.OutgoingCommand water = fixture.send("water", 5, 2_100);
        assertEquals("tunas/pot-1/cmd", water.topic);
        assertEquals(0, water.qos);
        assertFalse(water.retained);
        assertTrue(water.payload.contains("\"action\":\"water\""));
        assertTrue(water.payload.contains("\"value\":5"));
        assertNull(fixture.session.command("nutrition", 5, 2_200));
        assertEquals("Another command is awaiting acknowledgement", fixture.results.get(fixture.results.size() - 1));
        String id = water.payload.split("\"id\":\"")[1].split("\"")[0];
        fixture.session.message(fixture.session.plan().acknowledgementTopic, ack(id, true), 60, 2_300);
        assertFalse(fixture.successes.contains(Boolean.TRUE));
        fixture.session.commandPublishFinished(2_400);
        assertEquals(Boolean.TRUE, fixture.successes.get(fixture.successes.size() - 1));
        assertEquals("Device acknowledged command", fixture.results.get(fixture.results.size() - 1));
        assertFalse(fixture.session.isCommandPending());
    }

    @Test public void rejectionTimeoutAndLostPotDoNotRetry() {
        Fixture fixture = openOnline();
        MqttSession.OutgoingCommand nutrition = fixture.send("nutrition", 8, 5_000);
        fixture.session.commandPublishFinished(5_100);
        String id = nutrition.payload.split("\"id\":\"")[1].split("\"")[0];
        fixture.session.message(fixture.session.plan().acknowledgementTopic, ack(id, false), 70, 5_200);
        assertEquals(Boolean.FALSE, fixture.successes.get(fixture.successes.size() - 1));
        assertEquals("Device rejected command", fixture.results.get(fixture.results.size() - 1));

        MqttSession.OutgoingCommand again = fixture.send("target_moisture", 40, 6_000);
        fixture.session.commandPublishFinished(6_100);
        fixture.session.expire(6_000 + CommandTracker.TIMEOUT_MILLIS);
        assertFalse(fixture.session.isDeviceOnline());
        assertFalse(fixture.session.isCommandPending());
        assertEquals(1, fixture.closes.size());
        assertTrue(fixture.results.get(fixture.results.size() - 1).contains("outcome unknown"));
        assertNull(fixture.send("water", 5, 20_000));
        assertEquals(0, countPayloads(fixture, "water"));
        assertEquals(1, countPayloads(fixture, "target_moisture"));
        assertTrue(again.payload.contains("\"action\":\"target_moisture\""));
    }

    @Test public void malformedFramesAndWrongTopicsDoNotMoveThePot() {
        Fixture fixture = openOnline();
        int readings = fixture.telemetry.size();
        Map<String, Object> bad = new HashMap<>();
        bad.put("type", "telemetry");
        bad.put("moisture", "wet");
        fixture.session.message(fixture.session.plan().telemetryTopic, bad, 80, 3_000);
        fixture.session.message(fixture.session.plan().commandTopic, telemetry(12), 80, 3_000);
        fixture.session.message(fixture.session.plan().statusTopic, telemetry(12), 80, 3_000);
        assertEquals(readings, fixture.telemetry.size());
        assertTrue(fixture.session.isDeviceOnline());
        assertTrue(fixture.statuses.get(fixture.statuses.size() - 1).contains("Discarded"));
    }

    @Test public void offlineWillCancelsAnOpenCommandWithoutASecondPublish() {
        Fixture fixture = openOnline();
        fixture.send("water", 4, 4_000);
        fixture.session.commandPublishFinished(4_100);
        fixture.session.message(fixture.session.plan().statusTopic, status(false), 90, 4_200);
        assertFalse(fixture.session.isDeviceOnline());
        assertFalse(fixture.connected.get(fixture.connected.size() - 1));
        assertFalse(fixture.session.isCommandPending());
        assertTrue(fixture.results.get(fixture.results.size() - 1).contains("outcome unknown"));
        assertEquals(1, countPayloads(fixture, "water"));
        fixture.session.brokerDisconnected("Broker disconnected. Previous readings are stale.");
        assertFalse(fixture.session.isDeviceOnline());
    }

    @Test public void publishFailureReportsNotSentAndDropsTheLink() {
        Fixture fixture = openOnline();
        assertFalse(fixture.send("water", 5, 7_000).retained);
        fixture.session.commandPublishFailed();
        assertFalse(fixture.session.isCommandPending());
        assertFalse(fixture.session.isDeviceOnline());
        assertEquals("Command was not sent", fixture.results.get(fixture.results.size() - 1));
        assertEquals(1, fixture.closes.size());
        assertNull(fixture.send("water", 5, 8_000));
    }

    private static int countPayloads(Fixture fixture, String action) {
        int count = 0;
        for (MqttSession.OutgoingCommand command : fixture.payloads) {
            if (command.payload.contains("\"action\":\"" + action + "\"")) count++;
        }
        return count;
    }

    private static Fixture openOnline() {
        Fixture fixture = new Fixture();
        fixture.session.start(config());
        fixture.session.brokerConnected();
        fixture.session.message(fixture.session.plan().statusTopic, status(true), 20, 1_500);
        assertTrue(fixture.session.isDeviceOnline());
        assertEquals(Boolean.TRUE, fixture.connected.get(0));
        assertEquals("MQTT", fixture.statuses.get(fixture.statuses.size() - 1));
        return fixture;
    }

    private static MqttBrokerConfig config() {
        return MqttBrokerConfig.create("broker.example.com", 8883, "user", "secret", "pot-1");
    }

    private static Map<String, Object> status(boolean online) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("type", "status");
        fields.put("online", online);
        return fields;
    }

    private static Map<String, Object> ack(String id, boolean ok) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("type", "ack");
        fields.put("id", id);
        fields.put("ok", ok);
        return fields;
    }

    private static Map<String, Object> telemetry(int moisture) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("type", "telemetry");
        fields.put("moisture", moisture);
        return fields;
    }

    private static final class Fixture implements MqttSession.Listener {
        final List<String> statuses = new ArrayList<>();
        final List<Boolean> connected = new ArrayList<>();
        final List<Telemetry> telemetry = new ArrayList<>();
        final List<Boolean> successes = new ArrayList<>();
        final List<String> results = new ArrayList<>();
        final List<String> closes = new ArrayList<>();
        final List<MqttSession.OutgoingCommand> payloads = new ArrayList<>();
        final MqttSession session = new MqttSession(this, () -> "abc-1");

        MqttSession.OutgoingCommand send(String action, int value, long now) {
            MqttSession.OutgoingCommand command = session.command(action, value, now);
            if (command != null) payloads.add(command);
            return command;
        }

        @Override public void onStatus(String message) { statuses.add(message); }
        @Override public void onConnected(boolean isConnected) { connected.add(isConnected); }
        @Override public void onTelemetry(Telemetry value) { telemetry.add(value); }
        @Override public void onCommandResult(boolean success, String message) {
            successes.add(success);
            results.add(message);
        }
        @Override public void onCloseTransport(String reason) { closes.add(reason); }
    }
}
