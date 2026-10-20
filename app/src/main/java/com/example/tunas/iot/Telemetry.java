package com.example.tunas.iot;

/** Immutable, optional measurements; timestampMillis is local receipt time, not freshness. */
public final class Telemetry {
    public final Double health;
    public final Double moisture;
    public final Double nutrient;
    public final Double sunlight;
    public final Double temperature;
    public final Double humidity;
    public final Double ph;
    public final Double tds;
    public final Double nitrogen;
    public final Double phosphorus;
    public final Double potassium;
    public final long timestampMillis;

    public Telemetry(Double health, Double moisture, Double nutrient, Double sunlight,
                     Double temperature, Double humidity, long timestampMillis) {
        this(health, moisture, nutrient, sunlight, temperature, humidity,
                null, null, null, null, null, timestampMillis);
    }

    public Telemetry(Double health, Double moisture, Double nutrient, Double sunlight,
                     Double temperature, Double humidity, Double ph, Double tds,
                     Double nitrogen, Double phosphorus, Double potassium, long timestampMillis) {
        check("health", health, 0, 100);
        check("moisture", moisture, 0, 100);
        check("nutrient", nutrient, 0, 5000);
        check("sunlight", sunlight, 0, 100);
        check("temperature", temperature, -40, 85);
        check("humidity", humidity, 0, 100);
        check("ph", ph, 0, 14);
        check("tds", tds, 0, 10000);
        check("nitrogen", nitrogen, 0, 2000);
        check("phosphorus", phosphorus, 0, 2000);
        check("potassium", potassium, 0, 2000);
        if (timestampMillis < 0) throw new IllegalArgumentException("Negative receipt time");
        this.health = health;
        this.moisture = moisture;
        this.nutrient = nutrient;
        this.sunlight = sunlight;
        this.temperature = temperature;
        this.humidity = humidity;
        this.ph = ph;
        this.tds = tds;
        this.nitrogen = nitrogen;
        this.phosphorus = phosphorus;
        this.potassium = potassium;
        this.timestampMillis = timestampMillis;
    }

    private static void check(String name, Double value, double min, double max) {
        if (value != null && (!Double.isFinite(value) || value < min || value > max)) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }
}
