package com.example.tunas;

import android.content.Context;
import android.os.Process;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.tunas.data.AppStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Run writeSnapshot, force-stop the target, then run readSnapshot. */
@RunWith(AndroidJUnit4.class)
public class PersistenceProcessTest {
    @Test public void snapshotPersists() {
        String phase = InstrumentationRegistry.getArguments().getString("persistencePhase", "self");
        if (!phase.equals("read")) writeSnapshot();
        if (!phase.equals("write")) readSnapshot(phase.equals("read"));
    }
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private void writeSnapshot() {
        context().deleteSharedPreferences("tunas_local_v1");
        AppStore store = new AppStore(context());
        store.saveProfile("Persistence test", "persistence@example.test");
        AppStore.Room room = store.addRoom("Persistence room", "Medium");
        AppStore.Plant plant = store.addPlant("Monstera", "Persistence plant", room.id, 7);
        store.markWatered(plant.id);
        assertTrue(context().getSharedPreferences("process_probe", 0).edit().putInt("writer", Process.myPid()).commit());
        assertTrue(store.plants().get(0).lastWateredMillis > 0);
    }
    private void readSnapshot(boolean newProcess) {
        int writer = context().getSharedPreferences("process_probe", 0).getInt("writer", -1);
        assertTrue("Run writeSnapshot first", writer > 0);
        if (newProcess) assertNotEquals("The read must use a new Android process", writer, Process.myPid());
        AppStore store = new AppStore(context());
        assertEquals("Persistence test", store.name()); assertTrue(store.isSignedIn());
        assertEquals(1, store.rooms().size()); assertEquals(1, store.plants().size());
        assertEquals(store.rooms().get(0).id, store.plants().get(0).roomId);
        assertEquals("Persistence plant", store.plants().get(0).name);
        assertTrue(store.plants().get(0).lastWateredMillis > 0);
        assertTrue(store.plants().get(0).nextWateringMillis > store.plants().get(0).lastWateredMillis);
    }
}
