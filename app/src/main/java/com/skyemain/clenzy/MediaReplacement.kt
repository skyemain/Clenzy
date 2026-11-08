package com.skyemain.clenzy

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Keeps a synced original in private storage until a same-URI write has been verified. */
internal class MediaReplacement(context: Context) {
    private val resolver = context.contentResolver
    private val directory = File(context.noBackupFilesDir, "media-recovery")
    private val journal = AtomicFile(File(directory, "pending.json"))
    private val original = File(directory, "original")
    data class Pending(val uri: Uri, val name: String, val originalHash: String, val replacementHash: String)
    enum class Result { REPLACED, UNCHANGED, RECOVERY_NEEDED }

    fun pending(): Pending? {
        if (!journal.baseFile.exists()) return null
        val data = JSONObject(journal.readFully().decodeToString())
        return Pending(Uri.parse(data.getString("uri")), data.getString("name"),
            data.getString("originalHash"), data.getString("replacementHash"))
    }

    fun snapshot(item: GalleryItem, file: File) {
        require(currentName(item.uri) == item.name)
        requireNotNull(resolver.openInputStream(item.uri)).use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        check(file.length() == item.size && file.length() > 0)
    }

    fun replace(item: GalleryItem, stagedOriginal: File, replacement: File): Result = synchronized(lock) {
        check(pending() == null)
        require(replacement.length() in 1 until stagedOriginal.length())
        val sourceHash = digest(stagedOriginal)
        val outputHash = digest(replacement)
        if (currentName(item.uri) != item.name || currentHash(item.uri) != sourceHash) return Result.UNCHANGED

        directory.mkdirs()
        stagedOriginal.inputStream().use { input ->
            FileOutputStream(original).use { output -> input.copyTo(output); output.fd.sync() }
        }
        val transaction = Pending(item.uri, item.name, sourceHash, outputHash)
        val record = JSONObject().put("uri", item.uri.toString()).put("name", item.name)
            .put("originalHash", sourceHash).put("replacementHash", outputHash)
        val output = journal.startWrite()
        try {
            output.write(record.toString().toByteArray())
            journal.finishWrite(output)
        } catch (e: Exception) {
            journal.failWrite(output)
            throw e
        }

        // Check again after staging the recovery copy, before opening a truncating descriptor.
        if (currentName(item.uri) != item.name || currentHash(item.uri) != sourceHash) {
            clear()
            return Result.UNCHANGED
        }
        try {
            write(item.uri, replacement)
            check(currentHash(item.uri) == outputHash)
            clear()
            Result.REPLACED
        } catch (_: Exception) {
            if (restore(transaction)) Result.UNCHANGED else Result.RECOVERY_NEEDED
        }
    }

    /** Resolves an interrupted operation if the on-device copy is already intact. */
    fun unresolved(): Pending? = synchronized(lock) {
        val record = pending() ?: return null
        val hash = currentHash(record.uri)
        if (currentName(record.uri) == record.name &&
            (hash == record.originalHash || hash == record.replacementHash)) {
            clear()
            null
        } else record
    }

    fun restore(record: Pending): Boolean = synchronized(lock) {
        if (pending() != record || currentName(record.uri) != record.name) return false
        runCatching {
            check(digest(original) == record.originalHash)
            if (currentHash(record.uri) != record.originalHash) write(record.uri, original)
            check(currentHash(record.uri) == record.originalHash)
            clear()
        }.isSuccess
    }

    private fun clear() {
        journal.delete()
        original.delete()
    }

    private fun currentName(uri: Uri) = runCatching {
        resolver.query(uri, arrayOf("_display_name"), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    private fun currentHash(uri: Uri) = runCatching {
        requireNotNull(resolver.openInputStream(uri)).use(::digest)
    }.getOrNull()

    private fun write(uri: Uri, file: File) {
        val descriptor = requireNotNull(resolver.openFileDescriptor(uri, "rwt"))
        android.os.ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { out ->
            file.inputStream().use { it.copyTo(out) }
            out.fd.sync()
        }
    }

    private fun digest(file: File) = file.inputStream().use(::digest)
    private fun digest(stream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        while (true) {
            val count = stream.read(buffer)
            if (count == -1) break
            digest.update(buffer, 0, count)
        }
        return android.util.Base64.encodeToString(digest.digest(), android.util.Base64.NO_WRAP)
    }

    companion object { private val lock = Any() }
}
