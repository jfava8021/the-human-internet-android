package com.thehumaninternet.app.uploads

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves a copy of each capture to the device's own photo gallery, so users
 * keep their photos if they ever lose access to their account.
 *
 * That copy is deliberately the **raw capture — no watermark, no C2PA
 * manifest**. It carries no provenance claim and is not a verifiable
 * fallback; only the hosted copy is.
 *
 * Add-only by construction: inserting into the shared Pictures collection
 * needs no storage permission on Android 10+, and this app never reads the
 * gallery — importing photos would defeat the "taken live" guarantee.
 */
object PhotoLibrarySaver {
    private const val ALBUM = "The Human Internet"

    suspend fun save(context: Context, imageData: ByteArray) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Couldn't create a gallery entry")
        try {
            resolver.openOutputStream(uri)?.use { it.write(imageData) }
                ?: throw IOException("Couldn't open the gallery entry for writing")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
