package com.example.tunas.iot;

/** Command object as JSON. Action and id are already constrained. */
final class CommandJson {
    private CommandJson() { }

    static String encode(String id, String action, int value) {
        if (id == null || id.isEmpty() || id.length() > 64 || !id.matches("[0-9a-fA-F\\-]+")) {
            throw new IllegalArgumentException("Missing command id");
        }
        if (!CommandPolicy.isValid(action, value)) throw new IllegalArgumentException("Invalid command");
        return "{\"type\":\"command\",\"id\":\"" + id + "\",\"action\":\"" + action + "\",\"value\":" + value + "}";
    }
}
