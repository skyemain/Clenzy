package com.skyemain.clenzy

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.OutputStream

@RunWith(AndroidJUnit4::class)
class GalleryToolsJourneyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    private fun fixture(name: String, mime: String, write: (OutputStream) -> Unit): Uri {
        val collection = if (mime.startsWith("video"))
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = requireNotNull(context.contentResolver.insert(collection, ContentValues().apply {
            put("_display_name", name)
            put("mime_type", mime)
            put("is_pending", 1)
        }))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use(write)
        context.contentResolver.update(uri, ContentValues().apply { put("is_pending", 0) }, null, null)
        return uri
    }

    private fun photo(name: String) = fixture(name, "image/png") { stream ->
        val bitmap = Bitmap.createBitmap(1200, 1200, Bitmap.Config.ARGB_8888)
        try { bitmap.eraseColor(0xff43376b.toInt()); assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) }
        finally { bitmap.recycle() }
    }

    private fun click(selector: BySelector, timeout: Long = 6000) {
        requireNotNull(device.wait(Until.findObject(selector), timeout)) { "Missing ${selector}" }.click()
    }

    @Test fun swipeOpensPhotosDirectlyAndUndoClearsQueue() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_MEDIA_IMAGES)
        val photo = photo("Swipe Journey.png")
        try {
            ActivityScenario.launch(SwipeActivity::class.java).use {
                click(By.text("← Trash"))
                assertTrue(device.wait(Until.hasObject(By.text("Review Trash · 1")), 5000))
                assertFalse(TrashState.isTrashed(context.contentResolver, photo.toString()))
                click(By.text("Undo"))
                assertTrue(device.wait(Until.gone(By.text("Review Trash · 1")), 5000))
                assertTrue(device.hasObject(By.text("Keep →")))
            }
        } finally { context.contentResolver.delete(photo, null, null) }
    }

    @Test fun photoPreviewShowsRealSavingsAndKeepsSourceUntilConfirmed() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_MEDIA_IMAGES)
        val photo = photo("Clenzy compression journey.png")
        try {
            val initial = context.contentResolver.openInputStream(photo)!!.use { it.readBytes() }
            ActivityScenario.launch(CompressActivity::class.java).use {
                click(By.textStartsWith("Clenzy compression journey.png · "))
                click(By.text("Smallest"))
                click(By.text("Replace original"), 15000)
                assertTrue(device.wait(Until.hasObject(By.text("Replace original?")), 5000))
                click(By.text("Cancel"))
                val after = context.contentResolver.openInputStream(photo)!!.use { it.readBytes() }
                assertArrayEquals(initial, after)
            }
        } finally { context.contentResolver.delete(photo, null, null) }
    }

    @Test fun videoPreviewProducesSmallerPlayableMp4() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_MEDIA_VIDEO)
        val uri = fixture("Clenzy video journey.mp4", "video/mp4") { out ->
            instrumentation.context.assets.open("compress-fixture.mp4").use { it.copyTo(out) }
        }
        try {
            ActivityScenario.launch(CompressActivity::class.java).use {
                click(By.text("Videos"))
                click(By.textStartsWith("Clenzy video journey.mp4 · "))
                click(By.text("Smallest"))
                assertTrue("Video preview should pass size, duration and audio checks",
                    device.wait(Until.hasObject(By.text("Replace original")), 45000))
            }
        } finally { context.contentResolver.delete(uri, null, null) }
    }
}
