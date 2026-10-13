package com.thehumaninternet.app.uploads

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.thehumaninternet.app.logging.Log
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
 *
 * The one thing it can do to the gallery afterwards is take its own copy back
 * out, when the user deletes a photo and asks for that too ([removeCopies]).
 * That needs to know which gallery entry belongs to which photo, which is
 * remembered on this device only ([recordCopy]) — so it covers photos taken
 * on this phone since that was added, and nothing else. There is deliberately
 * no attempt to find older copies by name or date: removing the wrong picture
 * from someone's gallery is not a mistake worth risking.
 */
object PhotoLibrarySaver {
    private const val ALBUM = "The Human Internet"

    /** Saves the copy and returns its gallery entry. */
    suspend fun save(context: Context, imageData: ByteArray): Uri = withContext(Dispatchers.IO) {
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
        uri
    }

    private fun copies(context: Context) = context.getSharedPreferences("gallery_copies", Context.MODE_PRIVATE)

    /** Remembers that `uri` is the gallery copy of `photoID`. */
    fun recordCopy(context: Context, photoID: String, uri: Uri) {
        copies(context).edit().putString(photoID, uri.toString()).apply()
    }

    /** Whether this device holds a gallery copy of the photo that [removeCopies] could remove. */
    fun hasCopy(context: Context, photoID: String): Boolean = copies(context).contains(photoID)

    /** Forgets the copies of photos that no longer exist in the app, without touching the gallery. */
    fun forgetCopies(context: Context, photoIDs: Collection<String>) {
        copies(context).edit().apply { photoIDs.forEach(::remove) }.apply()
    }

    /**
     * Removes the gallery copies of these photos, where this device has one.
     * On Android 11+ they go to the gallery's trash, where the user can still
     * restore them for 30 days; Android 10 has no trash, so there they are
     * deleted outright.
     *
     * The app may do this unprompted to entries it created. After a
     * reinstall Android no longer counts the old copies as this app's, and
     * the user has to approve instead: the returned [IntentSender], if any,
     * shows that system prompt and must be launched by the caller.
     */
    suspend fun removeCopies(context: Context, photoIDs: Collection<String>): IntentSender? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val store = copies(context)
        val needingApproval = mutableListOf<Uri>()
        for (photoID in photoIDs) {
            val uri = store.getString(photoID, null)?.let(Uri::parse) ?: continue
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val trashed = ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }
                    resolver.update(uri, trashed, null, null)
                } else {
                    resolver.delete(uri, null, null)
                }
            } catch (error: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) needingApproval += uri
                else Log.photos.error("Couldn't remove the gallery copy of $photoID", error)
            } catch (error: Exception) {
                // Most likely already removed by hand; nothing left to do.
                Log.photos.error("Couldn't remove the gallery copy of $photoID", error)
            }
        }
        forgetCopies(context, photoIDs)
        if (needingApproval.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createTrashRequest(resolver, needingApproval, true).intentSender
        } else {
            null
        }
    }
}
