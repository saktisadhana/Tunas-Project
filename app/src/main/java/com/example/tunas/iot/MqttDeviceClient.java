package com.example.tunas.iot;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

/** Paho client for the pot broker. Callbacks are delivered on the main thread. */
public final class MqttDeviceClient implements AutoCloseable {
    public interface Listener {
        void onStatus(String message);
        void onConnected(boolean connected);
        void onTelemetry(Telemetry telemetry);
        void onCommandResult(boolean success, String message);
    }

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MqttSession session = new MqttSession(new MqttSession.Listener() {
        @Override public void onStatus(String message) { listener.onStatus(message); }
        @Override public void onConnected(boolean connected) { listener.onConnected(connected); }
        @Override public void onTelemetry(Telemetry telemetry) { listener.onTelemetry(telemetry); }
        @Override public void onCommandResult(boolean success, String message) {
            if (!session.isCommandPending()) cancelTimeout();
            listener.onCommandResult(success, message);
        }
        @Override public void onCloseTransport(String reason) { disconnectOnMain(reason); }
    });
    private MqttAsyncClient client;
    private int generation;
    private boolean closed;
    private Runnable commandTimeout;

    public MqttDeviceClient(Context context, Listener listener) {
        Objects.requireNonNull(context, "context");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public void connect(MqttBrokerConfig config) { onMain(() -> connectOnMain(config)); }
    public void disconnect() {
        onMain(() -> {
            if (!closed) disconnectOnMain("Disconnected. Previous readings are stale.");
        });
    }
    @Override public void close() {
        onMain(() -> {
            if (closed) return;
            closed = true;
            disconnectOnMain("Client closed. Previous readings are stale.");
        });
    }
    public boolean isConnected() { return session.isDeviceOnline(); }
    public void command(String action, int value) { onMain(() -> commandOnMain(action, value)); }

    private void onMain(Runnable action) {
        if (Looper.myLooper() == main.getLooper()) action.run();
        else main.post(action);
    }

    private void connectOnMain(MqttBrokerConfig config) {
        if (closed) {
            listener.onStatus("Client closed");
            return;
        }
        Objects.requireNonNull(config, "config");
        generation++;
        cancelTimeout();
        releaseSocket();
        session.brokerDisconnected("Disconnected. Previous readings are stale.");
        final int gen = generation;
        final MqttSession.Plan plan = session.start(config);
        listener.onStatus("Connecting to the MQTT broker…");
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(false);
            options.setCleanSession(true);
            options.setConnectionTimeout(20);
            options.setKeepAliveInterval(30);
            options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
            options.setSocketFactory(SSLContext.getDefault().getSocketFactory());
            options.setSSLHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier());
            if (!config.username.isEmpty()) options.setUserName(config.username);
            if (!config.password.isEmpty()) options.setPassword(config.password.toCharArray());
            MqttAsyncClient next = new MqttAsyncClient(config.serverUri(),
                    "tunas" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                    new MemoryPersistence());
            next.setCallback(new MqttCallback() {
                @Override public void connectionLost(Throwable cause) {
                    main.post(() -> {
                        if (gen != generation) return;
                        disconnectOnMain("Broker disconnected. Previous readings are stale.");
                    });
                }
                @Override public void messageArrived(String topic, MqttMessage message) {
                    byte[] payload = message == null || message.getPayload() == null
                            ? new byte[0] : Arrays.copyOf(message.getPayload(), message.getPayload().length);
                    main.post(() -> deliver(gen, topic, payload));
                }
                @Override public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) { }
            });
            client = next;
            next.connect(options, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken token) { main.post(() -> subscribe(gen, next, plan)); }
                @Override public void onFailure(IMqttToken token, Throwable exception) {
                    main.post(() -> {
                        if (gen != generation) return;
                        session.brokerDisconnected("Could not connect to the MQTT broker.");
                        releaseSocket();
                    });
                }
            });
        } catch (Exception failure) {
            session.brokerDisconnected("Could not connect to the MQTT broker.");
            releaseSocket();
        }
    }

    private void subscribe(int gen, MqttAsyncClient socket, MqttSession.Plan plan) {
        if (gen != generation || socket != client) return;
        try {
            socket.subscribe(
                    new String[]{plan.telemetryTopic, plan.acknowledgementTopic, plan.statusTopic},
                    new int[]{0, 0, 0}, null, new IMqttActionListener() {
                        @Override public void onSuccess(IMqttToken token) {
                            main.post(() -> {
                                if (gen != generation) return;
                                session.brokerConnected();
                            });
                        }
                        @Override public void onFailure(IMqttToken token, Throwable exception) {
                            main.post(() -> {
                                if (gen != generation) return;
                                disconnectOnMain("Could not subscribe to the pot.");
                            });
                        }
                    });
        } catch (Exception failure) {
            disconnectOnMain("Could not subscribe to the pot.");
        }
    }

    private void deliver(int gen, String topic, byte[] payload) {
        if (gen != generation) return;
        try {
            session.message(topic, FlatJson.fields(new String(payload, StandardCharsets.UTF_8), FlatJson.KEYS),
                    System.currentTimeMillis(), SystemClock.elapsedRealtime());
        } catch (JSONException invalid) {
            listener.onStatus("Discarded malformed MQTT frame");
        }
    }

    private void commandOnMain(String action, int value) {
        if (closed) {
            listener.onCommandResult(false, "Device is offline");
            return;
        }
        MqttSession.OutgoingCommand outgoing = session.command(action, value, SystemClock.elapsedRealtime());
        if (outgoing == null) return;
        if (client == null) {
            session.commandPublishFailed();
            return;
        }
        final int gen = generation;
        cancelTimeout();
        commandTimeout = () -> session.expire(SystemClock.elapsedRealtime());
        main.postDelayed(commandTimeout, CommandTracker.TIMEOUT_MILLIS);
        try {
            client.publish(outgoing.topic, outgoing.payload.getBytes(StandardCharsets.UTF_8),
                    outgoing.qos, outgoing.retained, null, new IMqttActionListener() {
                        @Override public void onSuccess(IMqttToken token) {
                            main.post(() -> {
                                if (gen != generation) return;
                                session.commandPublishFinished(SystemClock.elapsedRealtime());
                            });
                        }
                        @Override public void onFailure(IMqttToken token, Throwable exception) {
                            main.post(() -> {
                                if (gen != generation) return;
                                session.commandPublishFailed();
                            });
                        }
                    });
        } catch (Exception failure) {
            session.commandPublishFailed();
        }
    }

    private void disconnectOnMain(String reason) {
        generation++;
        cancelTimeout();
        session.brokerDisconnected(reason);
        releaseSocket();
    }

    private void releaseSocket() {
        MqttAsyncClient previous = client;
        client = null;
        if (previous == null) return;
        try {
            previous.disconnectForcibly(0, 0, false);
        } catch (Exception ignored) {
            // Fall through to close().
        }
        try {
            previous.close();
        } catch (Exception ignored) {
            // Already gone.
        }
    }

    private void cancelTimeout() {
        if (commandTimeout != null) main.removeCallbacks(commandTimeout);
        commandTimeout = null;
    }
}
