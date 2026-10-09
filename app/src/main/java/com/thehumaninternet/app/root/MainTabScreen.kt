package com.thehumaninternet.app.root

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.thehumaninternet.app.camera.CameraCaptureScreen
import com.thehumaninternet.app.designsystem.Theme
import com.thehumaninternet.app.models.LocalAppState
import com.thehumaninternet.app.profile.PhotoDetailScreen
import com.thehumaninternet.app.profile.ProfileScreen

private enum class Tab { CAMERA, PROFILE }

/**
 * The two tabs, each with its own one-level stack: a tab shows either its root
 * or the detail of one photo opened from it.
 */
@Composable
fun MainTabScreen() {
    val appState = LocalAppState.current
    var tab by rememberSaveable { mutableStateOf(Tab.CAMERA) }
    // Held by id, so a photo deleted from under an open detail screen closes it.
    var cameraDetailID by rememberSaveable { mutableStateOf<String?>(null) }
    var profileDetailID by rememberSaveable { mutableStateOf<String?>(null) }

    val detailID = if (tab == Tab.CAMERA) cameraDetailID else profileDetailID
    val detailPhoto = appState.photos.firstOrNull { it.id == detailID }
    val closeDetail = { if (tab == Tab.CAMERA) cameraDetailID = null else profileDetailID = null }

    BackHandler(enabled = detailPhoto != null, onBack = closeDetail)

    Scaffold(
        containerColor = Theme.background,
        bottomBar = {
            NavigationBar(containerColor = Theme.background) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Theme.selection,
                    selectedTextColor = Theme.selection,
                    unselectedIconColor = Theme.ctaForeground,
                    unselectedTextColor = Theme.ctaForeground,
                    indicatorColor = Color.Transparent,
                )
                NavigationBarItem(
                    selected = tab == Tab.CAMERA,
                    onClick = { tab = Tab.CAMERA },
                    icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                    label = { Text("Camera", style = Theme.font(11)) },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = tab == Tab.PROFILE,
                    onClick = { tab = Tab.PROFILE },
                    icon = { Icon(Icons.Filled.AccountBox, contentDescription = null) },
                    label = { Text("Profile", style = Theme.font(11)) },
                    colors = colors,
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
            when {
                detailPhoto != null -> PhotoDetailScreen(photo = detailPhoto, onClose = closeDetail)
                tab == Tab.CAMERA -> CameraCaptureScreen(onOpenPhoto = { cameraDetailID = it.id })
                else -> ProfileScreen(onOpenPhoto = { profileDetailID = it.id })
            }
        }
    }
}
