package com.thehumaninternet.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thehumaninternet.app.designsystem.CtaButton
import com.thehumaninternet.app.designsystem.RemotePhotoImage
import com.thehumaninternet.app.designsystem.Theme
import com.thehumaninternet.app.logging.Log
import com.thehumaninternet.app.models.LocalAppState
import com.thehumaninternet.app.models.VerifiedPhoto
import com.thehumaninternet.app.sharing.ShareSheet
import com.thehumaninternet.app.supabase.PhotoRepository
import com.thehumaninternet.app.uploads.PhotoUploadQueue
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import com.thehumaninternet.app.designsystem.DesignTokens

/**
 * One photo, with Share and delete. The brand mark is in the photo's pixels —
 * there's no UI overlay, except the provisional mark `RemotePhotoImage` draws
 * while the real one is still being burned in.
 */
@Composable
fun PhotoDetailScreen(photo: VerifiedPhoto, onClose: () -> Unit) {
    val appState = LocalAppState.current
    val scope = rememberCoroutineScope()
    var showShareSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    fun deletePhoto() {
        isDeleting = true
        scope.launch {
            try {
                PhotoRepository.delete(photo)
                PhotoUploadQueue.cancelPendingUpload(photo.id, appState)
                // Removing it from `photos` is also what closes this screen.
                appState.photos = appState.photos.filterNot { it.id == photo.id }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                deleteError = "Please try again."
                Log.photos.error("Delete failed for ${photo.id}", error)
            } finally {
                isDeleting = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Theme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = DesignTokens.Space.S2), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Theme.foreground)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showDeleteConfirmation = true }, enabled = !isDeleting) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete photo", tint = Theme.foreground)
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = DesignTokens.Space.S6).padding(top = DesignTokens.Space.S1, bottom = DesignTokens.Space.S4),
            contentAlignment = Alignment.TopCenter,
        ) {
            RemotePhotoImage(
                photo = photo,
                fill = false,
                isProvisional = photo.id in appState.provisionalPhotoIDs,
                modifier = Modifier.clip(RoundedCornerShape(DesignTokens.Radius.Lg)),
            )
        }

        CtaButton(
            title = "Share",
            onClick = { showShareSheet = true },
            modifier = Modifier.padding(horizontal = DesignTokens.Space.S6).padding(bottom = DesignTokens.Space.S6).fillMaxWidth(),
        )
    }

    if (showShareSheet) {
        ShareSheet(photo = photo, onDismiss = { showShareSheet = false })
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete this photo?", style = Theme.type.title) },
            text = {
                Text(
                    "This permanently deletes the photo and its verification link. It'll stay in your device's gallery.",
                    style = Theme.type.body,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirmation = false; deletePhoto() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") } },
            containerColor = Theme.background,
        )
    }

    if (deleteError != null) {
        AlertDialog(
            onDismissRequest = { deleteError = null },
            confirmButton = { TextButton(onClick = { deleteError = null }) { Text("OK") } },
            title = { Text("Couldn't delete photo.", style = Theme.type.title) },
            text = { Text(deleteError ?: "", style = Theme.type.body) },
            containerColor = Theme.background,
        )
    }
}
