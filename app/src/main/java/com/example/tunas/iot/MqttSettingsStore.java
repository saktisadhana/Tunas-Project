package com.example.tunas.iot;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Broker host, device ID and password in encrypted app-private preferences. Never hardcoded. */
public final class MqttSettingsStore {
    private static final String FILE_NAME = "tunas_mqtt_v1";
    private final SharedPreferences preferences;

    public MqttSettingsStore(Context context) {
        Objects.requireNonNull(context, "context");
        Context application = context.getApplicationContext();
        Context owner = application == null ? context : application;
        try {
            String masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            this.preferences = EncryptedSharedPreferences.create(
                    FILE_NAME,
                    masterKey,
                    owner,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException failure) {
            throw new IllegalStateException("This phone couldn't open secure storage for the MQTT broker.", failure);
        }
    }

    public void save(MqttBrokerConfig config) {
        Objects.requireNonNull(config, "config");
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, String> field : MqttSettingsFields.write(config).entrySet()) {
            editor.putString(field.getKey(), field.getValue());
        }
        if (!editor.commit()) {
            throw new IllegalStateException("Could not save the MQTT broker on this phone.");
        }
    }

    public MqttBrokerConfig load() {
        try {
            Map<String, String> fields = new LinkedHashMap<>();
            for (String key : MqttSettingsFields.KEYS) fields.put(key, preferences.getString(key, null));
            return MqttSettingsFields.read(fields);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    public void clear() {
        if (!preferences.edit().clear().commit()) {
            throw new IllegalStateException("Could not forget the MQTT broker on this phone.");
        }
    }
}
