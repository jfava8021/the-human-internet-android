@AGENTS.md

# The Human Internet — Android App

Native Kotlin + Jetpack Compose app for "the human internet" — photo verification proving a photo was captured live by a real human. It is a port of the iOS app and **a second front end over the same backend**: nothing server-side lives here, and nothing server-side was changed to make it work.

**Sibling repos**, all talking to the same Supabase project (`xpjkgngifffzdaikjakw`):

- `../the-human-internet-app/` — the iOS app. **Its `CLAUDE.md` is the reference for the product, the database, and every shared contract.** Read it before changing anything that touches the backend; this file only records what is specific to Android.
- `../the-human-internet/` — the Next.js website (marketing + the public verification page).
- `../the-human-internet-backend/` — the signing Lambda and the Supabase Edge Functions.

## What's ported (core flow)

- **Sign-in** → **profile setup** → (identity verification passed through, see below) → **welcome** → main app. Progress persists to `users.onboarding_step` after every step.
- **Camera tab**: CameraX capture, front/back flip, shutter flash, tap-through thumbnail of the last photo. No gallery import, by design — it would defeat the "taken live" guarantee.
- **Upload pipeline** (`uploads/PhotoUploadQueue.kt`): optimistic and durable, a faithful port of the iOS queue — on-disk manifest as the source of truth, both watermark paths, 2-at-a-time processing, resume on launch, prune on sign-out, the same expiry limits. One deliberate difference: **automatic retries back off** (10s, 1 min, 5 min, 30 min, then every 6 h; `retryDelay`), because each attempt spends a Play Integrity token against a daily quota shared by all users, and the iOS-style retry every 10 seconds could exhaust it from a few stuck phones. A manual retry always runs at once.
- **Profile tab**: photo grid, long-press multi-select and batch delete, the pending/failed banner.
- **Photo detail**: the photo, Share, delete.
- **Sharing**: Reddit and X link intents, Messages with the link pre-filled, Instagram/Facebook via clipboard + image share, and the copyable verification link.
- **Settings** (minimal): username (read-only), privacy, Log Out.

## Not ported yet

Stripe Identity verification, the in-app verification page and `thehumaninternet://photo/{id}` deep links, Preview (as a fellow human / as a lurker), username editing, the verification-page editor, Account Settings (Photo Fingerprint switch, account deletion), the developer menu and its tools, the PP Neue Montreal font flag, and New Relic monitoring.

Until Stripe Identity is ported, onboarding's verify step is passed through exactly as a manual Skip is on iOS: `verification_status` stated as `unverified`, then on to the next step.

## Differences from iOS that matter

- **Sign in with Google, not Apple** (`auth/GoogleAuthService.kt`). It runs Supabase's OAuth flow in the browser and returns to `thehumaninternet://login-callback`. An Apple sign-in and a Google sign-in are the same account only if Supabase links them by verified email — an iOS user who chose "Hide My Email" gets a second, separate account on Android.
- **Signing requests are attested with Play Integrity, not App Attest** (`verification/PlayIntegrityService.kt`). For each photo the app asks Google Play for a token bound to SHA-256 of the exact bytes, and sends it as `X-Play-Integrity-Token`; `sign-photo` has Google decode it (the backend's `_shared/playIntegrity.ts`). The same `require_app_attest` flag governs both platforms. Things to know:
  - **It is on**: `cloudProjectNumber` in `PlayIntegrityService` is the number of the Google Cloud project ("The Human Internet Android") linked in Play Console → App integrity. Setting it to 0 turns Play Integrity off and sends every request unattested, which the server refuses (`attestation_required`) while the flag is `all`.
  - **Only a build installed from Google Play, on a real phone, passes.** A debug build, a build installed over USB, and anything on the emulator get a real token that Google marks unrecognized, and the server refuses it — for admins too; there is deliberately no development exemption. So with the flag on, uploads can't be tested from Android Studio or the emulator: ship a build to a Play testing track (Android's TestFlight) and test on a phone. Everything before the signing call still works locally.
  - Like App Attest it never blocks an upload by itself: any failure to get a token sends the request unattested and the server decides.
  - The request-hash encoding is shared with the backend; `PlayIntegrityRequestHashTest` and the backend's `playIntegrity_test.ts` pin the same vector.
- **`HumanUser.socialLinks` is raw JSON**, not a typed list. Every profile save writes the whole row back (as on iOS), and this build has no editor for the handles, so it passes them through untouched rather than risk dropping one it doesn't recognise.
- **Ids are lowercase UUID strings**, not a UUID type — that's how supabase-kt and Postgres both render them.
- **The provisional watermark is drawn into the decoded bitmap**, not overlaid as a separate view. Same artwork, same `markRect`, same colour rule — so it picks its black or white from the (downscaled) preview it's drawn on.

