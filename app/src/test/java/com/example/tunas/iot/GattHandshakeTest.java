package com.example.tunas.iot;

import org.junit.Test;
import static org.junit.Assert.*;

public class GattHandshakeTest {
    @Test public void connectedMeansSuccessfulServicesAndCccdNotJustRadioLink() {
        GattHandshake setup = new GattHandshake();
        assertFalse(setup.isReady());
        assertFalse(setup.subscriptionConfirmed());
        setup.begin();
        assertFalse(setup.isReady());
        assertTrue(setup.linkConnected());
        assertFalse(setup.isReady());
        assertFalse(setup.subscriptionConfirmed());
        assertTrue(setup.servicesResolved());
        assertFalse(setup.isReady());
        assertTrue(setup.subscriptionConfirmed());
        assertTrue(setup.isReady());
        setup.reset();
        assertFalse(setup.isReady());
        assertFalse(setup.servicesResolved());
        assertFalse(setup.subscriptionConfirmed());
        setup.begin();
        assertFalse(setup.subscriptionConfirmed());
    }
}
