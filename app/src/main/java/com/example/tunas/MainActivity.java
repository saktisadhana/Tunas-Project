package com.example.tunas;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import com.example.tunas.data.AppStore;
import com.example.tunas.ui.PlantCatalog;
import android.app.AlertDialog;
import android.content.Intent;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.text.TextWatcher;
import android.text.Editable;
import java.text.DateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.example.tunas.ui.BotanicalBackground;
import com.example.tunas.ui.TunasUi;

public class MainActivity extends androidx.appcompat.app.AppCompatActivity {
    TunasUi ui;
    FrameLayout root;
    AppStore store;
    String screen = "welcome";
    GardenPages pages;
    private LinearLayout navigation;
    com.example.tunas.iot.BleDeviceClient device;
    com.example.tunas.iot.Telemetry telemetry;
    long receivedAt;
    String connectionStatus = "No sensor connected";
    String linkedRoom = "";
    private final android.os.Handler clock = new android.os.Handler(android.os.Looper.getMainLooper());
    private final TextView[] metrics = new TextView[6];
    private TextView metricStatus;
    private final Runnable freshness = new Runnable() {
        @Override public void run() { refreshReadings(); clock.postDelayed(this, 1000); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new TunasUi(this); store = new AppStore(this); pages = new GardenPages(this);
        linkedRoom = getSharedPreferences("device_assignment", MODE_PRIVATE).getString("room", "");
        device = new com.example.tunas.iot.BleDeviceClient(this, new com.example.tunas.iot.BleDeviceClient.Listener() {
            public void onStatus(String message) { connectionStatus = message; pages.connectionChanged(); refreshReadings(); }
            public void onDeviceFound(String address, String name, int rssi) { pages.deviceFound(address, name, rssi); }
            public void onConnected(boolean connected) {
                if (!connected) telemetry = null;
                pages.connectionChanged(); refreshReadings();
            }
            public void onTelemetry(com.example.tunas.iot.Telemetry value) {
                telemetry = value; receivedAt = android.os.SystemClock.elapsedRealtime(); refreshReadings();
            }
            public void onCommandResult(boolean success, String message) { pages.commandResult(success, message); }
        });
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (screen.equals("welcome") || screen.equals("home")) finish();
                else if (store.isSignedIn()) showHome(); else showWelcome();
            }
        });
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (!store.isSignedIn()) {
            if (state != null && state.getString("route", "welcome").equals("signup")) showProfileForm(false);
            else if (state != null && state.getString("route", "welcome").equals("login")) showProfileForm(true);
            else showWelcome();
        } else if (state == null) showHome();
        else {
            pages.restoreState(state);
            String plantId = state.getString("plant", "");
            String species = state.getString("species", "Monstera");
            switch (state.getString("route", "home")) {
                case "plants": pages.library(); break;
                case "maintenance": pages.maintenance(); break;
                case "profile": pages.profile(); break;
                case "species": pages.speciesDetail(species); break;
                case "plant": pages.savedDetail(plantId); break;
                case "addPlant": pages.plantForm(species, null); break;
                case "editPlant": pages.editPlant(plantId); break;
                case "addRoom": pages.roomForm(pages::maintenance); break;
                case "tasks": pages.tasks(); break;
                case "settings": pages.settings(); break;
                case "pair": pages.pair(); break;
                default: showHome();
            }
        }
        if (state == null) openFromNotification(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); openFromNotification(intent); }
    private void openFromNotification(Intent intent) {
        String plant = intent == null ? null : intent.getStringExtra(CareReminders.EXTRA_PLANT);
        if (plant != null && store.isSignedIn()) pages.savedDetail(plant);
    }
    void toggleReminders() {
        if (store.notificationsEnabled()) { store.setNotificationsEnabled(false); CareReminders.sync(this); pages.profile(); return; }
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 72); return;
        }
        enableReminders();
    }
    private void enableReminders() {
        store.setNotificationsEnabled(true); CareReminders.sync(this);
        boolean shown = CareReminders.preview(this);
        android.widget.Toast.makeText(this, shown ? "Reminders are on. Swipe down to see the first one." : "Reminders are on. Add a plant and Tunas will remind you when it's due.", android.widget.Toast.LENGTH_LONG).show();
        if (screen.equals("profile")) pages.profile(); else if (screen.equals("plant")) pages.savedDetail(pages.routePlant);
    }
    LinearLayout page(String name) {
        if (screen.equals("pair") && !name.equals("pair")) device.stopScan();
        screen = name;
        boolean tabs = name.equals("home") || name.equals("plants") || name.equals("maintenance") || name.equals("profile");
        root = new FrameLayout(this);
        root.addView(new BotanicalBackground(this, name), new FrameLayout.LayoutParams(-1, -1));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout column = ui.column(); column.setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(16));
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));
        navigation = tabs ? navigation(name) : null;
        if (navigation != null) {
            boolean large = getResources().getConfiguration().fontScale > 1.25f;
            FrameLayout.LayoutParams nav = new FrameLayout.LayoutParams(large ? -1 : ui.dp(304), -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            nav.leftMargin = nav.rightMargin = ui.dp(24); nav.bottomMargin = ui.dp(15);
            root.addView(navigation, nav);
        }
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            WindowInsets raw = getWindow().getDecorView().getRootWindowInsets();
            if (raw == null) raw = insets;
            android.graphics.Insets bars = raw.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            boolean keyboard = raw.isVisible(WindowInsets.Type.ime());
            FrameLayout.LayoutParams viewport = (FrameLayout.LayoutParams) scroll.getLayoutParams();
            int safeBottom = bars.bottom + (tabs && !keyboard ? ui.dp(getResources().getConfiguration().fontScale > 1.25f ? 112 : 86) : 0);
            if (viewport.leftMargin != bars.left || viewport.topMargin != bars.top || viewport.rightMargin != bars.right || viewport.bottomMargin != safeBottom) {
                viewport.setMargins(bars.left, bars.top, bars.right, safeBottom); scroll.setLayoutParams(viewport);
            }
            scroll.setPadding(0, 0, 0, 0);
            if (navigation != null) {
                navigation.setVisibility(keyboard ? View.GONE : View.VISIBLE);
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) navigation.getLayoutParams();
                p.bottomMargin = raw.getInsets(WindowInsets.Type.systemBars()).bottom + ui.dp(15);
                navigation.setLayoutParams(p);
            }
            return insets;
        });
        setContentView(root);
        CareReminders.sync(this); // every screen render may follow a plant change; keeps reminders honest
        root.requestApplyInsets(); ui.logo(column);
        // Gentle settle-in on every screen change; respects the system animator scale.
        column.setAlpha(0f); column.setTranslationY(ui.dp(10));
        column.animate().alpha(1f).translationY(0).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        return column;
    }
    private void showWelcome() {
        LinearLayout column = page("welcome");
        boolean compact = getResources().getConfiguration().fontScale > 1.25f || getResources().getConfiguration().screenHeightDp < 700;
        ui.gap(column, 15);
        FrameLayout hero = new FrameLayout(this); ui.card(hero, 10, 10);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(ui.dp(Math.min(304, getResources().getConfiguration().screenWidthDp - 32)), ui.dp(compact ? 240 : 301));
        heroParams.gravity = Gravity.CENTER_HORIZONTAL; column.addView(hero, heroParams);
        ImageView plant = ui.image(R.drawable.figma_plant_welcome);
        FrameLayout.LayoutParams plantParams = new FrameLayout.LayoutParams(ui.dp(compact ? 116 : 136), ui.dp(compact ? 142 : 166), Gravity.CENTER);
        hero.addView(plant, plantParams);
        ui.gap(column, compact ? 24 : 36);
        TextView title = ui.text("Tunas", 48, true); title.setGravity(Gravity.CENTER); ui.add(column, title);
        ui.gap(column, 8);
        TextView companion = ui.text("Your Plant Companion", 24, true); companion.setGravity(Gravity.CENTER); ui.add(column, companion);
        View flexible = new View(this); column.addView(flexible, new LinearLayout.LayoutParams(1, 0, 1));
        ui.gap(column, compact ? 28 : 44);
        android.widget.Button start = ui.button("Start my garden", false, () -> showProfileForm(false)); start.setTextSize(24);
        ui.clickable(start, ui.surface, 14, () -> showProfileForm(false)); start.setElevation(ui.dp(6));
        LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(ui.dp(Math.min(304, getResources().getConfiguration().screenWidthDp - 32)), ui.dp(68));
        action.gravity = Gravity.CENTER_HORIZONTAL; column.addView(start, action);
        TextView account = ui.text("I already have a profile", 16, true); account.setTextColor(ui.cream);
        account.setGravity(Gravity.CENTER); account.setPaintFlags(account.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        account.setMinHeight(ui.dp(48)); account.setFocusable(true); account.setOnClickListener(v -> showProfileForm(true)); ui.add(column, account);
    }
    private void showProfileForm(boolean existing) {
        LinearLayout column = page(existing ? "login" : "signup");
        ui.add(column, ui.back(this::showWelcome)); ui.gap(column, 20);
        ui.add(column, ui.title(existing ? "Open your profile" : "Create your profile")); ui.gap(column, 12);
        ui.add(column, ui.text("Tunas keeps your profile on this phone. Nothing syncs to the cloud yet.", 17, false));
        ui.gap(column, 24);
        EditText name = ui.input("Full name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setContentDescription("Full name");
        if (!existing) field(column, "Full name", name);
        EditText email = ui.input("you@example.com", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setContentDescription("Email"); field(column, "Email", email);
        TextView error = ui.text("", 16, false); error.setTextColor(ui.error); error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); ui.add(column, error);
        ui.gap(column, 12);
        ui.add(column, ui.button(existing ? "Open Profile" : "Create Profile", true, () -> {
            try {
                if (existing) {
                    if (!store.openProfile(email.getText().toString())) {
                        error.setText("No profile on this phone uses that email."); return;
                    }
                } else {
                    if (store.hasProfile()) { error.setText("This phone already has a profile. Open it instead to see your plants."); return; }
                    store.saveProfile(name.getText().toString(), email.getText().toString());
                }
                hideKeyboard(); showHome();
            } catch (IllegalArgumentException e) { error.setText(e.getMessage()); }
        }));
        ui.gap(column, 12);
        ui.add(column, ui.button(existing ? "Create a profile" : "Open my saved profile", false, () -> showProfileForm(!existing)));
    }
    void field(LinearLayout column, String label, View input) {
        ui.add(column, ui.text(label, 15, true)); ui.gap(column, 8); ui.add(column, input); ui.gap(column, 18);
    }
    void hideKeyboard() {
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(), 0);
    }
    void showHome() {
        LinearLayout column = page("home");
        LinearLayout heading = ui.row();
        heading.addView(ui.title("Your Ecosystem"), new LinearLayout.LayoutParams(0, -2, 1));
        metricStatus = ui.text("No sensor connected", 12, false); metricStatus.setGravity(Gravity.CENTER);
        metricStatus.setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6)); metricStatus.setMinHeight(ui.dp(32));
        metricStatus.setBackground(ui.shape(0x59A2B595, 40));
        metricStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        heading.addView(metricStatus, new LinearLayout.LayoutParams(-2, -2)); ui.add(column, heading); ui.gap(column, 14);
        String[] labels = {"Health", "Moisture", "Nutrient", "Sunlight", "Temp", "Humidity"};
        int[] icons = {R.drawable.figma_plant, R.drawable.figma_moisture, R.drawable.figma_nutrient,
            R.drawable.figma_sun, R.drawable.figma_temperature, R.drawable.figma_humidity};
        for (int row = 0; row < 3; row++) {
            LinearLayout line = ui.row();
            for (int col = 0; col < 2; col++) {
                int index = row * 2 + col;
                LinearLayout card = ui.row(); ui.card(card, 10, 5); ui.pad(card, 12); card.setMinimumHeight(ui.dp(104));
                card.addView(ui.image(icons[index]), new LinearLayout.LayoutParams(ui.dp(50), ui.dp(62)));
                LinearLayout copy = ui.column(); copy.setPadding(ui.dp(12), 0, 0, 0);
                metrics[index] = ui.lifted(ui.text("--", 30, true));
                metrics[index].setTypeface(android.graphics.Typeface.create(ui.heading, 800, false));
                TextView label = ui.lifted(ui.text(labels[index], 16, false)); label.setTypeface(android.graphics.Typeface.create(ui.body, 600, false));
                ui.add(copy, metrics[index]); ui.gap(copy, 2); ui.add(copy, label);
                card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); if (col == 0) p.rightMargin = ui.dp(16);
                line.addView(card, p);
            }
            ui.add(column, line); ui.gap(column, 14);
        }
        ui.add(column, ui.title("Upcoming Task")); ui.gap(column, 14);
        LinearLayout task = ui.row(); ui.card(task, 10, 6); ui.pad(task, 16);
        task.addView(ui.image(R.drawable.figma_clock), new LinearLayout.LayoutParams(ui.dp(52), ui.dp(62)));
        LinearLayout copy = ui.column(); copy.setPadding(ui.dp(18), 0, 0, 0);
        List<AppStore.Plant> plants = new ArrayList<>(store.plants());
        plants.sort(java.util.Comparator.comparingLong(p -> p.nextWateringMillis));
        AppStore.Plant next = plants.isEmpty() ? null : plants.get(0);
        ui.add(copy, ui.lifted(ui.text(next == null ? "No plants yet" : "Water " + next.name, 20, true))); ui.gap(copy, 4);
        ui.add(copy, ui.text(next == null ? "Pick one from Plants to schedule its first check." : pages.date(next.nextWateringMillis), 16, false));
                ui.clickable(task, ui.surface, 10, () -> { if (next == null) pages.library(); else pages.tasks(); });
        task.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chevron = ui.text("›", 30, false); chevron.setTextColor(ui.muted); task.addView(chevron);
        ui.add(column, task); refreshReadings();
    }
    private LinearLayout navigation(String active) {
        LinearLayout nav = ui.row(); nav.setPadding(ui.dp(12), ui.dp(3), ui.dp(12), ui.dp(3));
        nav.setBackground(ui.shape(ui.cream, 120)); nav.setElevation(ui.dp(10));
        String[] names = {"Home", "Plants", "Maintenance", "Profile"};
        String[] routes = {"home", "plants", "maintenance", "profile"};
        int[] icons = {R.drawable.figma_home, R.drawable.figma_plant, R.drawable.figma_wrench, R.drawable.figma_profile};
        Runnable[] actions = {this::showHome, pages::library, pages::maintenance, pages::profile};
        for (int i = 0; i < names.length; i++) {
            final int index = i; boolean selected = active.equals(routes[i]);
            LinearLayout tab = ui.column(); tab.setGravity(Gravity.CENTER); tab.setMinimumHeight(ui.dp(48));
            ui.clickable(tab, selected ? ui.olive : ui.cream, 40, () -> { hideKeyboard(); actions[index].run(); });
            tab.setSelected(selected); tab.setContentDescription(names[i]);
            ImageView icon = ui.image(icons[i]); icon.setColorFilter(selected ? ui.cream : ui.olive);
            tab.addView(icon, new LinearLayout.LayoutParams(ui.dp(21), ui.dp(21)));
            TextView label = ui.text(names[i], 10, false); label.setTypeface(android.graphics.Typeface.create(ui.body, 700, false));
            label.setTextColor(selected ? ui.cream : ui.olive); label.setGravity(Gravity.CENTER);
            tab.addView(label, new LinearLayout.LayoutParams(-1, -2));
            nav.addView(tab, new LinearLayout.LayoutParams(0, -2, i == 2 ? 1.3f : 1));
        }
        return nav;
    }
    boolean fresh() { return device != null && device.isConnected() && telemetry != null && android.os.SystemClock.elapsedRealtime() - receivedAt < 30000; }
    private void refreshReadings() {
        if (screen.equals("home") && metricStatus != null) {
            Double[] values = fresh() ? new Double[]{telemetry.health, telemetry.moisture, telemetry.nutrient, telemetry.sunlight, telemetry.temperature, telemetry.humidity} : new Double[6];
            for (int i = 0; i < 6; i++) if (metrics[i] != null) {
                metrics[i].setText(values[i] == null ? "--" : String.format(java.util.Locale.getDefault(), "%.0f%s", values[i], i == 4 ? "°C" : i == 2 ? "" : "%"));
                metrics[i].setTextSize(i == 2 && values[i] != null && values[i] >= 1000 ? 23 : 30);
            }
            metricStatus.setText(device.isConnected() ? (fresh() ? "Sensor connected" : "Waiting for readings") : "No sensor connected");
            metricStatus.setContentDescription(metricStatus.getText() + ". " + (device.isConnected() ? pages.roomName(linkedRoom) + ". Nutrient is measured in microsiemens per centimeter. " : "") + "Manage sensors from Maintenance.");
        }
        if (pages != null) pages.refreshReadings();
    }
    void assignRoom(String id) { linkedRoom = id; getSharedPreferences("device_assignment", MODE_PRIVATE).edit().putString("room", id).apply(); }
    void requestScan() {
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != android.content.pm.PackageManager.PERMISSION_GRANTED || checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT}, 71);
        } else device.startScan();
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == 72) {
            if (grants.length == 1 && grants[0] == 0) enableReminders();
            else new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Notifications are off")
                .setMessage("Allow notifications for Tunas in Android settings to get soil-check reminders.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Open settings", (d, w) -> startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()))).show();
        }
        if (code == 71) {
            if (grants.length == 2 && grants[0] == 0 && grants[1] == 0 && screen.equals("pair")) device.startScan();
            else { connectionStatus = "Tunas needs the Nearby devices permission to scan. Turn it on in Android settings."; pages.connectionChanged(); }
        }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("route", screen); out.putString("plant", pages.routePlant); out.putString("species", pages.routeSpecies);
        pages.saveState(out); super.onSaveInstanceState(out);
    }
    @Override protected void onResume() { super.onResume(); clock.post(freshness); }
    @Override protected void onPause() { clock.removeCallbacks(freshness); super.onPause(); }
    @Override protected void onStop() { super.onStop(); if (device != null) device.disconnect(); }
    @Override protected void onDestroy() { clock.removeCallbacksAndMessages(null); if (device != null) device.close(); super.onDestroy(); }


}

