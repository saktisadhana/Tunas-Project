package com.example.tunas.iot;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Main-thread confined MQTT session. Connecting to a broker does not make the pot online.
 * Commands are QoS 0 and not retained, so the broker cannot replay a pump action.
 */
final class MqttSession {
    interface Listener {
        void onStatus(String message);
        void onConnected(boolean connected);
        void onTelemetry(Telemetry telemetry);
        void onCommandResult(boolean success, String message);
        void onCloseTransport(String reason);
    }

    /** Topics the phone uses. The pot's last will is documentation for firmware, not a phone publish. */
    static final class Plan {
        final String telemetryTopic;
        final String commandTopic;
        final String acknowledgementTopic;
        final String statusTopic;
        final String deviceOfflinePayload;
        final String deviceOnlinePayload;

        Plan(String deviceId) {
            telemetryTopic = MqttTopics.telemetry(deviceId);
            commandTopic = MqttTopics.command(deviceId);
            acknowledgementTopic = MqttTopics.acknowledgement(deviceId);
            statusTopic = MqttTopics.status(deviceId);
            deviceOfflinePayload = MqttTopics.OFFLINE_STATUS;
            deviceOnlinePayload = MqttTopics.ONLINE_STATUS;
        }
    }

    static final class OutgoingCommand {
        final String topic;
        final String payload;
        final int qos;
        final boolean retained;

        OutgoingCommand(String topic, String payload) {
            this.topic = topic;
            this.payload = payload;
            this.qos = 0;
            this.retained = false;
        }
    }

    private final Listener listener;
    private final Supplier<String> ids;
    private final CommandTracker commands = new CommandTracker();
    private Plan plan;
    private boolean accepting;
    private boolean deviceOnline;
    private boolean sawPresence;
    private boolean publishOutstanding;
    private IncomingMessage deferredAck;

    MqttSession(Listener listener) {
        this(listener, () -> UUID.randomUUID().toString());
    }

    MqttSession(Listener listener, Supplier<String> ids) {
        this.listener = listener;
        this.ids = ids;
    }

    Plan start(MqttBrokerConfig config) {
        notifyLeftover(commands.cancel("Connection closed; command outcome unknown, do not retry automatically"));
        accepting = true;
        deviceOnline = false;
        sawPresence = false;
        publishOutstanding = false;
        deferredAck = null;
        plan = new Plan(config.deviceId);
        return plan;
    }

    boolean isDeviceOnline() { return accepting && deviceOnline; }

    boolean isCommandPending() { return commands.isPending(); }

    Plan plan() { return plan; }

    void brokerConnected() {
        if (!accepting || plan == null || deviceOnline) return;
        listener.onStatus("Broker connected. Waiting for the pot.");
    }

    void brokerDisconnected(String reason) {
        boolean wasOnline = deviceOnline;
        boolean wasAccepting = accepting;
        accepting = false;
        deviceOnline = false;
        publishOutstanding = false;
        deferredAck = null;
        CommandTracker.Result result = commands.cancel("Connection closed; command outcome unknown, do not retry automatically");
        if (wasOnline) listener.onConnected(false);
        if (wasAccepting) listener.onStatus(reason);
        notifyLeftover(result);
    }

    void message(String topic, Map<String, ?> fields, long receivedAt, long now) {
        if (!accepting || plan == null || topic == null || fields == null) return;
        try {
            if (topic.equals(plan.statusTopic)) {
                applyPresence(StatusFrame.decode(fields).online);
                return;
            }
            if (topic.equals(plan.telemetryTopic)) {
                IncomingMessage incoming = IncomingMessage.decode(fields, receivedAt);
                if (incoming.telemetry == null) {
                    listener.onStatus("Discarded malformed MQTT frame");
                    return;
                }
                applyPresence(true);
                if (accepting && deviceOnline) listener.onTelemetry(incoming.telemetry);
                return;
            }
            if (topic.equals(plan.acknowledgementTopic)) {
                IncomingMessage incoming = IncomingMessage.decode(fields, receivedAt);
                if (incoming.commandId == null) {
                    listener.onStatus("Discarded malformed MQTT frame");
                    return;
                }
                if (!deviceOnline) return;
                handleAck(incoming, now);
            }
        } catch (IllegalArgumentException invalid) {
            listener.onStatus("Discarded malformed MQTT frame");
        }
    }

    OutgoingCommand command(String action, int value, long now) {
        if (plan == null || !accepting) {
            listener.onCommandResult(false, "Device is offline");
            return null;
        }
        String id = ids.get();
        try {
            commands.begin(deviceOnline, id, action, value, now);
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            listener.onCommandResult(false, rejected.getMessage());
            return null;
        }
        try {
            publishOutstanding = true;
            deferredAck = null;
            return new OutgoingCommand(plan.commandTopic, CommandJson.encode(id, action, value));
        } catch (IllegalArgumentException encoding) {
            publishOutstanding = false;
            notifyLeftover(commands.cancel("Could not encode command; command not sent"));
            return null;
        }
    }

    void commandPublishFinished(long now) {
        if (!accepting) return;
        publishOutstanding = false;
        IncomingMessage held = deferredAck;
        deferredAck = null;
        if (held != null) handleAck(held, now);
    }

    void commandPublishFailed() {
        if (!accepting) return;
        publishOutstanding = false;
        deferredAck = null;
        CommandTracker.Result result = commands.cancel("Command was not sent");
        failAndClose(result, "MQTT publish failed. The command was not sent.");
    }

    void expire(long now) {
        if (!accepting) return;
        CommandTracker.Result expired = commands.expire(now);
        if (expired != null) failAndClose(expired, "Command acknowledgement timed out; device outcome unknown");
    }

    private void handleAck(IncomingMessage ack, long now) {
        CommandTracker.Result expired = commands.expire(now);
        if (expired != null) {
            failAndClose(expired, "Command acknowledgement timed out; device outcome unknown");
            return;
        }
        if (publishOutstanding) {
            deferredAck = ack;
            return;
        }
        notifyLeftover(commands.acknowledge(ack.commandId, ack.success, now));
    }

    private void applyPresence(boolean online) {
        if (!accepting) return;
        if (online) {
            sawPresence = true;
            if (!deviceOnline) {
                deviceOnline = true;
                listener.onConnected(true);
                listener.onStatus("MQTT");
            }
            return;
        }
        if (deviceOnline) {
            deviceOnline = false;
            sawPresence = true;
            CommandTracker.Result result = commands.cancel("Connection closed; command outcome unknown, do not retry automatically");
            listener.onConnected(false);
            listener.onStatus("The pot is offline. Previous readings are stale.");
            notifyLeftover(result);
            return;
        }
        if (!sawPresence) {
            sawPresence = true;
            listener.onStatus("The pot is offline.");
        }
    }

    private void failAndClose(CommandTracker.Result result, String reason) {
        boolean wasOnline = deviceOnline;
        accepting = false;
        deviceOnline = false;
        publishOutstanding = false;
        deferredAck = null;
        if (wasOnline) listener.onConnected(false);
        listener.onStatus(reason);
        notifyLeftover(result);
        listener.onCloseTransport(reason);
    }

    private void notifyLeftover(CommandTracker.Result result) {
        if (result != null) listener.onCommandResult(result.success, result.message);
    }
}
