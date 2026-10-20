package com.example.tunas;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.example.tunas.data.AppStore;
import com.example.tunas.iot.ActuatorGuard;
import com.example.tunas.iot.MqttBrokerConfig;
import com.example.tunas.ui.PlantCatalog;
import com.example.tunas.ui.TunasUi;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.DateFormat;
import java.util.*;

/** Native screens sharing the authored Figma system; plants stay local, sensors use BLE or MQTT. */
final class GardenPages {
    private final MainActivity host;
    private final TunasUi ui;
    private final AppStore store;
    private boolean myPlants;
    private String query = "";
    private int lightFilter, waterFilter;
    private TextView pairStatus, commandStatus, moistureValue, nutrientValue, liveStatus, maintenanceLink, extraReadings;
    private LinearLayout deviceResults, pairBody;
    private Boolean pairBuiltOnline;
    private String livePrefix = "";
    private final Map<String, String> found = new LinkedHashMap<>();
    private final Map<String, String> roomStates = new HashMap<>();
    private final Set<String> collapsed = new HashSet<>();
    private boolean commandPending;
    String routeSpecies = "Monstera", routePlant = "";

    GardenPages(MainActivity host) { this.host = host; ui = host.ui; store = host.store; }

    void saveState(android.os.Bundle out) {
        out.putBoolean("myPlants", myPlants); out.putString("query", query); out.putInt("lightFilter", lightFilter); out.putInt("waterFilter", waterFilter);
    }
    void restoreState(android.os.Bundle state) {
        myPlants = state.getBoolean("myPlants"); query = state.getString("query", ""); lightFilter = state.getInt("lightFilter"); waterFilter = state.getInt("waterFilter");
    }
    void editPlant(String id) { AppStore.Plant plant = findPlant(id); if (plant == null) library(); else plantForm(plant.species, plant); }
    String date(long time) { return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(time)); }
    String roomName(String id) {
        for (AppStore.Room room : store.rooms()) if (room.id.equals(id)) return room.name;
        return "Unassigned";
    }
    private TextView error(LinearLayout target) {
        TextView v = ui.text("", 16, false); v.setTextColor(ui.error);
        v.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); ui.add(target, v); return v;
    }
    private void message(String title, String body) { new MaterialAlertDialogBuilder(host).setTitle(title).setMessage(body).setPositiveButton("OK", null).show(); }
    private void back(LinearLayout column, Runnable action) { ui.add(column, ui.back(action)); ui.gap(column, 20); }
    private Spinner select(String description, String[] options) { return select(description, options, 0); }
    private Spinner select(String description, String[] options, int icon) {
        Spinner spinner = new Spinner(host); spinner.setContentDescription(description); spinner.setMinimumHeight(ui.dp(48));
        if (description.equals("Plant room")) spinner.setId(R.id.plant_room);
        else if (description.equals("Room light")) spinner.setId(R.id.room_light);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(host, android.R.layout.simple_spinner_item, options) {
            @Override public View getView(int position, View convert, ViewGroup parent) {
                TextView v = (TextView)super.getView(position, convert, parent);
                // Fill the 52dp field and centre the label; the platform item is top-aligned and shorter.
                v.setTextColor(0xFF2B3B30); v.setTypeface(ui.body); v.setTextSize(15); v.setSingleLine(true);
                v.setMinHeight(ui.dp(52)); v.setGravity(Gravity.CENTER_VERTICAL | Gravity.START); v.setIncludeFontPadding(false);
                android.graphics.drawable.Drawable arrow = host.getDrawable(android.R.drawable.arrow_down_float).mutate(); arrow.setTint(ui.forest);
                arrow.setBounds(0, 0, ui.dp(12), ui.dp(12));
                android.graphics.drawable.Drawable lead = null;
                if (icon != 0) { lead = host.getDrawable(icon).mutate(); lead.setTint(ui.forest); lead.setBounds(0, 0, ui.dp(18), ui.dp(18)); }
                v.setCompoundDrawablesRelative(lead, null, arrow, null); v.setCompoundDrawablePadding(ui.dp(8));
                v.setPadding(ui.dp(icon == 0 ? 16 : 12), 0, ui.dp(icon == 0 ? 16 : 10), 0); if (icon != 0) v.setTextSize(14); return v;
            }
            @Override public View getDropDownView(int position, View convert, ViewGroup parent) {
                TextView v = (TextView)super.getDropDownView(position, convert, parent);
                v.setTextColor(ui.forest); v.setTypeface(ui.body); v.setTextSize(15); v.setBackground(null);
                v.setMinHeight(ui.dp(48)); v.setPadding(ui.dp(16), 0, ui.dp(16), 0); return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter);
        android.graphics.drawable.GradientDrawable field = ui.shape(0xFFF7F6E4, 14); field.setStroke(ui.dp(1), ui.sage);
        spinner.setBackground(field); spinner.setMinimumHeight(ui.dp(52)); spinner.setPadding(0, 0, 0, 0);
        // Open the list just below the field, on the same cream surface, instead of over it.
        spinner.setPopupBackgroundDrawable(ui.shape(ui.cream, 14)); spinner.setDropDownVerticalOffset(ui.dp(56));
        return spinner;
    }
    private void watch(Spinner spinner, Runnable action) {
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) { action.run(); }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
    private void weight(LinearLayout row, View child, boolean gap) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); if (gap) p.rightMargin = ui.dp(14); row.addView(child, p);
    }
    private LinearLayout panel(LinearLayout column, int padding) {
        LinearLayout card = ui.column(); ui.pad(card, padding); ui.card(card, 10, 5); ui.add(column, card); return card;
    }
    private View artwork(String species, int height) {
        ImageView image = ui.image(species.equals("Monstera") ? R.drawable.figma_monstera : R.drawable.figma_plant_welcome);
        image.setScaleType(species.equals("Monstera") ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        image.setBackground(ui.shape(ui.cream, 10)); image.setClipToOutline(true);
        if (!species.equals("Monstera")) image.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
        image.setLayoutParams(new LinearLayout.LayoutParams(-1, ui.dp(height))); return image;
    }

    void library() {
        LinearLayout column = host.page("plants");
        LinearLayout controls = panel(column, 14);
        EditText search = ui.input("Search plants", InputType.TYPE_CLASS_TEXT);
        search.setContentDescription("Search plants"); search.setText(query);
        search.setBackground(null); search.setTextColor(0xFF2B3B30); search.setHintTextColor(0xB32B3B30); search.setPadding(ui.dp(10), 0, ui.dp(12), 0);
        LinearLayout field = ui.row(); field.setBackground(ui.shape(ui.sage, 26)); field.setElevation(ui.dp(2)); field.setPadding(ui.dp(4), ui.dp(4), 0, ui.dp(4));
        ImageView lens = ui.image(android.R.drawable.ic_menu_search); lens.setColorFilter(ui.cream); lens.setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9));
        lens.setBackground(ui.shape(ui.forest, 24)); field.addView(lens, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)));
        field.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout searchRow = ui.row(); searchRow.addView(field, new LinearLayout.LayoutParams(0, -2, 1));
        Runnable toggle = () -> { myPlants = !myPlants; query = ""; library(); };
        Button collection = ui.button(myPlants ? "Library" : "My Plants", true, toggle);
        if (myPlants) { ui.clickable(collection, ui.outlined(ui.cream, 26, ui.olive), 26, toggle); collection.setTextColor(ui.olive); }
        else ui.clickable(collection, ui.olive, 26, toggle);
        collection.setTextSize(12); collection.setPadding(ui.dp(8), ui.dp(10), ui.dp(8), ui.dp(10));
        LinearLayout.LayoutParams collectionParams = new LinearLayout.LayoutParams(ui.dp(92), ui.dp(52)); collectionParams.leftMargin = ui.dp(8);
        searchRow.addView(collection, collectionParams); ui.add(controls, searchRow); ui.gap(controls, 12);
        LinearLayout filters = ui.row();
        Spinner light = select("Filter by light", new String[]{"Any light", "Bright indirect", "Low light"}, R.drawable.figma_sun);
        Spinner water = select("Filter by watering", new String[]{"Any watering", "Weekly", "Less often"}, R.drawable.figma_humidity);
        light.setBackground(ui.shape(ui.sage, 26)); water.setBackground(ui.shape(ui.sage, 26)); light.setElevation(ui.dp(2)); water.setElevation(ui.dp(2));
        light.setSelection(lightFilter); water.setSelection(waterFilter); weight(filters, light, true); weight(filters, water, false); ui.add(controls, filters);
        ui.gap(column, 14); LinearLayout results = ui.column(); ui.add(column, results);
        Runnable render = () -> renderLibrary(results);
        watch(light, () -> { lightFilter = light.getSelectedItemPosition(); render.run(); });
        watch(water, () -> { waterFilter = water.getSelectedItemPosition(); render.run(); });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) { query = s.toString(); render.run(); }
            public void afterTextChanged(Editable s) {}
        });
        render.run();
    }
    private void renderLibrary(LinearLayout results) {
        results.removeAllViews(); List<View> cards = new ArrayList<>();
        if (myPlants) {
            for (AppStore.Plant p : store.plants()) if (matches(p.name, PlantCatalog.find(p.species))) cards.add(plantCard(p.name, PlantCatalog.find(p.species), () -> savedDetail(p.id)));
        } else {
            for (PlantCatalog.Species p : PlantCatalog.ALL) if (matches(p.name, p)) cards.add(plantCard(p.name, p, () -> speciesDetail(p.name)));
        }
        if (cards.isEmpty()) {
            LinearLayout empty = panel(results, 20);
            ui.add(empty, ui.text(myPlants && store.plants().isEmpty() ? "No plants saved yet" : "No matching plants", 20, true)); ui.gap(empty, 10);
            ui.add(empty, ui.text("Try another search or choose a plant from the library.", 16, false)); ui.gap(empty, 16);
            ui.add(empty, ui.button("Reset filters", false, () -> { query = ""; lightFilter = waterFilter = 0; myPlants = false; library(); }));
        }
        boolean single = host.getResources().getConfiguration().screenWidthDp < 350 || host.getResources().getConfiguration().fontScale > 1.3f;
        for (int i = 0; i < cards.size(); i += single ? 1 : 2) {
            LinearLayout row = ui.row(); row.setGravity(Gravity.TOP);
            weight(row, cards.get(i), !single);
            if (!single) weight(row, i + 1 < cards.size() ? cards.get(i + 1) : new View(host), false);
            ui.add(results, row); ui.gap(results, 12);
        }
        TextView footnote = ui.caption(myPlants ? "Saved on this phone" : "These schedules are starting points. Adjust them to your room and pot.");
        footnote.setGravity(Gravity.CENTER); ui.add(results, footnote);
    }
    private boolean matches(String name, PlantCatalog.Species p) {
        return (name + " " + p.name).toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT))
            && (lightFilter == 0 || p.light.equals(lightFilter == 1 ? "Bright indirect" : "Low light"))
            && (waterFilter == 0 || (waterFilter == 1 ? p.days == 7 : p.days > 7));
    }
    private View plantCard(String name, PlantCatalog.Species p, Runnable action) {
        LinearLayout card = ui.column(); ui.pad(card, 7); ui.card(card, 10, 5); card.setMinimumHeight(ui.dp(183));
        ui.clickable(card, ui.surface, 10, action); ui.add(card, artwork(p.name, 81)); ui.gap(card, 10);
        TextView title = ui.lifted(ui.text(name, 16, true)); title.setPadding(ui.dp(2), 0, 0, 0); ui.add(card, title); ui.gap(card, 10);
        iconLabel(card, R.drawable.figma_sun, p.light); ui.gap(card, 8); iconLabel(card, R.drawable.figma_humidity, p.water);
        ui.gap(card, 8); return card;
    }
    private void iconLabel(LinearLayout target, int resource, String text) {
        LinearLayout line = ui.row(); line.addView(ui.image(resource), new LinearLayout.LayoutParams(ui.dp(16), ui.dp(16)));
        TextView label = ui.text(text, 13, false); label.setTextColor(ui.muted); label.setPadding(ui.dp(10), 0, 0, 0);
        line.addView(label, new LinearLayout.LayoutParams(0, -2, 1)); ui.add(target, line);
    }
    void speciesDetail(String name) {
        routeSpecies = name; routePlant = ""; PlantCatalog.Species p = PlantCatalog.find(name);
        LinearLayout column = host.page("species"); back(column, this::library);
        View hero = artwork(name, 220); hero.setElevation(ui.dp(4)); ui.add(column, hero); ui.gap(column, 22); ui.add(column, ui.title(p.name));
        TextView botanical = ui.text(p.botanical, 18, false); botanical.setTypeface(Typeface.create(ui.body, 400, true)); botanical.setTextColor(ui.muted);
        ui.gap(column, 4); ui.add(column, botanical); ui.gap(column, 22);
        LinearLayout notes = panel(column, 18);
        iconLabel(notes, R.drawable.figma_sun, p.light); ui.gap(notes, 16); iconLabel(notes, R.drawable.figma_humidity, p.water);
        ui.gap(notes, 20); ui.add(notes, ui.text("Care notes", 20, true)); ui.gap(notes, 10); ui.add(notes, ui.text(p.note, 17, false));
        ui.divider(notes, 14); ui.add(notes, ui.caption("Check the soil before you water. If you have pets, look up whether this plant is safe for them."));
        ui.gap(column, 22); ui.add(column, ui.button("Add to My Plants", true, () -> plantForm(name, null)));
    }
    private AppStore.Plant findPlant(String id) { for (AppStore.Plant p : store.plants()) if (p.id.equals(id)) return p; return null; }
    void savedDetail(String id) {
        AppStore.Plant plant = findPlant(id); if (plant == null) { library(); return; }
        routePlant = id; routeSpecies = plant.species;
        LinearLayout column = host.page("plant"); back(column, () -> { myPlants = true; library(); });
        View hero = artwork(plant.species, 180); hero.setElevation(ui.dp(4)); ui.add(column, hero); ui.gap(column, 20); ui.add(column, ui.title(plant.name));
        TextView subtitle = ui.text(plant.species + ", " + roomName(plant.roomId), 18, false); subtitle.setTextColor(ui.muted);
        ui.gap(column, 4); ui.add(column, subtitle); ui.gap(column, 20);
        LinearLayout schedule = panel(column, 18);
        TextView scheduleTitle = ui.text("Watering Schedule", 20, true); ui.lead(scheduleTitle, R.drawable.figma_clock, ui.forest, 22);
        ui.add(schedule, scheduleTitle); ui.divider(schedule, 12);
        ui.add(schedule, ui.text("Next check: " + date(plant.nextWateringMillis), 17, false));
        ui.add(schedule, ui.text("Every " + plant.waterEveryDays + " days", 16, false)); ui.gap(schedule, 12);
        ui.add(schedule, ui.text(plant.lastWateredMillis == 0 ? "No watering logged yet" : "Last watered: " + date(plant.lastWateredMillis), 16, false));
        if (!store.notificationsEnabled()) {
            // Inline, non-blocking invite to reminders where the schedule is shown.
            ui.divider(schedule, 14); ui.add(schedule, ui.caption("Tunas can remind you when this plant is due. You can log the watering from the notification."));
            ui.gap(schedule, 10); Button remind = ui.button("Turn on reminders", false, host::toggleReminders); remind.setTextSize(14); ui.lead(remind, R.drawable.ic_stat_tunas, ui.olive, 16);
            ui.add(schedule, remind);
        }
        ui.gap(column, 20);
        ui.add(column, ui.button("Log watering", true, () -> new MaterialAlertDialogBuilder(host).setTitle("Did you water " + plant.name + "?")
            .setMessage("Tunas marks it watered today and moves the next check forward. No pump runs.").setNegativeButton("Cancel", null)
            .setPositiveButton("Log Watering", (d, w) -> mutate(() -> { store.markWatered(id); savedDetail(id); })).show()));
        ui.gap(column, 12); LinearLayout secondary = ui.row();
        weight(secondary, ui.button("Edit plant", false, () -> plantForm(plant.species, plant)), true);
        weight(secondary, ui.button("Care notes", false, () -> speciesDetail(plant.species)), false); ui.add(column, secondary);
        ui.gap(column, 12); Runnable confirmDelete = () -> new MaterialAlertDialogBuilder(host).setTitle("Delete “" + plant.name + "”?")
            .setMessage("Tunas removes its schedule and watering history from this phone.").setNegativeButton("Cancel", null)
            .setPositiveButton("Delete", (d, w) -> mutate(() -> { store.deletePlant(id); myPlants = true; library(); })).show();
        Button delete = ui.button("Delete plant", false, confirmDelete); ui.clickable(delete, 0x00000000, 14, confirmDelete);
        delete.setTextColor(ui.error); ui.add(column, delete);
    }
    void plantForm(String species, AppStore.Plant editing) {
        routeSpecies = species; routePlant = editing == null ? "" : editing.id;
        LinearLayout column = host.page(editing == null ? "addPlant" : "editPlant");
        back(column, () -> { if (editing == null) speciesDetail(species); else savedDetail(editing.id); });
        ui.add(column, ui.title(editing == null ? "Add Plant" : "Edit Plant")); ui.gap(column, 12);
        ui.add(column, ui.text(species, 20, true)); ui.gap(column, 22);
        EditText nickname = ui.input("Plant nickname", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nickname.setContentDescription("Plant nickname"); nickname.setText(editing == null ? species : editing.name); host.field(column, "Nickname", nickname);
        List<AppStore.Room> rooms = store.rooms(); String[] names = new String[rooms.size() + 1]; names[0] = "Unassigned";
        for (int i = 0; i < rooms.size(); i++) names[i + 1] = rooms.get(i).name;
        Spinner room = select("Plant room", names);
        if (editing != null) for (int i = 0; i < rooms.size(); i++) if (rooms.get(i).id.equals(editing.roomId)) room.setSelection(i + 1);
        host.field(column, "Room", room);
        ui.add(column, ui.button("Add a room", false, () -> {
            android.util.SparseArray<android.os.Parcelable> draft = new android.util.SparseArray<>();
            host.root.saveHierarchyState(draft);
            roomForm(() -> { plantForm(species, editing); host.root.restoreHierarchyState(draft); });
        })); ui.gap(column, 20);
        EditText days = ui.input("Days between checks", InputType.TYPE_CLASS_NUMBER); days.setContentDescription("Watering interval in days");
        days.setText(String.valueOf(editing == null ? PlantCatalog.find(species).days : editing.waterEveryDays)); host.field(column, "Days between watering (1 to 365)", days);
        ui.add(column, ui.text("Tunas reminds you to check the soil on this schedule. The schedule doesn't run any pump.", 16, false)); ui.gap(column, 16);
        TextView feedback = error(column); ui.gap(column, 12);
        ui.add(column, ui.button("Save Plant", true, () -> {
            try {
                int interval = Integer.parseInt(days.getText().toString()); int selection = room.getSelectedItemPosition();
                String roomId = selection == 0 ? "" : rooms.get(selection - 1).id;
                AppStore.Plant saved;
                if (editing == null) saved = store.addPlant(species, nickname.getText().toString(), roomId, interval);
                else { store.updatePlant(editing.id, nickname.getText().toString(), roomId, interval); saved = findPlant(editing.id); }
                host.hideKeyboard(); savedDetail(saved.id);
            } catch (NumberFormatException e) { feedback.setText("Enter a whole number from 1 to 365."); }
            catch (IllegalArgumentException | IllegalStateException e) { feedback.setText(e.getMessage()); }
        }));
    }
    void roomForm(Runnable after) {
        LinearLayout column = host.page("addRoom"); back(column, after); ui.add(column, ui.title("Add Room")); ui.gap(column, 24);
        EditText name = ui.input("e.g. Living Room", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS); name.setContentDescription("Room name"); host.field(column, "Room name", name);
        Spinner light = select("Room light", new String[]{"Low", "Medium", "Bright"}); light.setSelection(1); host.field(column, "Light conditions", light);
        ui.add(column, ui.text("Group plants by where they live, so you can see one room's plants together.", 17, false)); ui.gap(column, 20);
        TextView feedback = error(column); ui.add(column, ui.button("Create Room", true, () -> {
            try { store.addRoom(name.getText().toString(), light.getSelectedItem().toString()); host.hideKeyboard(); after.run(); }
            catch (IllegalArgumentException | IllegalStateException e) { feedback.setText(e.getMessage()); }
        }));
    }
    void tasks() {
        LinearLayout column = host.page("tasks"); back(column, host::showHome); ui.add(column, ui.title("Care Schedule")); ui.gap(column, 20);
        List<AppStore.Plant> plants = new ArrayList<>(store.plants()); plants.sort(Comparator.comparingLong(p -> p.nextWateringMillis));
        if (plants.isEmpty()) { ui.add(column, ui.text("You haven't added any plants yet.", 18, false)); ui.gap(column, 16); ui.add(column, ui.button("Browse plants", true, this::library)); }
        for (AppStore.Plant p : plants) {
            LinearLayout card = panel(column, 18); TextView check = ui.text("Check “" + p.name + "”", 20, true); ui.lead(check, R.drawable.figma_clock, ui.forest, 22);
            ui.add(card, check); ui.gap(card, 10);
            ui.add(card, ui.text(date(p.nextWateringMillis), 17, false)); ui.gap(card, 4); ui.add(card, ui.caption(roomName(p.roomId))); ui.gap(card, 14);
            ui.add(card, ui.button("View plant", false, () -> savedDetail(p.id))); ui.gap(column, 14);
        }
    }

    void maintenance() {
        LinearLayout column = host.page("maintenance");
        moistureValue = nutrientValue = liveStatus = commandStatus = maintenanceLink = extraReadings = null;
        LinearLayout status = ui.row(); status.setPadding(ui.dp(18), ui.dp(12), ui.dp(12), ui.dp(12)); ui.card(status, 10, 5);
        TextView online = ui.lifted(ui.text(host.linkUp() ? "1 Sensor Online" : "0 Sensors Online", 20, true));
        status.addView(online, new LinearLayout.LayoutParams(0, -2, 1));
        maintenanceLink = ui.text(host.linkLabel(), 13, true);
        maintenanceLink.setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6));
        maintenanceLink.setBackground(ui.shape(0x59A2B595, 40));
        maintenanceLink.setContentDescription("Connection " + host.linkLabel());
        LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(-2, -2);
        linkParams.rightMargin = ui.dp(8);
        status.addView(maintenanceLink, linkParams);
        Button pair = ui.button(host.linkUp() ? "Manage sensor" : "Pair Sensor", false, this::pair); pair.setTextSize(14);
        ui.clickable(pair, ui.sage, 26, this::pair); pair.setTextColor(ui.olive); pair.setElevation(ui.dp(3));
        pair.setMinWidth(0); pair.setMinimumWidth(0); pair.setPadding(ui.dp(16), 0, ui.dp(16), 0);
        status.addView(pair, new LinearLayout.LayoutParams(-2, ui.dp(48))); ui.add(column, status); ui.gap(column, 22);
        if (store.rooms().isEmpty()) {
            LinearLayout empty = panel(column, 20); TextView none = ui.text("No rooms yet", 22, true); ui.lead(none, R.drawable.figma_home, ui.forest, 22); ui.add(empty, none); ui.gap(empty, 12);
            ui.add(empty, ui.text("Add the room your plants are in. Then pair a sensor to see its readings here.", 17, false));
            ui.gap(column, 16);
        }
        for (AppStore.Room room : store.rooms()) {
            boolean linked = room.id.equals(host.linkedRoom);
            LinearLayout card = panel(column, 18);
            LinearLayout header = ui.row();
            ImageView roomIcon = ui.image(R.drawable.figma_home); roomIcon.setColorFilter(ui.forest); roomIcon.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12)); roomIcon.setBackground(ui.shape(ui.sage, 16));
            header.addView(roomIcon, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
            LinearLayout identity = ui.column(); identity.setPadding(ui.dp(12), 0, 0, 0);
            ui.add(identity, ui.text(room.name, 17, true)); ui.gap(identity, 3);
            int count = 0; for (AppStore.Plant p : store.plants()) if (p.roomId.equals(room.id)) count++;
            String presence = linked ? host.linkLabel() : "no sensor";
            TextView roomStatus = ui.caption(count + (count == 1 ? " plant" : " plants") + ", " + presence);
            ui.add(identity, roomStatus); header.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
            ImageView expand = ui.iconButton(collapsed.contains(room.id) ? android.R.drawable.arrow_down_float : android.R.drawable.arrow_up_float,
                "Expand or collapse " + room.name, () -> { if (!collapsed.remove(room.id)) collapsed.add(room.id); maintenance(); });
            header.addView(expand);
            TextView more = ui.text("⋮", 22, true); more.setGravity(Gravity.CENTER); more.setContentDescription("Room options for " + room.name);
            more.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x26454D39), null, ui.shape(0xFFFFFFFF, 24)));
            more.setFocusable(true); more.setClickable(true);
            more.setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(host, more); menu.getMenu().add("Assign sensor"); menu.getMenu().add("Delete room");
                menu.setOnMenuItemClickListener(item -> {
                    if (item.getTitle().equals("Assign sensor")) { host.assignRoom(room.id); pair(); }
                    else new MaterialAlertDialogBuilder(host).setTitle("Delete “" + room.name + "”?")
                        .setMessage("Move its plants to another room first. Tunas can't delete a room that still has plants.").setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d, w) -> mutate(() -> { store.deleteRoom(room.id); if (linked) host.assignRoom(""); maintenance(); })).show();
                    return true;
                }); menu.show();
            });
            header.addView(more, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(48))); ui.add(card, header);
            if (!collapsed.contains(room.id)) {
                ui.divider(card, 14);
                TextView moistureLabel = ui.text("Moisture Level", 16, false); ui.lead(moistureLabel, R.drawable.figma_moisture, ui.forest, 18);
                LinearLayout row = ui.row(); row.addView(moistureLabel, new LinearLayout.LayoutParams(0, -2, 1));
                TextView moisture = ui.lifted(ui.text("--", 26, true)); row.addView(moisture); ui.add(card, row); if (linked) moistureValue = moisture;
                ui.gap(card, 10);
                SeekBar target = new SeekBar(host); target.setMin(0); target.setMax(100); target.setProgress(40); target.setContentDescription("Moisture target for " + room.name);
                target.setMinimumHeight(ui.dp(48)); target.setProgressTintList(android.content.res.ColorStateList.valueOf(ui.forest)); target.setThumbTintList(android.content.res.ColorStateList.valueOf(ui.forest));
                target.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.sage)); target.setPadding(ui.dp(10), 0, ui.dp(10), 0); ui.add(card, target);
                LinearLayout ends = ui.row(); TextView drier = ui.caption("Drier"); TextView wetter = ui.caption("Wetter"); wetter.setGravity(Gravity.END);
                ends.addView(drier, new LinearLayout.LayoutParams(0, -2, 1)); ends.addView(wetter, new LinearLayout.LayoutParams(0, -2, 1)); ui.add(card, ends); ui.gap(card, 10);
                LinearLayout targetRow = ui.row(); TextView targetLabel = ui.text("Target 40%, not sent", 14, false);
                targetRow.addView(targetLabel, new LinearLayout.LayoutParams(0, -2, 1));
                Button sendTarget = ui.button("Send target", false, () -> send(room.id, "target_moisture", target.getProgress())); sendTarget.setTextSize(13);
                sendTarget.setMinHeight(ui.dp(44)); sendTarget.setMinimumHeight(ui.dp(44)); sendTarget.setPadding(0, 0, 0, 0);
                targetRow.addView(sendTarget, new LinearLayout.LayoutParams(ui.dp(105), -2)); ui.add(card, targetRow);
                target.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar bar, int value, boolean user) { targetLabel.setText("Target " + value + "%, not sent"); }
                    public void onStartTrackingTouch(SeekBar bar) {} public void onStopTrackingTouch(SeekBar bar) {}
                });
                ui.gap(card, 16); LinearLayout nutrientRow = ui.row();
                TextView nutrientLabel = ui.text("Nutrient Level", 16, false); ui.lead(nutrientLabel, R.drawable.figma_nutrient, ui.forest, 18);
                nutrientRow.addView(nutrientLabel, new LinearLayout.LayoutParams(0, -2, 1));
                TextView nutrient = ui.text("-- µS/cm", 14, true); nutrient.setBackground(ui.shape(0x59A2B595, 20)); nutrient.setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6));
                nutrientRow.addView(nutrient); ui.add(card, nutrientRow); if (linked) nutrientValue = nutrient;
                if (linked) {
                    extraReadings = ui.caption("");
                    extraReadings.setVisibility(View.GONE);
                    ui.gap(card, 8); ui.add(card, extraReadings);
                }
                ui.gap(card, 18); LinearLayout actions = ui.row();
                Button water = ui.button("Start Watering", true, () -> send(room.id, "water", 5)); water.setTextSize(14); ui.lead(water, R.drawable.figma_moisture, ui.cream, 16);
                Button nutrition = ui.button("Start Nutrition", false, () -> send(room.id, "nutrition", 5)); nutrition.setTextSize(14); ui.lead(nutrition, R.drawable.figma_nutrient, ui.olive, 16);
                water.setPadding(ui.dp(10), ui.dp(12), ui.dp(10), ui.dp(12)); nutrition.setPadding(ui.dp(10), ui.dp(12), ui.dp(10), ui.dp(12));
                weight(actions, water, true); weight(actions, nutrition, false); ui.add(card, actions);
                if (linked) {
                    livePrefix = count + (count == 1 ? " plant" : " plants") + ", ";
                    liveStatus = roomStatus;
                    commandStatus = ui.caption(roomStates.getOrDefault(room.id, "Tunas asks before it runs the pump."));
                    commandStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); ui.gap(card, 12); ui.add(card, commandStatus);
                }
            }
            ui.gap(column, 16);
        }
        ui.add(column, ui.button("Add Room", false, () -> roomForm(this::maintenance))); refreshReadings();
    }
    private ActuatorGuard.Block guard(String room) {
        return ActuatorGuard.assess(room.equals(host.linkedRoom), host.linkUp(), host.telemetry != null, host.readingAge(), commandPending);
    }
    private void send(String room, String action, int value) {
        ActuatorGuard.Block block = guard(room);
        if (block == ActuatorGuard.Block.OFFLINE) { message("No sensor connected", "Pair a sensor and assign it to this room first. Tunas didn't send anything."); return; }
        if (block == ActuatorGuard.Block.STALE) { message("No recent readings", "The sensor hasn't reported in the last 30 seconds. Tunas didn't send anything."); return; }
        if (block == ActuatorGuard.Block.BUSY) { message("Still waiting", "The sensor hasn't confirmed your last command. Try again once it does."); return; }
        boolean target = action.equals("target_moisture");
        boolean pump = action.equals("water");
        new MaterialAlertDialogBuilder(host).setTitle(target ? "Set moisture target to " + value + "%?" : pump ? "Water for 5 seconds?" : "Add nutrients for 5 seconds?")
            .setMessage((target ? "The sensor's firmware may water the plant to reach this level. " : "The pump runs for 5 seconds. Check the tubing and the reservoir first. ")
                + "Use this only with a sensor running the Tunas firmware. Tunas can't check that your pump is set up safely.")
            .setNegativeButton("Cancel", null).setPositiveButton(target ? "Set target" : pump ? "Water now" : "Add nutrients", (d, w) -> {
                if (guard(room) != ActuatorGuard.Block.NONE) { message("Sensor disconnected", "Tunas didn't send anything. Check the sensor and try again."); return; }
                commandPending = true; if (commandStatus != null) commandStatus.setText("Waiting for the sensor to confirm"); host.sendDeviceCommand(action, value);
            }).show();
    }
    void refreshReadings() {
        if (!host.screen.equals("maintenance")) return;
        boolean fresh = host.fresh();
        if (moistureValue != null) moistureValue.setText(fresh && host.telemetry.moisture != null ? String.format(Locale.getDefault(), "%.0f%%", host.telemetry.moisture) : "--");
        if (nutrientValue != null) nutrientValue.setText((fresh && host.telemetry.nutrient != null ? String.format(Locale.getDefault(), "%.0f", host.telemetry.nutrient) : "--") + " µS/cm");
        if (liveStatus != null) liveStatus.setText(livePrefix + host.linkLabel());
        if (maintenanceLink != null) {
            maintenanceLink.setText(host.linkLabel());
            maintenanceLink.setContentDescription("Connection " + host.linkLabel());
        }
        if (extraReadings != null) {
            String extras = fresh ? sensorExtras(host.telemetry) : "";
            extraReadings.setText(extras);
            extraReadings.setVisibility(extras.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }
    private String sensorExtras(com.example.tunas.iot.Telemetry reading) {
        if (reading == null) return "";
        StringBuilder text = new StringBuilder();
        appendReading(text, "pH", reading.ph, 1);
        appendReading(text, "TDS", reading.tds, 0);
        appendReading(text, "N", reading.nitrogen, 0);
        appendReading(text, "P", reading.phosphorus, 0);
        appendReading(text, "K", reading.potassium, 0);
        return text.toString();
    }
    private void appendReading(StringBuilder text, String label, Double value, int decimals) {
        if (value == null) return;
        if (text.length() > 0) text.append(" · ");
        text.append(label).append(' ').append(String.format(Locale.getDefault(), decimals == 1 ? "%.1f" : "%.0f", value));
    }
    void commandResult(boolean success, String message) {
        commandPending = false; String display = (success ? "Sensor confirmed: " : "Sensor didn't confirm: ") + message;
        roomStates.put(host.linkedRoom, display);
        if (host.screen.equals("maintenance") && commandStatus != null) commandStatus.setText(display);
        else if (!host.isFinishing() && !host.isDestroyed()) Toast.makeText(host, display, Toast.LENGTH_LONG).show();
    }

    void pair() {
        LinearLayout column = host.page("pair"); back(column, this::maintenance); ui.add(column, ui.title("Pair Sensor")); ui.gap(column, 12);
        ui.add(column, ui.text("Use Bluetooth when the pot is nearby. To reach it from another network, connect over MQTT with TLS. Tunas works with sensors running the Tunas firmware.", 17, false)); ui.gap(column, 20);
        pairStatus = ui.text(pairStatusText(), 17, true); pairStatus.setBackground(ui.shape(0x59A2B595, 14)); ui.pad(pairStatus, 14); pairStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); ui.add(column, pairStatus); ui.gap(column, 16);
        pairBody = ui.column(); ui.add(column, pairBody); deviceResults = null; pairBuiltOnline = null;
        connectionChanged();
    }
    void connectionChanged() {
        if (!host.screen.equals("pair") || pairBody == null || pairStatus == null) return;
        pairStatus.setText(pairStatusText());
        boolean online = host.linkUp();
        if (pairBuiltOnline != null && pairBuiltOnline == online && pairBody.getChildCount() > 0) return;
        pairBuiltOnline = online;
        pairBody.removeAllViews();
        if (host.mqttOnline()) buildAssignedLink("MQTT", "Connected over MQTT. Readings show up on Home after the pot sends a report.", () -> host.mqtt.disconnect());
        else if (host.bleOnline()) buildAssignedLink("BLE", "Connected over Bluetooth. Readings show up on Home after the sensor sends its first report.", () -> host.device.disconnect());
        else buildOfflinePairing();
    }
    private String pairStatusText() {
        if (host.mqttOnline()) return "MQTT";
        if (host.bleOnline()) return "BLE";
        return host.connectionStatus == null || host.connectionStatus.isEmpty() ? "Offline" : host.connectionStatus;
    }
    private void buildAssignedLink(String transport, String detail, Runnable disconnect) {
        deviceResults = null;
        ui.add(pairBody, ui.text(transport, 20, true)); ui.gap(pairBody, 8);
        ui.add(pairBody, ui.text(detail, 17, false)); ui.gap(pairBody, 18);
        List<AppStore.Room> rooms = store.rooms(); String[] labels = new String[rooms.size() + 1]; labels[0] = "Unassigned";
        for (int i = 0; i < rooms.size(); i++) labels[i + 1] = rooms.get(i).name;
        Spinner room = select("Assign sensor to room", labels);
        for (int i = 0; i < rooms.size(); i++) if (rooms.get(i).id.equals(host.linkedRoom)) room.setSelection(i + 1);
        host.field(pairBody, "Assign to room", room);
        ui.add(pairBody, ui.button("Done", true, () -> { int i = room.getSelectedItemPosition(); host.assignRoom(i == 0 ? "" : rooms.get(i - 1).id); maintenance(); }));
        ui.gap(pairBody, 12); ui.add(pairBody, ui.button("Disconnect", false, disconnect));
    }
    private void buildOfflinePairing() {
        ui.add(pairBody, ui.text("Bluetooth", 20, true)); ui.gap(pairBody, 8);
        ui.add(pairBody, ui.button("Scan nearby devices", true, () -> { found.clear(); renderDevices(); host.requestScan(); }));
        ui.gap(pairBody, 12); LinearLayout tools = ui.row();
        weight(tools, ui.button("Stop scan", false, () -> host.device.stopScan()), true);
        weight(tools, ui.button("Bluetooth settings", false, () -> host.startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))), false); ui.add(pairBody, tools);
        ui.gap(pairBody, 16);
        deviceResults = ui.column(); ui.add(pairBody, deviceResults); renderDevices();
        ui.gap(pairBody, 8); ui.divider(pairBody, 8);
        ui.add(pairBody, ui.text("MQTT", 20, true)); ui.gap(pairBody, 8);
        ui.add(pairBody, ui.text("Broker settings stay encrypted on this phone. Tunas always uses TLS and never stores a built-in broker.", 16, false)); ui.gap(pairBody, 16);
        MqttBrokerConfig saved = host.mqttSettings == null ? null : host.mqttSettings.load();
        EditText hostName = ui.input("Broker host", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        hostName.setContentDescription("Broker host");
        EditText port = ui.input("8883", InputType.TYPE_CLASS_NUMBER);
        port.setContentDescription("Broker port");
        port.setText(saved == null ? "8883" : Integer.toString(saved.port));
        EditText username = ui.input("Username", InputType.TYPE_CLASS_TEXT);
        username.setContentDescription("Broker username");
        EditText password = ui.input("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setContentDescription("Broker password");
        EditText deviceId = ui.input("Device ID", InputType.TYPE_CLASS_TEXT);
        deviceId.setContentDescription("Device ID");
        if (saved != null) {
            hostName.setText(saved.host);
            username.setText(saved.username);
            deviceId.setText(saved.deviceId);
        }
        host.field(pairBody, "Broker host", hostName);
        host.field(pairBody, "Port", port);
        host.field(pairBody, "Username", username);
        host.field(pairBody, "Password", password);
        if (saved != null && !saved.password.isEmpty()) {
            ui.add(pairBody, ui.caption("A password is already saved on this phone. Leave this blank to keep it."));
            ui.gap(pairBody, 12);
        }
        host.field(pairBody, "Device ID", deviceId);
        if (host.mqttSettings == null) ui.add(pairBody, ui.caption("Secure storage isn't available, so the broker password can't be saved."));
        TextView feedback = error(pairBody);
        ui.add(pairBody, ui.button("Connect over MQTT", true, () -> connectMqtt(saved, hostName, port, username, password, deviceId, feedback)));
        if (saved != null) {
            ui.gap(pairBody, 12);
            ui.add(pairBody, ui.button("Forget saved broker", false, () -> {
                try {
                    if (host.mqttSettings != null) host.mqttSettings.clear();
                    if (host.mqtt != null) host.mqtt.disconnect();
                    pair();
                } catch (IllegalStateException e) { feedback.setText(e.getMessage()); }
            }));
        }
    }
    private void connectMqtt(MqttBrokerConfig saved, EditText hostName, EditText port, EditText username, EditText password, EditText deviceId, TextView feedback) {
        String typedPassword = password.getText().toString();
        String passwordToUse = typedPassword;
        int portNumber;
        try {
            portNumber = Integer.parseInt(port.getText().toString().trim());
        } catch (NumberFormatException invalid) {
            feedback.setText("Enter a broker port from 1 to 65535. TLS usually uses 8883.");
            return;
        }
        if (typedPassword.isEmpty() && saved != null && !saved.password.isEmpty()) {
            try {
                MqttBrokerConfig typed = MqttBrokerConfig.create(hostName.getText().toString(), portNumber, username.getText().toString(), "", deviceId.getText().toString());
                if (typed.host.equals(saved.host) && typed.port == saved.port && typed.username.equals(saved.username) && typed.deviceId.equals(saved.deviceId)) {
                    passwordToUse = saved.password;
                } else {
                    feedback.setText("Enter the broker password. A saved password is only reused for the same host, username and device.");
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // The create call below reports the field error.
            }
        }
        try {
            MqttBrokerConfig config = MqttBrokerConfig.create(hostName.getText().toString(), portNumber, username.getText().toString(), passwordToUse, deviceId.getText().toString());
            if (host.mqttSettings != null) host.mqttSettings.save(config);
            host.hideKeyboard();
            feedback.setText("");
            host.connectMqtt(config);
        } catch (IllegalArgumentException | IllegalStateException e) {
            feedback.setText(e.getMessage());
        }
    }
    void deviceFound(String address, String name, int rssi) {
        found.put(address, name + "\n" + address + ", signal " + rssi + " dBm"); if (host.screen.equals("pair")) renderDevices();
    }
    private void renderDevices() {
        if (deviceResults == null || !host.screen.equals("pair") || host.linkUp()) return;
        deviceResults.removeAllViews();
        if (found.isEmpty()) ui.add(deviceResults, ui.caption("No sensors found yet. Move closer to the sensor and scan again."));
        for (Map.Entry<String, String> item : found.entrySet()) {
            LinearLayout card = panel(deviceResults, 16); ui.add(card, ui.text(item.getValue(), 16, false)); ui.gap(card, 12);
            ui.add(card, ui.button("Connect", false, () -> host.connectBle(item.getKey()))); ui.gap(deviceResults, 14);
        }
    }
    void profile() {
        LinearLayout column = host.page("profile"); ui.add(column, ui.title("Profile")); ui.gap(column, 24);
        LinearLayout identity = ui.row(); ui.pad(identity, 18); ui.card(identity, 10, 5); ui.add(column, identity);
        ImageView avatar = ui.image(R.drawable.figma_profile); avatar.setColorFilter(ui.cream); avatar.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16));
        avatar.setBackground(ui.shape(ui.sage, 40)); avatar.setElevation(ui.dp(2)); identity.addView(avatar, new LinearLayout.LayoutParams(ui.dp(68), ui.dp(68)));
        LinearLayout who = ui.column(); who.setPadding(ui.dp(16), 0, 0, 0); identity.addView(who, new LinearLayout.LayoutParams(0, -2, 1));
        ui.add(who, ui.lifted(ui.text(store.name(), 24, true))); ui.gap(who, 4); ui.add(who, ui.text(store.email(), 16, false));
        ui.gap(who, 8); ui.add(who, ui.caption("Stored on this phone")); ui.gap(column, 20);
        LinearLayout menu = panel(column, 6);
        ui.add(menu, ui.listRow(R.drawable.figma_plant, "My Plants (" + store.plants().size() + ")", () -> { myPlants = true; query = ""; lightFilter = waterFilter = 0; library(); })); ui.divider(menu, 0);
        ui.add(menu, ui.listRow(R.drawable.figma_profile, "Profile Settings", this::settings)); ui.divider(menu, 0);
        ui.add(menu, ui.listRow(R.drawable.figma_clock, "Care Schedule", this::tasks)); ui.divider(menu, 0);
        ui.add(menu, ui.listRow(R.drawable.ic_stat_tunas, "Care reminders: " + (store.notificationsEnabled() ? "on" : "off"), host::toggleReminders)); ui.divider(menu, 0);
        ui.add(menu, ui.listRow(R.drawable.figma_wrench, "Pair Sensor", this::pair)); ui.divider(menu, 0);
        ui.add(menu, ui.listRow(android.R.drawable.ic_lock_lock, "Privacy & data", () -> message("Privacy & data", "Tunas keeps your profile, plants and watering history on this phone. The MQTT broker password is encrypted in app-private storage and is not backed up. Tunas doesn't sync a cloud account or send analytics. It asks for Bluetooth only when you scan for a sensor. To erase everything, clear the app's storage in Android settings."))); ui.divider(menu, 0);
        ui.add(menu, ui.listRow(android.R.drawable.ic_menu_share, "Share App", () -> {
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "I'm using Tunas to keep track of watering my plants. It's not on the Play Store yet, so ask me for the APK.");
            host.startActivity(Intent.createChooser(share, "Share Tunas"));
        })); ui.gap(column, 24);
        Runnable logOut = () -> new MaterialAlertDialogBuilder(host).setTitle("Log out of Tunas?")
            .setMessage("Your plants and schedules stay on this phone. Open your profile again to see them.").setNegativeButton("Cancel", null)
            .setPositiveButton("Log Out", (d, w) -> mutate(() -> {
                host.device.disconnect();
                if (host.mqtt != null) host.mqtt.disconnect();
                store.signOut();
                CareReminders.sync(host);
                host.recreate();
            })).show();
        Button logOutButton = ui.button("Log Out", false, logOut); ui.clickable(logOutButton, ui.outlined(ui.cream, 14, ui.error), 14, logOut);
        logOutButton.setTextColor(ui.error); ui.add(column, logOutButton); ui.gap(column, 8);
    }
    void settings() {
        LinearLayout column = host.page("settings"); back(column, this::profile); ui.add(column, ui.title("Profile Settings")); ui.gap(column, 20);
        EditText name = ui.input("Full name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS); name.setText(store.name()); name.setContentDescription("Full name"); host.field(column, "Full name", name);
        EditText email = ui.input("Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS); email.setText(store.email()); email.setContentDescription("Email"); host.field(column, "Email", email);
        ui.add(column, ui.text("Tunas uses your email to find your profile on this phone. It doesn't verify the address or send you email.", 16, false)); ui.gap(column, 20);
        TextView feedback = error(column);
        ui.add(column, ui.button("Save Settings", true, () -> {
            try { store.saveProfile(name.getText().toString(), email.getText().toString()); host.hideKeyboard(); profile(); }
            catch (IllegalArgumentException | IllegalStateException e) { feedback.setText(e.getMessage()); }
        }));
    }
    private void mutate(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException e) { message("Could not save", e.getMessage()); }
    }
}
