package com.thehumaninternet.app.designsystem

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.thehumaninternet.app.logging.Log
import com.thehumaninternet.app.models.VerifiedPhoto
import com.thehumaninternet.app.supabase.PhotoRepository
import com.thehumaninternet.app.uploads.PhotoUploadQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

private const val THUMBNAIL_MAX_PIXEL_SIZE = 600

/**
 * Shows a photo: from the local pending copy if there is one, else downloaded
 * from the private `photos` bucket.
 *
 * `fill` crops to whatever size the caller's modifier gives it (grid tiles);
 * otherwise the image sizes itself to the photo's own aspect ratio.
 *
 * `isThumbnail` decodes downsampled and opts into [PhotoThumbnailCache] — a
 * lazy grid disposes and recreates its cells on scroll, so without it
 * re-scrolling re-downloads and re-decodes everything at full resolution.
 *
 * `isProvisional` marks a photo whose only copy is still the raw capture. The
 * brand mark is drawn onto the decoded frame so it looks watermarked from the
 * moment it's taken, and the image reloads when the flag clears. A
 * provisional frame is never cached: keyed by storage path alone, it would
 * outlive the real photo.
 */
@Composable
fun RemotePhotoImage(
    photo: VerifiedPhoto,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    isThumbnail: Boolean = false,
    isProvisional: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    val loadKey = "${photo.storagePath}#${if (isProvisional) "raw" else "final"}"
    var loaded by remember { mutableStateOf<Pair<String, Bitmap>?>(null) }
    var didFail by remember { mutableStateOf(false) }
    val bitmap = loaded?.takeIf { it.first == loadKey }?.second

    LaunchedEffect(loadKey) {
        if (bitmap != null) return@LaunchedEffect
        didFail = false
        val cacheable = isThumbnail && !isProvisional
        if (cacheable) {
            PhotoThumbnailCache.image(photo.storagePath)?.let {
                loaded = loadKey to it
                return@LaunchedEffect
            }
        }
        try {
            val data = PhotoUploadQueue.localImageData(photo.id) ?: PhotoRepository.downloadImage(photo.storagePath)
            val decoded = withContext(Dispatchers.Default) {
                decodeUpright(
                    data,
                    maxPixelSize = if (isThumbnail) THUMBNAIL_MAX_PIXEL_SIZE else null,
                    mutable = isProvisional,
                )?.also { if (isProvisional) PhotoWatermarker.drawMark(context, it) }
            }
            if (decoded == null) {
                didFail = true
            } else {
                loaded = loadKey to decoded
                if (cacheable) PhotoThumbnailCache.store(decoded, photo.storagePath)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            didFail = true
            Log.photos.error("Loading ${photo.storagePath} failed", error)
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
            modifier = if (fill) modifier else modifier.aspectRatio(bitmap.width.toFloat() / bitmap.height),
        )
    } else {
        Box(
            modifier = (if (fill) modifier else modifier.aspectRatio(3f / 4f)).background(Theme.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (didFail) {
                Icon(Icons.Outlined.Image, contentDescription = null, tint = Theme.mutedForeground)
            } else {
                Spinner()
            }
        }
    }
}

/** Decoded grid thumbnails, shared across every `RemotePhotoImage(isThumbnail = true)`. */
object PhotoThumbnailCache {
    // Sized in kilobytes: an eighth of the app's heap.
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun image(storagePath: String): Bitmap? = cache.get(storagePath)

    fun store(image: Bitmap, storagePath: String) {
        cache.put(storagePath, image)
    }

    /** Called on sign-out: these are the previous user's photos sitting in memory. */
    fun removeAll() = cache.evictAll()
}
