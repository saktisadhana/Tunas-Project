package com.example.tunas.data;

import org.junit.Test;

import static org.junit.Assert.*;

public class GardenTest {
    @Test public void localProfileCanBeReopenedWithoutPassword() {
        Garden garden = new Garden();
        garden.saveProfile("  Sakti  ", "  SAKTI@example.com ");
        assertTrue(garden.hasProfile());
        assertTrue(garden.isSignedIn());
        assertEquals("Sakti", garden.name());
        assertEquals("sakti@example.com", garden.email());
        garden.setNotificationsEnabled(true);
        garden.signOut();
        assertTrue(garden.hasProfile());
        assertFalse(garden.isSignedIn());
        assertFalse(garden.openProfile("nobody@example.com"));
        assertFalse(garden.isSignedIn());
        assertTrue(garden.openProfile(" SAKTI@EXAMPLE.COM "));
        assertTrue(garden.isSignedIn());
        assertTrue(garden.notificationsEnabled());
    }

    @Test public void invalidProfileDoesNotChangeSavedProfile() {
        Garden garden = new Garden();
        garden.saveProfile("Sakti", "sakti@example.com");
        for (String email : new String[]{null, "", "a", "a@", "a@b", "a b@example.com", "a@.com", "a..b@example.com"}) {
            assertThrows(IllegalArgumentException.class, () -> garden.saveProfile("Sakti", email));
        }
        assertThrows(IllegalArgumentException.class, () -> garden.saveProfile("  ", "ok@example.com"));
        assertEquals("sakti@example.com", garden.email());
        assertEquals("Sakti", garden.name());
        assertFalse(garden.openProfile(null));
    }

    @Test public void signedOutRegistrationCannotReplaceExistingLocalProfile() {
        Garden garden = new Garden();
        garden.saveProfile("Sakti", "sakti@example.com");
        garden.signOut();
        assertThrows(IllegalArgumentException.class,
                () -> garden.saveProfile("Other", "other@example.com"));
        assertEquals("Sakti", garden.name());
    }

    @Test public void roomsHaveStableIdsTrimmedNamesAndUniqueCaseInsensitiveNames() {
        Garden garden = new Garden();
        AppStore.Room room = garden.addRoom("  Living room  ", " Bright indirect ");
        assertEquals("Living room", room.name);
        assertEquals("Bright indirect", room.light);
        assertEquals(room.id, java.util.UUID.fromString(room.id).toString());
        assertEquals(room.id, garden.rooms().get(0).id);
        assertThrows(IllegalArgumentException.class, () -> garden.addRoom("living ROOM", "Low"));
        assertThrows(IllegalArgumentException.class, () -> garden.addRoom(" ", "Low"));
        assertThrows(IllegalArgumentException.class, () -> garden.addRoom("Bedroom", null));
        assertEquals(1, garden.rooms().size());
        assertThrows(UnsupportedOperationException.class, () -> garden.rooms().clear());
        garden.deleteRoom(room.id);
        assertTrue(garden.rooms().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> garden.deleteRoom(room.id));
    }

    @Test public void addedPlantStartsFutureScheduleWithoutInventedWateringLog() {
        Garden garden = new Garden();
        AppStore.Room room = garden.addRoom("Living", "Indirect");
        long now = 1_000_000L;
        AppStore.Plant plant = garden.addPlant(" Monstera ", "  Monty ", room.id, 7, now);
        assertEquals("Monstera", plant.species);
        assertEquals("Monty", plant.name);
        assertEquals(room.id, plant.roomId);
        assertEquals(7, plant.waterEveryDays);
        assertEquals(0L, plant.lastWateredMillis);
        assertEquals(now + 7 * 86_400_000L, plant.nextWateringMillis);
        assertEquals(plant.id, java.util.UUID.fromString(plant.id).toString());
        assertEquals(plant.id, garden.plants().get(0).id);
        assertThrows(UnsupportedOperationException.class, () -> garden.plants().clear());
        assertThrows(IllegalArgumentException.class, () -> garden.deleteRoom(room.id));
        assertEquals(1, garden.rooms().size());
        assertEquals(1, garden.plants().size());
    }

