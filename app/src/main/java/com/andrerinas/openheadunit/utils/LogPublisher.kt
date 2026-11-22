package com.andrerinas.openheadunit.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/**
 * Puts a copy of an exported log somewhere the head unit's own file manager can open.
 *
 * Since Android 10 the "Downloads" log location resolves to the app-specific
 * `Android/data/<package>/files/Download`, which stock file managers on Android 11+ cannot browse,
 * so an export reported a path nobody could reach. The log itself stays where capture and rotation
 * expect it; this only publishes a copy.
 */
object LogPublisher {

    private const val MIME = "text/plain"
    private const val DOWNLOADS_SUBDIR = "DiAuto"

    /**
     * Publishes [file] according to the configured log location and returns a human-readable
     * description of where the visible copy landed, or null when the location needs no copy
     * (app storage, or a pre-Android 10 public Downloads path that is already visible).
     *
     * @throws IOException when the destination refused the copy.
     */
    fun publish(context: Context, settings: Settings, file: File): String? {
        return when (settings.logLocation) {
            Settings.LogLocation.DEFAULT -> null
            Settings.LogLocation.DOWNLOADS -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
                copyToDownloads(context, file)
                "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOADS_SUBDIR/${file.name}"
            }
            Settings.LogLocation.CUSTOM -> {
                val tree = settings.logTreeUri.takeIf { it.isNotEmpty() }
                    ?: throw IOException("No folder selected")
                val treeUri = Uri.parse(tree)
                copyToTree(context, treeUri, file)
                "${describeTree(treeUri)}/${file.name}"
            }
        }
    }

    /** Copies [file] into a document the user created through the system "Save as" picker. */
    fun copyTo(context: Context, destination: Uri, file: File) {
        val out = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IOException("Destination is unavailable")
        out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
    }

    /** Readable name for a picked folder, e.g. "Download/logs" or "1A2B-3C4D/logs" for a USB stick. */
    fun describeTree(treeUri: Uri): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return treeUri.toString()
        val volume = docId.substringBefore(':')
        val path = docId.substringAfter(':', "")
        val root = if (volume == "primary") "" else volume
        return listOf(root, path).filter { it.isNotEmpty() }.joinToString("/").ifEmpty { "/" }
    }

    private fun copyToDownloads(context: Context, file: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOADS_SUBDIR")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Downloads could not create the log file")
        try {
            copyTo(context, uri, file)
            val published = resolver.update(uri, ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }, null, null)
            if (published != 1) throw IOException("Downloads could not publish the log file")
        } catch (error: Exception) {
            // Never leave a pending, half-written entry behind.
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun copyToTree(context: Context, treeUri: Uri, file: File) {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        val target = try {
            DocumentsContract.createDocument(resolver, parent, MIME, file.name)
        } catch (e: Exception) {
            // Typically a revoked permission or an unplugged USB stick.
            throw IOException("The selected folder is no longer available", e)
        } ?: throw IOException("The selected folder refused the log file")
        try {
            copyTo(context, target, file)
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            throw error
        }
    }
}
