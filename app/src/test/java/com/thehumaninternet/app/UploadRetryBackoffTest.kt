package com.thehumaninternet.app

import com.thehumaninternet.app.uploads.PhotoUploadQueue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/**
 * Each upload attempt spends a Play Integrity token against a daily quota
 * shared by every user, so how often a stuck photo retries is a limit worth
 * pinning.
 */
class UploadRetryBackoffTest {
    @Test
    fun aPhotoThatHasNeverFailedIsAttemptedAtOnce() {
        assertEquals(Duration.ZERO, PhotoUploadQueue.retryDelay(0))
    }

    @Test
    fun waitsGrowWithEachConsecutiveFailure() {
        assertEquals(Duration.ofSeconds(10), PhotoUploadQueue.retryDelay(1))
        assertEquals(Duration.ofMinutes(1), PhotoUploadQueue.retryDelay(2))
        assertEquals(Duration.ofMinutes(5), PhotoUploadQueue.retryDelay(3))
        assertEquals(Duration.ofMinutes(30), PhotoUploadQueue.retryDelay(4))
        assertEquals(Duration.ofHours(6), PhotoUploadQueue.retryDelay(5))
    }

    @Test
    fun aStuckPhotoSettlesAtAFewAttemptsADay() {
        assertEquals(Duration.ofHours(6), PhotoUploadQueue.retryDelay(6))
        assertEquals(Duration.ofHours(6), PhotoUploadQueue.retryDelay(500))
    }
}
