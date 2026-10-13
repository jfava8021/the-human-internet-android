package com.thehumaninternet.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thehumaninternet.app.designsystem.CtaButton
import com.thehumaninternet.app.designsystem.RemotePhotoImage
import com.thehumaninternet.app.designsystem.Theme
import com.thehumaninternet.app.logging.Log
import com.thehumaninternet.app.models.LocalAppState
import com.thehumaninternet.app.sharing.ShareSheet
import com.thehumaninternet.app.supabase.PhotoRepository
import com.thehumaninternet.app.uploads.PhotoLibrarySaver
import com.thehumaninternet.app.uploads.PhotoUploadQueue
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import com.thehumaninternet.app.designsystem.DesignTokens

/**
 * The user's photos one at a time, swiped through sideways in the same order
 * as the grid (newest first), with Share and delete acting on whichever is
 * showing. The brand mark is in each photo's pixels — there's no UI overlay,
 * except the provisional mark `RemotePhotoImage` draws while the real one is
 * still being burned in.
 *
 * Which photo is showing is owned by the caller, as an id rather than a page
 * number: the list changes underneath this screen (a capture finishing its
 * upload, a new photo arriving at the front), and an id keeps pointing at the
 * same photo when positions shift.
 */
@Composable
fun PhotoDetailScreen(photoID: String, onPhotoChange: (String) -> Unit, onClose: () -> Unit) {
    val appState = LocalAppState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val removeGalleryCopies = rememberGalleryCopyRemover()
    val photos = appState.photos
    val index = photos.indexOfFirst { it.id == photoID }
    val photo = photos.getOrNull(index) ?: return
    val pagerState = rememberPagerState(initialPage = index) { photos.size }

    // The pager follows the id: when the list shifts under it, or a delete
    // moves on to the neighbouring photo, jump to wherever that photo is now.
    LaunchedEffect(index) {
        if (index != pagerState.currentPage && !pagerState.isScrollInProgress) pagerState.scrollToPage(index)
    }
    // And the id follows the pager, once a swipe has come to rest.
    val currentPhotos by rememberUpdatedState(photos)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            currentPhotos.getOrNull(page)?.let { onPhotoChange(it.id) }
        }
    }
    var showShareSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    fun deletePhoto(alsoFromDevice: Boolean) {
        isDeleting = true
        scope.launch {
            try {
                PhotoRepository.delete(photo)
                PhotoUploadQueue.cancelPendingUpload(photo.id, appState)
                if (alsoFromDevice) removeGalleryCopies(listOf(photo.id)) else PhotoLibrarySaver.forgetCopies(context, listOf(photo.id))
                // Move on to the photo that takes its place — the next older
                // one, or the previous if this was the last — before removing
                // it, so the screen never points at a photo that's gone.
                val remaining = appState.photos.filterNot { it.id == photo.id }
                val position = appState.photos.indexOfFirst { it.id == photo.id }
                val neighbour = remaining.getOrNull(position) ?: remaining.lastOrNull()
                if (neighbour != null) onPhotoChange(neighbour.id) else onClose()
                appState.photos = remaining
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

        HorizontalPager(
            state = pagerState,
            key = { photos[it].id },
            contentPadding = PaddingValues(horizontal = DesignTokens.Space.S6),
            pageSpacing = DesignTokens.Space.S6,
            // Keeps the photo either side decoded, so a swipe reveals a
            // picture rather than a spinner.
            beyondViewportPageCount = 1,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = DesignTokens.Space.S1, bottom = DesignTokens.Space.S4),
        ) { page ->
            val pagePhoto = photos[page]
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                RemotePhotoImage(
                    photo = pagePhoto,
                    fill = false,
                    isProvisional = pagePhoto.id in appState.provisionalPhotoIDs,
                    modifier = Modifier.clip(RoundedCornerShape(DesignTokens.Radius.Lg)),
                )
            }
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
        DeletePhotosDialog(
            photoIDs = listOf(photo.id),
            onDismiss = { showDeleteConfirmation = false },
            onDelete = { alsoFromDevice ->
                showDeleteConfirmation = false
                deletePhoto(alsoFromDevice)
            },
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
