package com.thehumaninternet.app.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thehumaninternet.app.designsystem.CtaIcon
import com.thehumaninternet.app.designsystem.Theme
import com.thehumaninternet.app.logging.Log
import com.thehumaninternet.app.models.LocalAppState
import com.thehumaninternet.app.models.VerificationStatus
import com.thehumaninternet.app.models.VerifiedPhoto
import com.thehumaninternet.app.settings.SettingsSheet
import com.thehumaninternet.app.supabase.PhotoRepository
import com.thehumaninternet.app.uploads.PhotoUploadQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The grid of the user's photos. Tap a tile for the detail view; long-press to
 * enter multi-select and batch-delete. Selection mode has no separate flag —
 * it *is* a non-empty selection, so deselecting everything exits it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(onOpenPhoto: (VerifiedPhoto) -> Unit) {
    val appState = LocalAppState.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var showSettings by remember { mutableStateOf(false) }
    var selectedPhotoIDs by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val isSelecting = selectedPhotoIDs.isNotEmpty()

    fun toggleSelection(photo: VerifiedPhoto) {
        selectedPhotoIDs = if (photo.id in selectedPhotoIDs) selectedPhotoIDs - photo.id else selectedPhotoIDs + photo.id
    }

    fun deleteSelected() {
        val targets = appState.photos.filter { it.id in selectedPhotoIDs }
        isDeleting = true
        scope.launch {
            try {
                PhotoRepository.delete(targets)
                targets.forEach { PhotoUploadQueue.cancelPendingUpload(it.id, appState) }
                val deleted = targets.map { it.id }.toSet()
                appState.photos = appState.photos.filterNot { it.id in deleted }
                selectedPhotoIDs = emptySet()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                deleteError = "Please try again."
                Log.photos.error("Batch delete failed", error)
            } finally {
                isDeleting = false
            }
        }
    }

    BackHandler(enabled = isSelecting) { selectedPhotoIDs = emptySet() }

    Column(
        modifier = Modifier.fillMaxSize().background(Theme.background).statusBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSelecting) {
                val count = selectedPhotoIDs.size
                Text(
                    "$count photo${if (count == 1) "" else "s"} selected",
                    style = Theme.font(20, FontWeight.Bold),
                    color = Theme.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CtaIcon(Icons.Filled.Close, "Cancel selection", enabled = !isDeleting, onClick = { selectedPhotoIDs = emptySet() })
                    CtaIcon(Icons.Filled.DeleteOutline, "Delete selected", enabled = !isDeleting, onClick = { showDeleteConfirmation = true })
                }
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        appState.user.username.ifEmpty { "You" },
                        style = Theme.font(20, FontWeight.Bold),
                        color = Theme.textPrimary,
                    )
                    if (appState.user.verificationStatus == VerificationStatus.VERIFIED) {
                        Icon(Icons.Filled.Verified, contentDescription = "Verified", tint = Theme.success, modifier = Modifier.size(20.dp))
                    }
                }
                CtaIcon(Icons.Filled.Settings, "Settings", onClick = { showSettings = true })
            }
        }

        if (appState.processingPhotoIDs.isNotEmpty() || appState.failedPhotoIDs.isNotEmpty()) {
            PendingPhotosBanner()
        }

        Text("Privacy: ${appState.user.privacy.rawValue}", style = Theme.font(13), color = Theme.textSecondary)

        if (appState.photos.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Theme.textSecondary, modifier = Modifier.size(32.dp))
                Text("No photos yet. Take your first one!", style = Theme.font(14), color = Theme.textSecondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(appState.photos, key = { it.id }) { photo ->
                    PhotoThumbnail(
                        photo = photo,
                        selectionState = when {
                            !isSelecting -> SelectionState.INACTIVE
                            photo.id in selectedPhotoIDs -> SelectionState.SELECTED
                            else -> SelectionState.UNSELECTED
                        },
                        uploadState = appState.uploadState(photo.id),
                        isProvisional = photo.id in appState.provisionalPhotoIDs,
                        onRetry = { PhotoUploadQueue.retry(photo.id, appState) },
                        modifier = Modifier
                            .aspectRatio(1f)
                            .combinedClickable(
                                onClick = { if (isSelecting) toggleSelection(photo) else onOpenPhoto(photo) },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    toggleSelection(photo)
                                },
                            ),
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }

    if (showDeleteConfirmation) {
        val count = selectedPhotoIDs.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    "Are you sure you would like to delete $count photo${if (count == 1) "" else "s"}?",
                    style = Theme.font(18, FontWeight.SemiBold),
                )
            },
            text = {
                Text(
                    "This permanently deletes them and their verification links. They'll stay in your device's gallery.",
                    style = Theme.font(14),
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirmation = false; deleteSelected() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") } },
            containerColor = Theme.background,
        )
    }

    if (deleteError != null) {
        AlertDialog(
            onDismissRequest = { deleteError = null },
            confirmButton = { TextButton(onClick = { deleteError = null }) { Text("OK") } },
            title = { Text("Couldn't delete photos", style = Theme.font(18, FontWeight.SemiBold)) },
            text = { Text(deleteError ?: "", style = Theme.font(14)) },
            containerColor = Theme.background,
        )
    }
}

/**
 * Sits above the grid whenever something is still processing or has failed.
 * Tap the failed row to retry everything that failed.
 */
@Composable
private fun PendingPhotosBanner() {
    val appState = LocalAppState.current
    val shape = RoundedCornerShape(Theme.cornerRadius)

    // Belt and braces: re-kick the queue while there's anything pending.
    // Cheap to call this often — a photo that has been failing is only
    // actually retried once its backoff has elapsed.
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            appState.user.id?.let { PhotoUploadQueue.resumePendingUploads(it, appState) }
        }
    }

    fun countText(count: Int) = "$count photo${if (count == 1) "" else "s"}"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (appState.processingPhotoIDs.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().clip(shape).background(Theme.surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    "${countText(appState.processingPhotoIDs.size)} still being verified",
                    style = Theme.font(13, FontWeight.Medium),
                    color = Theme.textPrimary,
                )
            }
        }
        if (appState.failedPhotoIDs.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Theme.ctaBackground)
                    .clickable { appState.failedPhotoIDs.forEach { PhotoUploadQueue.retry(it, appState) } }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    "${countText(appState.failedPhotoIDs.size)} failed to verify or save. Tap this message to try again.",
                    style = Theme.font(13, FontWeight.Medium),
                    color = Theme.ctaForeground,
                )
            }
        }
    }
}