    @Test public void plantCanBeUnassignedAndNicknameDefaultsToSpecies() {
        Garden garden = new Garden();
        AppStore.Plant plant = garden.addPlant("Pothos", "  ", null, 1, 1000L);
        assertEquals("Pothos", plant.name);
        assertEquals("", plant.roomId);
    }

    @Test public void invalidPlantDoesNotLeavePartialRecord() {
        Garden garden = new Garden();
        assertThrows(IllegalArgumentException.class, () -> garden.addPlant(" ", "X", "", 1, 1000L));
        assertThrows(IllegalArgumentException.class, () -> garden.addPlant("Pothos", "X", "missing", 1, 1000L));
        assertThrows(IllegalArgumentException.class, () -> garden.addPlant("Pothos", "X", "", 0, 1000L));
        assertThrows(IllegalArgumentException.class, () -> garden.addPlant("Pothos", "X", "", 366, 1000L));
        assertThrows(IllegalArgumentException.class, () -> garden.addPlant("Pothos", "X", "", 1, -1L));
        assertThrows(IllegalArgumentException.class, () -> garden.addPlant("Pothos", "X", "", 1, Long.MAX_VALUE));
        assertTrue(garden.plants().isEmpty());
    }

    @Test public void editingPlantPreservesIdSpeciesAndOriginalScheduleBaseline() {
        Garden garden = new Garden();
        AppStore.Room first = garden.addRoom("First", "Low");
        AppStore.Room second = garden.addRoom("Second", "Bright");
        AppStore.Plant original = garden.addPlant("Pothos", "Old", first.id, 7, 1_000L);
        garden.updatePlant(original.id, " New ", second.id, 3);
        AppStore.Plant edited = garden.plants().get(0);
        assertEquals(original.id, edited.id);
        assertEquals("Pothos", edited.species);
        assertEquals("New", edited.name);
        assertEquals(second.id, edited.roomId);
        assertEquals(1_000L + 3 * 86_400_000L, edited.nextWateringMillis);
        assertEquals(0L, edited.lastWateredMillis);
        assertEquals("Old", original.name);
        garden.deleteRoom(first.id);
        assertThrows(IllegalArgumentException.class, () -> garden.updatePlant(original.id, "Bad", "missing", 4));
        assertThrows(IllegalArgumentException.class, () -> garden.updatePlant(original.id, "Bad", second.id, -1));
        assertEquals("New", garden.plants().get(0).name);
    }

    @Test public void manualWateringReschedulesFromLoggedTimeAndIntervalEditUsesThatLog() {
        Garden garden = new Garden();
        AppStore.Plant plant = garden.addPlant("Pothos", "P", "", 7, 1_000L);
        long watered = 9_000_000L;
        garden.markWatered(plant.id, watered);
        assertEquals(watered, garden.plants().get(0).lastWateredMillis);
        assertEquals(watered + 7 * 86_400_000L, garden.plants().get(0).nextWateringMillis);
        garden.updatePlant(plant.id, "", "", 2);
        assertEquals("Pothos", garden.plants().get(0).name);
        assertEquals(watered + 2 * 86_400_000L, garden.plants().get(0).nextWateringMillis);
        assertThrows(IllegalArgumentException.class, () -> garden.markWatered(plant.id, Long.MAX_VALUE));
        assertEquals(watered, garden.plants().get(0).lastWateredMillis);
    }

    @Test public void duePlantsIncludesOnlyDueRecordsSortedByDueTime() {
        Garden garden = new Garden();
        AppStore.Plant later = garden.addPlant("Fern", "Later", "", 3, 1_000L);
        AppStore.Plant earlier = garden.addPlant("Pothos", "Earlier", "", 1, 1_000L);
        garden.addPlant("Palm", "Future", "", 7, 1_000L);
        assertTrue(garden.duePlants(earlier.nextWateringMillis - 1).isEmpty());
        assertEquals(earlier.id, garden.duePlants(earlier.nextWateringMillis).get(0).id);
        java.util.List<AppStore.Plant> due = garden.duePlants(later.nextWateringMillis);
        assertEquals(2, due.size());
        assertEquals(earlier.id, due.get(0).id);
        assertEquals(later.id, due.get(1).id);
        assertThrows(UnsupportedOperationException.class, due::clear);
        garden.markWatered(earlier.id, later.nextWateringMillis);
        assertEquals(1, garden.duePlants(later.nextWateringMillis).size());
    }

