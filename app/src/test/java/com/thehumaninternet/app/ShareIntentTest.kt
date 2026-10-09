package com.thehumaninternet.app

import com.thehumaninternet.app.models.VerifiedPhoto
import com.thehumaninternet.app.sharing.ShareIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Pins every share URL shape, including the strict percent-encoding of the nested link. */
class ShareIntentTest {
    private val photo = VerifiedPhoto.create("p", "u", Instant.EPOCH, "Ab3dEf9h")
    private val encodedLink = "https%3A%2F%2Fthe-human-internet.com%2FAb3dEf9h"
    private val encodedCaption = "%28Add%20your%20caption%20here.%29%20Check%20this%20photo%20is%20real%20at%20$encodedLink"

    @Test
    fun redditGetsTheLinkAndNothingElse() {
        assertEquals("https://www.reddit.com/submit?url=$encodedLink", ShareIntent.REDDIT.composerURL(photo))
        assertNull(ShareIntent.REDDIT.appURL(photo))
    }

    @Test
    fun xGetsTheCaptionWithTheLinkEmbeddedAndNoSeparateURLParameter() {
        assertEquals("https://x.com/intent/post?text=$encodedCaption", ShareIntent.X.composerURL(photo))
        assertEquals("twitter://post?message=$encodedCaption", ShareIntent.X.appURL(photo))
    }
}
