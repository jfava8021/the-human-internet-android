package com.thehumaninternet.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thehumaninternet.app.designsystem.CtaIcon
import com.thehumaninternet.app.designsystem.RemotePhotoImage
import com.thehumaninternet.app.designsystem.Spinner
import com.thehumaninternet.app.models.PhotoUploadState
import com.thehumaninternet.app.models.VerifiedPhoto
import com.thehumaninternet.app.designsystem.DesignTokens
import com.thehumaninternet.app.designsystem.IconSurface
import com.thehumaninternet.app.designsystem.Theme

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
            Box(Modifier.fillMaxSize().background(Theme.foreground.copy(alpha = 0.25f)))
        }

        when (selectionState) {
            SelectionState.INACTIVE -> Unit
            SelectionState.UNSELECTED -> Icon(
                Icons.Outlined.RadioButtonUnchecked,
                contentDescription = "Not selected",
                tint = Theme.background,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(DesignTokens.Space.S2)
                    .size(DesignTokens.Size.Icon)
                    .background(Theme.foreground.copy(alpha = 0.35f), CircleShape),
            )
            SelectionState.SELECTED -> Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = "Selected",
                tint = Theme.background,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(DesignTokens.Space.S2)
                    .size(DesignTokens.Size.Icon)
                    .background(Theme.foreground, CircleShape),
            )
        }

        when (uploadState) {
            PhotoUploadState.IDLE -> Unit
            PhotoUploadState.PROCESSING -> Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(DesignTokens.Space.S2)
                    .size(DesignTokens.Space.S8)
                    .background(Theme.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Spinner(diameter = DesignTokens.Space.S4)
            }
            PhotoUploadState.FAILED -> CtaIcon(
                icon = Icons.Outlined.Refresh,
                contentDescription = "Retry upload",
                surface = IconSurface.Media,
                onClick = onRetry,
                modifier = Modifier.align(Alignment.BottomEnd).padding(DesignTokens.Space.S1),
            )
        }
    }
}
