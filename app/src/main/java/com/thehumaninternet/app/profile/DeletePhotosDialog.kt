package com.thehumaninternet.app.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.thehumaninternet.app.designsystem.Theme
import com.thehumaninternet.app.uploads.PhotoLibrarySaver

/**
 * Confirms deleting one or more photos, and — when this device holds a
 * gallery copy of any of them — offers to remove those copies too.
 *
 * Keeping the gallery copy is the default and the first choice: the copy is
 * the user's own backup, and taking it away is the less expected outcome.
 * `onDelete` is told which the user picked; the deletion itself is the
 * caller's, which then calls [removeGalleryCopies] if asked.
 */
@Composable
fun DeletePhotosDialog(
    photoIDs: Collection<String>,
    onDismiss: () -> Unit,
    onDelete: (alsoFromDevice: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val count = photoIDs.size
    val copies = remember(photoIDs) { photoIDs.count { PhotoLibrarySaver.hasCopy(context, it) } }
    val single = count == 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (single) "Delete this photo?" else "Are you sure you would like to delete $count photos?",
                style = Theme.type.title,
            )
        },
        text = {
            val whatGoes =
                if (single) "This permanently deletes the photo and its verification link."
                else "This permanently deletes them and their verification links."
            val onDevice = when {
                copies == 0 && single -> "It'll stay in your device's gallery."
                copies == 0 -> "They'll stay in your device's gallery."
                single -> "A copy is also saved in this device's gallery."
                copies == count -> "Copies are also saved in this device's gallery."
                else -> "$copies of them also have a copy saved in this device's gallery."
            }
            Text("$whatGoes $onDevice", style = Theme.type.body)
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (copies == 0) {
                    TextButton(onClick = { onDelete(false) }) { Text("Delete") }
                } else {
                    TextButton(onClick = { onDelete(false) }) { Text("Delete, keep on this device") }
                    TextButton(onClick = { onDelete(true) }) { Text("Delete from this device too") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        containerColor = Theme.background,
    )
}

/**
 * Returns a function that removes the gallery copies of the given photos,
 * showing Android's own approval prompt in the one case that needs it (see
 * `PhotoLibrarySaver.removeCopies`). Call it only after the photos have been
 * deleted from the app.
 */
@Composable
fun rememberGalleryCopyRemover(): suspend (Collection<String>) -> Unit {
    val context = LocalContext.current.applicationContext
    val approval = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    return remember(context, approval) {
        { photoIDs ->
            PhotoLibrarySaver.removeCopies(context, photoIDs)?.let {
                approval.launch(IntentSenderRequest.Builder(it).build())
            }
        }
    }
}
