package com.example.tunas.iot;

/** Shared gate for BLE and MQTT actuator commands. Offline, stale and busy all fail closed. */
public final class ActuatorGuard {
    public static final long FRESH_WINDOW_MILLIS = 30_000;

    public enum Block { NONE, OFFLINE, STALE, BUSY }

    private ActuatorGuard() { }

    public static Block assess(boolean roomAssigned, boolean linkUp, boolean hasReading, long ageMillis, boolean pending) {
        if (!roomAssigned || !linkUp) return Block.OFFLINE;
        if (!hasReading || ageMillis < 0 || ageMillis >= FRESH_WINDOW_MILLIS) return Block.STALE;
        if (pending) return Block.BUSY;
        return Block.NONE;
    }
}
