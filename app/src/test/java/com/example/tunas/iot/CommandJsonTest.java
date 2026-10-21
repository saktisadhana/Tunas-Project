package com.example.tunas.iot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class CommandJsonTest {
    @Test public void encodesOnlyABoundedCommand() {
        assertEquals("{\"type\":\"command\",\"id\":\"abc-1\",\"action\":\"water\",\"value\":5}",
                CommandJson.encode("abc-1", "water", 5));
        assertEquals("{\"type\":\"command\",\"id\":\"abc\",\"action\":\"nutrition\",\"value\":30}",
                CommandJson.encode("abc", "nutrition", 30));
        assertEquals("{\"type\":\"command\",\"id\":\"abc\",\"action\":\"target_moisture\",\"value\":0}",
                CommandJson.encode("abc", "target_moisture", 0));
        assertThrows(IllegalArgumentException.class, () -> CommandJson.encode(null, "water", 5));
        assertThrows(IllegalArgumentException.class, () -> CommandJson.encode("not json \"id", "water", 5));
        assertThrows(IllegalArgumentException.class, () -> CommandJson.encode("abc", "pump", 5));
        assertThrows(IllegalArgumentException.class, () -> CommandJson.encode("abc", "water", 31));
    }
}
