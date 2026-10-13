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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Verified
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
import androidx.compose.ui.platform.LocalContext
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
import com.thehumaninternet.app.uploads.PhotoLibrarySaver
import com.thehumaninternet.app.uploads.PhotoUploadQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import com.thehumaninternet.app.designsystem.DesignTokens
import com.thehumaninternet.app.designsystem.VerifiedBadge
import com.thehumaninternet.app.designsystem.LabelValue
import com.thehumaninternet.app.designsystem.HumanCard
import androidx.compose.ui.res.painterResource
import com.thehumaninternet.app.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues

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
    val context = LocalContext.current.applicationContext
    val removeGalleryCopies = rememberGalleryCopyRemover()
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

    fun deleteSelected(alsoFromDevice: Boolean) {
        val targets = appState.photos.filter { it.id in selectedPhotoIDs }
        isDeleting = true
        scope.launch {
            try {
                PhotoRepository.delete(targets)
                targets.forEach { PhotoUploadQueue.cancelPendingUpload(it.id, appState) }
                val deleted = targets.map { it.id }.toSet()
                if (alsoFromDevice) removeGalleryCopies(deleted) else PhotoLibrarySaver.forgetCopies(context, deleted)
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
        modifier = Modifier.fillMaxSize().background(Theme.background).statusBarsPadding().padding(DesignTokens.Space.S6),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSelecting) {
                val count = selectedPhotoIDs.size
                Text(
                    "$count photo${if (count == 1) "" else "s"} selected",
                    style = Theme.type.title,
                    color = Theme.foreground,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space.S3)) {
                    CtaIcon(Icons.Outlined.Close, "Cancel selection", enabled = !isDeleting, onClick = { selectedPhotoIDs = emptySet() })
                    CtaIcon(Icons.Outlined.DeleteOutline, "Delete selected", enabled = !isDeleting, onClick = { showDeleteConfirmation = true })
                }
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space.S2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        appState.user.username.ifEmpty { "You" },
                        style = Theme.type.h3,
                        color = Theme.foreground,
                    )
                    // "Verified" is always the spiral, never a check or seal.
                    if (appState.user.verificationStatus == VerificationStatus.VERIFIED) {
                        VerifiedBadge(painterResource(R.drawable.ic_brand_mark))
                    }
                }
                CtaIcon(Icons.Outlined.Settings, "Settings", onClick = { showSettings = true })
            }
        }

        if (appState.processingPhotoIDs.isNotEmpty() || appState.failedPhotoIDs.isNotEmpty()) {
            PendingPhotosBanner()
        }

        Text("Privacy: ${appState.user.privacy.rawValue}", style = Theme.type.label, color = Theme.mutedForeground)

        if (appState.photos.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S4, Alignment.CenterVertically),
            ) {
                // Empty state: one doodle, two beats.
                Image(
                    painter = painterResource(R.drawable.doodle_hand),
                    contentDescription = null,
                    modifier = Modifier.size(DesignTokens.Size.Thumbnail),
                )
                LabelValue("No photos yet.", "Take your first one.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space.S1),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S1),
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
        DeletePhotosDialog(
            photoIDs = selectedPhotoIDs,
            onDismiss = { showDeleteConfirmation = false },
            onDelete = { alsoFromDevice ->
                showDeleteConfirmation = false
                deleteSelected(alsoFromDevice)
            },
        )
    }

    if (deleteError != null) {
        AlertDialog(
            onDismissRequest = { deleteError = null },
            confirmButton = { TextButton(onClick = { deleteError = null }) { Text("OK") } },
            title = { Text("Couldn't delete photos.", style = Theme.type.title) },
            text = { Text(deleteError ?: "", style = Theme.type.body) },
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

    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S2)) {
        if (appState.processingPhotoIDs.isNotEmpty()) {
            HumanCard(Modifier.fillMaxWidth(), radius = DesignTokens.Radius.Md, padding = PaddingValues(DesignTokens.Space.S3)) {
                LabelValue("Verifying", "${countText(appState.processingPhotoIDs.size)} still being verified.")
            }
        }
        if (appState.failedPhotoIDs.isNotEmpty()) {
            HumanCard(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DesignTokens.Radius.Md))
                    .clickable(onClickLabel = "Retry") { appState.failedPhotoIDs.forEach { PhotoUploadQueue.retry(it, appState) } },
                radius = DesignTokens.Radius.Md,
                padding = PaddingValues(DesignTokens.Space.S3),
            ) {
                LabelValue("Not saved yet", "${countText(appState.failedPhotoIDs.size)} failed to verify or save. Tap to try again.")
            }
        }
    }
}
