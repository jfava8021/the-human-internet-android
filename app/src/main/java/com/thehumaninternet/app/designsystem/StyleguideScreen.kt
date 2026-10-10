package com.thehumaninternet.app.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thehumaninternet.app.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder

/*
 * Living styleguide. Shows every token and brand component in one scroll.
 * Keep it in sync with the web /styleguide and the iOS DesignSystemStyleguide:
 * same sections, same order. When you add a brand component, add it here.
 *
 * Open the @Preview in Android Studio.
 */
@Composable
fun StyleguideScreen(mark: Painter) {
    val c = HumanTheme.colors
    val t = HumanTheme.type
    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState())
            .padding(horizontal = DesignTokens.Space.S4, vertical = DesignTokens.Space.S8),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S12),
    ) {
        Section("Brand reveal") {
            BrandReveal(0) { Wordmark(height = DesignTokens.Space.S6) }
            BrandReveal(1) { Text("Made by a human.", style = t.h1) }
            BrandReveal(2) { Text("Signed by a phone.", style = t.h1) }
        }

        Section("Colors") {
            listOf(
                "background" to c.background, "surface" to c.surface, "surfaceHover" to c.surfaceHover,
                "foreground" to c.foreground, "mutedForeground" to c.mutedForeground,
                "subtleForeground (≥18sp only)" to c.subtleForeground, "border" to c.border,
                "highlight (badges only)" to c.highlight, "destructive (proposed)" to c.destructive,
            ).forEach { (name, color) -> Swatch(name, color) }
        }

        Section("Type · one weight (Medium)") {
            TypeRow("display", "Human.", t.display)
            TypeRow("h1", "We just never signed it.", t.h1)
            TypeRow("h2", "Verify your identity", t.h2)
            TypeRow("h3", "Verify your identity", t.h3)
            TypeRow("title", "iPhone 16 Pro Max", t.title)
            TypeRow("body", "A white, spacious canvas gives creators the spotlight.", t.body)
            TypeRow("label", "Weekly Streak", t.label.copy(color = c.mutedForeground))
            TypeRow("caption", "37.7749° N, 122.4194° W", t.caption.copy(color = c.mutedForeground))
        }

        Section("Radius") {
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space.S3)) {
                with(DesignTokens.Radius) {
                    listOf("sm" to Sm, "md" to Md, "lg" to Lg, "xl" to Xl, "2xl" to X2xl).forEach { (n, r) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(r)).background(c.surface))
                            Text(n, style = t.caption, color = c.mutedForeground)
                        }
                    }
                }
            }
        }

        Section("Buttons · three CTA roles") {
            HumanButton("Download on the App Store", onClick = {}, role = CtaRole.Primary)
            HumanButton("Share", onClick = {}, role = CtaRole.Secondary)
            HumanButton("Not now", onClick = {}, role = CtaRole.Tertiary)
            CtaButton("Full-width primary", onClick = {})
            CtaButton("Disabled", onClick = {}, enabled = false)
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space.S3), verticalAlignment = Alignment.CenterVertically) {
                HumanIconButton(onClick = {}, contentDescription = "New photo") { PlusGlyph(c.foreground) }
                CtaIcon(Icons.Outlined.FavoriteBorder, contentDescription = "Like", onClick = {})
                HighlightBadge("3x")
                VerifiedBadge(mark)
            }
        }

        Section("Input") {
            var value by remember { mutableStateOf("") }
            FieldLabel("Display name")
            HITextField(placeholder = "Sam Reyes", text = value, onTextChange = { value = it })
            FieldLabel("Handle")
            HITextField(placeholder = "", text = "sam reyes", onTextChange = {}, error = "Handles can't contain spaces.")
            RadioRow("Public", isSelected = true, onClick = {}, subtitle = "Anyone with the link sees the photo.")
            RadioRow("Humans Only", isSelected = false, onClick = {}, subtitle = "Only people on the human internet.")
        }

        Section("Cards") {
            ProofInfoCard(
                device = "iPhone 16 Pro Max", date = "September 18, 2026", time = "3:42 PM",
                os = "iOS 19.0.1", place = "San Francisco, CA", coordinates = "37.7749° N, 122.4194° W",
            )
            NotificationCard("Photo verified", "Go show them.",
                thumbnail = { VerifiedMark(mark, Modifier.size(32.dp), contentDescription = null) },
                cornerBadge = { VerifiedBadge(mark) })
            NotificationCard("Weekly Streak", "Hard to miss you now.",
                thumbnail = { Image(painterResource(R.drawable.doodle_flame), contentDescription = null, modifier = Modifier.fillMaxSize()) },
                cornerBadge = { HighlightBadge("3x") })
            NotificationCard("Photo shared", "They know it's you.",
                thumbnail = { Image(painterResource(R.drawable.doodle_hand), contentDescription = null, modifier = Modifier.fillMaxSize()) })
            HumanCard { LabelValue("HumanCard", "A grey surface on the white page. Nothing more.") }
        }

        Section("The mark") {
            Box(
                Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(DesignTokens.Radius.Lg))
                    .border(1.dp, c.border, RoundedCornerShape(DesignTokens.Radius.Lg)),
                contentAlignment = Alignment.Center,
            ) { BrandMark(size = 120.dp) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S3)) {
        Text(title, style = HumanTheme.type.caption, color = HumanTheme.colors.mutedForeground)
        content()
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(DesignTokens.Radius.Md)).background(color)
                .border(1.dp, HumanTheme.colors.border, RoundedCornerShape(DesignTokens.Radius.Md)),
        )
        Spacer(Modifier.width(DesignTokens.Space.S3))
        Text(name, style = HumanTheme.type.label)
    }
}

@Composable
private fun TypeRow(name: String, sample: String, style: TextStyle) {
    Column {
        Text(name, style = HumanTheme.type.caption, color = HumanTheme.colors.mutedForeground)
        Text(sample, style = style)
    }
}

/** Stand-in icon so the styleguide has no icon-library dependency. Use Material Symbols Outlined in the app. */
@Composable
private fun PlusGlyph(color: Color) {
    Canvas(Modifier.size(DesignTokens.Size.Icon)) {
        val w = 1.5.dp.toPx()
        drawLine(color, center.copy(x = size.width * 0.15f), center.copy(x = size.width * 0.85f), w, StrokeCap.Round)
        drawLine(color, center.copy(y = size.height * 0.15f), center.copy(y = size.height * 0.85f), w, StrokeCap.Round)
    }
}

@Preview(showBackground = true, heightDp = 2400)
@Composable
private fun StyleguidePreview() {
    HumanInternetTheme {
        StyleguideScreen(mark = painterResource(R.drawable.ic_brand_mark))
    }
}
