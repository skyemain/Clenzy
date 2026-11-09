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
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LiteJourneyTest {
    private final Context context = ApplicationProvider.getApplicationContext();
    private final android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final UiDevice device = UiDevice.getInstance(instrumentation);

    private void grantMedia() {
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
                    output.write(android.util.Base64.decode(stream.readAllBytes(), android.util.Base64.DEFAULT));
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

                find(By.text("Undo")).click();
                approveAndroid();
                find(By.text("Restored"));
                assertEquals(Boolean.FALSE, Gallery.trashState(context.getContentResolver(), items.get(1), false));
                assertEquals(Boolean.TRUE, Gallery.trashState(context.getContentResolver(), items.get(0), false));

                find(By.text("Undo")).click();
                approveAndroid();
                find(By.text("Restored"));
                assertEquals(Boolean.FALSE, Gallery.trashState(context.getContentResolver(), items.get(0), false));
                assertFalse(device.hasObject(By.text("Undo")));
            }
        } finally { clean(a, b, c); }
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
                if (!device.hasObject(By.text("Play"))) {
                    find(By.desc("Gallery photo")).longClick();
                    find(By.text("Select queue · Starred")).click();
                    find(By.text("Regular photos and videos")).click();
                }
                find(By.text("Play")).click();
                find(By.text("Pause"));
            }
        } finally { clean(photo, video); }
    }
}
