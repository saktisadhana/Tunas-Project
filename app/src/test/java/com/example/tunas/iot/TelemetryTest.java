package com.example.tunas.iot;

import org.junit.Test;
import static org.junit.Assert.*;

public class TelemetryTest {
    @Test public void rejectsNonfiniteOutOfRangeValuesAndNegativeReceiptTime() {
        for (int field = 0; field < 6; field++) {
            final int index = field;
            for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                assertThrows(IllegalArgumentException.class, () -> reading(index, bad));
            }
        }
        for (int field : new int[]{0, 1, 3, 5}) {
            final int index = field;
            assertThrows(IllegalArgumentException.class, () -> reading(index, -0.1));
            assertThrows(IllegalArgumentException.class, () -> reading(index, 100.1));
            reading(index, 0.0);
            reading(index, 100.0);
        }
        assertThrows(IllegalArgumentException.class, () -> reading(2, -0.1));
        assertThrows(IllegalArgumentException.class, () -> reading(2, 5000.1));
        assertThrows(IllegalArgumentException.class, () -> reading(4, -40.1));
        assertThrows(IllegalArgumentException.class, () -> reading(4, 85.1));
        reading(2, 5000.0);
        reading(4, -40.0);
        reading(4, 85.0);
        assertThrows(IllegalArgumentException.class,
                () -> new Telemetry(null, null, null, null, null, null, -1));
    }

    private static Telemetry reading(int field, double value) {
        Double[] v = new Double[6];
        v[field] = value;
        return new Telemetry(v[0], v[1], v[2], v[3], v[4], v[5], 0);
    }

    @Test public void absentMeasurementsStayNullAndReceiptTimeIsPreserved() {
        Telemetry t = new Telemetry(null, 42.5, null, null, 24.0, null, 1234);
        assertNull(t.health);
        assertNull(t.nutrient);
        assertNull(t.sunlight);
        assertNull(t.humidity);
        assertEquals(42.5, t.moisture, 0.0);
        assertEquals(24.0, t.temperature, 0.0);
        assertEquals(1234L, t.timestampMillis);
        assertNull(t.ph);
        assertNull(t.tds);
        assertNull(t.nitrogen);
        assertNull(t.phosphorus);
        assertNull(t.potassium);
    }

    @Test public void optionalPotSensorsStayNullUnlessPresentAndInRange() {
        Telemetry t = new Telemetry(null, null, null, null, null, null, 6.5, 400.0, 10.0, 20.0, 30.0, 5);
        assertEquals(6.5, t.ph, 0.0);
        assertEquals(400.0, t.tds, 0.0);
        assertEquals(10.0, t.nitrogen, 0.0);
        assertEquals(20.0, t.phosphorus, 0.0);
        assertEquals(30.0, t.potassium, 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> new Telemetry(null, null, null, null, null, null, 14.1, null, null, null, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Telemetry(null, null, null, null, null, null, null, 10000.1, null, null, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Telemetry(null, null, null, null, null, null, null, null, 2000.1, null, null, 0));
    }
}