    @Test public void deletingPlantRemovesCareTaskAndAllowsRoomDeletion() {
        Garden garden = new Garden();
        AppStore.Room room = garden.addRoom("Room", "Low");
        AppStore.Plant plant = garden.addPlant("Pothos", "P", room.id, 1, 1_000L);
        garden.deletePlant(plant.id);
        assertTrue(garden.plants().isEmpty());
        assertTrue(garden.duePlants(Long.MAX_VALUE).isEmpty());
        garden.deleteRoom(room.id);
        assertTrue(garden.rooms().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> garden.deletePlant(plant.id));
        assertThrows(IllegalArgumentException.class, () -> garden.updatePlant(plant.id, "X", "", 1));
        assertThrows(IllegalArgumentException.class, () -> garden.markWatered(plant.id, 1_000L));
    }

    @Test public void encodedStateRestoresProfileRoomPlantAndCareSchedule() {
        Garden garden = new Garden();
        garden.saveProfile("Sakti 🌱", "sakti@example.com");
        garden.setNotificationsEnabled(true);
        AppStore.Room room = garden.addRoom("Living room", "Indirect");
        AppStore.Plant plant = garden.addPlant("Monstera", "Monty", room.id, 7, 1_000L);
        garden.markWatered(plant.id, 2_000L);
        garden.updatePlant(plant.id, "M", room.id, 3);
        garden.signOut();
        Garden restored = Garden.decode(garden.encode());
        assertEquals("Sakti 🌱", restored.name());
        assertEquals("sakti@example.com", restored.email());
        assertTrue(restored.hasProfile());
        assertFalse(restored.isSignedIn());
        assertTrue(restored.notificationsEnabled());
        assertEquals(room.id, restored.rooms().get(0).id);
        assertEquals(room.name, restored.rooms().get(0).name);
        assertEquals(room.light, restored.rooms().get(0).light);
        AppStore.Plant saved = restored.plants().get(0);
        assertEquals(plant.id, saved.id);
        assertEquals("Monstera", saved.species);
        assertEquals("M", saved.name);
        assertEquals(room.id, saved.roomId);
        assertEquals(3, saved.waterEveryDays);
        assertEquals(2_000L, saved.lastWateredMillis);
        assertEquals(2_000L + 3 * 86_400_000L, saved.nextWateringMillis);
        assertTrue(restored.openProfile("sakti@example.com"));
        assertTrue(Garden.decode(restored.encode()).isSignedIn());
    }

    @Test public void emptyStateRoundTripsWithoutInventingRecords() {
        Garden restored = Garden.decode(new Garden().encode());
        assertFalse(restored.hasProfile());
        assertFalse(restored.isSignedIn());
        assertTrue(restored.plants().isEmpty());
        assertTrue(restored.rooms().isEmpty());
    }

    @Test public void profileEmailRejectsOverlongLocalPartsAndDomainLabels() {
        Garden garden = new Garden();
        assertThrows(IllegalArgumentException.class,
                () -> garden.saveProfile("Sakti", "x".repeat(65) + "@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> garden.saveProfile("Sakti", "x@" + "d".repeat(64) + ".com"));
        String longValid = "x".repeat(64) + "@" + "d".repeat(63) + "." + "e".repeat(63) + "." + "f".repeat(50) + ".com";
        garden.saveProfile("Sakti", longValid);
        assertEquals(longValid, garden.email());
    }

    @Test public void newGardenContainsNoProfilePlantsOrRooms() {
        Garden garden = new Garden();
        assertFalse(garden.hasProfile());
        assertFalse(garden.isSignedIn());
        assertEquals("", garden.name());
        assertEquals("", garden.email());
        assertFalse(garden.notificationsEnabled());
        assertTrue(garden.rooms().isEmpty());
        assertTrue(garden.plants().isEmpty());
    }
}