## Design system

The look is the shared design system, not anything local to this repo: **`DESIGN.md`** (intent, generated into this repo) and **`AGENTS.md`** (the hard rules and the hardcoded-value check). Values come from `design-system/tokens.json` in the **website repo**; `node design-system/build.mjs` there regenerates `designsystem/DesignTokens.kt` here — never edit that file. `HumanTheme` maps every Material 3 role, type slot and shape to the brand; `HumanInternetTheme` wraps it with Inter Display. Screens read `Theme.foreground` / `.mutedForeground` / `.surface` / `.border` / `.destructive` and `Theme.type.<display|h1|h2|h3|title|body|label|caption>` — plain values, usable outside a composable — plus `DesignTokens.Space / Radius / Size`. Buttons are pills (`CtaButton` / `HumanButton` with `CtaRole.Primary` black, `Secondary` grey, `Tertiary` text only); icon-only buttons are 48dp circles (`CtaIcon`, `IconSurface.Media` over a photo or the camera feed); cards are flat; motion is opacity only; icons are Material *Outlined*; **"verified" is always the spiral** (`BrandMark`, `VerifiedBadge` with `R.drawable.ic_brand_mark`), never a check or seal. The launcher icon is the same spiral, black on white (`drawable/ic_launcher_foreground.xml`, which is also the themed-icon monochrome layer). Haptics only on capture and "Photo verified". `StyleguideScreen` (`@Preview`) shows every token and component.

## Setup needed before sign-in works (Supabase and Google dashboards)

None of this can be done from the repo:

1. Google Cloud Console: create an OAuth client of type **Web application**, with `https://xpjkgngifffzdaikjakw.supabase.co/auth/v1/callback` as an authorised redirect URI.
2. Supabase → Authentication → Providers → **Google**: enable it with that client id and secret.
3. Supabase → Authentication → URL Configuration → Redirect URLs: add `thehumaninternet://login-callback`.

## Repo layout

```
app/src/main/java/com/thehumaninternet/app/
  HumanInternetApp.kt   <- Application: owns AppState and the application coroutine scope
  MainActivity.kt       <- single activity; receives the sign-in callback
  models/               <- Models.kt (rows + enums), AppState.kt
  supabase/             <- client + UserProfile/Photo/FeatureFlag repositories
  auth/  uploads/  verification/
  designsystem/         <- DesignTokens (generated), HumanTheme, Theme, Components,
                           BrandComponents, StyleguideScreen, PhotoWatermarker, RemotePhotoImage
  root/  onboarding/  camera/  profile/  settings/  sharing/  logging/
app/src/test/           <- JVM unit tests pinning the shared contracts
licenses/               <- third-party licence texts (Inter Display OFL)
```

## Architecture

Mirrors iOS: dumb screens that take callbacks, one coordinator per flow (`OnboardingFlow`, `MainTabScreen`), repositories for side effects, and one `AppState` (Compose snapshot state, provided through `LocalAppState`).

- **`AppState.hydrate(userID)` is the single path** from "authenticated" to "profile + photos + flags loaded", and its only caller is the `sessionStatus` collector in `RootScreen` — which handles cold-launch restore, a fresh sign-in returning from the browser, and a session that went invalid by itself.
- **`AppState` and the upload manifest are main-thread confined.** That is what keeps the manifest's read-modify-write cycles from interleaving; file and network work hops to a background dispatcher and comes back.
- Navigation is plain state, not a navigation library: two tabs, each with at most one photo detail on top.

## Shared contracts — change these and you break iOS, the website, or links in the wild

- `VerifiedPhoto.verificationURL`, the storage path shape, the deep-link scheme and the Base58 short code (`VerifiedPhotoLinkTest`).
- The three `text` + CHECK-constraint enums on `users`, with fallbacks that claim less, never more (`HumanUserDecodingTest`). Add a value to a constraint and add the case here in the same change.
- The watermark artwork and placement: `res/drawable-nodpi/brand_mark_watermark.png` is byte-identical to the iOS image set and the Lambda's `assets/brand-mark.png`, placed by `PhotoWatermarker.markRect` (`PhotoWatermarkerTest`). To change the artwork, replace the PNG in all three repos.
- The watermark's colour: the artwork's alpha is the spiral's shape, filled solid black or solid white by `PhotoWatermarker.markColor` — a 32×32 grid of points across the mark's box, mean Rec. 709 luma, black above 0.5 — the same rule as the iOS app and the Lambda's `mark_color`. `PhotoWatermarkerTest` pins the grid, the threshold and the artwork's 1024×1082 size.
- The share URL shapes (`ShareIntentTest`).
- The `sign-photo` headers in `RemotePhotoSigner`.

