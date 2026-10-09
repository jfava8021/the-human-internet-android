package com.thehumaninternet.app

import com.thehumaninternet.app.verification.PlayIntegrityService
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `sign-photo` recomputes this hash from the bytes it receives and refuses a
 * token bound to anything else, so the app and the backend must encode it
 * identically. The backend's `playIntegrity_test.ts` pins the same vector.
 */
class PlayIntegrityRequestHashTest {
    @Test
    fun requestHashIsSha256AsUnpaddedBase64Url() {
        // SHA-256("abc"), the FIPS 180 test vector. In standard base64 it
        // contains '+' and '/' and ends in '='.
        assertEquals(
            "ungWv48Bz-pBQUDeXa4iI7ADYaOWF3qctBD_YfIAFa0",
            PlayIntegrityService.requestHash("abc".toByteArray()),
        )
    }
}
