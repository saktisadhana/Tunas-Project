package com.example.tunas;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import com.example.tunas.data.AppStore;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Local care reminders: an inexact alarm wakes this receiver when the next plant is due, and the
 * notification shows the plant's "care journey" (last drink to next check) as a live progress bar.
 * Actions log manual watering or snooze straight from the shade. Nothing here touches pump hardware.
 */
public final class CareReminders extends BroadcastReceiver {
    static final String EXTRA_PLANT = "com.example.tunas.PLANT";
    private static final String CHECK = "com.example.tunas.CARE_CHECK";
    private static final String WATERED = "com.example.tunas.CARE_WATERED";
    private static final String SNOOZE = "com.example.tunas.CARE_SNOOZE";
    private static final String CHANNEL = "care";
    private static final String DUE = "Care check";
    private static final int SUMMARY = 1;
    private static final long HOUR = 3_600_000L, DAY = 24 * HOUR;

    @Override public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra(EXTRA_PLANT);
        if (WATERED.equals(intent.getAction()) && id != null) {
            AppStore store = new AppStore(context);
            snoozes(context).edit().remove(id).apply();
            try {
                store.markWatered(id);
                AppStore.Plant plant = find(store, id);
                if (plant != null) manager(context).notify(id, 0, thanks(context, plant));
            } catch (IllegalArgumentException | IllegalStateException gone) { manager(context).cancel(id, 0); }
        } else if (SNOOZE.equals(intent.getAction()) && id != null) {
            snoozes(context).edit().putLong(id, System.currentTimeMillis() + HOUR).apply();
            manager(context).cancel(id, 0);
        }
        sync(context);
    }

    /** Re-derives reminders from saved plants: posts what is due, clears stale ones, sets the next alarm. */
    static void sync(Context context) {
        AppStore store = new AppStore(context);
        NotificationManager manager = manager(context);
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent wake = broadcast(context, CHECK, null, 0);
        if (!store.isSignedIn() || !store.notificationsEnabled()) {
            alarms.cancel(wake);
            for (StatusBarNotification shown : manager.getActiveNotifications()) manager.cancel(shown.getTag(), shown.getId());
            return;
        }
        long now = System.currentTimeMillis(), next = Long.MAX_VALUE;
        SharedPreferences snoozed = snoozes(context);
        List<AppStore.Plant> due = new ArrayList<>();
        List<String> dueIds = new ArrayList<>();
        for (AppStore.Plant plant : store.plants()) {
            long at = Math.max(plant.nextWateringMillis, snoozed.getLong(plant.id, 0));
            if (at <= now) { due.add(plant); dueIds.add(plant.id); } else next = Math.min(next, at);
        }
        // Watered in the app, deleted or snoozed: withdraw its due reminder. Previews and thank-yous stay.
        for (StatusBarNotification shown : manager.getActiveNotifications())
            if (shown.getTag() != null && !dueIds.contains(shown.getTag())
                    && DUE.equals(shown.getNotification().extras.getString(Notification.EXTRA_SUB_TEXT)))
                manager.cancel(shown.getTag(), shown.getId());
        for (AppStore.Plant plant : due) manager.notify(plant.id, 0, reminder(context, plant, now));
        if (due.size() > 1) manager.notify(SUMMARY, summary(context, due)); else manager.cancel(SUMMARY);
        // ponytail: inexact alarm, can land minutes late; exact timing needs SCHEDULE_EXACT_ALARM
        if (next == Long.MAX_VALUE) alarms.cancel(wake); else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, wake);
    }

    /** Shows the soonest plant's journey right away, due or not, so the user sees what to expect. */
    static boolean preview(Context context) {
        AppStore.Plant soonest = null;
        for (AppStore.Plant plant : new AppStore(context).plants())
            if (soonest == null || plant.nextWateringMillis < soonest.nextWateringMillis) soonest = plant;
        if (soonest == null) return false;
        manager(context).notify(soonest.id, 0, reminder(context, soonest, System.currentTimeMillis()));
        return true;
    }

    private static Notification reminder(Context context, AppStore.Plant plant, long now) {
        long start = plant.nextWateringMillis - plant.waterEveryDays * DAY;
        int total = (int) Math.max(1, (plant.nextWateringMillis - start) / HOUR);
        int elapsed = (int) Math.max(0, Math.min(total, (now - start) / HOUR));
        boolean due = now >= plant.nextWateringMillis;
        long daysLeft = Math.max(1, (plant.nextWateringMillis - now + DAY - 1) / DAY);
        long daysSince = Math.max(0, (now - (plant.lastWateredMillis == 0 ? start : plant.lastWateredMillis)) / DAY);
        String title = due ? plant.name + " might be thirsty" : plant.name + " is fine for now";
        String text = due
            ? (plant.lastWateredMillis == 0 ? "Time for its first soil check." : "Last watered " + ago(daysSince) + ".") + " Water it if the top of the soil feels dry."
            : "Check the soil in " + daysLeft + (daysLeft == 1 ? " day" : " days") + ", on " + day(plant.nextWateringMillis) + ".";
        Notification.Builder builder = base(context, plant)
            .setContentTitle(title).setContentText(text)
            .setSubText(due ? DUE : "Next check").setOnlyAlertOnce(true)
            .addAction(action(context, plant, WATERED, "I watered it", 1))
            .addAction(action(context, plant, SNOOZE, "Remind me in 1 hour", 2));
        if (Build.VERSION.SDK_INT >= 36) builder.setStyle(journey(context, total, elapsed));
        else builder.setStyle(new Notification.BigTextStyle().bigText(text)).setProgress(total, elapsed, false);
        return builder.build();
    }

    private static Notification thanks(Context context, AppStore.Plant plant) {
        String text = "Next check on " + day(plant.nextWateringMillis) + ".";
        Notification.Builder builder = base(context, plant)
            .setContentTitle("Watered " + plant.name).setContentText(text)
            .setSubText("Watered").setOnlyAlertOnce(true).setTimeoutAfter(6_000);
        // The journey restarts: the droplet snaps back to the sprout.
        if (Build.VERSION.SDK_INT >= 36) builder.setStyle(journey(context, plant.waterEveryDays * 24, 0));
        else builder.setStyle(new Notification.BigTextStyle().bigText(text));
        return builder.build();
    }

    /** Sprout to droplet: fresh, drying and check-soon segments, with a droplet tracking today. */
    @androidx.annotation.RequiresApi(36)
    private static Notification.ProgressStyle journey(Context context, int total, int elapsed) {
        int fresh = Math.max(1, total * 55 / 100), drying = Math.max(1, total * 30 / 100), check = Math.max(1, total - fresh - drying);
        List<Notification.ProgressStyle.Segment> parts = new ArrayList<>();
        parts.add(new Notification.ProgressStyle.Segment(fresh).setColor(context.getColor(R.color.tunas_sage)));
        parts.add(new Notification.ProgressStyle.Segment(drying).setColor(0xFF7E9470));
        parts.add(new Notification.ProgressStyle.Segment(check).setColor(context.getColor(R.color.tunas_forest)));
        return new Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(parts)
            .setProgress(Math.min(elapsed, fresh + drying + check))
            .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_stat_tunas).setTint(context.getColor(R.color.tunas_forest)))
            .setProgressStartIcon(Icon.createWithResource(context, R.drawable.figma_plant))
            .setProgressEndIcon(Icon.createWithResource(context, R.drawable.figma_moisture));
    }

    private static Notification summary(Context context, List<AppStore.Plant> due) {
        Notification.InboxStyle inbox = new Notification.InboxStyle().setSummaryText(DUE);
        for (AppStore.Plant plant : due) inbox.addLine(plant.name);
        return new Notification.Builder(context, channel(context))
            .setSmallIcon(R.drawable.ic_stat_tunas).setColor(context.getColor(R.color.tunas_forest))
            .setContentTitle(due.size() + " plants are due for a check").setStyle(inbox)
            .setGroup(CHANNEL).setGroupSummary(true).setOnlyAlertOnce(true).setAutoCancel(true)
            .setContentIntent(open(context, null)).build();
    }

    private static Notification.Builder base(Context context, AppStore.Plant plant) {
        int art = plant.species.equals("Monstera") ? R.drawable.figma_monstera : R.drawable.figma_plant_welcome;
        return new Notification.Builder(context, channel(context))
            .setSmallIcon(R.drawable.ic_stat_tunas).setColor(context.getColor(R.color.tunas_forest))
            .setLargeIcon(Icon.createWithResource(context, art))
            .setCategory(Notification.CATEGORY_REMINDER).setGroup(CHANNEL).setAutoCancel(true)
            .setContentIntent(open(context, plant.id));
    }

    private static Notification.Action action(Context context, AppStore.Plant plant, String what, String label, int slot) {
        return new Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_tunas), label,
            broadcast(context, what, plant.id, slot)).build();
    }

    private static PendingIntent broadcast(Context context, String what, String plant, int slot) {
        Intent intent = new Intent(context, CareReminders.class).setAction(what);
        if (plant != null) intent.putExtra(EXTRA_PLANT, plant);
        int code = plant == null ? 0 : plant.hashCode() * 8 + slot;
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent open(Context context, String plant) {
        Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (plant != null) intent.putExtra(EXTRA_PLANT, plant);
        return PendingIntent.getActivity(context, plant == null ? 0 : plant.hashCode() * 8, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static String channel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Care reminders", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Reminders to check the soil of your saved plants");
        manager(context).createNotificationChannel(channel); // no-op once it exists
        return CHANNEL;
    }

    private static AppStore.Plant find(AppStore store, String id) { for (AppStore.Plant p : store.plants()) if (p.id.equals(id)) return p; return null; }
    private static String ago(long days) { return days == 0 ? "today" : days == 1 ? "yesterday" : days + " days ago"; }
    private static String day(long time) { return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(time)); }
    private static SharedPreferences snoozes(Context context) { return context.getSharedPreferences("care_reminders", Context.MODE_PRIVATE); }
    private static NotificationManager manager(Context context) { return context.getSystemService(NotificationManager.class); }
}
