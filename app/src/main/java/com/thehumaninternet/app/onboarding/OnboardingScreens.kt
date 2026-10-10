package com.thehumaninternet.app.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thehumaninternet.app.designsystem.BrandMark
import com.thehumaninternet.app.designsystem.CtaButton
import com.thehumaninternet.app.designsystem.CtaIcon
import com.thehumaninternet.app.designsystem.FieldLabel
import com.thehumaninternet.app.designsystem.HITextField
import com.thehumaninternet.app.designsystem.Theme
import com.thehumaninternet.app.designsystem.DesignTokens
import com.thehumaninternet.app.designsystem.Wordmark
import com.thehumaninternet.app.designsystem.BrandReveal
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.thehumaninternet.app.R
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi

@Composable
fun WelcomeScreen(isBusy: Boolean, onSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Theme.background).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        // A brand moment: the mark, then the words, faded in by opacity
        // only (`BrandReveal`).
        BrandReveal(0) {
            BrandMark(size = DesignTokens.Size.Thumbnail, modifier = Modifier.padding(bottom = DesignTokens.Space.S6))
        }
        BrandReveal(1) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S3),
            ) {
                Text("Welcome to", style = Theme.type.h2, color = Theme.foreground, textAlign = TextAlign.Center)
                Wordmark(height = DesignTokens.Space.S6)
            }
        }
        Spacer(Modifier.weight(1f))
        // The screen's one primary CTA: a black pill.
        CtaButton(
            title = "Sign in with Google",
            enabled = !isBusy,
            onClick = onSignIn,
            modifier = Modifier
                .padding(horizontal = DesignTokens.Space.S8)
                .padding(bottom = DesignTokens.Space.S12)
                .fillMaxWidth(),
        )
    }
}

/** Index into this list is what `users.profile_icon_index` stores; the order matches iOS. */
private val profileIcons = listOf(
    Icons.Outlined.Person,
    Icons.Outlined.Pets,
    Icons.Outlined.Star,
    Icons.Outlined.LocalFireDepartment,
    Icons.Outlined.Eco,
    Icons.Outlined.DarkMode,
)

// FlowRow is stable in current Compose; the opt-in is harmless where it isn't needed.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(
    initialUsername: String,
    initialIconIndex: Int,
    isBusy: Boolean,
    onNext: (username: String, iconIndex: Int) -> Unit,
) {
    // Resuming after a previous session prefills what was already entered.
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var selectedIcon by rememberSaveable { mutableIntStateOf(initialIconIndex) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.background)
            .systemBarsPadding()
            .padding(DesignTokens.Space.S6)
            .padding(top = DesignTokens.Space.S6),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S8),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S2)) {
            Text("Set up profile", style = Theme.type.h2, color = Theme.foreground)
            Text(
                "The information below may be visible to the public. It can be changed later.",
                style = Theme.type.body,
                color = Theme.mutedForeground,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S2)) {
            FieldLabel("Username")
            HITextField(placeholder = "Your username", text = username, onTextChange = { username = it })
        }

        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S3)) {
            FieldLabel("Profile icon")
            // Six 48dp circles don't fit a narrow phone in one row.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space.S2),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S2),
            ) {
                profileIcons.forEachIndexed { index, icon ->
                    CtaIcon(
                        icon = icon,
                        contentDescription = "Profile icon ${index + 1}",
                        onClick = { selectedIcon = index },
                        modifier = Modifier.border(
                            2.dp,
                            if (selectedIcon == index) Theme.foreground else Color.Transparent,
                            CircleShape,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        CtaButton(
            title = "Next",
            enabled = username.isNotBlank() && !isBusy,
            onClick = { onNext(username, selectedIcon) },
        )
    }
}

@Composable
fun WelcomeHumanScreen(onTakePhoto: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Theme.background).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S6),
    ) {
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.doodle_hand),
            contentDescription = null,
            modifier = Modifier.size(DesignTokens.Space.S24),
        )
        Text("Welcome human.", style = Theme.type.h2, color = Theme.foreground)
        Text(
            "You're verified. From here on, every photo you take can carry proof that a real person took it — not a bot, not a model. Let's take your first one.",
            style = Theme.type.body,
            color = Theme.mutedForeground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = DesignTokens.Space.S8),
        )
        Spacer(Modifier.weight(1f))
        CtaButton(
            title = "Take your first photo",
            onClick = onTakePhoto,
            modifier = Modifier.padding(horizontal = DesignTokens.Space.S8).padding(bottom = DesignTokens.Space.S8).fillMaxWidth(),
        )
    }
}
