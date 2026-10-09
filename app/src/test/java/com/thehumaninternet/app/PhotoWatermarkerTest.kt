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
        assertEquals(860, mark.height)
    }
}
