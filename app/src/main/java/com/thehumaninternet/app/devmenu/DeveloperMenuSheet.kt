package com.thehumaninternet.app.devmenu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.thehumaninternet.app.designsystem.CtaIcon
import com.thehumaninternet.app.designsystem.DesignTokens
import com.thehumaninternet.app.designsystem.Theme
import com.thehumaninternet.app.logging.Log
import com.thehumaninternet.app.models.LocalAppState
import com.thehumaninternet.app.supabase.FeatureFlagKey
import com.thehumaninternet.app.supabase.FeatureFlagRepository
import kotlin.coroutines.cancellation.CancellationException

private enum class DevMenuPage(val title: String) {
    MENU("Developer menu"),
    TOOLS("Developer tools"),
    FLAGS("Feature flags"),
}

/** Same flags, labels and order as the iOS Feature Flags page. */
private val FLAGS = listOf(
    "Show Stripe Identity verification" to FeatureFlagKey.STRIPE_IDENTITY_VERIFICATION,
    "Custom verification pages" to FeatureFlagKey.CUSTOM_VERIFICATION_PAGES,
    "Require App Attest for signing" to FeatureFlagKey.REQUIRE_APP_ATTEST,
    "Server-side watermark" to FeatureFlagKey.SERVER_SIDE_WATERMARK,
    "Neue font: pay to enable" to FeatureFlagKey.NEUE_FONT,
)

/** Reached by shaking the phone while signed in as an admin; see `ShakeDetector`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperMenuSheet(onDismiss: () -> Unit) {
    var page by remember { mutableStateOf(DevMenuPage.MENU) }
    BackHandler(enabled = page != DevMenuPage.MENU) { page = DevMenuPage.MENU }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Theme.background) {
        Column(
            modifier = Modifier.padding(horizontal = DesignTokens.Space.S6).padding(bottom = DesignTokens.Space.S6),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S4),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space.S3),
            ) {
                if (page != DevMenuPage.MENU) {
                    CtaIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onClick = { page = DevMenuPage.MENU })
                }
                Text(page.title, style = Theme.type.h3, color = Theme.foreground)
            }

            when (page) {
                DevMenuPage.MENU -> {
                    MenuRow("Developer tools") { page = DevMenuPage.TOOLS }
                    HorizontalDivider(color = Theme.border)
                    MenuRow("Feature flags") { page = DevMenuPage.FLAGS }
                }
                DevMenuPage.TOOLS -> DeveloperToolsPage()
                DevMenuPage.FLAGS -> FeatureFlagsPage()
            }
        }
    }
}

@Composable
private fun MenuRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DesignTokens.Size.MinTouch)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Theme.type.body, color = Theme.foreground, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Theme.mutedForeground)
    }
}

@Composable
private fun Description(text: String) {
    Text(text, style = Theme.type.caption, color = Theme.mutedForeground)
}

@Composable
private fun DeveloperToolsPage() {
    Description("These are only available to admins. Switch them all off before testing the real user experience.")

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S1)) {
            Text("Skip C2PA verification", style = Theme.type.body, color = Theme.foreground)
            Description("New photos are watermarked and uploaded as usual but not signed, so they carry no C2PA manifest. This phone only.")
        }
        Switch(
            checked = DeveloperPreferences.skipC2PASigning,
            onCheckedChange = { DeveloperPreferences.skipC2PASigning = it },
        )
    }
}

/**
 * View-only on Android for now: flags are shared with iOS, so editing them
 * from here waits on Marina's sign-off. Re-reads the table on open, so a
 * change made on iOS shows up without relaunching.
 */
@Composable
private fun FeatureFlagsPage() {
    val appState = LocalAppState.current

    LaunchedEffect(Unit) {
        try {
            appState.featureFlags = FeatureFlagRepository.fetchAll()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.devMenu.error("Refreshing feature flags failed", error)
        }
    }

    Description("Flags are shared: they change the app for every user they apply to, on iOS and Android. View only here. Change them from the iOS developer menu.")

    for ((title, key) in FLAGS) {
        // A flag with no row says so, rather than looking like one someone
        // deliberately switched off.
        val stored = appState.featureFlags[key]
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = DesignTokens.Size.MinTouch),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = Theme.type.body, color = Theme.foreground, modifier = Modifier.weight(1f))
            Text(
                stored?.label ?: "${FeatureFlagKey.fallbackAudience(key).label} (not set)",
                style = Theme.type.body,
                color = Theme.mutedForeground,
            )
        }
    }

    Description("All: on for every user. Admin: on only for admin accounts. Off: on for nobody.")
}
