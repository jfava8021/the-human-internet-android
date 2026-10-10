package com.thehumaninternet.app.designsystem

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.thehumaninternet.app.R

/**
 * The façade screens read colours and type from, built from the generated
 * [DesignTokens] (from `design-system/tokens.json` in the website repo — edit
 * values there and run `node design-system/build.mjs`, never here).
 *
 * Plain values rather than composition locals so they work anywhere,
 * including outside a composable. They match [HumanTheme.colors] /
 * [HumanTheme.type], which [HumanInternetTheme] provides; the app is
 * light-only, so the two can't disagree.
 */
object Theme {
    private val light = DesignTokens.Light

    /** Every screen's background. */
    val background = light.Background

    /** Cards, fields, icon buttons, banners. */
    val surface = light.Surface
    val surfaceHover = light.SurfaceHover

    /** Primary text, icons, the mark. */
    val foreground = light.Foreground

    /** Labels, metadata, secondary copy. AA at every size. */
    val mutedForeground = light.MutedForeground

    /** Placeholders and decorative text only — fails AA under 18sp. */
    val subtleForeground = light.SubtleForeground

    /** Hairlines and dividers. Prefer spacing or a `surface` fill. */
    val border = light.Border

    /** Errors and destructive confirmations. */
    val destructive = light.Destructive

    /**
     * Inter Display, SIL OFL — see `licenses/InterDisplay-OFL.txt`. One
     * weight in the design system; the other cuts stay bundled so nothing
     * that asks for them falls back to a synthesised bold.
     */
    val brandFonts = FontFamily(
        Font(R.font.inter_display_regular, FontWeight.Normal),
        Font(R.font.inter_display_medium, FontWeight.Medium),
        Font(R.font.inter_display_semibold, FontWeight.SemiBold),
        Font(R.font.inter_display_bold, FontWeight.Bold),
    )

    /** The type scale: display, h1, h2, h3, title, body, label, caption. Sizes are sp. */
    val type: HumanType = humanType(brandFonts)
}

/**
 * Wraps the app once. [HumanTheme] maps every Material 3 role, type slot and
 * shape to the brand; this adds the brand font and the default text style
 * and colour for unstyled text.
 */
@Composable
fun HumanInternetTheme(content: @Composable () -> Unit) {
    HumanTheme(fontFamily = Theme.brandFonts) {
        CompositionLocalProvider(LocalContentColor provides Theme.foreground) {
            ProvideTextStyle(Theme.type.body, content)
        }
    }
}
