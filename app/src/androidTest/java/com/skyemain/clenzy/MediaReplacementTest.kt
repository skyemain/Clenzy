package com.skyemain.clenzy

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MediaReplacementTest {
    @Test fun replacesSameGalleryItemAndRejectsStaleSource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = context.contentResolver
        val name = "Clenzy test ${java.util.UUID.randomUUID()}.png"
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply { put("_display_name", name); put("mime_type", "image/png"); put("is_pending", 1) }))
        val original = File.createTempFile("test-original", ".png", context.cacheDir)
        val smaller = File.createTempFile("test-output", ".png", context.cacheDir)
        fun image(edge: Int, target: java.io.OutputStream) {
            val bitmap = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
            try { target.use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
        }
        try {
            image(512, requireNotNull(resolver.openOutputStream(uri)))
            resolver.update(uri, ContentValues().apply { put("is_pending", 0) }, null, null)
            val item = galleryItems(context, false).single { it.uri == uri }
            val service = MediaReplacement(context)
            service.snapshot(item, original)
            image(40, smaller.outputStream())
            assertEquals(MediaReplacement.Result.REPLACED, service.replace(item, original, smaller))
            assertNull(service.pending())
            assertEquals(name, galleryItems(context, false).single { it.uri == uri }.name)
            assertEquals(40, resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it).width })
            assertEquals(MediaReplacement.Result.UNCHANGED, service.replace(item, original, smaller))
        } finally {
            original.delete()
            smaller.delete()
            resolver.delete(uri, null, null)
        }
    }
}
