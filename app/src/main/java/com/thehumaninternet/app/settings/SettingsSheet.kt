package com.thehumaninternet.app.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.thehumaninternet.app.applicationScope
import com.thehumaninternet.app.designsystem.DesignTokens
import com.thehumaninternet.app.designsystem.LabelValue
import com.thehumaninternet.app.designsystem.Theme
import com.thehumaninternet.app.logging.Log
import com.thehumaninternet.app.models.LocalAppState
import com.thehumaninternet.app.models.VerificationStatus
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** The off-app destinations Settings links out to. Same URLs as iOS. */
private object ExternalLink {
    const val DISCORD = "https://discord.com/invite/SjYNad553"
    const val FEEDBACK = "https://thehumaninternet.featurebase.app/"
}

/**
 * Settings, laid out like the iOS screen: one row per setting, each opening
 * its own sheet. Account Settings, the verification-page editor and
 * Identity Verification aren't in this build yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val appState = LocalAppState.current
    val context = LocalContext.current
    var showEditUsername by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showVerificationInfo by remember { mutableStateOf(false) }
    var showLogOutConfirmation by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Settings is where verification status is read, so it refreshes on open.
    // No polling: nothing can start a verification on Android yet.
    LaunchedEffect(Unit) { appState.refreshVerificationStatus() }

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
        Column(Modifier.padding(bottom = DesignTokens.Space.S6)) {
            Text(
                "Settings",
                style = Theme.type.h3,
                color = Theme.foreground,
                modifier = Modifier.padding(horizontal = DesignTokens.Space.S6).padding(bottom = DesignTokens.Space.S2),
            )

            SettingsRow("Username", appState.user.username.ifEmpty { "—" }, Icons.Outlined.Edit) { showEditUsername = true }
            HorizontalDivider(color = Theme.border)
            SettingsRow("Privacy", appState.user.privacy.rawValue, Icons.Outlined.Edit) { showPrivacy = true }
            HorizontalDivider(color = Theme.border)
            SettingsRow("Verification status", appState.user.verificationStatus.label, Icons.Outlined.Info) {
                showVerificationInfo = true
            }
            HorizontalDivider(color = Theme.border)
            // Leaves the app on purpose: a Discord invite needs the user's own
            // signed-in Discord, in its app or the browser.
            SettingsRow("Contact us", "Join our Discord", Icons.AutoMirrored.Outlined.OpenInNew) {
                context.openLink(ExternalLink.DISCORD)
            }
            HorizontalDivider(color = Theme.border)
            SettingsRow("Feedback", "Share an idea or issue", Icons.AutoMirrored.Outlined.OpenInNew) {
                context.openLink(ExternalLink.FEEDBACK)
            }
            HorizontalDivider(color = Theme.border)

            // A plain row with no fill, on purpose — as on iOS.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = DesignTokens.Size.MinTouch)
                    .clickable(role = Role.Button) { showLogOutConfirmation = true }
                    .padding(horizontal = DesignTokens.Space.S6, vertical = DesignTokens.Space.S4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Log out", style = Theme.type.body, color = Theme.foreground, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = Theme.foreground)
            }
        }
    }

    if (showEditUsername) EditUsernameSheet(onDismiss = { showEditUsername = false })
    if (showPrivacy) PrivacyEditSheet(onDismiss = { showPrivacy = false })
    if (showVerificationInfo) {
        VerificationStatusSheet(appState.user.verificationStatus, onDismiss = { showVerificationInfo = false })
    }

    if (showLogOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogOutConfirmation = false },
            title = { Text("Log out of the human internet?", style = Theme.type.title) },
            text = {
                Text("Photos that haven't finished uploading will be removed from this device.", style = Theme.type.body)
            },
            confirmButton = {
                TextButton(onClick = { showLogOutConfirmation = false; onDismiss(); logOut() }) { Text("Log out") }
            },
            dismissButton = { TextButton(onClick = { showLogOutConfirmation = false }) { Text("Cancel") } },
            containerColor = Theme.background,
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } },
            title = { Text("Something went wrong.", style = Theme.type.title) },
            text = { Text(message, style = Theme.type.body) },
            containerColor = Theme.background,
        )
    }
}

/** iOS's settings row: a small grey label over the value, with an icon on the right. */
@Composable
private fun SettingsRow(title: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DesignTokens.Size.MinTouch)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = DesignTokens.Space.S6, vertical = DesignTokens.Space.S4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelValue(title, value, Modifier.weight(1f))
        Icon(icon, contentDescription = null, tint = Theme.mutedForeground)
    }
}

/** What Settings shows for each status. Same wording as iOS. */
private val VerificationStatus.label: String
    get() = when (this) {
        VerificationStatus.UNVERIFIED -> "Not verified"
        VerificationStatus.IN_PROGRESS -> "In progress"
        VerificationStatus.VERIFIED -> "Verified"
        VerificationStatus.FAILED -> "Failed"
    }

private fun Context.openLink(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