## Building and running

Open the folder in Android Studio, or from a terminal:

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug        # build
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:bundleRelease        # the .aab Google Play takes
```

The emulator has a virtual camera, so capture works there, unlike the iOS Simulator.

## Shipping (Play internal testing)

Pushing to `main` runs the unit tests, builds a signed release bundle and rolls it out to the **internal testing** track in Google Play Console — GitHub Actions (`ubuntu-latest`) → `fastlane android internal` → Play. The Android counterpart of the iOS repo's TestFlight pipeline. `.github/workflows/tests.yml` separately runs the unit tests on every pull request, with no secrets. A second, separate upload then pushes the **Play Store listing icon** — separate because Google applies one upload as an all-or-nothing change, and on 2026-10-10 a missing "Manage store presence" permission on the CI service account failed the icon and took every build down with it; that step now only warns. It pushes, `fastlane/metadata/android/en-US/images/icon.png` (512×512, the spiral in black on white, matching the launcher icon) — so a new icon is a change to that file, not a Play Console edit. Listing text and screenshots stay in Play Console (`skip_upload_metadata` / `skip_upload_screenshots`).

- **Never add a `pull_request` trigger to `play-internal.yml`.** The repo is public and that job holds the upload key and the Play publishing credentials.
- **Five repository secrets drive it**: `PLAY_UPLOAD_KEYSTORE_BASE64` (the upload keystore, base64), `PLAY_UPLOAD_KEYSTORE_PASSWORD`, `PLAY_UPLOAD_KEY_ALIAS`, `PLAY_UPLOAD_KEY_PASSWORD`, and `PLAY_SERVICE_ACCOUNT_JSON` (a Google Cloud service account key that has been invited in Play Console → Users and permissions with release rights, with the Google Play Android Developer API enabled in its project). While the keystore or the service account is missing the workflow **skips with a warning** instead of failing, which is also what a fork gets.
- **Signing reads the same four values from two places** (`app/build.gradle.kts`): `keystore.properties` locally, `PLAY_UPLOAD_*` environment variables on CI. Neither the keystore nor that file is ever committed. With neither, a release build falls back to the debug key — fine for a local smoke test, rejected by Play.
- **The version code is the workflow run number + 100**, passed as `-PversionCode`. The offset keeps CI above the build that had to be uploaded by hand. **Re-running a finished run reuses its number, which Play rejects as a duplicate — push a new commit instead.** `versionName` is still edited by hand in `app/build.gradle.kts`.
- **Google's API cannot do an app's first upload.** One build has to go up through the Play Console website before CI can publish anything; until then the upload step fails with a "package not found" error.
- **Builds roll out to internal testers automatically; production is always manual.** Internal testing reaches only the testers listed in Play Console, with no Google review. (It was briefly draft-only on 2026-10-09 and changed back the same day — the owner meant that the *production* push should be manual.) One exception the lane handles by itself: until the app has been through Google's first review it is a "draft app" and the API rejects anything but a draft release ("Only releases with status draft may be created on draft app"), so the lane retries as a draft and says so in the log; that build then needs **Start rollout** in Play Console → Testing → Internal testing. The fallback has not been exercised against Google yet.
- Release builds are shrunk with R8 (~6.5 MB APK against ~85 MB for debug). Nothing app-specific is in `proguard-rules.pro`; if a library starts failing only in release builds, that is where the fix goes.

## Known gaps / to do

1. **Open-source licence notices (not done — required before any public release).** The app bundles third-party code whose licences (Apache 2.0, MIT, SIL OFL) require their notices to ship with it: Kotlin, Jetpack/AndroidX and Compose, CameraX, supabase-kt, Ktor, kotlinx.serialization and coroutines, OkHttp, and the Inter Display font. It needs a "Licences" screen in Settings; Google's `oss-licenses` Gradle plugin can generate the list. Only the Inter licence text is in the repo so far (`licenses/`), and it is not yet shown in-app. **The iOS app has the same gap** (supabase-swift, c2pa-swift, the New Relic agent) — do both together.
2. **Play Integrity is switched on in the app and deployed in the backend (2026-10-09), but has not yet been seen to pass end to end.** The backend needs the `PLAY_INTEGRITY_SERVICE_ACCOUNT` secret set in Supabase, and the first real request is also the first time the decode call runs against Google.
3. **Sign-in button artwork.** The button is plain text; Google's branding guidelines call for their logo on it.
4. **Uploads only run while the app is alive.** They resume on next launch, as on iOS, but Android can do better with WorkManager.
5. **CI is written but has never uploaded a build** — see "Shipping". It needs the five secrets, and Google requires the app's first build to be uploaded by hand.
6. Everything under "Not ported yet".
