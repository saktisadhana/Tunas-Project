package com.example.tunas.data;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** In-memory implementation of the platform storage contract; no Android runtime required. */
final class MemoryPreferences implements SharedPreferences {
    final Map<String, Object> values = new HashMap<>();
    boolean failCommit;
    int commits;

    @Override public Map<String, ?> getAll() { return new HashMap<>(values); }
    @Override public String getString(String key, String def) {
        Object value = values.get(key);
        return value == null ? def : (String) value;
    }
    @Override public Set<String> getStringSet(String key, Set<String> def) {
        Object value = values.get(key);
        if (value == null) return def;
        @SuppressWarnings("unchecked") Set<String> result = (Set<String>) value;
        return new HashSet<>(result);
    }
    @Override public int getInt(String key, int def) { return values.containsKey(key) ? (Integer) values.get(key) : def; }
    @Override public long getLong(String key, long def) { return values.containsKey(key) ? (Long) values.get(key) : def; }
    @Override public float getFloat(String key, float def) { return values.containsKey(key) ? (Float) values.get(key) : def; }
    @Override public boolean getBoolean(String key, boolean def) { return values.containsKey(key) ? (Boolean) values.get(key) : def; }
    @Override public boolean contains(String key) { return values.containsKey(key); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }

    @Override public Editor edit() {
        return new Editor() {
            private final Map<String, Object> staged = new HashMap<>();
            private final Set<String> removed = new HashSet<>();
            private boolean clear;
            @Override public Editor putString(String key, String value) { staged.put(key, value); return this; }
            @Override public Editor putStringSet(String key, Set<String> value) {
                staged.put(key, value == null ? null : new HashSet<>(value)); return this;
            }
            @Override public Editor putInt(String key, int value) { staged.put(key, value); return this; }
            @Override public Editor putLong(String key, long value) { staged.put(key, value); return this; }
            @Override public Editor putFloat(String key, float value) { staged.put(key, value); return this; }
            @Override public Editor putBoolean(String key, boolean value) { staged.put(key, value); return this; }
            @Override public Editor remove(String key) { removed.add(key); return this; }
            @Override public Editor clear() { clear = true; return this; }
            @Override public boolean commit() {
                commits++;
                if (failCommit) return false;
                if (clear) values.clear();
                for (String key : removed) values.remove(key);
                for (Map.Entry<String, Object> entry : staged.entrySet()) {
                    if (entry.getValue() == null) values.remove(entry.getKey());
                    else values.put(entry.getKey(), entry.getValue());
                }
                return true;
            }
            @Override public void apply() { commit(); }
        };
    }
}
