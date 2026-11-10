package com.skyemain.clenzylite;

import static org.junit.Assert.*;
import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.IOException;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LiteJourneyTest {
    private final Context context = ApplicationProvider.getApplicationContext();
    private final android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final UiDevice device = UiDevice.getInstance(instrumentation);

    private void grantMedia() {
        context.getSharedPreferences("lite", Context.MODE_PRIVATE).edit().putBoolean("accessSetupV2", true).putBoolean("confirmationMode", true).commit();
        instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(), Manifest.permission.READ_MEDIA_IMAGES);
        instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(), Manifest.permission.READ_MEDIA_VIDEO);
    }

    private Uri fixture(String name, boolean video) throws IOException {
        var resolver = context.getContentResolver();
        Uri collection = video ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) :
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        var values = new ContentValues();
        values.put("_display_name", name);
        values.put("mime_type", video ? "video/mp4" : "image/png");
        values.put("is_pending", 1);
        Uri uri = java.util.Objects.requireNonNull(resolver.insert(collection, values));
        try (var output = java.util.Objects.requireNonNull(resolver.openOutputStream(uri))) {
            if (video) {
                try (var stream = instrumentation.getContext().getAssets().open("sample-video.b64")) {
                    try (var decoded = new android.util.Base64InputStream(stream, android.util.Base64.DEFAULT)) {
                        byte[] buffer = new byte[4096];
                        for (int size; (size = decoded.read(buffer)) != -1;) output.write(buffer, 0, size);
                    }
                }
            } else {
                var bitmap = Bitmap.createBitmap(700, 900, Bitmap.Config.ARGB_8888);
                try { bitmap.eraseColor(Color.rgb(75, 57, 126)); assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); }
                finally { bitmap.recycle(); }
            }
        }
        var publish = new ContentValues();
        publish.put("is_pending", 0);
        resolver.update(uri, publish, null, null);
        return uri;
    }

    private void clean(Uri... items) {
        Bundle args = new Bundle();
        args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE);
        for (Uri item : items) context.getContentResolver().delete(item, args);
        context.getSharedPreferences("lite", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private UiObject2 find(BySelector selector) {
        var result = device.wait(Until.findObject(selector), 7000);
        assertNotNull("Missing " + selector, result);
        return result;
    }

    private void swipeLeft() {
        var area = find(By.desc("Gallery photo")).getVisibleBounds();
        device.waitForIdle();
        assertTrue(device.swipe(area.left + area.width() * 3 / 4, area.centerY(),
            area.left + area.width() / 4, area.centerY(), 16));
    }

    private void approveAndroid() {
        var allow = find(By.text(Pattern.compile("(?i)(allow|restore|trash|move to trash|remove from trash)")));
        assertNotEquals(context.getPackageName(), allow.getApplicationPackage());
        allow.click();
    }

    @Test public void twoSwipesCanBothBeRestoredThroughAndroid() throws Exception {
        grantMedia();
        var a = fixture("Lite test A.png", false);
        var b = fixture("Lite test B.png", false);
        var c = fixture("Lite test C.png", false);
        try {
            var items = Gallery.load(context, Gallery.access(context));
            assertEquals(3, items.size());
            try (var scenario = ActivityScenario.launch(LiteActivity.class)) {
                find(By.text("1 / 3"));
                swipeLeft();
                approveAndroid();
                find(By.text("1 / 2"));
                assertEquals(Boolean.TRUE, Gallery.trashState(context.getContentResolver(), items.get(0), false));

                swipeLeft();
                approveAndroid();
                find(By.text("1 / 1"));
                assertEquals(Boolean.TRUE, Gallery.trashState(context.getContentResolver(), items.get(1), false));

                find(By.desc(Pattern.compile("Undo Trash · [12]"))).click();
                approveAndroid();
                find(By.desc("Undo Trash · 1"));
                assertEquals(Boolean.FALSE, Gallery.trashState(context.getContentResolver(), items.get(1), false));
                assertEquals(Boolean.TRUE, Gallery.trashState(context.getContentResolver(), items.get(0), false));

                find(By.desc(Pattern.compile("Undo Trash · [12]"))).click();
                approveAndroid();
                find(By.desc("Undo unavailable"));
                assertEquals(Boolean.FALSE, Gallery.trashState(context.getContentResolver(), items.get(0), false));
                assertFalse(find(By.desc("Undo unavailable")).isEnabled());
                assertFalse(device.hasObject(By.text("Restored")));
            }
        } finally { clean(a, b, c); }
    }

    @Test public void mediaManagementGrantAllowsSwipeAndUndoWithoutDialog() throws Exception {
        grantMedia();
        var photo = fixture("Lite prompt-free.png", false);
        String command = "cmd appops set " + context.getPackageName() + " MANAGE_MEDIA ";
        try {
            device.executeShellCommand(command + "allow");
            assertTrue("Media management grant", MediaStore.canManageMedia(context));
            var item = Gallery.load(context, Gallery.access(context)).get(0);
            try (var scenario = ActivityScenario.launch(LiteActivity.class)) {
                swipeLeft();
                find(By.desc("Undo Trash · 1"));
                assertFalse(device.hasObject(By.text("Moved to Trash")));
                assertEquals(Boolean.TRUE, Gallery.trashState(context.getContentResolver(), item, false));
                find(By.desc(Pattern.compile("Undo Trash · [12]"))).click();
                find(By.desc("Undo unavailable"));
                assertEquals(Boolean.FALSE, Gallery.trashState(context.getContentResolver(), item, false));
            }
        } finally {
            device.executeShellCommand(command + "default");
            clean(photo);
        }
    }

    @Test public void reopeningResumesAtTheNextUnreviewedPhoto() throws Exception {
        grantMedia();
        var a = fixture("Lite resume A.png", false);
        var b = fixture("Lite resume B.png", false);
        try {
            try (var scenario = ActivityScenario.launch(LiteActivity.class)) {
                var area = find(By.desc("Gallery photo")).getVisibleBounds();
                assertTrue(device.swipe(area.left + area.width() / 4, area.centerY(),
                    area.left + area.width() * 3 / 4, area.centerY(), 16));
                find(By.text("2 / 2"));
            }
            try (var scenario = ActivityScenario.launch(LiteActivity.class)) {
                find(By.desc("Star current item")).click();
                find(By.desc("Unstar current item"));
                var regular = Gallery.load(context, Gallery.access(context));
                var stars = context.getSharedPreferences("lite", Context.MODE_PRIVATE)
                    .getStringSet("stars", java.util.Set.of());
                assertEquals(java.util.Set.of(regular.get(1).uri()), stars);
            }
        } finally { clean(a, b); }
    }

    @Test public void firstLaunchOffersMediaManagementAndReturnsToGalleryAfterGrant() throws Exception {
        grantMedia();
        context.getSharedPreferences("lite", Context.MODE_PRIVATE).edit().remove("accessSetupV2").commit();
        String command = "cmd appops set " + context.getPackageName() + " MANAGE_MEDIA ";
        var photo = fixture("Lite setup.png", false);
        try {
            device.executeShellCommand(command + "default");
            try (var scenario = ActivityScenario.launch(LiteActivity.class)) {
                find(By.text("Enable media management")).click();
                assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings").depth(0)), 7000));
                device.executeShellCommand(command + "allow");
                device.pressBack();
                find(By.desc("Gallery photo"));
                assertTrue(context.getSharedPreferences("lite", Context.MODE_PRIVATE).getBoolean("accessSetupV2", false));
                assertFalse(find(By.desc("Undo unavailable")).isEnabled());
            }
        } finally { device.executeShellCommand(command + "default"); clean(photo); }
    }

    @Test public void declinedMediaManagementCanContinueAndDoesNotRepeatSetup() throws Exception {
        grantMedia();
        context.getSharedPreferences("lite", Context.MODE_PRIVATE).edit().remove("accessSetupV2").commit();
        var photo = fixture("Lite declined.png", false);
        try {
            try (var scenario = ActivityScenario.launch(LiteActivity.class)) {
                find(By.text("Continue with confirmations")).click();
                find(By.desc("Gallery photo"));
                scenario.recreate();
                find(By.desc("Gallery photo"));
                assertFalse(device.hasObject(By.text("Enable media management")));
            }
        } finally { clean(photo); }
    }

    @Test public void checkpointSurvivesReopeningAndNewMedia() throws Exception {
        grantMedia();
        var a = fixture("Lite checkpoint A.png", false);
        var b = fixture("Lite checkpoint B.png", false);
        Uri added = null;
        try {
            String checkpoint;
            try (var scenario = ActivityScenario.launch(LiteActivity.class)) {
                var area = find(By.desc("Gallery photo")).getVisibleBounds();
                assertTrue(device.swipe(area.left + area.width() / 4, area.centerY(),
                    area.left + area.width() * 3 / 4, area.centerY(), 16));
                find(By.text("2 / 2"));
                checkpoint = context.getSharedPreferences("lite", Context.MODE_PRIVATE).getString("resumeUri", "");
                assertFalse(checkpoint.isEmpty());
            }
            added = fixture("Lite newly arrived.png", false);
            try (var scenario = ActivityScenario.launch(LiteActivity.class)) {
                find(By.desc("Star current item")).click();
                find(By.desc("Unstar current item"));
                assertEquals(java.util.Set.of(checkpoint), context.getSharedPreferences("lite", Context.MODE_PRIVATE)
                    .getStringSet("stars", java.util.Set.of()));
            }
        } finally { if (added != null) clean(added); clean(a, b); }
    }

    @Test public void firstTrashOpensExactMediaSettingsAndKeepsPhotoUntouched() throws Exception {
        grantMedia();
        context.getSharedPreferences("lite", Context.MODE_PRIVATE).edit().putBoolean("confirmationMode", false).commit();
        String command = "cmd appops set " + context.getPackageName() + " MANAGE_MEDIA ";
        var photo = fixture("Lite first Trash access.png", false);
        try {
            device.executeShellCommand(command + "default");
            var item = Gallery.load(context, Gallery.access(context)).get(0);
            try (var scenario = ActivityScenario.launch(LiteActivity.class)) {
                swipeLeft();
                assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings").depth(0)), 7000));
                assertEquals(Boolean.FALSE, Gallery.trashState(context.getContentResolver(), item, false));
                device.executeShellCommand(command + "allow");
                device.pressBack();
                find(By.desc("Gallery photo"));
                swipeLeft();
                find(By.desc("Undo Trash · 1"));
                assertEquals(Boolean.TRUE, Gallery.trashState(context.getContentResolver(), item, false));
            }
        } finally { device.executeShellCommand(command + "default"); clean(photo); }
    }

    @Test public void photoFitsInsideViewportWithoutCropping() throws Exception {
        grantMedia();
        var photo = fixture("Lite uncropped.png", false);
        try (var scenario = ActivityScenario.launch(LiteActivity.class)) {
            find(By.desc("Gallery photo"));
            var checked = new java.util.concurrent.atomic.AtomicBoolean(false);
            long deadline = android.os.SystemClock.uptimeMillis() + 7000;
            while (!checked.get() && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity(activity -> {
                    var content = (android.view.ViewGroup) activity.findViewById(android.R.id.content);
                    var root = (android.view.ViewGroup) content.getChildAt(0);
                    for (int i = 0; i < root.getChildCount(); i++) {
                        if (root.getChildAt(i) instanceof android.widget.ImageView image && image.getDrawable() != null) {
                            var drawable = image.getDrawable();
                            var rect = new android.graphics.RectF(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                            image.getImageMatrix().mapRect(rect);
                            assertTrue(rect.left >= -1 && rect.top >= -1);
                            assertTrue(rect.right <= image.getWidth() + 1 && rect.bottom <= image.getHeight() + 1);
                            assertEquals(700f / 900f, rect.width() / rect.height(), 0.01f);
                            checked.set(true);
                        }
                    }
                });
                if (!checked.get()) android.os.SystemClock.sleep(100);
            }
            assertTrue("Photo decoded and fit without cropping", checked.get());
        } finally { clean(photo); }
    }

    @Test public void starsPersistAndVideoPlays() throws Exception {
        grantMedia();
        var photo = fixture("Lite star.png", false);
        var video = fixture("Lite video.mp4", true);
        try {
            try (var scenario = ActivityScenario.launch(LiteActivity.class)) {
                // Recent order can differ by provider, so star the visible item and check the other.
                find(By.desc("Star current item")).click();
                find(By.desc("Unstar current item"));
            }
            try (var scenario = ActivityScenario.launch(LiteActivity.class)) {
                find(By.text("1 / 1"));
                find(By.desc("Star current item"));
                var stage = device.wait(Until.findObject(By.desc(Pattern.compile("Gallery (photo|video)"))), 5000);
                assertNotNull(stage);
                stage.longClick();
                find(By.text("Select queue · Regular")).click();
                find(By.text("Starred photos and videos")).click();
                find(By.text("Starred · 1 / 1"));
                find(By.desc("Unstar current item"));
                if (!device.hasObject(By.desc("Play video"))) {
                    find(By.desc("Gallery photo")).longClick();
                    find(By.text("Select queue · Starred")).click();
                    find(By.text("Regular photos and videos")).click();
                }
                var play = find(By.desc("Play video"));
                assertFalse(device.hasObject(By.desc("Video position")));
                var videoArea = find(By.desc("Gallery video")).getVisibleBounds();
                var playArea = play.getVisibleBounds();
                assertTrue(Math.abs(videoArea.centerX() - playArea.centerX()) < 4);
                assertTrue(Math.abs(videoArea.centerY() - playArea.centerY()) < 4);
                play.click();
                assertTrue(find(By.desc("Video position")).isEnabled());
                assertFalse(device.hasObject(By.text("Play")));

            }
        } finally { clean(photo, video); }
    }
}
