package com.thehumaninternet.app.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/*
 * Brand components. Build screens from these (and from Material 3 components,
 * which HumanTheme already restyles). Never hardcode colors, sizes or radii;
 * read DesignTokens / HumanTheme.
 */

// ---------------------------------------------------------------- Buttons

/** The three CTA roles. At most one Primary per screen. */
enum class CtaRole { Primary, Secondary, Tertiary }

@Composable
fun HumanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: CtaRole = CtaRole.Primary,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val c = HumanTheme.colors
    val content: @Composable RowScope.() -> Unit = {
        if (leadingIcon != null) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { leadingIcon() }
            Box(Modifier.size(DesignTokens.Space.S2))
        }
        Text(text, style = HumanTheme.type.label)
    }
    val sized = modifier.height(DesignTokens.Size.ControlHeight)
    val padding = PaddingValues(horizontal = 20.dp)
    when (role) {
        CtaRole.Primary -> Button(
            onClick = onClick, modifier = sized, enabled = enabled, shape = CircleShape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.primaryForeground),
            elevation = null, content = content,
        )
        CtaRole.Secondary -> Button(
            onClick = onClick, modifier = sized, enabled = enabled, shape = CircleShape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = c.surface, contentColor = c.foreground),
            elevation = null, content = content,
        )
        CtaRole.Tertiary -> TextButton(
            onClick = onClick, modifier = sized, enabled = enabled, shape = CircleShape,
            contentPadding = padding,
            colors = ButtonDefaults.textButtonColors(contentColor = c.foreground), content = content,
        )
    }
}

/** 48dp light-gray circle with one outlined icon. Pass Icon(..., contentDescription = null); the button carries the label. */
@Composable
fun HumanIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val c = HumanTheme.colors
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(DesignTokens.Size.IconButton).semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.surface, contentColor = c.foreground),
    ) {
        Box(Modifier.size(DesignTokens.Size.Icon), contentAlignment = Alignment.Center) { icon() }
    }
}

// ---------------------------------------------------------------- Badges & the mark

/** Count/streak badge, e.g. "3x". The ONLY place the blue highlight is used. */
@Composable
fun HighlightBadge(text: String, modifier: Modifier = Modifier) {
    val c = HumanTheme.colors
    Box(
        modifier
            .defaultMinSize(minWidth = 32.dp, minHeight = 32.dp)
            .clip(CircleShape)
            .background(c.highlight)
            .padding(horizontal = DesignTokens.Space.S2),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = HumanTheme.type.caption, color = c.highlightForeground) }
}

/**
 * The spiral mark = "verified". Never use a check, shield or lock instead.
 * Pass painterResource(R.drawable.ic_brand_mark).
 */
@Composable
fun VerifiedMark(mark: Painter, modifier: Modifier = Modifier, contentDescription: String? = "Verified") {
    Image(
        painter = mark,
        contentDescription = contentDescription,
        modifier = modifier.size(20.dp),
        colorFilter = ColorFilter.tint(HumanTheme.colors.foreground),
    )
}

/** Mark in a small white circle, used as a corner badge on cards/avatars. */
@Composable
fun VerifiedBadge(mark: Painter, modifier: Modifier = Modifier) {
    val c = HumanTheme.colors
    Box(
        modifier.size(32.dp).clip(CircleShape).background(c.background),
        contentAlignment = Alignment.Center,
    ) { VerifiedMark(mark, Modifier.size(18.dp)) }
}

// ---------------------------------------------------------------- Cards

/** Muted label over a black value. The core text pattern. */
@Composable
fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = HumanTheme.type.label, color = HumanTheme.colors.mutedForeground)
        Text(value, style = HumanTheme.type.body, color = HumanTheme.colors.foreground)
    }
}

/** Flat light-gray card: no border, no shadow. */
@Composable
fun HumanCard(
    modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp = DesignTokens.Radius.Lg,
    padding: PaddingValues = PaddingValues(DesignTokens.Space.S4),
    content: @Composable () -> Unit,
) {
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(HumanTheme.colors.surface).padding(padding),
    ) { content() }
}

/**
 * Notification row: white square thumbnail (photo or doodle) + label/value,
 * optional corner badge (VerifiedBadge or HighlightBadge).
 */
@Composable
fun NotificationCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    thumbnail: @Composable () -> Unit,
    cornerBadge: (@Composable () -> Unit)? = null,
) {
    Box(modifier) {
        HumanCard(padding = PaddingValues(DesignTokens.Space.S3)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space.S4)) {
                Box(
                    Modifier.size(DesignTokens.Size.Thumbnail).clip(RoundedCornerShape(DesignTokens.Radius.Sm))
                        .background(HumanTheme.colors.background),
                    contentAlignment = Alignment.Center,
                ) { thumbnail() }
                LabelValue(label, value)
            }
        }
        if (cornerBadge != null) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-10).dp)) { cornerBadge() }
        }
    }
}

/** Proof metadata block shown under a verified photo. */
@Composable
fun ProofInfoCard(
    device: String,
    date: String,
    time: String,
    os: String,
    place: String,
    coordinates: String,
    modifier: Modifier = Modifier,
) {
    val c = HumanTheme.colors
    val title = HumanTheme.type.title
    val cap = HumanTheme.type.caption
    fun line(main: String, secondary: String?) = buildAnnotatedString {
        append(main)
        if (secondary != null) {
            append(" ")
            withStyle(SpanStyle(fontSize = cap.fontSize, color = c.mutedForeground)) { append(secondary) }
        }
    }
    HumanCard(modifier, radius = DesignTokens.Radius.Md, padding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        Column {
            Text(line(device, null), style = title, color = c.foreground)
            Text(line(date, time), style = title, color = c.foreground)
            Text(line(os, null), style = title, color = c.foreground)
            Text(line(place, coordinates), style = title, color = c.foreground)
        }
    }
}

// ---------------------------------------------------------------- Motion

/**
 * Brand reveal: opacity only, 750ms ease-out, staggered 800ms per index.
 * Use for brand moments (onboarding, success), not routine UI.
 * Honors the system "Remove animations" setting automatically (animator scale 0).
 */
@Composable
fun BrandReveal(index: Int = 0, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(
            tween(
                durationMillis = DesignTokens.Motion.SlowMs,
                delayMillis = index * DesignTokens.Motion.StaggerMs,
                easing = DesignTokens.Motion.EaseOut,
            ),
        ),
    ) { content() }
}
