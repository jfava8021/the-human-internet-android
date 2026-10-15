package com.thehumaninternet.app.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thehumaninternet.app.designsystem.CtaButton
import com.thehumaninternet.app.designsystem.CtaRole
import com.thehumaninternet.app.designsystem.DesignTokens
import com.thehumaninternet.app.designsystem.Theme

/** Shown when the signed-in user's profile can't be loaded, in place of onboarding. */
@Composable
fun ProfileLoadErrorScreen(onRetry: () -> Unit, onLogOut: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Theme.background).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S6),
    ) {
        Spacer(Modifier.weight(1f))
        Text("Couldn't load your profile.", style = Theme.type.h2, color = Theme.foreground, textAlign = TextAlign.Center)
        Text(
            "Check your connection and try again. Your photos and settings are safe.",
            style = Theme.type.body,
            color = Theme.mutedForeground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = DesignTokens.Space.S8),
        )
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.padding(horizontal = DesignTokens.Space.S8).padding(bottom = DesignTokens.Space.S8),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S3),
        ) {
            CtaButton(title = "Try again", onClick = onRetry, modifier = Modifier.fillMaxWidth())
            CtaButton(title = "Log out", onClick = onLogOut, modifier = Modifier.fillMaxWidth(), role = CtaRole.Secondary)
        }
    }
}
