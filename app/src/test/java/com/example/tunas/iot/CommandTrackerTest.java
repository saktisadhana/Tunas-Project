package com.example.tunas.iot;

import org.junit.Test;
import static org.junit.Assert.*;

public class CommandTrackerTest {
    @Test public void missingCorrelationIdCannotCreateAnUntrackedCommand() {
        CommandTracker tracker = new CommandTracker();
        for (String id : new String[]{null, ""}) {
            assertThrows(IllegalArgumentException.class,
                    () -> tracker.begin(true, id, "water", 2, 100));
        }
        assertFalse(tracker.isPending());
    }

    @Test public void timeoutAndDisconnectClearPendingWithoutReplay() {
        CommandTracker tracker = new CommandTracker();
        tracker.begin(true, "a", "water", 2, 100);
        assertNull(tracker.expire(10099));
        assertFalse(tracker.expire(10100).success);
        assertNull(tracker.expire(20000));
        assertFalse(tracker.isPending());
        tracker.begin(true, "b", "nutrition", 2, 20001);
        assertFalse(tracker.cancel("Disconnected; outcome unknown").success);
        assertNull(tracker.cancel("again"));
        tracker.begin(true, "c", "target_moisture", 60, 20002);
        assertNull(tracker.acknowledge("b", true, 20003));
        assertTrue(tracker.isPending());
        assertTrue(tracker.acknowledge("c", true, 20004).success);
    }

    @Test public void anAckAtDeadlineIsFailureNotSuccess() {
        CommandTracker tracker = new CommandTracker();
        tracker.begin(true, "a", "water", 2, 100);
        CommandTracker.Result result = tracker.acknowledge("a", true, 10100);
        assertFalse(result.success);
        assertFalse(tracker.isPending());
        assertNull(tracker.acknowledge("a", true, 10101));
    }

    @Test public void oneCommandRequiresMatchingExplicitAckAndDuplicateAckIsIgnored() {
        CommandTracker tracker = new CommandTracker();
        tracker.begin(true, "a", "water", 2, 100);
        assertTrue(tracker.isPending());
        assertThrows(IllegalStateException.class,
                () -> tracker.begin(true, "b", "nutrition", 2, 101));
        assertNull(tracker.acknowledge("other", true, 102));
        assertTrue(tracker.isPending());
        CommandTracker.Result result = tracker.acknowledge("a", true, 103);
        assertTrue(result.success);
        assertFalse(tracker.isPending());
        assertNull(tracker.acknowledge("a", true, 104));
        tracker.begin(true, "b", "nutrition", 2, 105);
        assertFalse(tracker.acknowledge("b", false, 106).success);
    }

    @Test public void rejectsOfflineOrInvalidCommandsWithoutPendingWork() {
        CommandTracker tracker = new CommandTracker();
        assertThrows(IllegalStateException.class,
                () -> tracker.begin(false, "a", "water", 2, 100));
        assertFalse(tracker.isPending());
        assertThrows(IllegalArgumentException.class,
                () -> tracker.begin(true, "a", "water", 31, 100));
        assertFalse(tracker.isPending());
    }
}
