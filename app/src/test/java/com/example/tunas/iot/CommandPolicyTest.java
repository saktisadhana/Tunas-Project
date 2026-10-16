package com.example.tunas.iot;

import org.junit.Test;
import static org.junit.Assert.*;

public class CommandPolicyTest {
    @Test public void targetIsPercentageAndUnknownActionsFailClosed() {
        assertTrue(CommandPolicy.isValid("target_moisture", 0));
        assertTrue(CommandPolicy.isValid("target_moisture", 100));
        assertFalse(CommandPolicy.isValid("target_moisture", -1));
        assertFalse(CommandPolicy.isValid("target_moisture", 101));
        assertFalse(CommandPolicy.isValid("pump", 10));
        assertFalse(CommandPolicy.isValid("Water", 10));
        assertFalse(CommandPolicy.isValid(null, 10));
        assertFalse(CommandPolicy.isValid("water\n", 10));
    }

    @Test public void physicalActionsRequireOneToThirtySeconds() {
        for (String action : new String[]{"water", "nutrition"}) {
            assertTrue(CommandPolicy.isValid(action, 1));
            assertTrue(CommandPolicy.isValid(action, 30));
            assertFalse(CommandPolicy.isValid(action, 0));
            assertFalse(CommandPolicy.isValid(action, 31));
            assertFalse(CommandPolicy.isValid(action, -1));
            assertFalse(CommandPolicy.isValid(action, Integer.MAX_VALUE));
        }
    }
}
