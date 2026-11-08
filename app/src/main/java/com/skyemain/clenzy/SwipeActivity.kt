package com.skyemain.clenzy

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class SwipeActivity : GalleryToolActivity() {
    internal data class Decision(val item: GalleryItem, val trash: Boolean)
    internal class Session : ViewModel() {
        var deck: List<GalleryItem> = emptyList()
        val decisions = mutableListOf<Decision>()
        var pending: List<GalleryItem> = emptyList()
        var loaded = false
    }

    private val session by lazy { ViewModelProvider(this)[Session::class.java] }
    private val prefs by lazy { getSharedPreferences("swipe-review", MODE_PRIVATE) }
    private var preview: Job? = null
    private var busy = false
    private var notice = ""
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { load() }
    private val trash = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { response ->
        val requested = session.pending
        session.pending = emptyList()
        if (response.resultCode != Activity.RESULT_OK) {
            notice = "Trash request canceled. Your queue is still available."
            render()
        } else lifecycleScope.launch {
            busy = true
            render()
            val moved = withContext(Dispatchers.IO) {
                requested.filter { TrashState.isTrashed(contentResolver, it.uri.toString()) }
            }.toSet()
            session.deck = session.deck.filterNot { it in moved }
            session.decisions.removeAll { it.item in moved }
            CleanupHistory.record(this@SwipeActivity, moved.sumOf { it.size }, moved.size, requested.size - moved.size)
            busy = false
            notice = if (moved.isEmpty()) "No photos moved to Trash." else "${moved.size} moved to Trash"
            render()
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (busy || session.pending.isNotEmpty()) return
                if (session.decisions.none { it.trash }) finish()
                else AuroraDialog.Builder(this@SwipeActivity).setTitle("Leave swipe review?")
                    .setMessage("Photos in your queue will stay in the gallery.")
                    .setNegativeButton("Continue review", null)
                    .setPositiveButton("Leave", { _, _ -> finish() }).show()
            }
        })
        if (session.loaded) render() else load()
    }

    private fun load() {
        if (!hasGalleryAccess(this, false)) {
            screen("Swipe review")
            page.addView(text("Allow photo access to review your gallery."))
            addButton("Allow photo access", true) { permission.launch(mediaPermission(false)) }
            return
        }
        screen("Swipe review")
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
        screen("Swipe review")
        if (!session.loaded) {
            page.addView(text(notice))
            addButton("Try again") { load() }
            return
        }
        val current = session.deck.getOrNull(session.decisions.size)
        val queued = session.decisions.filter { it.trash }.map { it.item }
        page.addView(text("${session.decisions.size} of ${session.deck.size} photos reviewed", color = Aurora.MUTED))
        if (notice.isNotEmpty()) page.addView(text(notice, color = Aurora.MUTED))
        if (busy) {
            page.addView(text("Checking Trash…"))
            return
        }
        if (current == null) {
            page.addView(text(if (session.deck.isEmpty()) "No photos to review" else "Gallery reviewed", 21))
        } else {
            val frame = FrameLayout(this).apply {
                background = Aurora.surface(this@SwipeActivity, Aurora.SURFACE, 20)
                clipToOutline = true
                contentDescription = "Gallery photo"
            }
            val photo = photo()
            frame.addView(photo, FrameLayout.LayoutParams(-1, -1))
            page.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
            preview = lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) { galleryPreview(this@SwipeActivity, current, 1080) }
                if (bitmap != null) photo.setImageBitmap(bitmap)
            }
            val hints = prefs.getInt("hints", 0)
            if (hints < 3 && shownHints.add(current.uri.toString())) {
                prefs.edit().putInt("hints", hints + 1).apply()
                val cue = text("← Trash                 Keep →", 15).apply {
                    gravity = Gravity.CENTER
                    background = Aurora.surface(this@SwipeActivity, Aurora.BASE, 10)
                }
                frame.addView(cue, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                    setMargins(dp(12), 0, dp(12), dp(16))
                })
                cue.translationX = -dp(10).toFloat()
                cue.animate().translationX(0f).setDuration(230).withEndAction {
                    cue.animate().alpha(0f).setStartDelay(1000).setDuration(280).start()
                }.start()
            }
            var startX = 0f
            frame.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { startX = event.rawX; true }
                    MotionEvent.ACTION_MOVE -> {
                        frame.translationX = (event.rawX - startX).coerceIn(-dp(75).toFloat(), dp(75).toFloat())
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val delta = event.rawX - startX
                        if (abs(delta) >= dp(65)) decide(current, delta < 0)
                        else { frame.translationX = 0f; frame.performClick() }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> { frame.translationX = 0f; true }
                    else -> false
                }
            }
            val actions = LinearLayout(this)
            actions.addView(control("← Trash") { decide(current, true) }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(control("Keep →") { decide(current, false) }, LinearLayout.LayoutParams(0, -2, 1f))
            page.addView(actions)
        }
        if (session.decisions.isNotEmpty()) addButton("Undo") {
            session.decisions.removeAt(session.decisions.lastIndex)
            notice = ""
            render()
        }
        if (queued.isNotEmpty()) addButton("Review Trash · ${queued.size}", true) { requestTrash(queued) }
        else if (current == null) addButton("Done", true) { finish() }
    }

    private val shownHints = mutableSetOf<String>()

    private fun decide(item: GalleryItem, moveToTrash: Boolean) {
        if (busy || session.pending.isNotEmpty() || session.deck.getOrNull(session.decisions.size) != item) return
        notice = ""
        session.decisions += Decision(item, moveToTrash)
        render()
    }

    private fun requestTrash(queued: List<GalleryItem>) {
        if (Build.VERSION.SDK_INT < 30) {
            AuroraDialog.Builder(this).setTitle("Trash requires Android 11")
                .setMessage("You can review photos on this device. Use your Gallery app to remove them.")
                .setPositiveButton("Done", null).show()
            return
        }
        val batch = queued.take(1000)
        AuroraDialog.Builder(this).setTitle("Move ${batch.size} photos to Trash?")
            .setMessage("Android will ask you to confirm. Only photos in this queue are included.")
            .setNegativeButton("Keep reviewing", null).setPositiveButton("Continue") { _, _ ->
                if (session.pending.isNotEmpty()) return@setPositiveButton
                runCatching {
                    val request = MediaStore.createTrashRequest(contentResolver, batch.map { it.uri }, true)
                    session.pending = batch
                    trash.launch(IntentSenderRequest.Builder(request).build())
                }.onFailure {
                    session.pending = emptyList()
                    notice = "Android could not open Trash confirmation."
                    render()
                }
            }.show()
    }
}
