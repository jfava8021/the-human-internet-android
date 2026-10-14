package com.thehumaninternet.app

import com.thehumaninternet.app.devmenu.DeveloperPreferences
import com.thehumaninternet.app.models.AppState
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperToolsTest {
    @After
    fun reset() {
        DeveloperPreferences.skipC2PASigning = false
    }

    @Test
    fun skippingC2PANeedsAnAdmin() {
        val appState = AppState()
        DeveloperPreferences.skipC2PASigning = true

        appState.isAdmin = false
        assertFalse(appState.isC2PASigningSkipped)

        appState.isAdmin = true
        assertTrue(appState.isC2PASigningSkipped)
    }
}
