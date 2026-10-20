package com.example.tunas.iot;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.HashMap;
import java.util.Map;

/** One flat JSON object, matching the BLE frame adapter. Unknown keys are dropped. */
final class FlatJson {
    static final String[] KEYS = {"type", "id", "ok", "online", "health", "moisture", "nutrient",
            "sunlight", "temperature", "humidity", "ph", "tds", "nitrogen", "phosphorus", "potassium"};

    private FlatJson() { }

    static Map<String, Object> fields(String text, String[] keys) throws JSONException {
        JSONTokener tokener = new JSONTokener(text);
        Object value = tokener.nextValue();
        if (!(value instanceof JSONObject) || tokener.nextClean() != 0) throw new JSONException("Expected one object");
        JSONObject object = (JSONObject) value;
        Map<String, Object> fields = new HashMap<>();
        for (String key : keys) {
            Object field = object.opt(key);
            fields.put(key, field == JSONObject.NULL ? null : field);
        }
        return fields;
    }
}
