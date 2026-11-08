package com.skyemain.clenzy

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.VideoEncoderSettings
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class CompressActivity : GalleryToolActivity() {
    private enum class Quality(val label: String, val photoEdge: Int, val jpegQuality: Int, val videoHeight: Int) {
        HIGH("Higher quality", 2560, 90, 1080),
        BALANCED("Balanced", 1600, 82, 720),
        SMALL("Smallest", 960, 70, 480)
    }
    private data class Preview(val item: GalleryItem, val directory: File, val original: File, val output: File)
    private val replacement by lazy { MediaReplacement(this) }
    private var video = false
    private var busy = false
    private var writing = false
    private var item: GalleryItem? = null
    private var preview: Preview? = null
    private var encoding: Transformer? = null
    private var workingDirectory: File? = null
    private var recovery: MediaReplacement.Pending? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { listMedia() }
    private val writePermission = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        writing = false
        if (result.resultCode == Activity.RESULT_OK) commit()
        else {
            val pending = recovery
            val draft = preview
            if (pending != null) showRecovery(pending)
            else if (draft != null) showPreview(draft)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        video = intent.getBooleanExtra("video", false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (busy || writing) return
                if (item != null) { discardPreview(); item = null; listMedia() } else finish()
            }
        })
        screen("Compress media")
        if (Build.VERSION.SDK_INT < 30) {
            page.addView(text("Same-name replacement requires Android 11 or later."))
            return
        }
        lifecycleScope.launch {
            val pending = withContext(Dispatchers.IO) { runCatching { replacement.unresolved() } }
            pending.fold(
                onSuccess = { if (it != null) showRecovery(it) else listMedia() },
                onFailure = { message("Recovery could not be checked. Compression is unavailable until it can be read.") }
            )
        }
    }

    private fun listMedia() {
        screen("Compress media")
        val tabs = LinearLayout(this)
        tabs.addView(control("Photos", !video) { video = false; listMedia() }, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(control("Videos", video) { video = true; listMedia() }, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(tabs)
        page.addView(text(if (video) "Choose an MP4 video" else "Choose a JPEG or PNG photo", color = Aurora.MUTED))
        if (!hasGalleryAccess(this, video)) {
            addButton(if (video) "Allow video access" else "Allow photo access", true) { permission.launch(mediaPermission(video)) }
            return
        }
        val requestedVideo = video
        val target = page
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                runCatching { galleryItems(this@CompressActivity, requestedVideo) }
            }
            if (page !== target) return@launch
            val supported = files.getOrDefault(emptyList()).filter {
                it.size > 0 && when (it.mime) {
                    "video/mp4" -> it.name.endsWith(".mp4", true)
                    "image/jpeg" -> it.name.endsWith(".jpg", true) || it.name.endsWith(".jpeg", true)
                    "image/png" -> it.name.endsWith(".png", true)
                    else -> false
                }
            }.sortedByDescending { it.size }
            if (supported.isEmpty()) {
                page.addView(text(if (files.isFailure) "Media could not be read." else "No supported files found."))
                return@launch
            }
            val list = ListView(this@CompressActivity)
            list.adapter = ArrayAdapter(this@CompressActivity, android.R.layout.simple_list_item_1,
                supported.map { "${it.name} · ${bytes(it.size)}" })
            list.setOnItemClickListener { _, _, position, _ -> chooseQuality(supported[position]) }
            page.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    private fun chooseQuality(selected: GalleryItem) {
        discardPreview()
        item = selected
        screen("Compress media")
        page.addView(text(selected.name, 19))
        page.addView(text("${bytes(selected.size)} · Choose a preset to create a preview", color = Aurora.MUTED))
        for (quality in Quality.entries) addButton(quality.label, quality == Quality.BALANCED) { prepare(selected, quality) }
    }

    private fun prepare(selected: GalleryItem, quality: Quality) {
        busy = true
        screen("Compress media")
        page.addView(text("Preparing preview…", 19))
        page.addView(android.widget.ProgressBar(this))
        lifecycleScope.launch {
            try {
                val directory = withContext(Dispatchers.IO) {
                    val dir = File(cacheDir, "compress-${java.util.UUID.randomUUID()}")
                    check(dir.mkdir())
                    dir
                }
                workingDirectory = directory
                val source = File(directory, if (selected.mime == "video/mp4") "original.mp4" else "original-image")
                val output = File(directory, if (video) "compressed.mp4" else "compressed-image")
                withContext(Dispatchers.IO) { replacement.snapshot(selected, source) }
                val draft = Preview(selected, directory, source, output)
                if (selected.mime == "video/mp4") encodeVideo(draft, quality)
                else {
                    withContext(Dispatchers.IO) { encodePhoto(draft, quality) }
                    completePreview(draft)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                failPreview(selected, "This file could not be compressed. The original was kept.")
            }
        }
    }

    private fun encodePhoto(draft: Preview, quality: Quality) {
        if (Build.VERSION.SDK_INT < 28) throw UnsupportedOperationException("Image decoding requires Android 9")
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(draft.original)) { decoder, info, _ ->
            require(!info.isAnimated)
            val width = info.size.width
            val height = info.size.height
            val scale = minOf(1f, quality.photoEdge.toFloat() / maxOf(width, height))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
        }
        try {
            val format = if (draft.item.mime == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            draft.output.outputStream().use { check(bitmap.compress(format, quality.jpegQuality, it)) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(draft.output.path, bounds)
            check(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType == draft.item.mime)
        } finally { bitmap.recycle() }
    }

    private data class VideoInfo(val duration: Long, val audio: Boolean, val height: Int, val hdr: Boolean)
    private fun videoInfo(file: File): VideoInfo = MediaMetadataRetriever().let { retriever ->
        try {
            retriever.setDataSource(file.path)
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes")
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val heightKey = if (rotation == 90 || rotation == 270)
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH else MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            val transfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)?.toIntOrNull()
            VideoInfo(requireNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
                retriever.extractMetadata(heightKey)?.toIntOrNull() ?: 0, transfer == 6 || transfer == 7)
        } finally { retriever.release() }
    }

    private suspend fun encodeVideo(draft: Preview, quality: Quality) {
        val source = withContext(Dispatchers.IO) { videoInfo(draft.original) }
        require(source.duration > 0 && source.height > 0 && !source.hdr)
        val targetHeight = minOf(source.height, quality.videoHeight).let { it - it % 2 }
        require(targetHeight >= 2)
        val media = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(draft.original)))
            .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(targetHeight)))).build()
        val bitrateFraction = when (quality) {
            Quality.HIGH -> 0.75
            Quality.BALANCED -> 0.50
            Quality.SMALL -> 0.30
        }
        val bitrate = (draft.original.length() * 8000.0 / source.duration * bitrateFraction)
            .coerceIn(160_000.0, 12_000_000.0).toInt()
        val encoder = DefaultEncoderFactory.Builder(this).setRequestedVideoEncoderSettings(
            VideoEncoderSettings.Builder().setBitrate(bitrate).build()).build()
        encoding = Transformer.Builder(this).setEncoderFactory(encoder).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    encoding = null
                    lifecycleScope.launch {
                        val output = withContext(Dispatchers.IO) { runCatching { videoInfo(draft.output) }.getOrNull() }
                        if (output != null && output.audio == source.audio && kotlin.math.abs(output.duration - source.duration) <= 750)
                            completePreview(draft)
                        else failPreview(draft.item, "The video preview did not pass verification. The original was kept.")
                    }
                }
                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                    encoding = null
                    failPreview(draft.item, "The device could not encode this video. The original was kept.")
                }
            }).build().also { it.start(media, draft.output.path) }
    }

    private fun completePreview(draft: Preview) {
        busy = false
        if (draft.output.length() !in 1 until draft.original.length()) {
            failPreview(draft.item, "This preset didn’t make a smaller file. Try a different preset.")
            return
        }
        preview = draft
        showPreview(draft)
    }

    private fun failPreview(selected: GalleryItem, error: String) {
        busy = false
        chooseQuality(selected)
        message(error)
    }

    private fun showPreview(draft: Preview) {
        screen("Compress media")
        page.addView(text(draft.item.name, 18))
        page.addView(text("${bytes(draft.original.length())} → ${bytes(draft.output.length())}", 19, Aurora.ACCENT))
        val comparison = FrameLayout(this)
        val before = photo()
        val after = photo()
        comparison.addView(before, FrameLayout.LayoutParams(-1, -1))
        comparison.addView(after, FrameLayout.LayoutParams(-1, -1))
        page.addView(comparison, LinearLayout.LayoutParams(-1, 0, 1f))
        val slider = SeekBar(this).apply { max = 100; progress = 50; contentDescription = "Original and compressed comparison" }
        fun clip() { after.clipBounds = Rect(after.width * slider.progress / 100, 0, after.width, after.height) }
        comparison.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> clip() }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) = clip()
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
        page.addView(text(if (draft.item.mime == "video/mp4") "Original frame ↔ Compressed frame" else "Original ↔ Compressed", color = Aurora.MUTED))
        page.addView(slider)
        lifecycleScope.launch {
            val first = withContext(Dispatchers.IO) { sampleFrame(draft.original, draft.item.mime == "video/mp4") }
            val second = withContext(Dispatchers.IO) { sampleFrame(draft.output, draft.item.mime == "video/mp4") }
            before.setImageBitmap(first)
            after.setImageBitmap(second)
        }
        addButton("Change preset") { chooseQuality(draft.item) }
        addButton("Replace original", true) {
            AuroraDialog.Builder(this).setTitle("Replace original?")
                .setMessage("${draft.item.name} will keep the same filename. Compression may reduce quality and remove metadata.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm replacement") { _, _ -> requestWrite(draft.item.uri) }.show()
        }
    }

    private fun sampleFrame(file: File, isVideo: Boolean): Bitmap? {
        if (Build.VERSION.SDK_INT < 28) return null
        return runCatching {
            if (!isVideo) ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val scale = minOf(1f, 1080f / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1))
            } else MediaMetadataRetriever().let {
                try {
                    it.setDataSource(file.path)
                    it.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST, 640, 640)
                } finally { it.release() }
            }
        }.getOrNull()
    }

    private fun requestWrite(uri: Uri) {
        if (busy || writing || Build.VERSION.SDK_INT < 30) return
        runCatching {
            val request = MediaStore.createWriteRequest(contentResolver, listOf(uri))
            writing = true
            writePermission.launch(IntentSenderRequest.Builder(request).build())
        }.onFailure { writing = false; message("Android could not open the write confirmation.") }
    }

    private fun commit() {
        val draft = preview
        val pending = recovery
        if (draft == null && pending == null) return
        busy = true
        screen("Compress media")
        page.addView(text(if (pending == null) "Verifying replacement…" else "Restoring original…"))
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO + NonCancellable) {
                runCatching {
                    if (pending != null) {
                        if (replacement.restore(pending)) MediaReplacement.Result.UNCHANGED
                        else MediaReplacement.Result.RECOVERY_NEEDED
                    } else {
                        val data = requireNotNull(draft)
                        replacement.replace(data.item, data.original, data.output)
                    }
                }
            }
            busy = false
            val remaining = withContext(Dispatchers.IO) { runCatching { replacement.unresolved() } }
            remaining.getOrNull()?.let { showRecovery(it); return@launch }
            if (remaining.isFailure) {
                message("Recovery could not be checked. Reopen Compress media before trying again.")
                return@launch
            }
            if (result.getOrNull() == MediaReplacement.Result.REPLACED && draft != null) {
                val saved = draft.original.length() - draft.output.length()
                CleanupHistory.record(this@CompressActivity, saved, 1, 0)
                discardPreview()
                item = null
                screen("Compress media")
                page.addView(text("${bytes(saved)} saved", 22, Aurora.ACCENT))
                page.addView(text("${draft.item.name} · Original replaced"))
                addButton("Done", true) { finish() }
            } else if (pending != null && result.getOrNull() == MediaReplacement.Result.UNCHANGED) {
                recovery = null
                listMedia()
                message("Original restored.")
            } else {
                draft?.let { chooseQuality(it.item) } ?: listMedia()
                message("Replacement could not be completed. The original was kept.")
            }
        }
    }

    private fun showRecovery(pending: MediaReplacement.Pending) {
        recovery = pending
        screen("Recover original")
        page.addView(text("Clenzy saved an original copy of ${pending.name} before an interrupted replacement."))
        addButton("Restore original", true) { requestWrite(pending.uri) }
    }

    private fun message(value: String) {
        AuroraDialog.Builder(this).setMessage(value).setPositiveButton("Done", null).show()
    }

    private fun discardPreview() {
        preview = null
        workingDirectory?.deleteRecursively()
        workingDirectory = null
    }

    override fun onDestroy() {
        encoding?.cancel()
        if (!busy) discardPreview()
        super.onDestroy()
    }
}
