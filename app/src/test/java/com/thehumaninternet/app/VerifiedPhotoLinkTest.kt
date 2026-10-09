package com.thehumaninternet.app

import com.thehumaninternet.app.models.VerifiedPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Pins this app's half of contracts that outlive it: the public link matches
 * the website's `/[photoId]` route, the storage path is matched by a Storage
 * RLS policy, and the short code by a CHECK constraint. Changing any of them
 * breaks links already shared in the wild.
 */
class VerifiedPhotoLinkTest {
    private val photo = VerifiedPhoto.create(
        id = "0B5E7C1A-2D3F-4A6B-8C9D-0E1F2A3B4C5D",
        userID = "A1B2C3D4-E5F6-4A7B-8C9D-0E1F2A3B4C5D",
        capturedAt = Instant.parse("2026-10-09T12:00:00Z"),
        shortCode = "Ab3dEf9h",
    )

    @Test
    fun verificationURLIsTheShortLinkOnTheWebHost() {
        assertEquals("the-human-internet.com/Ab3dEf9h", photo.verificationLink)
        assertEquals("https://the-human-internet.com/Ab3dEf9h", photo.verificationURL)
    }

    @Test
    fun storagePathIsLowercasedUserThenPhoto() {
        assertEquals(
            "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d/0b5e7c1a-2d3f-4a6b-8c9d-0e1f2a3b4c5d.jpg",
            photo.storagePath,
        )
        assertEquals(photo.storagePath, VerifiedPhoto.originalStoragePath(photo.userID, photo.id))
    }

    @Test
    fun deepLinkUsesTheSharedSchemeAndALowercasedId() {
        assertEquals("thehumaninternet://photo/0b5e7c1a-2d3f-4a6b-8c9d-0e1f2a3b4c5d", photo.verificationDeepLink)
    }

    @Test
    fun shortCodesAreEightBase58Characters() {
        val format = Regex("^[1-9A-HJ-NP-Za-km-z]{8}$")
        repeat(500) {
            val code = VerifiedPhoto.generateShortCode()
            assertTrue("'$code' is not 8 Base58 characters", format.matches(code))
        }
    }
}
