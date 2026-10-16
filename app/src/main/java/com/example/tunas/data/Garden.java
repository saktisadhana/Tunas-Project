package com.example.tunas.data;

import java.util.Collections;
import java.util.List;

/** Android-free local garden state. No records are pre-populated. */
final class Garden {
    private String profileName = "";
    private String profileEmail = "";
    private boolean signedIn;
    private boolean notifications;

    boolean hasProfile() { return !profileEmail.isEmpty(); }
    boolean isSignedIn() { return signedIn; }
    String name() { return profileName; }
    String email() { return profileEmail; }
    boolean notificationsEnabled() { return notifications; }
    void setNotificationsEnabled(boolean enabled) { notifications = enabled; }
    void signOut() { signedIn = false; }

    void saveProfile(String name, String email) {
        String cleanName = required(name, "Name");
        String cleanEmail = email(email);
        if (hasProfile() && !signedIn && !profileEmail.equals(cleanEmail)) {
            throw new IllegalArgumentException("A local profile already exists. Open it first.");
        }
        profileName = cleanName;
        profileEmail = cleanEmail;
        signedIn = true;
    }

    boolean openProfile(String email) {
        if (email != null && hasProfile() && profileEmail.equals(email.trim().toLowerCase(java.util.Locale.ROOT))) {
            signedIn = true;
            return true;
        }
        return false;
    }

