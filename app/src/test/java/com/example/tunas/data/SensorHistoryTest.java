package com.example.tunas.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.tunas.iot.Telemetry;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SensorHistoryTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private static final long T0 = 1_790_000_000_000L;

    private static Telemetry reading(Double moisture, long at) {
        return new Telemetry(80.0, moisture, null, 60.0, 24.5, 55.0, at);
    }

    @Test public void readingsInsideOneBucketAreSavedOnce() throws Exception {
        SensorHistory history = new SensorHistory(folder.newFile());
        assertTrue(history.record(reading(40.0, T0)));
        assertFalse(history.record(reading(41.0, T0 + 60_000)));
        assertEquals(1, history.since(0).size());
    }

    @Test public void savedReadingsSurviveReloadWithMissingValuesKept() throws Exception {
        File file = folder.newFile();
        SensorHistory history = new SensorHistory(file);
        history.record(reading(40.0, T0));
        history.record(reading(null, T0 + SensorHistory.BUCKET_MILLIS));
        List<SensorHistory.Sample> loaded = new SensorHistory(file).since(0);
        assertEquals(2, loaded.size());
        assertEquals(40.0, loaded.get(0).values[1], 0.001);
        assertTrue(Double.isNaN(loaded.get(1).values[1]));
        assertTrue(Double.isNaN(loaded.get(0).values[2])); // nutrient never sent
        assertEquals(24.5, loaded.get(1).values[4], 0.001);
    }

    @Test public void weekOldReadingsArePruned() throws Exception {
        File file = folder.newFile();
        SensorHistory history = new SensorHistory(file);
        history.record(reading(40.0, T0));
        long later = T0 + SensorHistory.KEEP_MILLIS + 2 * 60 * 60_000;
        history.record(reading(50.0, later));
        List<SensorHistory.Sample> loaded = new SensorHistory(file).since(0);
        assertEquals(1, loaded.size());
        assertEquals(later, loaded.get(0).time);
    }

    @Test public void emptyReadingIsIgnoredAndCorruptLinesAreSkipped() throws Exception {
        File file = folder.newFile();
        try (Writer out = new FileWriter(file)) { out.write("garbage\n" + T0 + ",1,2,,4,5,6\n12,half"); }
        SensorHistory history = new SensorHistory(file);
        assertFalse(history.record(new Telemetry(null, null, null, null, null, null, T0 + SensorHistory.BUCKET_MILLIS)));
        List<SensorHistory.Sample> loaded = history.since(0);
        assertEquals(1, loaded.size());
        assertEquals(2.0, loaded.get(0).values[1], 0.001);
    }
}
