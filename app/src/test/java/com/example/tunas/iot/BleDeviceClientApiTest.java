package com.example.tunas.iot;

import org.junit.Test;
import java.lang.reflect.Modifier;
import static org.junit.Assert.*;

/** API shape only: the Android stack and radio require on-device verification. */
public class BleDeviceClientApiTest {
    @Test public void exposesRequiredNativeUiBoundary() throws Exception {
        Class<?> client;
        try {
            client = Class.forName("com.example.tunas.iot.BleDeviceClient");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Native BLE boundary is not implemented", missing);
        }
        assertTrue(Modifier.isPublic(client.getModifiers()));
        assertTrue(Modifier.isFinal(client.getModifiers()));
        Class<?> listener = Class.forName("com.example.tunas.iot.BleDeviceClient$Listener");
        client.getConstructor(Class.forName("android.content.Context"), listener);
        for (String method : new String[]{"startScan", "stopScan", "disconnect", "close"}) {
            assertEquals(void.class, client.getMethod(method).getReturnType());
        }
        client.getMethod("connect", String.class);
        client.getMethod("command", String.class, int.class);
        assertEquals(boolean.class, client.getMethod("isConnected").getReturnType());
        listener.getMethod("onStatus", String.class);
        listener.getMethod("onDeviceFound", String.class, String.class, int.class);
        listener.getMethod("onConnected", boolean.class);
        listener.getMethod("onTelemetry", Telemetry.class);
        listener.getMethod("onCommandResult", boolean.class, String.class);
    }
}
