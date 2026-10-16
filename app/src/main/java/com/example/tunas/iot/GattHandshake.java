package com.example.tunas.iot;

/** Successful ordered callbacks only; timers may fail setup, never complete it. */
final class GattHandshake {
    private enum Stage { IDLE, LINK, SERVICES, CCCD, READY }
    private Stage stage = Stage.IDLE;

    void begin() { stage = Stage.LINK; }
    void reset() { stage = Stage.IDLE; }
    boolean isReady() { return stage == Stage.READY; }
    boolean linkConnected() { return advance(Stage.LINK, Stage.SERVICES); }
    boolean servicesResolved() { return advance(Stage.SERVICES, Stage.CCCD); }
    boolean subscriptionConfirmed() { return advance(Stage.CCCD, Stage.READY); }

    private boolean advance(Stage expected, Stage next) {
        if (stage != expected) return false;
        stage = next;
        return true;
    }
}
