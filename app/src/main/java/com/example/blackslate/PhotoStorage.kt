package com.example.blackslate

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ImportedPhotos(val paths: List<String>, val skipped: Int)

object PhotoStorage {
    private fun directory(context: Context): File = File(context.filesDir, "transaction_photos")
    fun cameraFile(context: Context): File = File.createTempFile("draft-photo-", ".jpg", File(context.cacheDir, "camera").apply { mkdirs() })
    fun cameraUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    suspend fun importSelected(context: Context, uris: List<String>): ImportedPhotos = withContext(Dispatchers.IO) {
        val saved = mutableListOf<String>()
        var skipped = 0
        try {
        for (source in uris.take(12)) {
            val output = File(directory(context).apply { mkdirs() }, UUID.randomUUID().toString() + ".image")
            try {
                context.contentResolver.openInputStream(Uri.parse(source)).use { input ->
                    requireNotNull(input) { "Image unavailable" }
                    output.outputStream().use { destination ->
                        val buffer = ByteArray(8192)
                        var bytes = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            bytes += count
                            require(bytes <= 20L * 1024 * 1024) { "Image too large" }
                            destination.write(buffer, 0, count)
                        }
                        require(bytes > 0) { "Empty image" }
                    }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(output.absolutePath, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image" }
                saved += output.absolutePath
            } catch (cancelled: CancellationException) {
                output.delete()
                throw cancelled
            } catch (_: Exception) {
                output.delete()
                skipped++
            }
        }
        ImportedPhotos(saved, skipped + (uris.size - 12).coerceAtLeast(0))
        } catch (cancelled: CancellationException) {
            saved.forEach { File(it).delete() }
            throw cancelled
        }
    }
    fun bitmap(context: Context, path: String, maxEdge: Int = 1600): ImageBitmap? {
        val file = safeFile(context, path) ?: return null
        if (!file.isFile) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }
    fun delete(context: Context, paths: List<String>) {
        paths.forEach { path -> safeFile(context, path)?.delete() }
    }
    fun discardCameraDrafts(context: Context, uris: List<String>) {
        val root = File(context.cacheDir, "camera").canonicalFile
        uris.forEach { raw ->
            val uri = Uri.parse(raw)
            if (uri.authority == context.packageName + ".fileprovider") {
                val name = uri.lastPathSegment ?: return@forEach
                val file = File(root, name).canonicalFile
                if (file.parentFile == root && name.startsWith("draft-photo-") && name.endsWith(".jpg")) file.delete()
            }
        }
    }
    fun releaseDraftGrants(context: Context, uris: List<String>) {
        uris.forEach { raw ->
            val uri = Uri.parse(raw)
            if (uri.scheme == "content" && uri.authority != context.packageName + ".fileprovider")
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
        }
    }
    private fun safeFile(context: Context, path: String): File? {
        val root = directory(context).canonicalFile
        val candidate = File(path).canonicalFile
        return if (candidate.parentFile == root && candidate.name.endsWith(".image")) candidate else null
    }
}
