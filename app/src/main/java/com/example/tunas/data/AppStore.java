package com.example.tunas.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Local-only plant care data, intended for single UI-thread use.
 * A profile is a convenience on this installation, not password or cloud authentication.
 * All state is saved as one versioned value in private app preferences.
 */
public final class AppStore {
    private static final String PREFERENCES_NAME = "tunas_local_v1";
    private static final String SNAPSHOT_KEY = "snapshot";
    private final SharedPreferences preferences;

    public AppStore(Context context) {
        this(privatePreferences(context));
    }

    // Package-private seam for JVM contract tests of the persistence adapter.
    AppStore(SharedPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    private static SharedPreferences privatePreferences(Context context) {
        Objects.requireNonNull(context, "context");
        Context application = context.getApplicationContext();
        return (application == null ? context : application)
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public String name() { return read().name(); }
    public String email() { return read().email(); }
    public boolean hasProfile() { return read().hasProfile(); }
    public boolean isSignedIn() { return read().isSignedIn(); }

    public void saveProfile(String name, String email) {
        change(garden -> { garden.saveProfile(name, email); return null; });
    }

    public boolean openProfile(String email) {
        Garden garden = read();
        if (!garden.openProfile(email)) return false;
        persist(garden);
        return true;
    }

    /** Clears only the session; saved profile, rooms, plants and settings remain. */
    public void signOut() {
        change(garden -> { garden.signOut(); return null; });
    }

    /** In-app reminder preference only; does not register or send remote push notifications. */
    public void setNotificationsEnabled(boolean enabled) {
        change(garden -> { garden.setNotificationsEnabled(enabled); return null; });
    }

    public boolean notificationsEnabled() { return read().notificationsEnabled(); }
    public List<Room> rooms() { return read().rooms(); }

    public Room addRoom(String name, String light) {
        return change(garden -> garden.addRoom(name, light));
    }

    /** Occupied rooms cannot be deleted. Move or delete their plants first. */
    public void deleteRoom(String id) {
        change(garden -> { garden.deleteRoom(id); return null; });
    }

    public List<Plant> plants() { return read().plants(); }

    /** An empty/null room ID means unassigned; blank nickname defaults to species. */
    public Plant addPlant(String species, String nickname, String roomId, int waterEveryDays) {
        return change(garden -> garden.addPlant(species, nickname, roomId, waterEveryDays,
                System.currentTimeMillis()));
    }

    /** Interval changes use the last care log, or initial scheduling time if never watered. */
    public void updatePlant(String id, String nickname, String roomId, int waterEveryDays) {
        change(garden -> { garden.updatePlant(id, nickname, roomId, waterEveryDays); return null; });
    }

    public void deletePlant(String id) {
        change(garden -> { garden.deletePlant(id); return null; });
    }

    /** Records manual care only. No watering hardware or network command is issued. */
    public void markWatered(String id) {
        change(garden -> { garden.markWatered(id, System.currentTimeMillis()); return null; });
    }

    public List<Plant> duePlants(long nowMillis) { return read().duePlants(nowMillis); }

    private Garden read() {
        try {
            return Garden.decode(preferences.getString(SNAPSHOT_KEY, null));
        } catch (ClassCastException malformedPreference) {
            return new Garden();
        }
    }

    private <T> T change(Function<Garden, T> mutation) {
        // Reload for each operation so sequential store instances cannot overwrite stale state.
        Garden next = read();
        T result = mutation.apply(next);
        persist(next);
        return result;
    }

    private void persist(Garden garden) {
        // One committed value prevents partially persisted room/plant relationships.
        if (!preferences.edit().putString(SNAPSHOT_KEY, garden.encode()).commit()) {
            throw new IllegalStateException("Could not save your garden on this device. Please try again.");
        }
    }

    public static final class Plant {
        public final String id;
        public final String species;
        public final String name;
        /** Empty string means no room assigned. */
        public final String roomId;
        public final int waterEveryDays;
        /** Zero until a manual watering has been logged. */
        public final long lastWateredMillis;
        public final long nextWateringMillis;

        Plant(String id, String species, String name, String roomId, int waterEveryDays,
              long lastWateredMillis, long nextWateringMillis) {
            this.id = id;
            this.species = species;
            this.name = name;
            this.roomId = roomId;
            this.waterEveryDays = waterEveryDays;
            this.lastWateredMillis = lastWateredMillis;
            this.nextWateringMillis = nextWateringMillis;
        }
    }

    public static final class Room {
        public final String id;
        public final String name;
        public final String light;

        Room(String id, String name, String light) {
            this.id = id;
            this.name = name;
            this.light = light;
        }
    }
}
