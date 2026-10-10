# AGENTS.md: UI rules for The Human Internet (Android)

Read `DESIGN.md` before building or changing any UI. It explains the brand intent and is shared with web and iOS.

Stack: Kotlin 2.4, Jetpack Compose + Material 3, CameraX, supabase-kt.

## Where things live (`app/src/main/java/.../designsystem/`)
- `DesignTokens.kt`: **generated, never edit.** Colors (light/dark), type scale, radius, spacing, sizes, shadow, motion. To change a value, edit `design-system/tokens.json` in the **web repo** and run `node design-system/build.mjs` there.
- `HumanTheme.kt`: wraps `MaterialTheme` and maps every M3 color role, type slot and shape to the brand. Read brand-only values via `HumanTheme.colors` and `HumanTheme.type`.
- `Theme.kt`: `HumanInternetTheme` (the app's wrapper, with Inter Display) and `Theme.*` / `Theme.type.*`, the same values as plain properties for use anywhere.
- `Components.kt`: `CtaButton`, `CtaIcon` (`IconSurface.Page` / `.Media`), `HITextField` (with an error state), `FieldLabel`, `RadioRow`, `BrandMark`, `Wordmark`, `Spinner`.
- `BrandComponents.kt`: HumanButton (Primary/Secondary/Tertiary), HumanIconButton, HighlightBadge, VerifiedMark, VerifiedBadge, LabelValue, HumanCard, NotificationCard, ProofInfoCard, BrandReveal.
- `StyleguideScreen.kt`: the living styleguide (`@Preview`).
- `res/drawable/ic_brand_mark.xml`, `ic_wordmark.xml`: the logo.

## Hard rules
1. **No raw values.** Never write `Color(0x…)`, raw `.dp` or `.sp` numbers, `RoundedCornerShape(<number>)`, or `FontWeight.Bold` in screens. Use `Theme` / `HumanTheme.colors`, `Theme.type` / `HumanTheme.type`, `DesignTokens.Space / Radius / Size / Motion`, and `MaterialTheme.shapes`. If a token you need doesn't exist, stop and ask. Don't invent one.
2. **Reuse before you create.** Use `BrandComponents.kt` first. Plain Material 3 components are OK because `HumanTheme` restyles them, but don't override their colors locally. Any new reusable composable goes in `BrandComponents.kt` **and** gets added to `StyleguideScreen.kt`.
3. **One font weight.** Medium only. Create hierarchy with `HumanTheme.type.*` size and with `foreground` vs `mutedForeground`.
4. **Text sizes are `sp`** and come from `HumanTheme.type`, so they scale with the system font size. Test at 200% font scale.
5. **No new colors.** No dynamic color (Material You). Never call `dynamicLightColorScheme`. `highlight` (blue) is only for count/streak badges. Photos provide the color.
6. **"Verified" is always the spiral mark** (`VerifiedMark` / `VerifiedBadge`). Never use a check, shield or lock icon to mean verified.
7. **Flat surfaces.** Cards are `HumanCard`, with no border and no elevation. Don't use `ElevatedCard` or tonal elevation. Shadows are only for bottom sheets and dialogs.
8. **Motion is opacity only.** Use `tween(DesignTokens.Motion.BaseMs, easing = DesignTokens.Motion.EaseOut)` for UI and `BrandReveal` for brand moments. No slide, scale or spring.
9. **Icons are Material Symbols *Outlined*, weight 300**, imported as vector drawables. Every icon-only button needs a content description. Touch targets must be at least 48dp.
10. **Haptics:** use `HapticFeedbackType.Confirm` on capture and on "Photo verified". Nowhere else unless asked.

## Copy voice
- Use short declarative sentences ending in periods, often in pairs: "Photo verified. Go show them."
- Use sentence case. The wordmark is always lowercase: "the human internet".
- Don't use "C2PA", "cryptographic", or "blockchain" in user-facing copy unless the screen is explicitly technical.

## Before you finish a UI change
- Check `StyleguideScreen` and your screen in Compose previews, at 100% and 200% font scale.
- Run `./gradlew :app:compileDebugKotlin`.
- Run the hardcoded-value check, which should print nothing outside `designsystem/`:
  `grep -rnE "Color\(0x|FontWeight\.(Bold|SemiBold)|RoundedCornerShape\([0-9]|[0-9]+\.sp\b" --include=*.kt app/src | grep -v /designsystem/`
