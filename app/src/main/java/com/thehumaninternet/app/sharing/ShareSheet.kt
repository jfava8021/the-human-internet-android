package com.thehumaninternet.app.sharing

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.thehumaninternet.app.designsystem.CtaButton
import com.thehumaninternet.app.designsystem.CtaIcon
import com.thehumaninternet.app.designsystem.Spinner
import com.thehumaninternet.app.designsystem.Theme
import com.thehumaninternet.app.logging.Log
import com.thehumaninternet.app.models.VerifiedPhoto
import com.thehumaninternet.app.supabase.PhotoRepository
import com.thehumaninternet.app.uploads.PhotoUploadQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import com.thehumaninternet.app.designsystem.DesignTokens
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow

/** How a destination is reached — decided by what that platform can actually accept. */
private sealed interface ShareKind {
    /** Its own composer, pre-filled with the link. No image. */
    data class LinkIntent(val intent: ShareIntent) : ShareKind

    /** The SMS app, with the link as the message body. */
    data object Messages : ShareKind

    /**
     * Copy the link, explain, then hand over just the image. For platforms
     * that refuse caption text from another app, so the user pastes the link
     * themselves. `packageName` aims the share at that app when it's installed.
     */
    data class Platform(val packageName: String) : ShareKind
}

private data class ShareOption(val label: String, val icon: ImageVector, val kind: ShareKind)

private val shareOptions = listOf(
    ShareOption("Instagram", Icons.Outlined.Camera, ShareKind.Platform("com.instagram.android")),
    ShareOption("Reddit", Icons.Outlined.Forum, ShareKind.LinkIntent(ShareIntent.REDDIT)),
    ShareOption("X", Icons.Outlined.Close, ShareKind.LinkIntent(ShareIntent.X)),
    ShareOption("Facebook", Icons.Outlined.People, ShareKind.Platform("com.facebook.katana")),
    ShareOption("Messages", Icons.Outlined.Sms, ShareKind.Messages),
)

/**
 * Five destinations above the verification link, which doubles as its own
 * copy button. What's shared is always `VerifiedPhoto.verificationURL` — the
 * public link that resolves signed-out — never the `thehumaninternet://` deep
 * link, which only opens for people who already have the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(photo: VerifiedPhoto, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var didCopy by remember { mutableStateOf(false) }
    // Non-null while the "Before you share" step is showing for that app.
    var instructionsFor by remember { mutableStateOf<ShareKind.Platform?>(null) }
    var isPreparingShare by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf(false) }

    fun beginPlatformShare(platform: ShareKind.Platform) {
        context.copyToClipboard(photo.verificationURL)
        instructionsFor = platform
    }

    /** The generic fallback when a destination's own path isn't available. */
    fun beginGenericShare() = beginPlatformShare(ShareKind.Platform(packageName = ""))

    fun begin(kind: ShareKind) {
        when (kind) {
            is ShareKind.Platform -> beginPlatformShare(kind)

            ShareKind.Messages -> {
                val message = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
                    // First person, unlike the other destinations: a text goes
                    // to someone who already knows you.
                    .putExtra("sms_body", "Proof I took this photo: ${photo.verificationURL}")
                if (!context.tryStart(message)) beginGenericShare()
            }

            // App scheme first, web composer second, generic share last.
            is ShareKind.LinkIntent -> {
                val opened = kind.intent.appURL(photo)?.let { context.tryStart(viewIntent(it)) } == true ||
                    context.tryStart(viewIntent(kind.intent.composerURL(photo)))
                if (!opened) beginGenericShare()
            }
        }
    }

    fun shareImage(platform: ShareKind.Platform) {
        isPreparingShare = true
        scope.launch {
            try {
                val data = PhotoUploadQueue.localImageData(photo.id) ?: PhotoRepository.downloadImage(photo.storagePath)
                val uri = withContext(Dispatchers.IO) { context.writeShareFile(data) }
                val send = Intent(Intent.ACTION_SEND)
                    .setType("image/jpeg")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val aimed = platform.packageName.isNotEmpty() &&
                    context.tryStart(Intent(send).setPackage(platform.packageName))
                if (!aimed) context.startActivity(Intent.createChooser(send, null))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                shareError = true
                Log.sharing.error("Preparing share file failed", error)
            } finally {
                isPreparingShare = false
            }
        }
    }

    LaunchedEffect(didCopy) {
        if (didCopy) {
            delay(1600)
            didCopy = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Theme.background) {
        val platform = instructionsFor
        if (platform != null) {
            Column(
                modifier = Modifier.padding(horizontal = DesignTokens.Space.S6).padding(bottom = DesignTokens.Space.S6),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S4),
            ) {
                Text("Before you share.", style = Theme.type.title, color = Theme.foreground)
                Text(
                    "We've copied the verification link to your clipboard. Paste it into your caption alongside the photo, so anyone who sees it can verify it's real.",
                    style = Theme.type.body,
                    color = Theme.mutedForeground,
                )
                CtaButton(
                    title = "Continue",
                    onClick = {
                        instructionsFor = null
                        shareImage(platform)
                    },
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = DesignTokens.Space.S6).padding(bottom = DesignTokens.Space.S8),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S6),
            ) {
                Text(
                    if (shareError) "Couldn't prepare photo. Please try again." else "Share your human photo.",
                    style = Theme.type.title,
                    color = if (shareError) Theme.destructive else Theme.foreground,
                )

                Box(contentAlignment = Alignment.Center) {
                    Row(
                        modifier = Modifier.alpha(if (isPreparingShare) 0.5f else 1f),
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space.S4),
                    ) {
                        shareOptions.forEach { option ->
                            Column(
                                modifier = Modifier.clickable(enabled = !isPreparingShare, role = Role.Button) {
                                    shareError = false
                                    begin(option.kind)
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S2),
                            ) {
                                CtaIcon(option.icon, contentDescription = null)
                                Text(option.label, style = Theme.type.caption, color = Theme.mutedForeground)
                            }
                        }
                    }
                    if (isPreparingShare) Spinner()
                }

                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = DesignTokens.Size.ControlHeight)
                        .clip(CircleShape)
                        .background(Theme.surface)
                        .clickable(role = Role.Button, onClickLabel = "Copy verification link") {
                            context.copyToClipboard(photo.verificationURL)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            didCopy = true
                        }
                        .padding(horizontal = DesignTokens.Space.S4),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space.S2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (didCopy) "Link copied." else photo.verificationLink,
                        style = Theme.type.label,
                        color = Theme.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        if (didCopy) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = Theme.foreground,
                        modifier = Modifier.size(DesignTokens.Space.S4),
                    )
                }
            }
        }
    }
}

private fun viewIntent(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))

/**
 * Starts an activity and reports whether anything handled it. Opened
 * speculatively rather than checked first: asking whether another app is
 * installed needs a `<queries>` manifest entry per app, whereas a failed start
 * already says so.
 */
private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}

private fun Context.copyToClipboard(text: String) {
    getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("Verification link", text))
}

/**
 * Writes a temp copy of the photo for the receiving app to read, and returns
 * a `content://` URI for it. The previous share's copy is cleared first —
 * Android gives no callback when a share finishes, so this is when cleanup
 * happens.
 */
private fun Context.writeShareFile(data: ByteArray): Uri {
    val directory = File(cacheDir, "shared").apply {
        deleteRecursively()
        mkdirs()
    }
    val file = File(directory, "${UUID.randomUUID()}.jpg").apply { writeBytes(data) }
    return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
}
