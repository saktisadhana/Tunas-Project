package com.example.tunas.data;

import android.content.Context;
import com.example.tunas.iot.Telemetry;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Recent sensor readings for the history chart, kept in a private CSV file on this phone.
 * One line per saved reading: epochMillis,health,moisture,nutrient,sunlight,temperature,humidity.
 * A missing measurement is an empty field. History is a convenience: write failures skip the
 * reading instead of interrupting the screen that received it.
 */
public final class SensorHistory {
    public static final int METRICS = 6;
    public static final long KEEP_MILLIS = 7L * 24 * 60 * 60_000;
    static final long BUCKET_MILLIS = 5 * 60_000;
    /** A longer silence than this is drawn as a gap: the app was closed or the sensor offline. */
    public static final long GAP_MILLIS = 30 * 60_000;

    public static final class Sample {
        public final long time;
        /** Indexed like the Home cards; NaN where the sensor sent nothing. */
        public final double[] values;
        Sample(long time, double[] values) { this.time = time; this.values = values; }
    }

    private final File file;
    private List<Sample> samples;

    public SensorHistory(Context context) { this(new File(context.getFilesDir(), "sensor_history.csv")); }
    SensorHistory(File file) { this.file = file; }

    /** Returns true when the reading was saved, so an open chart knows to redraw. */
    public boolean record(Telemetry reading) {
        double[] values = {or(reading.health), or(reading.moisture), or(reading.nutrient),
                or(reading.sunlight), or(reading.temperature), or(reading.humidity)};
        boolean any = false;
        for (double value : values) any |= !Double.isNaN(value);
        if (!any) return false;
        List<Sample> all = load();
        Sample last = all.isEmpty() ? null : all.get(all.size() - 1);
        long now = reading.timestampMillis;
        if (last != null && !shouldRecord(last.time, now, last.values, values)) return false;
        Sample sample = new Sample(now, values);
        all.add(sample);
        try {
            // Rewrite at most about once an hour to drop week-old lines; otherwise append one line.
            if (all.get(0).time < now - KEEP_MILLIS - 60 * 60_000) { prune(now); rewrite(); }
            else try (Writer out = new FileWriter(file, true)) { out.write(line(sample)); }
            return true;
        } catch (IOException failed) {
            all.remove(all.size() - 1);
            return false;
        }
    }

    /** Saved readings at or after {@code fromMillis}, oldest first. */
    public List<Sample> since(long fromMillis) {
        List<Sample> out = new ArrayList<>();
        for (Sample sample : load()) if (sample.time >= fromMillis) out.add(sample);
        return Collections.unmodifiableList(out);
    }

    /**
     * Decides whether a new reading earns a row in the history file.
     *
     * Called for every reading the sensor sends (often every few seconds, from BLE and MQTT),
     * with the last saved row's time and values. Returning true appends a row.
     */
    static boolean shouldRecord(long lastSavedMillis, long nowMillis, double[] lastValues, double[] nextValues) {
        // TODO(you): pick the saving rule. Current default: one row per 5-minute bucket.
        return nowMillis - lastSavedMillis >= BUCKET_MILLIS;
    }

    private List<Sample> load() {
        if (samples != null) return samples;
        samples = new ArrayList<>();
        if (!file.exists()) return samples;
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            for (String line; (line = in.readLine()) != null; ) {
                Sample sample = parse(line);
                if (sample != null) samples.add(sample);
            }
        } catch (IOException unreadable) {
            samples.clear();
        }
        return samples;
    }

    private void prune(long now) {
        long cutoff = now - KEEP_MILLIS;
        while (!samples.isEmpty() && samples.get(0).time < cutoff) samples.remove(0);
    }

    private void rewrite() throws IOException {
        File next = new File(file.getPath() + ".tmp");
        try (Writer out = new FileWriter(next, false)) { for (Sample sample : samples) out.write(line(sample)); }
        if (!next.renameTo(file)) {
            // Windows (JVM tests) won't rename over an existing file.
            if (!file.delete() || !next.renameTo(file)) throw new IOException("Could not replace history file");
        }
    }

    private static String line(Sample sample) {
        StringBuilder text = new StringBuilder().append(sample.time);
        for (double value : sample.values) {
            text.append(',');
            if (!Double.isNaN(value)) text.append(String.format(Locale.ROOT, "%.2f", value));
        }
        return text.append('\n').toString();
    }

    private static Sample parse(String line) {
        String[] parts = line.split(",", -1);
        if (parts.length != METRICS + 1) return null;
        try {
            double[] values = new double[METRICS];
            for (int i = 0; i < METRICS; i++) values[i] = parts[i + 1].isEmpty() ? Double.NaN : Double.parseDouble(parts[i + 1]);
            return new Sample(Long.parseLong(parts[0]), values);
        } catch (NumberFormatException corrupt) {
            return null; // skip a half-written line instead of losing the whole history
        }
    }

    private static double or(Double value) { return value == null ? Double.NaN : value; }
}
