package com.thehumaninternet.app.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thehumaninternet.app.R

/*
 * Buttons and fields, built on BrandComponents.kt. Every value comes from
 * DesignTokens / Theme.
 */

/** A full-width pill in the given CTA role. Pass `Modifier.weight(1f)` to pair two in a row. */
@Composable
fun CtaButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    role: CtaRole = CtaRole.Primary,
    enabled: Boolean = true,
) {
    HumanButton(text = title, onClick = onClick, modifier = modifier, role = role, enabled = enabled)
}

/** Where an icon-only button sits. Either way it's a 48dp circle with one outlined icon. */
enum class IconSurface {
    /** Light grey on the white page. */
    Page,

    /** White, for a button over a photo or the live camera feed. */
    Media,
}

/** An icon-only CTA: the glyph centred on a 48dp circle. Always give it a content description. */
@Composable
fun CtaIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    surface: IconSurface = IconSurface.Page,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(DesignTokens.Size.IconButton)
            .clip(CircleShape)
            .background(if (surface == IconSurface.Page) Theme.surface else Theme.background)
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                else Modifier
            )
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Theme.foreground, modifier = Modifier.size(DesignTokens.Size.Icon))
    }
}

/** Grey field, no stroke. Pass [error] to outline it and show the message below. */
@Composable
fun HITextField(
    placeholder: String,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    val shape = RoundedCornerShape(DesignTokens.Radius.Md)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.S2)) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = Theme.type.body.copy(color = Theme.foreground),
            cursorBrush = SolidColor(Theme.foreground),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = DesignTokens.Size.ControlHeight)
                .clip(shape)
                .background(Theme.surface)
                .then(if (error != null) Modifier.border(1.dp, Theme.destructive, shape) else Modifier)
                .padding(horizontal = DesignTokens.Space.S4),
            decorationBox = { field ->
                Box(Modifier.defaultMinSize(minHeight = DesignTokens.Size.ControlHeight), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(placeholder, style = Theme.type.body, color = Theme.subtleForeground)
                    }
                    field()
                }
            },
        )
        if (error != null) {
            Text(error, style = Theme.type.label, color = Theme.destructive)
        }
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(text, style = Theme.type.label, color = Theme.mutedForeground)
}

@Composable
fun RadioRow(title: String, isSelected: Boolean, onClick: () -> Unit, subtitle: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DesignTokens.Size.MinTouch)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = DesignTokens.Space.S1),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space.S3),
    ) {
        Icon(
            if (isSelected) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = Theme.foreground,
        )
        Column {
            Text(title, style = Theme.type.body, color = Theme.foreground)
            if (subtitle != null) {
                Text(subtitle, style = Theme.type.label, color = Theme.mutedForeground)
            }
        }
    }
}

/**
 * The spiral "imprint" — the only way the app says *verified*. The vector
 * `ic_brand_mark`, the same artwork as the website's public/brand/mark.svg,
 * tinted to [color]: black on the page, white over a photo.
 *
 * Not the watermark: that is `brand_mark_watermark`, a PNG shared with the
 * signing Lambda — see `PhotoWatermarker`.
 */
@Composable
fun BrandMark(size: Dp = DesignTokens.Size.Icon, modifier: Modifier = Modifier, color: Color = Theme.foreground) {
    Image(
        painter = painterResource(R.drawable.ic_brand_mark),
        contentDescription = null,
        modifier = modifier.height(size),
        colorFilter = ColorFilter.tint(color),
    )
}

/** "the human ~ internet". Always lowercase, never re-set in type. */
@Composable
fun Wordmark(height: Dp = DesignTokens.Space.S4, modifier: Modifier = Modifier, color: Color = Theme.foreground) {
    Image(
        painter = painterResource(R.drawable.ic_wordmark),
        contentDescription = "the human internet",
        modifier = modifier.height(height),
        colorFilter = ColorFilter.tint(color),
    )
}

@Composable
fun Spinner(modifier: Modifier = Modifier, color: Color = Theme.foreground, diameter: Dp = DesignTokens.Space.S6) {
    CircularProgressIndicator(modifier = modifier.size(diameter), color = color, strokeWidth = 2.dp)
}
