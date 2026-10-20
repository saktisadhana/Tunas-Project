package com.example.tunas.iot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ActuatorGuardTest {
    @Test public void offlineStaleAndBusyNeverLookReady() {
        assertEquals(ActuatorGuard.Block.OFFLINE, ActuatorGuard.assess(false, true, true, 0, false));
        assertEquals(ActuatorGuard.Block.OFFLINE, ActuatorGuard.assess(true, false, true, 0, false));
        assertEquals(ActuatorGuard.Block.STALE, ActuatorGuard.assess(true, true, false, 0, false));
        assertEquals(ActuatorGuard.Block.STALE, ActuatorGuard.assess(true, true, true, -1, false));
        assertEquals(ActuatorGuard.Block.STALE, ActuatorGuard.assess(true, true, true, ActuatorGuard.FRESH_WINDOW_MILLIS, false));
        assertEquals(ActuatorGuard.Block.BUSY, ActuatorGuard.assess(true, true, true, ActuatorGuard.FRESH_WINDOW_MILLIS - 1, true));
        assertEquals(ActuatorGuard.Block.NONE, ActuatorGuard.assess(true, true, true, 0, false));
        assertEquals(ActuatorGuard.Block.NONE, ActuatorGuard.assess(true, true, true, 29_999, false));
    }
}
