package com.thehumaninternet.app.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/*
 * The Human Internet theme for Jetpack Compose.
 *
 * Wrap the app once:   HumanTheme(fontFamily = BrandFonts) { AppNavHost() }
 *
 * It maps EVERY Material 3 color role to the brand, so no default M3 purple,
 * tonal tint, or colored container can leak into vibe-coded screens. Brand-only
 * tokens that M3 has no slot for (surface vs. background, muted text, highlight…)
 * are on HumanTheme.colors.
 *
 * Values come from DesignTokens.kt (generated). Don't put raw values here.
 */

/** Brand colors M3 doesn't have a name for. Read as HumanTheme.colors.x */
@Immutable
data class HumanColors(
    val background: Color,
    val foreground: Color,
    val surface: Color,
    val surfaceHover: Color,
    val primary: Color,
    val primaryForeground: Color,
    val mutedForeground: Color,
    val subtleForeground: Color,
    val border: Color,
    val highlight: Color,
    val highlightForeground: Color,
    val destructive: Color,
    val destructiveForeground: Color,
    val ring: Color,
)

private val LightHumanColors = with(DesignTokens.Light) {
    HumanColors(Background, Foreground, Surface, SurfaceHover, Primary, PrimaryForeground,
        MutedForeground, SubtleForeground, Border, Highlight, HighlightForeground,
        Destructive, DestructiveForeground, Ring)
}

private val DarkHumanColors = with(DesignTokens.Dark) {
    HumanColors(Background, Foreground, Surface, SurfaceHover, Primary, PrimaryForeground,
        MutedForeground, SubtleForeground, Border, Highlight, HighlightForeground,
        Destructive, DestructiveForeground, Ring)
}

/**
 * Every M3 role mapped, so default components (TextField, Switch, Dialog…) look on-brand.
 * (lightColorScheme is only a data builder; every role we use is set explicitly, so
 * it is correct for dark values too.)
 */
private fun HumanColors.toMaterial(): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = primaryForeground,
    primaryContainer = surface,
    onPrimaryContainer = foreground,
    inversePrimary = primaryForeground,
    secondary = surface,
    onSecondary = foreground,
    secondaryContainer = surface,
    onSecondaryContainer = foreground,
    tertiary = foreground,
    onTertiary = background,
    tertiaryContainer = highlight,
    onTertiaryContainer = highlightForeground,
    background = background,
    onBackground = foreground,
    surface = background,               // M3 "surface" = screen background
    onSurface = foreground,
    surfaceVariant = surface,           // our light-gray surface
    onSurfaceVariant = mutedForeground,
    surfaceTint = Color.Transparent,    // no tonal tint: the brand is flat
    inverseSurface = foreground,
    inverseOnSurface = background,
    error = destructive,
    onError = destructiveForeground,
    errorContainer = surface,
    onErrorContainer = destructive,
    outline = border,
    outlineVariant = border,
    scrim = Color.Black.copy(alpha = 0.4f),
    surfaceBright = background,
    surfaceContainer = surface,
    surfaceContainerHigh = surfaceHover,
    surfaceContainerHighest = surfaceHover,
    surfaceContainerLow = surface,
    surfaceContainerLowest = background,
    surfaceDim = surfaceHover,
)

/** Brand type styles. Read as HumanTheme.type.h1 etc. One weight: Medium. */
@Immutable
data class HumanType(
    val display: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

private fun style(t: DesignTokens.TypeToken, family: FontFamily) = TextStyle(
    fontFamily = family,
    fontWeight = FontWeight.Medium,
    fontSize = t.sizeSp,
    lineHeight = t.lineHeightSp,
    letterSpacing = t.letterSpacing,
)

internal fun humanType(family: FontFamily) = with(DesignTokens.Type) {
    HumanType(
        display = style(Display, family), h1 = style(H1, family), h2 = style(H2, family),
        h3 = style(H3, family), title = style(Title, family), body = style(Body, family),
        label = style(Label, family), caption = style(Caption, family),
    )
}

/** M3 slots → brand styles, so plain Text() and M3 components pick up the brand. */
private fun HumanType.toMaterial() = Typography(
    displayLarge = display, displayMedium = h1, displaySmall = h2,
    headlineLarge = h1, headlineMedium = h2, headlineSmall = h3,
    titleLarge = h3, titleMedium = title, titleSmall = label,
    bodyLarge = body, bodyMedium = label, bodySmall = caption,
    labelLarge = label, labelMedium = caption, labelSmall = caption,
)

private val HumanShapes = with(DesignTokens.Radius) {
    Shapes(
        extraSmall = RoundedCornerShape(Sm),
        small = RoundedCornerShape(Md),
        medium = RoundedCornerShape(Lg),
        large = RoundedCornerShape(Xl),
        extraLarge = RoundedCornerShape(X2xl),
    )
}

private val LocalHumanColors = staticCompositionLocalOf { LightHumanColors }
private val LocalHumanType = staticCompositionLocalOf { humanType(FontFamily.Default) }

object HumanTheme {
    val colors: HumanColors
        @Composable get() = LocalHumanColors.current
    val type: HumanType
        @Composable get() = LocalHumanType.current
}

/**
 * @param fontFamily pass the bundled brand font, e.g.
 *   val BrandFonts = FontFamily(Font(R.font.inter_display_medium, FontWeight.Medium))
 * @param darkTheme dark values are PROVISIONAL; keep false until the brand defines dark.
 */
@Composable
fun HumanTheme(
    fontFamily: FontFamily = FontFamily.Default,
    darkTheme: Boolean = false, // swap to isSystemInDarkTheme() once dark is designed
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkHumanColors else LightHumanColors
    val type = humanType(fontFamily)
    CompositionLocalProvider(
        LocalHumanColors provides colors,
        LocalHumanType provides type,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = type.toMaterial(),
            shapes = HumanShapes,
            content = content,
        )
    }
}
