package com.example.tunas.iot;

/** Main-thread confined, one pending command, no queue and no retry. */
final class CommandTracker {
    static final long TIMEOUT_MILLIS = 10_000;
    private String pendingId;
    private long startedAt;
    void begin(boolean connected, String id, String action, int value, long now) {
        if (!connected) throw new IllegalStateException("Device is offline");
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("Missing command id");
        if (!CommandPolicy.isValid(action, value)) throw new IllegalArgumentException("Invalid command");
        if (isPending()) throw new IllegalStateException("Another command is awaiting acknowledgement");
        pendingId = id;
        startedAt = now;
    }

    boolean isPending() { return pendingId != null; }

    Result acknowledge(String id, boolean success, long now) {
        if (!isPending() || !pendingId.equals(id)) return null;
        if (now - startedAt >= TIMEOUT_MILLIS) {
            pendingId = null;
            return new Result(false, "Acknowledgement timed out; outcome unknown, do not retry automatically");
        }
        pendingId = null;
        return new Result(success, success ? "Device acknowledged command" : "Device rejected command");
    }

    Result expire(long now) {
        if (!isPending() || now - startedAt < TIMEOUT_MILLIS) return null;
        return cancel("Acknowledgement timed out; outcome unknown, do not retry automatically");
    }

    Result cancel(String reason) {
        if (!isPending()) return null;
        pendingId = null;
        return new Result(false, reason);
    }

    static final class Result {
        final boolean success;
        final String message;
        Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
