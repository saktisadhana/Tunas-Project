package com.example.tunas.data;

import android.content.Context;
import org.junit.Test;

import java.lang.reflect.Modifier;
import java.util.Map;

import static org.junit.Assert.*;

public class AppStoreTest {
    @Test public void publicContextConstructorIsAvailableForScreens() throws Exception {
        assertTrue(Modifier.isPublic(AppStore.class.getConstructor(Context.class).getModifiers()));
    }

    @Test public void newPreferencesHaveNoSampleRecordsAndReadsDoNotWrite() {
        MemoryPreferences preferences = new MemoryPreferences();
        AppStore store = new AppStore(preferences);
        assertFalse(store.hasProfile());
        assertFalse(store.isSignedIn());
        assertEquals("", store.name());
        assertEquals("", store.email());
        assertFalse(store.notificationsEnabled());
        assertTrue(store.rooms().isEmpty());
        assertTrue(store.plants().isEmpty());
        assertTrue(store.duePlants(Long.MAX_VALUE).isEmpty());
        assertEquals(0, preferences.commits);
    }

    @Test public void profileRoomPlantAndManualCareSurviveReopeningStore() {
        MemoryPreferences preferences = new MemoryPreferences();
        AppStore store = new AppStore(preferences);
        store.saveProfile("Sakti", "sakti@example.com");
        store.setNotificationsEnabled(true);
        AppStore.Room room = store.addRoom("Living", "Indirect");
        long before = System.currentTimeMillis();
        AppStore.Plant plant = store.addPlant("Pothos", "P", room.id, 7);
        long after = System.currentTimeMillis();
        assertTrue(plant.nextWateringMillis >= before + 7 * 86_400_000L);
        assertTrue(plant.nextWateringMillis <= after + 7 * 86_400_000L);
        store.markWatered(plant.id);
        AppStore reopened = new AppStore(preferences);
        assertEquals("Sakti", reopened.name());
        assertEquals("sakti@example.com", reopened.email());
        assertTrue(reopened.isSignedIn());
        assertTrue(reopened.notificationsEnabled());
        assertEquals(room.id, reopened.rooms().get(0).id);
        AppStore.Plant logged = reopened.plants().get(0);
        assertTrue(logged.lastWateredMillis >= before);
        assertEquals(logged.lastWateredMillis + 7 * 86_400_000L, logged.nextWateringMillis);
        reopened.updatePlant(plant.id, "Edited", "", 3);
        AppStore.Plant edited = new AppStore(preferences).plants().get(0);
        assertEquals("Edited", edited.name);
        assertEquals("", edited.roomId);
        assertEquals(logged.lastWateredMillis + 3 * 86_400_000L, edited.nextWateringMillis);
        reopened.deleteRoom(room.id);
        reopened.deletePlant(plant.id);
        assertTrue(new AppStore(preferences).plants().isEmpty());
        assertTrue(new AppStore(preferences).rooms().isEmpty());
    }

    @Test public void signOutOnlyClearsSessionAndReopeningMatchesSavedEmail() {
        MemoryPreferences preferences = new MemoryPreferences();
        AppStore store = new AppStore(preferences);
        store.saveProfile("Sakti", "sakti@example.com");
        AppStore.Room room = store.addRoom("Living", "Low");
        AppStore.Plant plant = store.addPlant("Pothos", "P", room.id, 1);
        store.setNotificationsEnabled(true);
        store.signOut();
        AppStore reopened = new AppStore(preferences);
        assertFalse(reopened.isSignedIn());
        assertTrue(reopened.hasProfile());
        assertTrue(reopened.notificationsEnabled());
        assertEquals(plant.id, reopened.plants().get(0).id);
        assertFalse(reopened.openProfile("not-saved@example.com"));
        assertFalse(new AppStore(preferences).isSignedIn());
        assertTrue(reopened.openProfile(" SAKTI@EXAMPLE.COM "));
        assertTrue(new AppStore(preferences).isSignedIn());
    }

    @Test public void invalidMutationDoesNotPersistOrDeleteAttachedPlants() {
        MemoryPreferences preferences = new MemoryPreferences();
        AppStore store = new AppStore(preferences);
        AppStore.Room room = store.addRoom("Living", "Low");
        AppStore.Plant plant = store.addPlant("Pothos", "P", room.id, 1);
        Map<String, ?> before = preferences.getAll();
        assertThrows(IllegalArgumentException.class, () -> store.deleteRoom(room.id));
        assertThrows(IllegalArgumentException.class, () -> store.updatePlant(plant.id, "Bad", room.id, 0));
        assertThrows(IllegalArgumentException.class, () -> store.saveProfile("", "bad"));
        assertEquals(before, preferences.getAll());
        assertEquals(plant.id, new AppStore(preferences).plants().get(0).id);
    }

    @Test public void twoSequentialStoreInstancesDoNotOverwriteEachOthersChanges() {
        MemoryPreferences preferences = new MemoryPreferences();
        AppStore first = new AppStore(preferences);
        AppStore second = new AppStore(preferences);
        first.addRoom("First", "Low");
        second.addRoom("Second", "Bright");
        assertEquals(2, first.rooms().size());
        assertEquals(2, second.rooms().size());
    }

    @Test public void failedCommitIsReportedInsteadOfClaimingPersistence() {
        MemoryPreferences preferences = new MemoryPreferences();
        AppStore store = new AppStore(preferences);
        store.saveProfile("Sakti", "sakti@example.com");
        Map<String, ?> before = preferences.getAll();
        preferences.failCommit = true;
        assertThrows(IllegalStateException.class, () -> store.addRoom("Room", "Low"));
        assertEquals(before, preferences.getAll());
        assertTrue(store.rooms().isEmpty());
    }

    @Test public void malformedStoredStateDoesNotCrashOrOverwriteOriginalOnRead() {
        MemoryPreferences preferences = new MemoryPreferences();
        preferences.values.put("snapshot", "garbage!");
        AppStore store = new AppStore(preferences);
        assertFalse(store.hasProfile());
        assertTrue(store.plants().isEmpty());
        assertEquals("garbage!", preferences.values.get("snapshot"));
        assertEquals(0, preferences.commits);
        preferences.values.put("snapshot", 42);
        assertFalse(store.isSignedIn());
        assertTrue(store.rooms().isEmpty());
        assertEquals(42, preferences.values.get("snapshot"));
    }
}
