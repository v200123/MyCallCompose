package com.lastcoffee.top.oppo_call_compose.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object CallMediaFiles {
    suspend fun importDocument(context: Context, uri: Uri, directory: String): ImportedFile? = withContext(Dispatchers.IO) {
        runCatching {
            require(directory.matches(Regex("[a-z0-9_]+"))) { "Invalid media directory" }
            val resolver = context.contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "media"
            val extension = name.substringAfterLast('.', "").lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
            val reportedMime = resolver.getType(uri)
            val mime = reportedMime?.takeUnless { it == "application/octet-stream" }
                ?: extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
                ?: "application/octet-stream"
            val folder = File(context.filesDir, "call_media/$directory")
            check(folder.isDirectory || folder.mkdirs()) { "Could not create media directory" }
            val id = UUID.randomUUID().toString()
            val destination = File(folder, "$id${extension?.let { ".$it" }.orEmpty()}")
            val temporary = File(folder, "$id.part")
            try {
                val input = resolver.openInputStream(uri) ?: return@runCatching null
                input.use { source ->
                    temporary.outputStream().use { output ->
                        check(source.copyTo(output, 64 * 1024) > 0) { "Media file is empty" }
                        output.fd.sync()
                    }
                }
                check(temporary.renameTo(destination)) { "Could not finish media import" }
                ImportedFile(destination.absolutePath, name.substringBeforeLast('.', name), mime)
            } finally {
                temporary.delete()
            }
        }.getOrNull()
    }

    suspend fun delete(context: Context, path: String?): Unit = withContext(Dispatchers.IO) {
        path?.let { raw ->
            runCatching {
                val root = File(context.filesDir, "call_media").canonicalFile.toPath()
                val target = File(raw).canonicalFile
                if (target.isFile && target.toPath().startsWith(root)) target.delete()
            }
        }
        Unit
    }
}

data class ImportedFile(val path: String, val title: String, val mimeType: String)
