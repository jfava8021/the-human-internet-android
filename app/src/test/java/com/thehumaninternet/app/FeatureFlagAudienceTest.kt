package com.thehumaninternet.app

import com.thehumaninternet.app.supabase.FeatureFlagAudience
import com.thehumaninternet.app.supabase.FeatureFlagKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagAudienceTest {
    @Test
    fun audiencesResolveAgainstTheUser() {
        assertTrue(FeatureFlagAudience.ALL.includes(isAdmin = false))
        assertTrue(FeatureFlagAudience.ADMIN.includes(isAdmin = true))
        assertFalse(FeatureFlagAudience.ADMIN.includes(isAdmin = false))
        assertFalse(FeatureFlagAudience.OFF.includes(isAdmin = true))
    }

    @Test
    fun rawValuesMirrorTheAudienceCheckConstraint() {
        assertEquals(listOf("all", "admin", "off"), FeatureFlagAudience.entries.map { it.rawValue })
        assertNull(FeatureFlagAudience.fromRawValue("beta"))
    }

    @Test
    fun everyFlagFallsBackToOff() {
        assertEquals(FeatureFlagAudience.OFF, FeatureFlagKey.fallbackAudience(FeatureFlagKey.SERVER_SIDE_WATERMARK))
        assertEquals(FeatureFlagAudience.OFF, FeatureFlagKey.fallbackAudience("a_flag_this_build_has_never_heard_of"))
    }
}
