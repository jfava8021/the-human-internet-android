package com.thehumaninternet.app.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.thehumaninternet.app.applicationScope
import com.thehumaninternet.app.designsystem.CTARole
import com.thehumaninternet.app.designsystem.CtaButton
import com.thehumaninternet.app.designsystem.CtaIcon
import com.thehumaninternet.app.designsystem.Theme
import com.thehumaninternet.app.logging.Log
import com.thehumaninternet.app.models.LocalAppState
import com.thehumaninternet.app.models.VerifiedPhoto
import com.thehumaninternet.app.profile.PhotoThumbnail
import com.thehumaninternet.app.uploads.PhotoLibrarySaver
import com.thehumaninternet.app.uploads.PhotoUploadQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private enum class CameraAuthorization { NOT_DETERMINED, AUTHORIZED, DENIED }

/**
 * The capture tab. A real camera, and only a camera: there is deliberately no
 * photo picker and no gallery import, since either would let someone upload
 * an AI image or someone else's photo and defeat the "taken live by a human"
 * guarantee.
 *
 * Capture is optimistic — the shutter doesn't wait on watermarking, signing
 * or the network. See `PhotoUploadQueue`.
 */
@Composable
fun CameraCaptureScreen(onOpenPhoto: (VerifiedPhoto) -> Unit) {
    val context = LocalContext.current
    var authorization by remember {
        mutableStateOf(if (context.hasCameraPermission()) CameraAuthorization.AUTHORIZED else CameraAuthorization.NOT_DETERMINED)
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        authorization = if (granted) CameraAuthorization.AUTHORIZED else CameraAuthorization.DENIED
    }
    LaunchedEffect(Unit) {
        if (authorization == CameraAuthorization.NOT_DETERMINED) {
            permissionRequest.launch(Manifest.permission.CAMERA)
        }
    }
    // Picks up a permission granted in system Settings while the app was away.
    LifecycleResumeEffect(Unit) {
        if (context.hasCameraPermission()) authorization = CameraAuthorization.AUTHORIZED
        onPauseOrDispose {}
    }

    Box(Modifier.fillMaxSize().background(Theme.background), contentAlignment = Alignment.Center) {
        when (authorization) {
            CameraAuthorization.NOT_DETERMINED -> Unit
            CameraAuthorization.DENIED -> PermissionDeniedView()
            CameraAuthorization.AUTHORIZED -> CameraView(onOpenPhoto)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraView(onOpenPhoto: (VerifiedPhoto) -> Unit) {
    val appState = LocalAppState.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }
    // Null until CameraX has finished looking for cameras.
    var isCameraAvailable by remember { mutableStateOf<Boolean?>(null) }
    var showCongrats by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val shutterFlash = remember { Animatable(0f) }

    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        controller.initializationFuture.addListener({
            val hasBack = controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            val hasFront = controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            if (!hasBack && hasFront) controller.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            isCameraAvailable = hasBack || hasFront
        }, ContextCompat.getMainExecutor(context))
        onDispose { controller.unbind() }
    }

    fun flipCamera() {
        val other =
            if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA
        if (controller.hasCamera(other)) controller.cameraSelector = other
    }

    fun capture() {
        val userID = appState.user.id ?: return
        // Cosmetic only — nothing waits on it.
        scope.launch {
            shutterFlash.animateTo(1f, tween(50, easing = LinearEasing))
            shutterFlash.animateTo(0f, tween(120))
        }
        // On the application scope: switching tabs mid-capture must not lose the photo.
        applicationScope.launch {
            try {
                val imageData = controller.capturePhoto(context)
                // Fired and forgotten: the gallery copy is a courtesy backup.
                launch(Dispatchers.IO) {
                    try {
                        PhotoLibrarySaver.save(context.applicationContext, imageData)
                    } catch (error: Exception) {
                        Log.camera.error("Gallery backup save failed", error)
                    }
                }
                val isFirstPhoto = appState.photos.isEmpty()
                val photo = PhotoUploadQueue.enqueue(imageData, userID, appState)
                appState.photos = listOf(photo) + appState.photos
                if (isFirstPhoto) showCongrats = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = "Please try again."
                Log.camera.error("Capture failed", error)
            }
        }
    }

    if (isCameraAvailable == false) {
        Message(
            icon = Icons.Filled.NoPhotography,
            title = "No camera available",
            body = "This device doesn't have a usable camera.",
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = shutterFlash.value }.background(Color.Black))

        CtaIcon(
            icon = Icons.Filled.Cameraswitch,
            contentDescription = "Flip camera",
            role = CTARole.TERTIARY,
            diameter = 44.dp,
            onClick = ::flipCamera,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp),
        )

        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxSize().padding(horizontal = 32.dp).padding(bottom = 24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            appState.photos.firstOrNull()?.let { last ->
                val shape = RoundedCornerShape(10.dp)
                PhotoThumbnail(
                    photo = last,
                    uploadState = appState.uploadState(last.id),
                    isProvisional = last.id in appState.provisionalPhotoIDs,
                    onRetry = { PhotoUploadQueue.retry(last.id, appState) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 12.dp)
                        .size(48.dp)
                        .clip(shape)
                        .border(1.dp, Theme.divider, shape)
                        .clickable { onOpenPhoto(last) },
                )
            }
            // The shutter: a tertiary CTA, since it stands alone over the live feed.
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Theme.ctaTertiaryBackground)
                    .clickable(role = Role.Button, onClickLabel = "Take photo", onClick = ::capture)
                    .padding(4.dp)
                    .border(4.dp, Theme.ctaTertiaryForeground, CircleShape),
            )
        }
    }

    if (showCongrats) {
        ModalBottomSheet(onDismissRequest = { showCongrats = false }, containerColor = Theme.background) {
            CongratsSheet(onDone = { showCongrats = false })
        }
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } },
            title = { Text("Couldn't take photo", style = Theme.font(18, FontWeight.SemiBold)) },
            text = { Text(errorMessage ?: "", style = Theme.font(14)) },
            containerColor = Theme.background,
        )
    }
}

