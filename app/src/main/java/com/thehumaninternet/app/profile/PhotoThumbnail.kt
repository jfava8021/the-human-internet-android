package com.thehumaninternet.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thehumaninternet.app.designsystem.CTARole
import com.thehumaninternet.app.designsystem.CtaIcon
import com.thehumaninternet.app.designsystem.RemotePhotoImage
import com.thehumaninternet.app.designsystem.Spinner
import com.thehumaninternet.app.models.PhotoUploadState
import com.thehumaninternet.app.models.VerifiedPhoto

enum class SelectionState { INACTIVE, UNSELECTED, SELECTED }

/**
 * A square photo tile with its selection and upload status drawn over it.
 * The overlays keep their dark backing discs, since they sit on pixels rather
 * than the page.
 */
@Composable
fun PhotoThumbnail(
    photo: VerifiedPhoto,
    modifier: Modifier = Modifier,
    selectionState: SelectionState = SelectionState.INACTIVE,
    uploadState: PhotoUploadState = PhotoUploadState.IDLE,
    isProvisional: Boolean = false,
    onRetry: () -> Unit = {},
) {
    Box(modifier) {
        RemotePhotoImage(photo, Modifier.fillMaxSize(), isThumbnail = true, isProvisional = isProvisional)

        if (selectionState == SelectionState.SELECTED) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        }

        when (selectionState) {
            SelectionState.INACTIVE -> Unit
            SelectionState.UNSELECTED -> Icon(
                Icons.Filled.RadioButtonUnchecked,
                contentDescription = "Not selected",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape),
            )
            SelectionState.SELECTED -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .background(Color.Black, CircleShape),
            )
        }

        when (uploadState) {
            PhotoUploadState.IDLE -> Unit
            PhotoUploadState.PROCESSING -> Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Spinner(color = Color.White, diameter = 14.dp)
            }
            PhotoUploadState.FAILED -> CtaIcon(
                icon = Icons.Filled.Refresh,
                contentDescription = "Retry upload",
                role = CTARole.TERTIARY,
                diameter = 24.dp,
                onClick = onRetry,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            )
        }
    }
}
