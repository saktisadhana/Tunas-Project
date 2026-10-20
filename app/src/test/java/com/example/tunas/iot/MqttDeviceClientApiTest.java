package com.example.tunas.iot;

import org.junit.Test;

import java.lang.reflect.Modifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Method signatures only. Does not connect. */
public class MqttDeviceClientApiTest {
    @Test public void exposesTheSameCommandBoundaryAsBle() throws Exception {
        Class<?> client = Class.forName("com.example.tunas.iot.MqttDeviceClient");
        assertTrue(Modifier.isPublic(client.getModifiers()));
        assertTrue(Modifier.isFinal(client.getModifiers()));
        Class<?> listener = Class.forName("com.example.tunas.iot.MqttDeviceClient$Listener");
        client.getConstructor(Class.forName("android.content.Context"), listener);
        client.getMethod("connect", MqttBrokerConfig.class);
        client.getMethod("disconnect");
        client.getMethod("close");
        client.getMethod("command", String.class, int.class);
        assertEquals(boolean.class, client.getMethod("isConnected").getReturnType());
        listener.getMethod("onStatus", String.class);
        listener.getMethod("onConnected", boolean.class);
        listener.getMethod("onTelemetry", Telemetry.class);
        listener.getMethod("onCommandResult", boolean.class, String.class);
        assertTrue(MqttBrokerConfig.create("broker.example.com", 8883, "", "", "pot1").serverUri().startsWith("ssl://"));
    }
}
