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
import java.util.regex.Pattern

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

    private fun swipePhoto(left: Boolean) {
        val photo = requireNotNull(device.wait(Until.findObject(By.desc("Gallery photo")), 6000))
        val area = photo.visibleBounds
        val start = area.left + area.width() * (if (left) 3 else 1) / 4
        val end = area.left + area.width() * (if (left) 1 else 3) / 4
        assertTrue(device.swipe(start, area.centerY(), end, area.centerY(), 15))
    }

    @Test fun swipeActsOnCurrentPhotoWithoutClenzyConfirmation() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_MEDIA_IMAGES)
        val older = photo("Swipe Journey A.png")
        val newest = photo("Swipe Journey B.png")
        try {
            val ordered = galleryItems(context, false)
            assertTrue(ordered[0].uri in listOf(older, newest) && ordered[1].uri in listOf(older, newest))
            ActivityScenario.launch(SwipeActivity::class.java).use {
                assertFalse(device.hasObject(By.text("← Trash")))
                assertFalse(device.hasObject(By.text("Keep →")))
                swipePhoto(left = false)
                assertTrue(device.wait(Until.hasObject(By.textStartsWith("1 of ")), 5000))
                assertFalse(TrashState.isTrashed(context.contentResolver, ordered[0].uri.toString()))

                swipePhoto(left = true)
                val deny = By.text(Pattern.compile("(?i)(deny|cancel)"))
                val prompt = requireNotNull(device.wait(Until.findObject(deny), 5000))
                assertNotEquals(context.packageName, prompt.applicationPackage)
                prompt.click()
                assertTrue(device.wait(Until.hasObject(By.text("Photo kept in the gallery")), 5000))
                assertFalse(TrashState.isTrashed(context.contentResolver, ordered[1].uri.toString()))

                swipePhoto(left = true)
                click(By.text(Pattern.compile("(?i)(allow|trash|move to trash)")))
                assertTrue(device.wait(Until.hasObject(By.text("Moved to Trash")), 8000))
                assertTrue(TrashState.isTrashed(context.contentResolver, ordered[1].uri.toString()))
                assertFalse(TrashState.isTrashed(context.contentResolver, ordered[0].uri.toString()))
                assertFalse(device.hasObject(By.textStartsWith("Review Trash")))
            }
        } finally {
            val args = android.os.Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            }
            listOf(older, newest).forEach { context.contentResolver.delete(it, args) }
        }
    }

    @Test fun photoPreviewShowsRealSavingsAndKeepsSourceUntilConfirmed() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_MEDIA_IMAGES)
        val photo = photo("Clenzy compression journey.png")
        try {
            val initial = context.contentResolver.openInputStream(photo)!!.use { it.readBytes() }
            ActivityScenario.launch(CompressActivity::class.java).use {
                click(By.descStartsWith("Clenzy compression journey.png · "))
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
                click(By.descStartsWith("Clenzy video journey.mp4 · "))
                click(By.text("Smallest"))
                assertTrue("Video preview should pass size, duration and audio checks",
                    device.wait(Until.hasObject(By.text("Replace original")), 45000))
            }
        } finally { context.contentResolver.delete(uri, null, null) }
    }
}
