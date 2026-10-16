package com.example.tunas.iot;

/** Fail-closed command bounds; physical actuators require a firmware-enforced stop. */
final class CommandPolicy {
    private CommandPolicy() { }

    static boolean isValid(String action, int value) {
        if ("target_moisture".equals(action)) return value >= 0 && value <= 100;
        return ("water".equals(action) || "nutrition".equals(action))
                && value >= 1 && value <= 30;
    }
}
