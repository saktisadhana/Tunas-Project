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

/** Broker settings in encrypted app prefs. */
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

    public void rememberNode(String address, String deviceId) {
        if (address.isEmpty() || (address.equals(bleAddress()) && deviceId.equals(bleDeviceId()))) return;
        if (!preferences.edit().putString("ble_address", address).putString("ble_device", deviceId).commit())
            throw new IllegalStateException("Could not remember the authenticated node.");
    }
    public String bleAddress() { return preferences.getString("ble_address", ""); }
    public String bleDeviceId() { return preferences.getString("ble_device", ""); }
    public void forgetNode() {
        if (!preferences.edit().remove("ble_address").remove("ble_device").commit())
            throw new IllegalStateException("Could not clear the previous Bluetooth association.");
    }

    public void savePending(ProvisioningConfig config) {
        SharedPreferences.Editor editor = preferences.edit().putString("pending_id", config.requestId);
        for (Map.Entry<String, String> field : MqttSettingsFields.write(config.broker).entrySet())
            editor.putString("pending_" + field.getKey(), field.getValue());
        if (!editor.commit()) throw new IllegalStateException("Could not securely save pending setup.");
        // Wi-Fi credentials and the BLE passkey are deliberately not persisted on the phone.
    }

    public boolean promote(NodeSetupStatus status) {
        if (!status.mqttReady || !"ready".equals(status.stage) || status.requestId.isEmpty()
                || !status.requestId.equals(preferences.getString("pending_id", ""))) return false;
        Map<String, String> fields = new LinkedHashMap<>();
        for (String key : MqttSettingsFields.KEYS) fields.put(key, preferences.getString("pending_" + key, null));
        MqttBrokerConfig config = MqttSettingsFields.read(fields);
        if (config == null || !status.confirms(preferences.getString("pending_id", ""), config.deviceId)) return false;
        SharedPreferences.Editor editor = preferences.edit().remove("pending_id");
        for (Map.Entry<String, String> field : MqttSettingsFields.write(config).entrySet()) {
            editor.putString(field.getKey(), field.getValue()); editor.remove("pending_" + field.getKey());
        }
        if (!editor.commit()) throw new IllegalStateException("Could not save verified broker settings.");
        return true;
    }

    public void clear() {
        if (!preferences.edit().clear().commit()) {
            throw new IllegalStateException("Could not forget the MQTT broker on this phone.");
        }
    }
}
