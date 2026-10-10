package com.thehumaninternet.app

import com.thehumaninternet.app.designsystem.PhotoWatermarker
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * The mark's placement and artwork are shared with the iOS app and the
 * signing Lambda; a provisional mark that doesn't sit exactly where the real
 * one lands is visible as a jump when the photo finishes processing.
 */
class PhotoWatermarkerTest {
    @Test
    fun theMarkSitsTopTrailingSizedOffTheShorterSide() {
        // A 3000×4000 portrait capture, with artwork 0.5 as tall as it is wide.
        val rect = PhotoWatermarker.markRect(3000f, 4000f, markAspectRatio = 0.5f)
        assertEquals(270f, rect.width, 0.01f)        // 9% of 3000
        assertEquals(135f, rect.height, 0.01f)
        assertEquals(105f, rect.top, 0.01f)          // 3.5% of 3000
        assertEquals(3000f - 270f - 105f, rect.left, 0.01f)

        // Landscape measures off the shorter side too.
        val landscape = PhotoWatermarker.markRect(4000f, 3000f, markAspectRatio = 0.5f)
        assertEquals(270f, landscape.width, 0.01f)
        assertEquals(4000f - 270f - 105f, landscape.left, 0.01f)
    }

    @Test
    fun theArtworkIsTheSharedBrandMark() {
        // Byte-identical to the iOS image set and the Lambda's assets/brand-mark.png.
        val mark = ImageIO.read(File("src/main/res/drawable-nodpi/brand_mark_watermark.png"))
        assertEquals(1024, mark.width)
        assertEquals(1082, mark.height)
    }

    /** Mirrors the Lambda's `mark_color`: grid size, threshold, and luma (not a plain average). */
    @Test
    fun theMarkIsBlackOnLightAndWhiteOnDark() {
        assertEquals(32, PhotoWatermarker.COLOR_SAMPLE_GRID)
        assertEquals(0.5f, PhotoWatermarker.LIGHT_BACKGROUND_LUMA, 0f)
        val rect = PhotoWatermarker.MarkRect(0f, 0f, 64f, 64f)
        fun solid(argb: Int) = PhotoWatermarker.markColor(rect, 64, 64) { _, _ -> argb }
        assertEquals(PhotoWatermarker.MarkColor.WHITE, solid(0xFF7F7F7F.toInt()))   // just under 0.5
        assertEquals(PhotoWatermarker.MarkColor.BLACK, solid(0xFF808080.toInt()))   // just over
        assertEquals(PhotoWatermarker.MarkColor.BLACK, solid(0xFF00FF00.toInt()))   // green reads light
        assertEquals(PhotoWatermarker.MarkColor.WHITE, solid(0xFF0000FF.toInt()))   // blue reads dark
    }

    /** Only the box under the mark decides: a light patch there on a dark photo gets a black mark. */
    @Test
    fun theColourComesFromTheAreaUnderTheMark() {
        val rect = PhotoWatermarker.markRect(1000f, 800f, markAspectRatio = 1082f / 1024f)
        val color = PhotoWatermarker.markColor(rect, 1000, 800) { x, y ->
            val inside = x >= rect.left && x < rect.left + rect.width && y >= rect.top && y < rect.top + rect.height
            if (inside) 0xFFF0F0F0.toInt() else 0xFF141414.toInt()
        }
        assertEquals(PhotoWatermarker.MarkColor.BLACK, color)
    }
}
