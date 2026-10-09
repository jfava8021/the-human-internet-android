package com.thehumaninternet.app.designsystem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.exifinterface.media.ExifInterface
import com.thehumaninternet.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

sealed class PhotoWatermarkerException(message: String) : Exception(message) {
    class InvalidImage : PhotoWatermarkerException("The capture couldn't be decoded")
    class MarkMissing : PhotoWatermarkerException("The brand mark artwork is missing from the app")
    class EncodeFailed : PhotoWatermarkerException("The watermarked photo couldn't be encoded")
}

/**
 * Burns the brand mark into a captured JPEG's actual pixels, so the mark
 * travels with the photo wherever it's uploaded, shared, or downloaded.
 *
 * **Not the main path.** With `server_side_watermark` on, the signing Lambda
 * burns the mark in so the capture can be signed first and kept as the
 * watermarked photo's C2PA ingredient. This stays for the flag-off path.
 *
 * **The mark is an image** — `res/drawable-nodpi/brand_mark_watermark.png`,
 * byte-identical to the iOS app's `BrandMarkWatermark` image set and the
 * Lambda's `assets/brand-mark.png`. Every rendering (this, the Lambda's, the
 * iOS app's, and the provisional mark `RemotePhotoImage` draws) places that
 * artwork at [markRect], so they agree on where it sits. To change the
 * artwork, replace the PNG in all three repos — nothing else.
 */
object PhotoWatermarker {
    /**
     * Mark width, and its inset from the top and trailing edges, as fractions
     * of the image's shorter side. Mirrored by the iOS `PhotoWatermarker` and
     * the Lambda's `MARK_SIZE_FRACTION` / `MARK_PADDING_FRACTION`.
     */
    const val MARK_SIZE_FRACTION = 0.09f
    const val MARK_PADDING_FRACTION = 0.035f

    data class MarkRect(val left: Float, val top: Float, val width: Float, val height: Float)

    /**
     * Where the mark goes on an image of the given size, for artwork whose
     * height/width is `markAspectRatio`: top-trailing, inset.
     */
    fun markRect(imageWidth: Float, imageHeight: Float, markAspectRatio: Float): MarkRect {
        val shorter = minOf(imageWidth, imageHeight)
        val width = shorter * MARK_SIZE_FRACTION
        val padding = shorter * MARK_PADDING_FRACTION
        return MarkRect(left = imageWidth - width - padding, top = padding, width = width, height = width * markAspectRatio)
    }

    /** Draws the mark onto `bitmap` in place, at [markRect]. */
    fun drawMark(context: Context, bitmap: Bitmap) {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val mark = BitmapFactory.decodeResource(context.resources, R.drawable.brand_mark_watermark, options)
            ?.takeIf { it.width > 0 }
            ?: throw PhotoWatermarkerException.MarkMissing()
        val rect = markRect(bitmap.width.toFloat(), bitmap.height.toFloat(), mark.height.toFloat() / mark.width)
        Canvas(bitmap).drawBitmap(
            mark,
            null,
            RectF(rect.left, rect.top, rect.left + rect.width, rect.top + rect.height),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        mark.recycle()
    }

    /**
     * Composites off the main thread — real work at full photo resolution.
     * The output has its EXIF orientation applied to the pixels and carries no
     * metadata, the same as the iOS app's and the Lambda's.
     */
    suspend fun watermark(context: Context, imageData: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val bitmap = decodeUpright(imageData, mutable = true) ?: throw PhotoWatermarkerException.InvalidImage()
        try {
            drawMark(context, bitmap)
            val output = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                throw PhotoWatermarkerException.EncodeFailed()
            }
            output.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}

/**
 * Decodes a JPEG the way a viewer shows it. `BitmapFactory` ignores EXIF
 * orientation, and a camera capture is usually stored sideways with a tag
 * saying how to turn it, so the rotation is applied here.
 *
 * `maxPixelSize` caps the longer side by subsampling at decode time, so a
 * thumbnail never holds the full-resolution bitmap in memory.
 */
fun decodeUpright(data: ByteArray, maxPixelSize: Int? = null, mutable: Boolean = false): Bitmap? {
    val options = BitmapFactory.Options()
    if (maxPixelSize != null) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxPixelSize) {
            sampleSize *= 2
        }
        options.inSampleSize = sampleSize
    }
    options.inMutable = mutable
    val decoded = BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: return null

    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(data))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        else -> return decoded
    }
    val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (upright !== decoded) decoded.recycle()
    // `createBitmap` with a matrix returns an immutable bitmap.
    return if (mutable && !upright.isMutable) {
        upright.copy(Bitmap.Config.ARGB_8888, true).also { upright.recycle() }
    } else {
        upright
    }
}
