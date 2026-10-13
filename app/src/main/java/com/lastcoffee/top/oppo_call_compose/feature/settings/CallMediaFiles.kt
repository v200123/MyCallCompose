package com.lastcoffee.top.oppo_call_compose.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object CallMediaFiles {
    suspend fun importDocument(context: Context, uri: Uri, directory: String): ImportedFile? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "media"
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val folder = File(context.filesDir, "call_media/$directory").apply { mkdirs() }
            val extension = name.substringAfterLast('.', "").takeIf { it.length in 1..8 }?.let { ".$it" } ?: ""
            val destination = File(folder, "${UUID.randomUUID()}$extension")
            resolver.openInputStream(uri)?.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
                ?: return@runCatching null
            ImportedFile(destination.absolutePath, name.substringBeforeLast('.', name), mime)
        }.getOrNull()
    }

    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        path?.let(::File)?.delete()
    }
}

data class ImportedFile(val path: String, val title: String, val mimeType: String)
