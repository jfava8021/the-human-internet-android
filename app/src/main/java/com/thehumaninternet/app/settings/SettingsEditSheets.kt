package com.thehumaninternet.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thehumaninternet.app.designsystem.CtaButton
import com.thehumaninternet.app.designsystem.DesignTokens
import com.thehumaninternet.app.designsystem.HITextField
import com.thehumaninternet.app.designsystem.RadioRow
import com.thehumaninternet.app.designsystem.Theme
import com.thehumaninternet.app.logging.Log
import com.thehumaninternet.app.models.LocalAppState
import com.thehumaninternet.app.models.PrivacyLevel
import com.thehumaninternet.app.models.VerificationStatus
import com.thehumaninternet.app.supabase.UserProfileRepository
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun EditUsernameSheet(onDismiss: () -> Unit) {
    val appState = LocalAppState.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf(appState.user.username) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun save() {
        val userID = appState.user.id ?: return
        errorMessage = null
        isSaving = true
        scope.launch {
            try {
                UserProfileRepository.saveUsername(userID, username)
                appState.user = appState.user.copy(username = username)
                onDismiss()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage =
                    if (UserProfileRepository.isUsernameConflict(error)) "That username is already taken."
                    else "Couldn't save — please try again."
                Log.settings.error("Username update failed", error)
            } finally {
                isSaving = false
            }
        }
    }

    EditSheet(title = "Edit username", onDismiss = onDismiss) {
        HITextField(placeholder = "New username here", text = username, onTextChange = { username = it }, error = errorMessage)
        CtaButton(
            title = if (isSaving) "Saving…" else "Save",
            onClick = ::save,
            enabled = !isSaving && username.isNotBlank(),
        )
    }
}

@Composable
fun PrivacyEditSheet(onDismiss: () -> Unit) {
    val appState = LocalAppState.current
    val scope = rememberCoroutineScope()
    var selection by remember { mutableStateOf(appState.user.privacy) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun save() {
        val userID = appState.user.id ?: return
        errorMessage = null
        isSaving = true
        scope.launch {
            try {
                UserProfileRepository.savePrivacy(userID, selection)
                appState.user = appState.user.copy(privacy = selection)
                onDismiss()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = "Couldn't save — please try again."
                Log.settings.error("Privacy update failed", error)
            } finally {
                isSaving = false
            }
        }
    }

    EditSheet(title = "Privacy", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S1)) {
            for (level in PrivacyLevel.entries) {
                RadioRow(title = level.rawValue, isSelected = selection == level, onClick = { selection = level })
            }
        }
        errorMessage?.let { Text(it, style = Theme.type.caption, color = Theme.destructive) }
        CtaButton(title = if (isSaving) "Saving…" else "Save", onClick = ::save, enabled = !isSaving)
    }
}

/** Explains the user's current verification status. Same wording as iOS. */
@Composable
fun VerificationStatusSheet(status: VerificationStatus, onDismiss: () -> Unit) {
    EditSheet(title = "Verification status", onDismiss = onDismiss) {
        for (paragraph in status.explanation) {
            Text(paragraph, style = Theme.type.body, color = Theme.mutedForeground)
        }
    }
}

private val VerificationStatus.explanation: List<String>
    get() = when (this) {
        VerificationStatus.UNVERIFIED -> listOf(
            "Identity verification is 100% optional. You can capture and share verified photos without it.",
            "If you would ever like to use the human internet to make claims about the ownership of your content, you will need to verify your identity. You can start that any time from Settings.",
        )
        VerificationStatus.IN_PROGRESS -> listOf(
            "Your identity verification has been submitted and is being reviewed.",
            "While it is in progress, content you share will clearly state that you are an unverified human.",
        )
        VerificationStatus.VERIFIED -> listOf(
            "Your identity is verified. Content you share is attributed to you as a verified human.",
        )
        VerificationStatus.FAILED -> listOf(
            "Your identity verification didn't go through.",
            "You can keep using the human internet — verification is optional — but you'll need a successful verification to make claims about the ownership of your content.",
        )
    }

/** The shared shape of Settings' sheets: a title, then the content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Theme.background) {
        Column(
            modifier = Modifier.padding(horizontal = DesignTokens.Space.S6).padding(bottom = DesignTokens.Space.S6),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S4),
        ) {
            Text(title, style = Theme.type.h3, color = Theme.foreground)
            content()
        }
    }
}
