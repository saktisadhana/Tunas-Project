package com.example.tunas.iot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MqttPairStatusTest {
    @Test public void formLineShowsConnectProgressAndLeavesOtherStatesBlank() {
        assertEquals("", MqttPairStatus.line(null, "pot1"));
        assertEquals("", MqttPairStatus.line("No sensor connected", "pot1"));
        assertEquals("", MqttPairStatus.line("Scanning for nearby BLE devices", "pot1"));
        assertEquals("Connecting…", MqttPairStatus.line("Connecting to the MQTT broker…", "pot1"));
        assertEquals("Connected to broker, waiting for pot pot1.",
                MqttPairStatus.line("Broker connected. Waiting for the pot.", " pot1 "));
        assertEquals("Connected to broker, waiting for the pot.",
                MqttPairStatus.line("Broker connected. Waiting for the pot.", " "));
        assertEquals("Could not connect to the MQTT broker.",
                MqttPairStatus.line("Could not connect to the MQTT broker.", "pot1"));
        assertEquals("The pot is offline.", MqttPairStatus.line("The pot is offline.", "pot1"));
    }
}
