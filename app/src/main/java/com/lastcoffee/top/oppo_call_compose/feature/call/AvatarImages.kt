package com.lastcoffee.top.oppo_call_compose.feature.call

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/** Decode only enough pixels for the small avatar shown in the form and call screen. */
internal fun decodeAvatarFile(path: String?): Bitmap? = path?.let { raw ->
    val file = File(raw)
    if (!file.isFile) return@let null
    decodeSampledAvatar { file.inputStream() }
}

internal fun decodeAvatarUri(context: Context, uri: Uri?): Bitmap? = uri?.let { selected ->
    decodeSampledAvatar { context.contentResolver.openInputStream(selected) }
}

private inline fun decodeSampledAvatar(open: () -> java.io.InputStream?): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
    open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
}.getOrNull()