    private static String email(String value) {
        String result = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (result.length() > 254 || result.indexOf('@') > 64
                || !result.matches("[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        return result;
    }

    private static String required(String value, String label) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty() || result.length() > 200 || result.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + " is required (maximum 200 characters).");
        }
        return result;
    }
    private final java.util.LinkedHashMap<String, AppStore.Room> roomMap = new java.util.LinkedHashMap<>();

    List<AppStore.Room> rooms() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(roomMap.values()));
    }

    AppStore.Room addRoom(String name, String light) {
        String cleanName = required(name, "Room name");
        String cleanLight = required(light, "Light");
        for (AppStore.Room room : roomMap.values()) {
            if (room.name.equalsIgnoreCase(cleanName)) {
                throw new IllegalArgumentException("A room with that name already exists.");
            }
        }
        AppStore.Room room = new AppStore.Room(java.util.UUID.randomUUID().toString(), cleanName, cleanLight);
        roomMap.put(room.id, room);
        return room;
    }

    void deleteRoom(String id) {
        if (!roomMap.containsKey(id)) {
            throw new IllegalArgumentException("Room not found.");
        }
        for (AppStore.Plant plant : plantMap.values()) {
            if (plant.roomId.equals(id)) {
                throw new IllegalArgumentException("Move or delete this room's plants first.");
            }
        }
        roomMap.remove(id);
    }

    private final java.util.LinkedHashMap<String, AppStore.Plant> plantMap = new java.util.LinkedHashMap<>();
    private static final long DAY_MILLIS = 86_400_000L;

    List<AppStore.Plant> plants() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(plantMap.values()));
    }

    AppStore.Plant addPlant(String species, String nickname, String roomId, int waterEveryDays, long now) {
        String cleanSpecies = required(species, "Species");
        String cleanName = plantName(nickname, cleanSpecies);
        String cleanRoom = roomId(roomId);
        long next = nextWatering(now, waterEveryDays);
        AppStore.Plant plant = new AppStore.Plant(java.util.UUID.randomUUID().toString(), cleanSpecies,
                cleanName, cleanRoom, waterEveryDays, 0L, next);
        plantMap.put(plant.id, plant);
        return plant;
    }

    void updatePlant(String id, String nickname, String roomId, int waterEveryDays) {
        AppStore.Plant old = plant(id);
        String cleanName = plantName(nickname, old.species);
        String cleanRoom = roomId(roomId);
        long baseline = old.nextWateringMillis - old.waterEveryDays * DAY_MILLIS;
        long next = nextWatering(baseline, waterEveryDays);
        plantMap.put(id, new AppStore.Plant(id, old.species, cleanName, cleanRoom,
                waterEveryDays, old.lastWateredMillis, next));
    }

    void deletePlant(String id) {
        plant(id);
        plantMap.remove(id);
    }

    /** Records the user's manual care log; it never issues a device command. */
    void markWatered(String id, long now) {
        AppStore.Plant old = plant(id);
        long next = nextWatering(now, old.waterEveryDays);
        plantMap.put(id, new AppStore.Plant(id, old.species, old.name, old.roomId,
                old.waterEveryDays, now, next));
    }

    List<AppStore.Plant> duePlants(long now) {
        java.util.ArrayList<AppStore.Plant> due = new java.util.ArrayList<>();
        for (AppStore.Plant plant : plantMap.values()) {
            if (plant.nextWateringMillis <= now) due.add(plant);
        }
        due.sort(java.util.Comparator.comparingLong(plant -> plant.nextWateringMillis));
        return Collections.unmodifiableList(due);
    }

    private AppStore.Plant plant(String id) {
        AppStore.Plant plant = plantMap.get(id);
        if (plant == null) throw new IllegalArgumentException("Plant not found.");
        return plant;
    }

    private String roomId(String value) {
        if (value == null || value.isEmpty()) return "";
        if (!roomMap.containsKey(value)) throw new IllegalArgumentException("Room not found.");
        return value;
    }

    private static String plantName(String value, String species) {
        return value == null || value.trim().isEmpty() ? species : required(value, "Plant name");
    }

    // A versioned binary envelope avoids Android-only JSON in the tested domain.
    // Base64 is an encoding, not encryption; the Android adapter uses private preferences.
    private static final int MAGIC = 0x54554E41;
    private static final int SCHEMA_VERSION = 1;

    String encode() {
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(SCHEMA_VERSION);
            out.writeUTF(profileName);
            out.writeUTF(profileEmail);
            out.writeBoolean(signedIn);
            out.writeBoolean(notifications);
            out.writeInt(roomMap.size());
            for (AppStore.Room room : roomMap.values()) {
                out.writeUTF(room.id);
                out.writeUTF(room.name);
                out.writeUTF(room.light);
            }
            out.writeInt(plantMap.size());
            for (AppStore.Plant plant : plantMap.values()) {
                out.writeUTF(plant.id);
                out.writeUTF(plant.species);
                out.writeUTF(plant.name);
                out.writeUTF(plant.roomId);
                out.writeInt(plant.waterEveryDays);
                out.writeLong(plant.lastWateredMillis);
                out.writeLong(plant.nextWateringMillis);
            }
            out.flush();
            return java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (java.io.IOException impossible) {
            throw new IllegalStateException("Could not encode local garden.", impossible);
        }
    }

    /** Invalid or unsupported snapshots fail closed as an empty garden, never partial records. */
    static Garden decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return new Garden();
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(encoded);
            java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes));
            if (in.readInt() != MAGIC || in.readInt() != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported local garden format.");
            }
            Garden garden = new Garden();
            garden.profileName = in.readUTF();
            garden.profileEmail = in.readUTF();
            garden.signedIn = readFlag(in);
            garden.notifications = readFlag(in);
            if (garden.profileName.isEmpty() && garden.profileEmail.isEmpty()) {
                if (garden.signedIn) throw new IllegalArgumentException("Session has no profile.");
            } else {
                canonicalField(garden.profileName, "Name");
                if (!garden.profileEmail.equals(email(garden.profileEmail))) {
                    throw new IllegalArgumentException("Invalid saved email.");
                }
            }
            int roomCount = readCount(in);
            for (int i = 0; i < roomCount; i++) {
                AppStore.Room room = new AppStore.Room(in.readUTF(), in.readUTF(), in.readUTF());
                canonicalId(room.id);
                canonicalField(room.name, "Room name");
                canonicalField(room.light, "Light");
                if (garden.roomMap.containsKey(room.id)) throw new IllegalArgumentException("Duplicate room.");
                for (AppStore.Room existing : garden.roomMap.values()) {
                    if (existing.name.equalsIgnoreCase(room.name)) throw new IllegalArgumentException("Duplicate room name.");
                }
                garden.roomMap.put(room.id, room);
            }
            int plantCount = readCount(in);
            for (int i = 0; i < plantCount; i++) {
                AppStore.Plant plant = new AppStore.Plant(in.readUTF(), in.readUTF(), in.readUTF(),
                        in.readUTF(), in.readInt(), in.readLong(), in.readLong());
                canonicalId(plant.id);
                canonicalField(plant.species, "Species");
                canonicalField(plant.name, "Plant name");
                garden.roomId(plant.roomId);
                long interval = nextWatering(0L, plant.waterEveryDays);
                if (plant.lastWateredMillis < 0 || plant.nextWateringMillis < interval
                        || (plant.lastWateredMillis > 0 && plant.nextWateringMillis
                        != nextWatering(plant.lastWateredMillis, plant.waterEveryDays))) {
                    throw new IllegalArgumentException("Invalid saved care schedule.");
                }
                if (garden.plantMap.containsKey(plant.id) || garden.roomMap.containsKey(plant.id)) {
                    throw new IllegalArgumentException("Duplicate plant.");
                }
                garden.plantMap.put(plant.id, plant);
            }
            if (in.available() != 0) throw new IllegalArgumentException("Unexpected saved data.");
            return garden;
        } catch (java.io.IOException | IllegalArgumentException invalid) {
            return new Garden();
        }
    }

    private static boolean readFlag(java.io.DataInputStream in) throws java.io.IOException {
        int flag = in.readUnsignedByte();
        if (flag > 1) throw new IllegalArgumentException("Invalid saved setting.");
        return flag == 1;
    }

    private static int readCount(java.io.DataInputStream in) throws java.io.IOException {
        int count = in.readInt();
        // Each record needs at least two bytes even for a single empty UTF field.
        if (count < 0 || count > in.available() / 2) throw new IllegalArgumentException("Invalid record count.");
        return count;
    }

    private static void canonicalField(String value, String label) {
        if (!value.equals(required(value, label))) throw new IllegalArgumentException("Invalid saved " + label + ".");
    }

    private static void canonicalId(String value) {
        if (!java.util.UUID.fromString(value).toString().equals(value)) {
            throw new IllegalArgumentException("Invalid record identifier.");
        }
    }

    private static long nextWatering(long baseline, int days) {
        if (days < 1 || days > 365) {
            throw new IllegalArgumentException("Choose a watering interval from 1 to 365 days.");
        }
        if (baseline < 0 || baseline > Long.MAX_VALUE - days * DAY_MILLIS) {
            throw new IllegalArgumentException("Invalid care date.");
        }
        return baseline + days * DAY_MILLIS;
    }
}
