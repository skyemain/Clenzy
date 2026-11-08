package com.skyemain.clenzy

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class SwipeActivity : GalleryToolActivity() {
    internal class Session : ViewModel() {
        var deck: List<GalleryItem> = emptyList()
        var reviewed = 0
        var pending: GalleryItem? = null
        var approved = false
        var loaded = false
    }

    private val session by lazy { ViewModelProvider(this)[Session::class.java] }
    private val prefs by lazy { getSharedPreferences("swipe-review", MODE_PRIVATE) }
    private val shownHints = mutableSetOf<String>()
    private var preview: Job? = null
    private var busy = false
    private var notice = ""
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { load() }
    private val trash = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { response ->
        if (session.pending != null) {
            if (response.resultCode == Activity.RESULT_OK) {
                session.approved = true
                verifyTrash()
            } else {
                session.pending = null
                busy = false
                notice = "Photo kept in the gallery"
                render()
            }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!busy && session.pending == null) finish()
            }
        })
        if (session.loaded) {
            render()
            if (session.approved) verifyTrash()
        } else load()
    }

    private fun load() {
        screen("Swipe review")
        if (!hasGalleryAccess(this, false)) {
            page.addView(text("Allow photo access to review your gallery."))
            addButton("Allow photo access", true) { permission.launch(mediaPermission(false)) }
            return
        }
        page.addView(text("Loading photos…"))
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { galleryItems(this@SwipeActivity, false) } }
            session.deck = result.getOrDefault(emptyList())
            session.loaded = result.isSuccess
            notice = if (result.isFailure) "Gallery access is unavailable." else ""
            render()
        }
    }

    private fun render() {
        preview?.cancel()
        if (!session.loaded) {
            screen("Swipe review")
            page.addView(text(notice))
            addButton("Try again") { load() }
            return
        }
        val current = session.deck.getOrNull(session.reviewed)
        if (current == null) {
            screen("Swipe review")
            if (notice.isNotEmpty()) page.addView(text(notice, color = Aurora.MUTED))
            page.addView(text(if (session.deck.isEmpty()) "No photos to review" else "Gallery reviewed", 21))
            addButton("Done", true) { finish() }
            return
        }

        Aurora.window(this)
        val frame = FrameLayout(this).apply { background = Aurora.background() }
        val photo = photo().apply { setBackgroundColor(Aurora.BASE) }
        frame.addView(photo, FrameLayout.LayoutParams(-1, -1))

        // Keep the review controls over the image so they do not reduce its available height.
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xe6101014.toInt())
        }
        header.addView(Aurora.iconButton(this, "back", "Back") { onBackPressedDispatcher.onBackPressed() },
            LinearLayout.LayoutParams(dp(48), dp(48)))
        val labels = column()
        labels.addView(text("Swipe review", 19).apply { setPadding(0, 0, 0, 0) })
        labels.addView(text("${session.reviewed} of ${session.deck.size} photos reviewed", 12, Aurora.MUTED)
            .apply { setPadding(0, 0, 0, 0) })
        header.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        frame.addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val status = text(if (session.pending != null || busy) "Moving to Trash…" else notice, 13).apply {
            gravity = Gravity.CENTER
            background = Aurora.surface(this@SwipeActivity, Aurora.BASE, 10)
            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        frame.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(16), 0, dp(16), dp(14))
        })
        setContentView(frame)
        ViewCompat.setOnApplyWindowInsetsListener(frame) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            header.setPadding(bars.left, bars.top, bars.right, 0)
            status.translationY = -bars.bottom.toFloat()
            insets
        }
        ViewCompat.requestApplyInsets(frame)
        preview = lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { galleryPreview(this@SwipeActivity, current, 1440) }
            if (bitmap != null) {
                photo.setImageBitmap(bitmap)
                if (!busy && session.pending == null) showHint(frame, current)
            } else if (!busy && session.pending == null) {
                status.text = "Photo unavailable. Swipe right to continue."
                status.visibility = View.VISIBLE
            }
            bindGestures(photo, current, bitmap != null)
        }
    }

    private fun showHint(frame: FrameLayout, item: GalleryItem) {
        val hints = prefs.getInt("hints", 0)
        if (hints >= 3 || !shownHints.add(item.uri.toString())) return
        prefs.edit().putInt("hints", hints + 1).apply()
        val cue = text("← Trash     ·     Keep →", 15).apply {
            gravity = Gravity.CENTER
            background = Aurora.surface(this@SwipeActivity, Aurora.BASE, 10)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        frame.addView(cue, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        cue.translationX = -dp(10).toFloat()
        cue.animate().translationX(0f).setDuration(230).withEndAction {
            cue.animate().alpha(0f).setStartDelay(1000).setDuration(280)
                .withEndAction { frame.removeView(cue) }.start()
        }.start()
    }

    private fun bindGestures(photo: View, item: GalleryItem, loaded: Boolean) {
        photo.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        photo.contentDescription = if (loaded) "Gallery photo" else "Photo unavailable"
        photo.isFocusable = true
        ViewCompat.addAccessibilityAction(photo, "Keep photo") { _, _ -> decide(item, false); true }
        if (loaded) ViewCompat.addAccessibilityAction(photo, "Move photo to Trash") { _, _ -> decide(item, true); true }
        photo.setOnKeyListener { _, key, event ->
            if (event.action != KeyEvent.ACTION_UP) false
            else when (key) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> { decide(item, false); true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { if (loaded) decide(item, true); true }
                else -> false
            }
        }

        var startX = 0f
        var startY = 0f
        var tracking = false
        photo.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    tracking = !busy && session.pending == null
                    tracking
                }
                MotionEvent.ACTION_MOVE -> {
                    if (tracking) {
                        val dx = event.rawX - startX
                        photo.translationX = dx.coerceIn(-dp(75).toFloat(), dp(75).toFloat())
                    }
                    tracking
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    photo.translationX = 0f
                    if (tracking && abs(dx) >= dp(65) && abs(dx) > abs(dy) * 1.25f && (dx > 0 || loaded)) {
                        decide(item, dx < 0)
                    }
                    tracking = false
                    true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    photo.translationX = 0f
                    tracking = false
                    true
                }
                else -> false
            }
        }
    }

    private fun decide(item: GalleryItem, moveToTrash: Boolean) {
        if (busy || session.pending != null || session.deck.getOrNull(session.reviewed) != item) return
        notice = ""
        if (!moveToTrash) {
            session.reviewed++
            render()
            return
        }
        if (Build.VERSION.SDK_INT < 30) {
            notice = "Trash requires Android 11 or later."
            render()
            return
        }
        busy = true
        lifecycleScope.launch {
            val unchanged = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.query(item.uri, arrayOf("_display_name", "_size", "date_modified"), null, null, null)
                        ?.use { it.moveToFirst() && it.getString(0) == item.name &&
                            it.getLong(1) == item.size && it.getLong(2) == item.modified } == true
                }.getOrDefault(false)
            }
            if (unchanged) {
                try {
                    val request = MediaStore.createTrashRequest(contentResolver, listOf(item.uri), true)
                    session.pending = item
                    trash.launch(IntentSenderRequest.Builder(request).build())
                    return@launch
                } catch (_: Exception) {
                    session.pending = null
                }
            }
            busy = false
            notice = if (unchanged) "Android could not move this photo to Trash." else "Photo changed or is no longer available."
            render()
        }
    }

    private fun verifyTrash() {
        val item = session.pending ?: return
        busy = true
        render()
        lifecycleScope.launch {
            val moved = withContext(Dispatchers.IO) { TrashState.isTrashed(contentResolver, item.uri.toString()) }
            if (moved) {
                session.reviewed++
                CleanupHistory.record(this@SwipeActivity, item.size, 1, 0)
            }
            session.pending = null
            session.approved = false
            busy = false
            notice = if (moved) "Moved to Trash" else "Photo was not moved to Trash"
            render()
        }
    }
}
