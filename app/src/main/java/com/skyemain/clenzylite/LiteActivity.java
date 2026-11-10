package com.skyemain.clenzylite;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import java.util.HashSet;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.activity.ComponentActivity;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.Set;
import java.util.concurrent.Executors;

public final class LiteActivity extends ComponentActivity {
    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private ReviewHistory history;
    private ReviewSession review;
    private FrameLayout root;
    private VideoView player;
    private Button playbackButton;
    private Dialog sheet;
    private boolean busy, light, previewReady;
    private int access = -1;
    private int loadVersion, previewVersion;
    private ReviewSession.Item pending;
    private ReviewSession.Removed restoring;

    private final androidx.activity.result.ActivityResultLauncher<String[]> permission =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grants -> load());
    private final androidx.activity.result.ActivityResultLauncher<Intent> mediaSettings =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(this) && Gallery.access(this) != 0) {
                prefs.edit().putBoolean("accessSetupV2", true).apply();
            }
            load();
        });
    private final androidx.activity.result.ActivityResultLauncher<IntentSenderRequest> trash =
        registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(),
            result -> onTrashResult(result.getResultCode() == Activity.RESULT_OK));

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("lite", MODE_PRIVATE);
        history = new ReviewHistory(getApplicationContext());
        review = new ReviewSession(prefs.getStringSet("stars", Set.of()));
        light = prefs.getBoolean("light", false);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        load();
    }

    @Override protected void onResume() {
        super.onResume();
        if (access >= 0 && access != Gallery.access(this) && !busy) load();
    }

    @Override protected void onPause() {
        pauseVideo();
        super.onPause();
    }

    @Override protected void onDestroy() {
        ++loadVersion;
        ++previewVersion;
        stopVideo();
        if (sheet != null) sheet.dismiss();
        worker.execute(history::close);
        worker.shutdown();
        super.onDestroy();
    }

    private void load() {
        access = Gallery.access(this);
        if (access == 0 || !prefs.getBoolean("accessSetupV2", false)) {
            showSetup();
            return;
        }
        final int level = access;
        final int generation = ++loadVersion;
        resetScreen();
        center("Loading gallery…", "");
        worker.execute(() -> {
            try {
                var items = Gallery.load(this, level);
                var reviewed = history.ranks();
                runOnUiThread(() -> {
                    if (generation != loadVersion || isDestroyed()) return;
                    review.load(items, reviewed);
                    review.resume(prefs.getString("resumeUri", ""), prefs.getBoolean("resumeStarred", false));
                    show(null, false);
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (generation != loadVersion || isDestroyed()) return;
                    resetScreen();
                    center("Gallery unavailable", "Check your media access and try again.")
                        .addView(button("Try again", true, this::load));
                });
            }
        });
    }

    private void showSetup() {
        ++loadVersion;
        resetScreen();
        var content = column();
        content.setPadding(dp(24), dp(32), dp(24), dp(24));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        content.addView(label("Clenzy Lite", 18, light ? 0xff6550b4 : 0xffd3c6ff));
        var title = label("A little setup.\nThen just swipe.", 32, ink());
        title.setTypeface(null, Typeface.BOLD);
        content.addView(title);
        content.addView(label("Connect your gallery and enable smooth swiping.", 15, muted()));
        content.addView(label(access == 0 ? "1  Photos & videos" : "✓  Photos & videos connected", 18, ink()));
        content.addView(label(access == 0 ? "Choose all media or selected items through Android." :
            (access & 3) == 3 ? "All photos and videos allowed." : "Only your authorized media will appear.", 14, muted()));
        boolean managed = Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(this);
        content.addView(label(managed ? "✓  Media management enabled" : "2  Trash without interruptions", 18, ink()));
        content.addView(label(Build.VERSION.SDK_INT < 31 ? "Android 11 requires confirmation for Trash and Restore." :
            managed ? "Android can skip individual Trash prompts for accessible media." :
            "Enable Clenzy Lite in Android’s Media management apps. You approve this once.", 14, muted()));
        content.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        String action = access == 0 ? "Allow gallery access" : managed || Build.VERSION.SDK_INT < 31 ? "Start swiping" : "Enable media management";
        content.addView(button(action, true, () -> {
            if (access == 0) permission.launch(Gallery.permissions());
            else if (Build.VERSION.SDK_INT >= 31 && !MediaStore.canManageMedia(this)) openMediaSettings();
            else finishSetup();
        }), new LinearLayout.LayoutParams(-1, -2));
        if (access != 0 && Build.VERSION.SDK_INT >= 31 && !managed) {
            content.addView(button("Continue with confirmations", false, this::finishSetup), new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void finishSetup() {
        prefs.edit().putBoolean("accessSetupV2", true)
            .putBoolean("confirmationMode", Build.VERSION.SDK_INT < 31 || !MediaStore.canManageMedia(this)).apply();
        load();
    }

    private void openMediaSettings() {
        try {
            mediaSettings.launch(new Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:" + getPackageName())));
        } catch (android.content.ActivityNotFoundException error) {
            showBanner("Media management is unavailable. Continue with Android confirmations.", false, false);
        }
    }

    private void show(String notice, boolean offerUndo) {
        resetScreen();
        var item = review.current();
        String checkpoint = item == null ? "" : item.uri();
        if (!checkpoint.equals(prefs.getString("resumeUri", "")) || review.isStarredQueue() != prefs.getBoolean("resumeStarred", false)) {
            prefs.edit().putString("resumeUri", checkpoint).putBoolean("resumeStarred", review.isStarredQueue()).commit();
        }
        if (item == null) {
            String heading = review.size() > 0 ? "You’re caught up" :
                review.isStarredQueue() ? "No starred media" : "No media to review";
            var empty = center(heading, "Press and hold for options.");
            if (review.size() > 0) empty.addView(button("Review again", true, () -> selectQueue(review.isStarredQueue())));
            else empty.addView(button("Options", false, this::options));
            gestures(root, null, null);
        } else {
            final int version = previewVersion;
            var photo = new ImageView(this);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            root.addView(photo, new FrameLayout.LayoutParams(-1, -1));
            var gestureArea = new View(this);
            root.addView(gestureArea, new FrameLayout.LayoutParams(-1, -1));
            gestureArea.setContentDescription(item.video() ? "Gallery video" : "Gallery photo");
            gestureArea.setFocusable(true);
            gestures(gestureArea, photo, item);
            ViewCompat.addAccessibilityAction(gestureArea, "Next item", (view, args) -> { next(); return true; });
            ViewCompat.addAccessibilityAction(gestureArea, "Move to Trash", (view, args) -> { remove(); return true; });
            ViewCompat.addAccessibilityAction(gestureArea, "Options", (view, args) -> { options(); return true; });

            var header = new LinearLayout(this);
            header.setGravity(Gravity.CENTER_VERTICAL);
            var count = label((review.isStarredQueue() ? "Starred · " : "") +
                (review.position() + 1) + " / " + review.size(), 13, Color.WHITE);
            count.setPadding(dp(11), dp(8), dp(11), dp(8));
            count.setBackground(surface(0xdb17141e, 20));
            header.addView(count);
            header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
            var star = new CompactControl(this, false);
            star.setOnClickListener(view -> {
                if (!busy && item.equals(review.current())) {
                    review.star(item);
                    saveStars();
                    setStar((CompactControl) header.getChildAt(2), item);
                }
            });
            setStar(star, item);
            header.addView(star, new LinearLayout.LayoutParams(dp(48), dp(48)));
            root.addView(header, overlayParams(Gravity.TOP, 8));

            if (item.video()) videoControls(item, photo);
            worker.execute(() -> {
                Bitmap bitmap = null;
                try { bitmap = Gallery.preview(getContentResolver(), item); }
                catch (Exception ignored) { /* Video playback can still work without a thumbnail. */ }
                final Bitmap result = bitmap;
                runOnUiThread(() -> {
                    if (version != previewVersion || isDestroyed()) { if (result != null) result.recycle(); return; }
                    if (result != null) {
                        photo.setImageBitmap(result);
                        showSwipeTutorial(item);
                    }
                    previewReady = result != null || item.video();
                    if (!previewReady) showBanner("Photo unavailable · swipe right to continue", false, false);
                });
            });
        }
        var undoControl = new CompactControl(this, true);
        undoControl.setCount(review.undoCount());
        undoControl.setEnabled(review.undoCount() > 0);
        undoControl.setContentDescription(review.undoCount() > 0 ? "Undo Trash · " + review.undoCount() : "Undo unavailable");
        undoControl.setOnClickListener(view -> undo());
        var undoParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM | Gravity.START);
        undoParams.setMargins(dp(12), 0, 0, dp(item != null && item.video() ? 72 : 12));
        root.addView(undoControl, undoParams);
        if (notice != null) showBanner(notice, false, item != null && item.video());
    }

    private void showSwipeTutorial(ReviewSession.Item item) {
        var shown = new HashSet<>(prefs.getStringSet("tutorialMedia", Set.of()));
        if (shown.size() >= 3 || !shown.add(item.uri())) return;
        prefs.edit().putStringSet("tutorialMedia", shown).apply();

        var cue = new LinearLayout(this);
        cue.setGravity(Gravity.CENTER);
        cue.setPadding(dp(8), dp(5), dp(8), dp(5));
        cue.setBackground(surface(0xe8221c31, 14));
        cue.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        cue.addView(label("← Swipe to Trash", 13, 0xffffbbc7));
        cue.addView(label("Swipe for next →", 13, 0xffd9ceff));
        var host = root;
        var params = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        host.addView(cue, params);
        cue.setTranslationX(-dp(14));
        cue.setAlpha(0);
        cue.animate().alpha(1).translationX(0).setDuration(260).withEndAction(() ->
            cue.animate().alpha(0).translationX(dp(14)).setStartDelay(1650).setDuration(260)
                .withEndAction(() -> host.removeView(cue)).start()).start();
    }

    private void setStar(CompactControl star, ReviewSession.Item item) {
        boolean marked = review.isStarred(item);
        star.setMarked(marked);
        star.setContentDescription(marked ? "Unstar current item" : "Star current item");
        star.setSelected(marked);
    }

    private void videoControls(ReviewSession.Item item, ImageView poster) {
        var controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(5), dp(4), dp(8), dp(4));
        controls.setBackground(surface(0xea17141e, 15));
        var play = overlayButton("Play", () -> {});
        playbackButton = play;
        controls.addView(play);
        var seek = new SeekBar(this);
        seek.setContentDescription("Video position");
        seek.setEnabled(false);
        seek.setProgressTintList(ColorStateList.valueOf(0xffc4b8ff));
        controls.addView(seek, new LinearLayout.LayoutParams(0, dp(44), 1f));
        root.addView(controls, overlayParams(Gravity.BOTTOM, 10));
        play.setOnClickListener(view -> {
            if (busy || !item.equals(review.current())) return;
            if (player != null) {
                if (player.isPlaying()) { player.pause(); play.setText("Play"); }
                else { player.start(); play.setText("Pause"); }
                return;
            }
            VideoView video = new VideoView(this);
            player = video;
            root.addView(video, 0, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            play.setText("Loading…");
            play.setEnabled(false);
            video.setOnPreparedListener(mp -> {
                if (video != player) return;
                poster.setVisibility(View.INVISIBLE);
                seek.setMax(Math.max(1, video.getDuration()));
                seek.setEnabled(true);
                video.start();
                play.setEnabled(true);
                play.setText("Pause");
                tickVideo(video, seek);
            });
            video.setOnCompletionListener(mp -> play.setText("Replay"));
            video.setOnErrorListener((mp, what, extra) -> {
                android.util.Log.w("ClenzyLite", "Platform video player error: " + what + "/" + extra);
                if (video == player) {
                    stopVideo();
                    root.removeView(video);
                    poster.setVisibility(View.VISIBLE);
                    play.setText("Play");
                    play.setEnabled(true);
                    seek.setEnabled(false);
                    showBanner("This video could not be played", false, true);
                }
                return true;
            });
            video.setVideoURI(Uri.parse(item.uri()));
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user && player != null) player.seekTo(value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    private void tickVideo(VideoView video, SeekBar seek) {
        if (video != player) return;
        seek.setProgress(video.getCurrentPosition());
        seek.postDelayed(() -> tickVideo(video, seek), 250);
    }

    private void pauseVideo() {
        if (player != null && player.isPlaying()) {
            player.pause();
            if (playbackButton != null) playbackButton.setText("Play");
        }
    }

    private void stopVideo() {
        if (player != null) {
            player.stopPlayback();
            player = null;
        }
    }

    private void gestures(View target, View image, ReviewSession.Item item) {
        var detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public void onLongPress(MotionEvent event) { if (!busy) options(); }
        });
        target.setOnTouchListener(new View.OnTouchListener() {
            float x, y;
            boolean tracking;
            @Override public boolean onTouch(View view, MotionEvent event) {
                int action = event.getActionMasked();
                detector.onTouchEvent(event);
                if (action == MotionEvent.ACTION_DOWN) {
                    x = event.getRawX(); y = event.getRawY();
                    tracking = !busy;
                } else if (action == MotionEvent.ACTION_MOVE && tracking && image != null) {
                    float delta = event.getRawX() - x;
                    image.setTranslationX(Math.max(-dp(85), Math.min(dp(85), delta)));
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
                    if (image != null) image.setTranslationX(0);
                    float delta = event.getRawX() - x;
                    if (item != null && tracking && action == MotionEvent.ACTION_UP && (sheet == null || !sheet.isShowing())
                            && Math.abs(delta) > dp(66) && Math.abs(delta) > 1.25f * Math.abs(event.getRawY() - y)) {
                        if (delta < 0) remove(); else next();
                    }
                    tracking = false;
                }
                return true;
            }
        });
        target.setOnKeyListener((view, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_UP) return false;
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) { remove(); return true; }
            if (key == KeyEvent.KEYCODE_DPAD_RIGHT) { next(); return true; }
            if (key == KeyEvent.KEYCODE_MENU) { options(); return true; }
            return false;
        });
    }

    private void next() {
        var item = review.current();
        if (busy || item == null) return;
        busy = true;
        worker.execute(() -> {
            try {
                history.mark(item.uri());
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy = false;
                    review.next();
                    show(null, false);
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy = false;
                    show("Could not save your place. Please try again.", false);
                });
            }
        });
    }

    private void remove() {
        var item = review.current();
        if (busy || !previewReady || item == null) return;
        if (Build.VERSION.SDK_INT >= 31 && !MediaStore.canManageMedia(this)
                && !prefs.getBoolean("confirmationMode", false)) {
            manageMedia();
            return;
        }
        request(item, null);
    }

    private void undo() {
        var latest = review.latestUndo();
        if (!busy && latest != null) request(latest.item(), latest);
    }

    private void request(ReviewSession.Item item, ReviewSession.Removed undo) {
        busy = true;
        pauseVideo();
        worker.execute(() -> {
            Boolean isTrashed = Gallery.trashState(getContentResolver(), item, undo == null);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (isTrashed == null || isTrashed != (undo != null)) {
                    busy = false;
                    show("Media changed or is no longer accessible", false);
                    return;
                }
                try {
                    var request = MediaStore.createTrashRequest(getContentResolver(), java.util.List.of(Uri.parse(item.uri())), undo == null);
                    pending = item;
                    restoring = undo;
                    trash.launch(new IntentSenderRequest.Builder(request).build());
                } catch (RuntimeException error) {
                    pending = null;
                    restoring = null;
                    busy = false;
                    show("Android could not complete this action", false);
                }
            });
        });
    }

    private void onTrashResult(boolean approved) {
        var item = pending;
        var undo = restoring;
        pending = null;
        restoring = null;
        if (item == null) return;
        if (!approved) {
            busy = false;
            show(undo == null ? "Kept in gallery" : "Restore canceled", undo != null);
            return;
        }
        worker.execute(() -> {
            Boolean isTrashed = Gallery.trashState(getContentResolver(), item, false);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                busy = false;
                if (isTrashed == null || isTrashed != (undo == null)) {
                    show(undo == null ? "Item was not moved to Trash" : "Item was not restored", undo != null);
                    return;
                }
                if (undo == null) review.trashed(item); else review.restored(undo);
                saveStars();
                show(null, false);
                root.announceForAccessibility(undo == null ? "Moved to Trash" : "Restored");
                root.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
            });
        });
    }

    private void saveStars() { prefs.edit().putStringSet("stars", review.stars()).apply(); }

    private void options() {
        if (busy) return;
        var menu = sheet("Options");
        if (review.undoCount() > 0) row(menu, "Undo Trash · " + review.undoCount(), this::undo);
        row(menu, "Select queue · " + (review.isStarredQueue() ? "Starred" : "Regular"), this::queues);
        row(menu, "Settings", this::settings);
        if (Build.VERSION.SDK_INT >= 31) row(menu, "Swipe access", this::manageMedia);
        row(menu, "About Clenzy Lite", this::about);
    }

    private void manageMedia() {
        if (Build.VERSION.SDK_INT < 31) return;
        boolean managed = MediaStore.canManageMedia(this);
        int mediaAccess = Gallery.access(this);
        var menu = sheet("Swipe access");
        menu.addView(label("Media management: " + (managed ? "Enabled" : "Not enabled"), 16, ink()));
        menu.addView(label("Gallery: " + ((mediaAccess & 3) == 3 ? "All photos and videos" : "Limited access"), 16, ink()));
        menu.addView(label(managed ? "If Android still asks before Trash, check that all photos and videos are allowed. Device-specific confirmation rules may still apply." :
            "Android will ask for every Trash action until Media management is enabled for Clenzy Lite.", 14, muted()));
        row(menu, "Open media management", this::openMediaSettings);
        if ((mediaAccess & 3) != 3) row(menu, "Allow all photos and videos", () -> permission.launch(Gallery.permissions()));
        if (!managed) row(menu, "Continue with confirmations", () -> prefs.edit().putBoolean("confirmationMode", true).apply());
        row(menu, "Back", this::options);
    }

    private void queues() {
        var menu = sheet("Select queue");
        row(menu, "Regular photos and videos", () -> selectQueue(false));
        row(menu, "Starred photos and videos", () -> selectQueue(true));
        row(menu, "Back", this::options);
    }

    private void selectQueue(boolean starred) {
        review.switchQueue(starred);
        show(null, false);
    }

    private void settings() {
        var menu = sheet("Settings");
        menu.addView(label("Theme", 14, muted()));
        row(menu, light ? "Dark" : "Dark  ✓", () -> theme(false));
        row(menu, light ? "Light  ✓" : "Light", () -> theme(true));
        row(menu, "Back", this::options);
    }

    private void theme(boolean value) {
        light = value;
        prefs.edit().putBoolean("light", light).apply();
        show(null, false);
        settings();
    }

    private void about() {
        var menu = sheet("Clenzy Lite " + BuildConfig.VERSION_NAME);
        menu.addView(label("Swipe left to move media to Android Trash. Swipe right for the next item. Starred items stay out of the regular queue.", 15, muted()));
        row(menu, "Back", this::options);
    }

    private LinearLayout sheet(String title) {
        pauseVideo();
        if (sheet != null) sheet.dismiss();
        sheet = new Dialog(this);
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        var menu = column();
        menu.setPadding(dp(16), dp(12), dp(16), dp(16));
        menu.setBackground(surface(panel(), 23));
        var heading = label(title, 22, ink());
        heading.setTypeface(null, Typeface.BOLD);
        menu.addView(heading);
        sheet.setContentView(menu);
        var window = sheet.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setGravity(Gravity.BOTTOM);
        sheet.show();
        window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(440)), -2);
        return menu;
    }

    private void row(LinearLayout menu, String title, Runnable action) {
        var row = button(title, false, () -> { if (sheet != null) sheet.dismiss(); action.run(); });
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        menu.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showBanner(String message, boolean offerUndo, boolean aboveVideo) {
        var banner = new LinearLayout(this);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(10), dp(3), dp(6), dp(3));
        banner.setBackground(surface(0xec201b2c, 12));
        var status = label(message, 14, Color.WHITE);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        banner.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));

        var host = root;
        host.addView(banner, overlayParams(Gravity.BOTTOM, aboveVideo ? 69 : 12));
        banner.postDelayed(() -> host.removeView(banner), 1900);
    }

    private void resetScreen() {
        previewVersion++;
        previewReady = false;
        stopVideo();
        root = new FrameLayout(this);
        root.setBackgroundColor(light ? 0xfff2eff6 : 0xff101014);
        setContentView(root);
        var controller = WindowCompat.getInsetsController(getWindow(), root);
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private LinearLayout center(String heading, String copy) {
        var content = column();
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(22), dp(15), dp(22), dp(15));
        var title = label(heading, 28, ink());
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        content.addView(title);
        if (!copy.isEmpty()) {
            var message = label(copy, 15, muted());
            message.setGravity(Gravity.CENTER);
            content.addView(message);
        }
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        return content;
    }

    private FrameLayout.LayoutParams overlayParams(int gravity, int margin) {
        var params = new FrameLayout.LayoutParams(-1, -2, gravity);
        params.setMargins(dp(12), dp(margin), dp(12), dp(margin));
        return params;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int ink() { return light ? 0xff211c2d : 0xfff7f4fb; }
    private int muted() { return light ? 0xff625d6e : 0xffaca7ba; }
    private int panel() { return light ? 0xffffffff : 0xff202029; }

    private LinearLayout column() {
        var layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView label(String value, int size, int color) {
        var text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setPadding(dp(4), dp(7), dp(4), dp(7));
        return text;
    }

    private Button button(String title, boolean primary, Runnable action) {
        return makeButton(title, primary ? 0xffc4b8ff : panel(), primary ? 0xff1c1237 : ink(), action);
    }

    private Button overlayButton(String title, Runnable action) {
        return makeButton(title, 0xe6322b41, Color.WHITE, action);
    }

    private Button makeButton(String title, int background, int color, Runnable action) {
        var button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextSize(14);
        button.setTextColor(color);
        button.setMinHeight(dp(46));
        button.setMinWidth(dp(46));
        button.setPadding(dp(9), dp(5), dp(9), dp(5));
        button.setStateListAnimator(null);
        button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337e64b5), surface(background, 13), null));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private GradientDrawable surface(int color, int radius) {
        var drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }
}
