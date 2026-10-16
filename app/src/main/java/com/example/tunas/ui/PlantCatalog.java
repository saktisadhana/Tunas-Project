package com.example.tunas.ui;

import java.util.Arrays;
import java.util.List;

/** Starter library entries are reference material, never the user's saved plants. */
public final class PlantCatalog {
    public static final class Species {
        public final String name, botanical, light, water, note;
        public final int days;
        Species(String name, String botanical, String light, String water, int days, String note) {
            this.name = name; this.botanical = botanical; this.light = light;
            this.water = water; this.days = days; this.note = note;
        }
    }
    public static final List<Species> ALL = java.util.Collections.unmodifiableList(Arrays.asList(
        new Species("Monstera", "Monstera deliciosa", "Bright indirect", "Weekly", 7,
            "Check the top layer of soil before watering. Let excess water drain; adjust the schedule to your room and pot."),
        new Species("Snake Plant", "Dracaena trifasciata", "Low light", "Every 3 weeks", 21,
            "Allow the potting mix to dry between waterings. Avoid leaving the pot in standing water."),
        new Species("Pothos", "Epipremnum aureum", "Low light", "Weekly", 7,
            "Check soil before watering. A brighter position may need more frequent care than a shaded room."),
        new Species("Air Plant", "Tillandsia", "Bright indirect", "Twice weekly", 3,
            "Air plants do not use a soil moisture probe. Let the plant dry thoroughly after misting or soaking.")
    ));
    public static Species find(String name) {
        for (Species item : ALL) if (item.name.equals(name)) return item;
        return new Species(name, "Custom plant", "Set for your space", "Custom schedule", 7, "Set a care interval based on your plant's actual conditions.");
    }
    private PlantCatalog() {}
}
