package com.thehumaninternet.app.designsystem

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thehumaninternet.app.R

/**
 * The only place styling lives. Mirrors the iOS app's `Theme.swift` token for
 * token; the website follows the same rules.
 *
 * Light-only. Three rules: backgrounds are pure white; text not on a button
 * is pure black; every CTA takes one of the three [CTARole]s.
 */
object Theme {
    val background = Color.White

    /** Text fields, cards and banners: the one step off white, shared with the CTA fill. */
    val surface = Color(0xFFF6F6F6)

    val textPrimary = Color.Black

    /** Black like all text, by design. Its own token so a hierarchy can come back in one line. */
    val textSecondary = Color.Black

    /** Text-field placeholders only: a hint, not copy. */
    val placeholder = Color(0xFFA2A2A2)

    val ctaBackground = surface
    val ctaForeground = Color(0xFF767676)
    val ctaTertiaryBackground = Color(0xFFE0E0E0)
    val ctaTertiaryForeground = Color.White

    val divider = Color.Black.copy(alpha = 0.08f)

    /** State indicators — radio buttons, the selected tab. */
    val selection = Color.Black

    /** Status *icons* (checkmarks, seals). Status text is black like all text. */
    val success = Color(0xFF4DC773)

    val cornerRadius = 14.dp

    /** Inter Display, SIL OFL — see `licenses/InterDisplay-OFL.txt`. */
    private val interDisplay = FontFamily(
        Font(R.font.inter_display_regular, FontWeight.Normal),
        Font(R.font.inter_display_medium, FontWeight.Medium),
        Font(R.font.inter_display_semibold, FontWeight.SemiBold),
        Font(R.font.inter_display_bold, FontWeight.Bold),
    )

    /** Every text style goes through here. Sizes are the iOS app's point sizes, as sp. */
    fun font(size: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = interDisplay, fontSize = size.sp, fontWeight = weight)
}

enum class CTARole {
    /** `#767676` on `#F6F6F6`. The default, and the primary of a pair. */
    PRIMARY,

    /** `#767676` label and 1dp outline on white — the secondary of a pair. */
    SECONDARY,

    /** White on `#E0E0E0`. **Only for a button standing alone on something that isn't white** — over the camera feed, over a photo. */
    TERTIARY;

    val foreground: Color
        get() = if (this == TERTIARY) Theme.ctaTertiaryForeground else Theme.ctaForeground

    val background: Color
        get() = when (this) {
            PRIMARY -> Theme.ctaBackground
            SECONDARY -> Theme.background
            TERTIARY -> Theme.ctaTertiaryBackground
        }

    val outline: Color
        get() = if (this == SECONDARY) Theme.ctaForeground else Color.Transparent
}

/**
 * Material is used for its machinery (sheets, dialogs, the tab bar), not its
 * look: the colour scheme is pinned to this app's palette and the default
 * text style to Inter Display.
 */
@Composable
fun HumanInternetTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Theme.ctaForeground,
        onPrimary = Color.White,
        background = Theme.background,
        onBackground = Theme.textPrimary,
        surface = Theme.background,
        onSurface = Theme.textPrimary,
        surfaceContainer = Theme.background,
        surfaceContainerLow = Theme.background,
        surfaceContainerHigh = Theme.background,
        surfaceContainerHighest = Theme.surface,
        onSurfaceVariant = Theme.textPrimary,
        outlineVariant = Theme.divider,
    )
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalContentColor provides Theme.textPrimary) {
            ProvideTextStyle(Theme.font(17), content)
        }
    }
}
