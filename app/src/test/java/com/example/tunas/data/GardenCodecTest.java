package com.example.tunas.data;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Base64;

import static org.junit.Assert.*;

public class GardenCodecTest {
    private static final String ROOM_ID = "83ce64e2-e8d5-4fc6-800b-52778916b359";
    private static final String PLANT_ID = "33ce64e2-e8d5-4fc6-800b-52778916b359";
    private static final AppStore.Room ROOM = new AppStore.Room(ROOM_ID, "Room", "Low");

    @Test public void missingOrUnparseableDataIsRejectedWithoutCrashing() {
        for (String value : new String[]{null, "", "not base64!", "AA=="}) {
            assertEmpty(Garden.decode(value));
        }
    }

    @Test public void unknownSchemaIsRejectedWithoutLoadingPartialData() throws Exception {
        assertEmpty(Garden.decode(snapshot(2, "Sakti", "sakti@example.com", true,
                new AppStore.Room[]{ROOM}, new AppStore.Plant[0])));
    }

    @Test public void truncatedAndTrailingBytesAreRejected() throws Exception {
        String valid = snapshot(1, "Sakti", "sakti@example.com", true,
                new AppStore.Room[]{ROOM}, new AppStore.Plant[0]);
        byte[] bytes = Base64.getDecoder().decode(valid);
        assertEmpty(Garden.decode(Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(bytes, bytes.length - 1))));
        assertEmpty(Garden.decode(Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(bytes, bytes.length + 1))));
    }

    @Test public void duplicateRoomIdsAndNamesAreRejectedAsWholeSnapshot() throws Exception {
        AppStore.Room sameId = new AppStore.Room(ROOM_ID, "Other", "Bright");
        AppStore.Room sameName = new AppStore.Room(PLANT_ID, "ROOM", "Bright");
        for (AppStore.Room invalid : new AppStore.Room[]{sameId, sameName}) {
            assertEmpty(Garden.decode(snapshot(1, "Sakti", "sakti@example.com", true,
                    new AppStore.Room[]{ROOM, invalid}, new AppStore.Plant[0])));
        }
    }

    @Test public void nonCanonicalIdsAndUntrimmedFieldsAreRejected() throws Exception {
        for (AppStore.Room invalid : new AppStore.Room[]{
                new AppStore.Room("1-1-1-1-1", "Other", "Bright"),
                new AppStore.Room(PLANT_ID, " Other ", "Bright"),
                new AppStore.Room(PLANT_ID, "Other", "\n"),
                new AppStore.Room(PLANT_ID, "X".repeat(201), "Bright")}) {
            assertEmpty(Garden.decode(snapshot(1, "Sakti", "sakti@example.com", true,
                    new AppStore.Room[]{ROOM, invalid}, new AppStore.Plant[0])));
        }
    }

    @Test public void orphanedAndDuplicatePlantsAreRejected() throws Exception {
        AppStore.Plant valid = new AppStore.Plant(PLANT_ID, "Pothos", "P", ROOM_ID, 1, 0L, 86_401_000L);
        AppStore.Plant orphan = new AppStore.Plant(PLANT_ID, "Pothos", "P", "missing", 1, 0L, 86_401_000L);
        for (AppStore.Plant[] plants : new AppStore.Plant[][]{{valid, valid}, {orphan}}) {
            assertEmpty(Garden.decode(snapshot(1, "Sakti", "sakti@example.com", true,
                    new AppStore.Room[]{ROOM}, plants)));
        }
    }

    @Test public void invalidWateringIntervalsAndTimestampsAreRejected() throws Exception {
        for (AppStore.Plant invalid : new AppStore.Plant[]{
                new AppStore.Plant(PLANT_ID, "Pothos", "P", "", 0, 0L, 86_401_000L),
                new AppStore.Plant(PLANT_ID, "Pothos", "P", "", 366, 0L, 86_401_000L),
                new AppStore.Plant(PLANT_ID, "Pothos", "P", "", 1, -1L, 86_401_000L),
                new AppStore.Plant(PLANT_ID, "Pothos", "P", "", 1, 0L, -1L),
                new AppStore.Plant(PLANT_ID, "Pothos", "P", "", 1, 0L, 1_000L),
                new AppStore.Plant(PLANT_ID, "Pothos", "P", "", 1, 2_000L, 86_401_000L)}) {
            assertEmpty(Garden.decode(snapshot(1, "Sakti", "sakti@example.com", true,
                    new AppStore.Room[]{ROOM}, new AppStore.Plant[]{invalid})));
        }
    }

    @Test public void inconsistentProfileOrSessionIsRejected() throws Exception {
        for (String[] profile : new String[][]{{"", "sakti@example.com"}, {"Sakti", "invalid"},
                {"Sakti", ""}, {" Sakti ", "sakti@example.com"}, {"", ""}}) {
            assertEmpty(Garden.decode(snapshot(1, profile[0], profile[1], true,
                    new AppStore.Room[]{ROOM}, new AppStore.Plant[0])));
        }
    }

    @Test public void malformedBooleanByteIsNotTreatedAsEnabled() throws Exception {
        String valid = snapshot(1, "", "", false, new AppStore.Room[]{ROOM}, new AppStore.Plant[0]);
        byte[] bytes = Base64.getDecoder().decode(valid);
        // Eight header bytes, two empty UTF fields (two bytes each), then session and reminders.
        bytes[13] = 2;
        assertEmpty(Garden.decode(Base64.getEncoder().encodeToString(bytes)));
    }

    @Test public void unreasonableRecordCountsAreRejected() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x54554E41);
        out.writeInt(1);
        out.writeUTF("Sakti");
        out.writeUTF("sakti@example.com");
        out.writeBoolean(true);
        out.writeBoolean(false);
        out.writeInt(-1);
        out.writeInt(0);
        assertEmpty(Garden.decode(Base64.getEncoder().encodeToString(bytes.toByteArray())));
    }

    private static void assertEmpty(Garden garden) {
        assertFalse(garden.hasProfile());
        assertFalse(garden.isSignedIn());
        assertFalse(garden.notificationsEnabled());
        assertEquals("", garden.name());
        assertTrue(garden.rooms().isEmpty());
        assertTrue(garden.plants().isEmpty());
    }

    private static String snapshot(int version, String name, String email, boolean signedIn,
                                   AppStore.Room[] rooms, AppStore.Plant[] plants) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x54554E41);
        out.writeInt(version);
        out.writeUTF(name);
        out.writeUTF(email);
        out.writeBoolean(signedIn);
        out.writeBoolean(true);
        out.writeInt(rooms.length);
        for (AppStore.Room room : rooms) {
            out.writeUTF(room.id);
            out.writeUTF(room.name);
            out.writeUTF(room.light);
        }
        out.writeInt(plants.length);
        for (AppStore.Plant plant : plants) {
            out.writeUTF(plant.id);
            out.writeUTF(plant.species);
            out.writeUTF(plant.name);
            out.writeUTF(plant.roomId);
            out.writeInt(plant.waterEveryDays);
            out.writeLong(plant.lastWateredMillis);
            out.writeLong(plant.nextWateringMillis);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
