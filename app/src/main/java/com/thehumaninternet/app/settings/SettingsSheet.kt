package com.thehumaninternet.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thehumaninternet.app.applicationScope
import com.thehumaninternet.app.designsystem.FieldLabel
import com.thehumaninternet.app.designsystem.RadioRow
import com.thehumaninternet.app.designsystem.Theme
import com.thehumaninternet.app.logging.Log
import com.thehumaninternet.app.models.LocalAppState
import com.thehumaninternet.app.models.PrivacyLevel
import com.thehumaninternet.app.supabase.UserProfileRepository
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The minimum a first build needs: who you're signed in as, the privacy
 * setting the first-photo sheet tells people about, and Log Out. The rest of
 * the iOS Settings screen (username editing, verification, account settings,
 * account deletion) isn't ported yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val appState = LocalAppState.current
    var showLogOutConfirmation by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    /** Optimistic, reverted if the save fails. */
    fun setPrivacy(privacy: PrivacyLevel) {
        val previous = appState.user.privacy
        if (privacy == previous) return
        appState.user = appState.user.copy(privacy = privacy)
        applicationScope.launch {
            try {
                UserProfileRepository.upsert(appState.user)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                appState.user = appState.user.copy(privacy = previous)
                errorMessage = "Couldn't save — please try again."
                Log.settings.error("Saving privacy failed", error)
            }
        }
    }

    fun logOut() {
        applicationScope.launch {
            try {
                appState.signOut()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = "Couldn't log out — please try again."
                Log.auth.error("Sign-out failed", error)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Theme.background) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Settings", style = Theme.font(20, FontWeight.Bold), color = Theme.textPrimary)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("Username")
                Text(appState.user.username.ifEmpty { "You" }, style = Theme.font(16), color = Theme.textPrimary)
            }

            HorizontalDivider(color = Theme.divider)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("Privacy")
                RadioRow(
                    title = PrivacyLevel.PUBLIC.rawValue,
                    subtitle = "Anyone with the link can see your photos.",
                    isSelected = appState.user.privacy == PrivacyLevel.PUBLIC,
                    onClick = { setPrivacy(PrivacyLevel.PUBLIC) },
                )
                RadioRow(
                    title = PrivacyLevel.HUMANS_ONLY.rawValue,
                    subtitle = "Only people signed in to the human internet can see your photos.",
                    isSelected = appState.user.privacy == PrivacyLevel.HUMANS_ONLY,
                    onClick = { setPrivacy(PrivacyLevel.HUMANS_ONLY) },
                )
            }

            HorizontalDivider(color = Theme.divider)

            // A plain row with no fill, on purpose — as on iOS.
            Text(
                "Log Out",
                style = Theme.font(16, FontWeight.Medium),
                color = Theme.ctaForeground,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { showLogOutConfirmation = true }
                    .padding(vertical = 10.dp),
            )
        }
    }

    if (showLogOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogOutConfirmation = false },
            title = { Text("Log out?", style = Theme.font(18, FontWeight.SemiBold)) },
            text = {
                Text(
                    "Photos that haven't finished uploading will be removed from this device.",
                    style = Theme.font(14),
                )
            },
            confirmButton = {
                TextButton(onClick = { showLogOutConfirmation = false; onDismiss(); logOut() }) { Text("Log Out") }
            },
            dismissButton = { TextButton(onClick = { showLogOutConfirmation = false }) { Text("Cancel") } },
            containerColor = Theme.background,
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } },
            title = { Text("Something went wrong", style = Theme.font(18, FontWeight.SemiBold)) },
            text = { Text(errorMessage ?: "", style = Theme.font(14)) },
            containerColor = Theme.background,
        )
    }
}
