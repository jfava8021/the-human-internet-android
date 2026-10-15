package com.thehumaninternet.app

import com.thehumaninternet.app.models.PrivacyLevel
import com.thehumaninternet.app.supabase.privacyColumns
import com.thehumaninternet.app.supabase.usernameColumns
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Settings saves write only their own column, never the whole row (BUG-1). */
class ProfileSaveColumnsTest {
    @Test
    fun usernameSaveWritesOnlyUsername() {
        val columns = usernameColumns("jordan")
        assertEquals(setOf("username"), columns.keys)
        assertEquals("jordan", columns.getValue("username").jsonPrimitive.content)
    }

    @Test
    fun privacySaveWritesOnlyPrivacy() {
        val columns = privacyColumns(PrivacyLevel.HUMANS_ONLY)
        assertEquals(setOf("privacy"), columns.keys)
        assertEquals("Humans Only", columns.getValue("privacy").jsonPrimitive.content)
    }
}
