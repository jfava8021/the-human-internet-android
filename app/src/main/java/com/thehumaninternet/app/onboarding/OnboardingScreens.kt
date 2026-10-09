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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Star
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

@Composable
fun WelcomeScreen(isBusy: Boolean, onSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Theme.background).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        BrandMark(width = 56.dp, modifier = Modifier.padding(bottom = 24.dp))
        Text(
            "Welcome to\nthe human internet",
            style = Theme.font(28, FontWeight.Bold),
            color = Theme.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        // The one button that isn't a CTA role: the sign-in provider's own
        // button, black so it shows on white — as Sign in with Apple is on iOS.
        Box(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .padding(bottom = 48.dp)
                .fillMaxWidth()
                .height(50.dp)
                .alpha(if (isBusy) 0.5f else 1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .clickable(enabled = !isBusy, role = Role.Button, onClick = onSignIn),
            contentAlignment = Alignment.Center,
        ) {
            Text("Sign in with Google", style = Theme.font(17, FontWeight.Medium), color = Color.White)
        }
    }
}

/** Index into this list is what `users.profile_icon_index` stores; the order matches iOS. */
private val profileIcons = listOf(
    Icons.Filled.Person,
    Icons.Filled.Pets,
    Icons.Filled.Star,
    Icons.Filled.LocalFireDepartment,
    Icons.Filled.Eco,
    Icons.Filled.DarkMode,
)

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
            .padding(24.dp)
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Set up profile", style = Theme.font(24, FontWeight.Bold), color = Theme.textPrimary)
            Text(
                "The information below may be visible to the public. It can be changed later.",
                style = Theme.font(14),
                color = Theme.textSecondary,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Username")
            HITextField(placeholder = "Your Username", text = username, onTextChange = { username = it })
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FieldLabel("Profile Icon")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                profileIcons.forEachIndexed { index, icon ->
                    CtaIcon(
                        icon = icon,
                        contentDescription = null,
                        diameter = 44.dp,
                        onClick = { selectedIcon = index },
                        modifier = Modifier.border(
                            2.dp,
                            if (selectedIcon == index) Theme.selection else Color.Transparent,
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
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text("👋", fontSize = 64.sp)
        Text("Welcome human.", style = Theme.font(26, FontWeight.Bold), color = Theme.textPrimary)
        Text(
            "You're verified. From here on, every photo you take can carry proof that a real person took it — not a bot, not a model. Let's take your first one.",
            style = Theme.font(15),
            color = Theme.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.weight(1f))
        CtaButton(
            title = "Take your first photo",
            onClick = onTakePhoto,
            modifier = Modifier.padding(horizontal = 32.dp).padding(bottom = 28.dp).fillMaxWidth(),
        )
    }
}
