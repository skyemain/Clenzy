package com.skyemain.clenzy

import android.Manifest.permission.*
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.Size
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

internal data class GalleryItem(val uri: Uri, val name: String, val mime: String, val size: Long, val modified: Long)

internal fun galleryItems(context: Context, video: Boolean): List<GalleryItem> {
    val volumes = if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(context) else setOf("external")
    val selection = when {
        Build.VERSION.SDK_INT >= 30 -> "is_pending=0 AND is_trashed=0"
        Build.VERSION.SDK_INT == 29 -> "is_pending=0"
        else -> null
    }
    return buildList {
        for (volume in volumes) {
            val uri = if (video) MediaStore.Video.Media.getContentUri(volume) else MediaStore.Images.Media.getContentUri(volume)
            context.contentResolver.query(uri, arrayOf("_id", "_display_name", "mime_type", "_size", "date_modified"),
                selection, null, "date_modified DESC, _id DESC")?.use { cursor ->
                while (cursor.moveToNext()) add(GalleryItem(ContentUris.withAppendedId(uri, cursor.getLong(0)),
                    cursor.getString(1) ?: "Media", cursor.getString(2).orEmpty(), cursor.getLong(3), cursor.getLong(4)))
            }
        }
    }.sortedByDescending { it.modified }
}

internal fun mediaPermission(video: Boolean): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(if (video) READ_MEDIA_VIDEO else READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(if (video) READ_MEDIA_VIDEO else READ_MEDIA_IMAGES)
    else -> arrayOf(READ_EXTERNAL_STORAGE)
}

internal fun hasGalleryAccess(context: Context, video: Boolean) =
    mediaPermission(video).any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

internal fun galleryPreview(context: Context, item: GalleryItem, edge: Int): Bitmap? = runCatching {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= 29) resolver.loadThumbnail(item.uri, Size(edge, edge), null)
    else {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(item.uri).use { BitmapFactory.decodeStream(it, null, options) }
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > edge) options.inSampleSize *= 2
        resolver.openInputStream(item.uri).use { BitmapFactory.decodeStream(it, null, options) }
    }
}.getOrNull()

abstract class GalleryToolActivity : ComponentActivity() {
    protected lateinit var page: LinearLayout
    protected fun dp(value: Int) = Aurora.dp(this, value)
    protected fun bytes(value: Long) = Formatter.formatShortFileSize(this, value)
    protected fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    protected fun screen(title: String) {
        Aurora.window(this)
        page = column().apply { background = Aurora.background() }
        val header = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        header.addView(Aurora.iconButton(this, "back", "Back") { onBackPressedDispatcher.onBackPressed() },
            LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(text(title, 23).apply { setTypeface(null, android.graphics.Typeface.BOLD) })
        page.addView(header)
        setContentView(page)
        ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(14) + bars.left, dp(5) + bars.top, dp(14) + bars.right, dp(8) + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(page)
    }

    protected fun text(value: String, size: Int = 14, color: Int = Aurora.INK) = TextView(this).apply {
        this.text = value
        textSize = size.toFloat()
        setTextColor(color)
        setPadding(dp(4), dp(7), dp(4), dp(7))
    }

    protected fun control(label: String, primary: Boolean = false, onClick: () -> Unit) =
        MaterialButton(this).apply {
            text = label
            Aurora.button(this, primary)
            setOnClickListener { onClick() }
        }

    protected fun addButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
        page.addView(control(label, primary, onClick), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
    }

    protected fun photo() = ImageView(this).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setBackgroundColor(Aurora.SURFACE)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}
