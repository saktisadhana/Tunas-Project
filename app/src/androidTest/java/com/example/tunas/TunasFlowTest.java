package com.example.tunas;

import android.content.Context;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertNotNull;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
public class TunasFlowTest {
    @org.junit.Before public void resetLocalProfile() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        java.io.File folder = new java.io.File(context.getApplicationInfo().dataDir, "shared_prefs");
        java.io.File[] files = folder.listFiles();
        if (files != null) for (java.io.File file : files) {
            if (file.getName().endsWith(".xml")) context.deleteSharedPreferences(file.getName().replace(".xml", ""));
        }
    }

    @Test public void localProfileReachesHonestOfflineDashboard() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(launch);
        onView(withText("Start my garden")).perform(androidx.test.espresso.action.ViewActions.click());
        onView(withText("Create your profile")).check(matches(isDisplayed()));
        onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Full name"))
            .perform(androidx.test.espresso.action.ViewActions.typeText("Sakti"), androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
        onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Email"))
            .perform(androidx.test.espresso.action.ViewActions.typeText("sakti@example.test"), androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
        onView(withText("Create Profile")).perform(androidx.test.espresso.action.ViewActions.scrollTo(), androidx.test.espresso.action.ViewActions.click());
        onView(withText("Your Ecosystem")).check(matches(isDisplayed()));
        onView(withText("No sensor connected")).check(matches(isDisplayed()));
    }

    @Test public void nativeTabsReachLibraryMaintenanceAndProfile() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new com.example.tunas.data.AppStore(context).saveProfile("Sakti", "sakti@example.test");
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); context.startActivity(launch);
        onView(withText("Plants")).perform(androidx.test.espresso.action.ViewActions.click());
        onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Search plants")).check(matches(isDisplayed()));
        onView(withText("Monstera")).check(matches(isDisplayed()));
        onView(withText("Maintenance")).perform(androidx.test.espresso.action.ViewActions.click());
        capture("after-maintenance-tap");
        onView(withText("0 Sensors Online")).check(matches(isDisplayed()));
        onView(withText("Pair Sensor")).check(matches(isDisplayed()));
        onView(withText("Profile")).perform(androidx.test.espresso.action.ViewActions.click());
        onView(withText("Sakti")).check(matches(isDisplayed())); capture("profile");
    }

    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private void launchGarden() {
        new com.example.tunas.data.AppStore(context()).saveProfile("Sakti", "sakti@example.test");
        Intent launch = context().getPackageManager().getLaunchIntentForPackage(context().getPackageName());
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); context().startActivity(launch);
        onView(withText("Your Ecosystem")).check(matches(isDisplayed()));
    }
    private void tap(String text) { onView(withText(text)).perform(androidx.test.espresso.action.ViewActions.click()); }
    private void scrollTap(String text) {
        onView(withText(text)).perform(androidx.test.espresso.action.ViewActions.scrollTo());
        onView(withText(text)).perform(androidx.test.espresso.action.ViewActions.click());
    }
    private void enter(String description, String value) {
        onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription(description)).perform(
            androidx.test.espresso.action.ViewActions.scrollTo(), androidx.test.espresso.action.ViewActions.replaceText(value), androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
    }
    private void capture(String name) {
        onView(androidx.test.espresso.matcher.ViewMatchers.isRoot()).check(matches(isDisplayed()));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.graphics.Bitmap screen = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        java.io.File target = new java.io.File(context().getExternalFilesDir(null), name + ".png");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(target)) { screen.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out); }
        catch (java.io.IOException e) { throw new AssertionError(e); }
        screen.recycle();
    }
    @Test public void roomPlantAndManualCarePersistThroughTheNativeFlow() {
        launchGarden(); capture("home"); tap("Maintenance"); tap("Add Room");
        enter("Room name", "Living Room"); scrollTap("Create Room");
        org.junit.Assert.assertEquals(1, new com.example.tunas.data.AppStore(context()).rooms().size());
        capture("maintenance");
        tap("Plants"); capture("plants"); tap("Monstera"); scrollTap("Add to My Plants");
        enter("Plant nickname", "Window Monstera");
        onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Plant room")).perform(androidx.test.espresso.action.ViewActions.click());
        androidx.test.espresso.Espresso.onData(org.hamcrest.Matchers.equalTo("Living Room")).perform(androidx.test.espresso.action.ViewActions.click());
        scrollTap("Save Plant");
        com.example.tunas.data.AppStore store = new com.example.tunas.data.AppStore(context());
        org.junit.Assert.assertEquals("Window Monstera", store.plants().get(0).name);
        org.junit.Assert.assertEquals(store.rooms().get(0).id, store.plants().get(0).roomId);
        capture("plant-detail"); scrollTap("Log watering"); tap("Log Watering");
        org.junit.Assert.assertTrue(store.plants().get(0).lastWateredMillis > 0);
        scrollTap("Edit plant"); enter("Plant nickname", "Desk Monstera"); scrollTap("Save Plant");
        org.junit.Assert.assertEquals("Desk Monstera", store.plants().get(0).name);
        scrollTap("Delete plant"); tap("Delete"); org.junit.Assert.assertTrue(store.plants().isEmpty());
    }
    @Test public void searchHasAUsefulEmptyStateAndCanReset() {
        launchGarden(); tap("Plants"); enter("Search plants", "no-such-plant");
        onView(withText("No matching plants")).check(matches(isDisplayed()));
        scrollTap("Reset filters"); onView(withText("Monstera")).check(matches(isDisplayed()));
    }
    @Test public void offlineActuatorNeverPretendsItRan() {
        launchGarden(); com.example.tunas.data.AppStore store = new com.example.tunas.data.AppStore(context());
        store.addRoom("Living Room", "Medium"); tap("Maintenance");
        scrollTap("Start Watering"); onView(withText("No sensor connected")).check(matches(isDisplayed()));
        tap("OK"); scrollTap("Start Nutrition"); onView(withText("No sensor connected")).check(matches(isDisplayed()));
        tap("OK"); scrollTap("Pair Sensor"); capture("pair");
        onView(withText("Scan nearby devices")).check(matches(isDisplayed()));
    }
    @Test public void plantDraftSurvivesActivityRecreation() {
        launchGarden(); tap("Plants"); tap("Monstera"); scrollTap("Add to My Plants"); enter("Plant nickname", "Draft plant");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            java.util.Collection<android.app.Activity> activities = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED);
            activities.iterator().next().recreate();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        onView(withText("Add Plant")).check(matches(isDisplayed()));
        onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Plant nickname")).check(matches(withText("Draft plant")));
    }

    @Test public void addingARoomKeepsThePlantDraft() {
        launchGarden(); tap("Plants"); tap("Monstera"); scrollTap("Add to My Plants");
        enter("Plant nickname", "Draft beside window"); scrollTap("Add a room");
        enter("Room name", "Bedroom"); scrollTap("Create Room");
        onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Plant nickname")).check(matches(withText("Draft beside window")));
    }

    @Test public void applicationLaunchesTheFigmaWelcome() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull("Tunas needs a launchable native welcome screen", launch);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(launch);
        onView(withText("Start my garden")).check(matches(isDisplayed()));
        onView(withText("Your Plant Companion")).check(matches(isDisplayed())); capture("welcome");
    }
}