@Composable
private fun PermissionDeniedView() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Message(
            icon = Icons.Filled.PhotoCamera,
            title = "Camera access is off",
            body = "Turn on camera access in Settings to take a photo.",
        )
        CtaButton(
            title = "Open Settings",
            modifier = Modifier.size(width = 160.dp, height = 48.dp),
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            },
        )
    }
}

@Composable
private fun Message(icon: ImageVector, title: String, body: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, contentDescription = null, tint = Theme.textSecondary, modifier = Modifier.size(40.dp))
        Text(title, style = Theme.font(17, FontWeight.SemiBold), color = Theme.textPrimary)
        Text(body, style = Theme.font(14), color = Theme.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CongratsSheet(onDone: () -> Unit) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("🎉", fontSize = 44.sp)
        Text(
            "Congrats on your\nfirst verified photo!",
            style = Theme.font(22, FontWeight.Bold),
            color = Theme.textPrimary,
            textAlign = TextAlign.Center,
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Bullet("Your photos are currently public, which means anyone with the link can see them — including the robots. You can change this to humans only in your profile, so only people signed up to the human internet can verify photo contents.")
            Bullet("All photos are also saved to your device's gallery, so if you ever lose access to your account, you keep your photos.")
        }
        Spacer(Modifier.size(4.dp))
        CtaButton(title = "Got it", onClick = onDone)
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = Theme.font(14), color = Theme.textSecondary)
        Text(text, style = Theme.font(14), color = Theme.textSecondary)
    }
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * Takes a photo and returns the JPEG exactly as the camera produced it,
 * EXIF orientation tag included — these are the bytes that get signed.
 */
private suspend fun LifecycleCameraController.capturePhoto(context: Context): ByteArray =
    suspendCancellableCoroutine { continuation ->
        takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bytes = image.use {
                        val buffer = it.planes[0].buffer
                        ByteArray(buffer.remaining()).also(buffer::get)
                    }
                    continuation.resume(bytes)
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            },
        )
    }
