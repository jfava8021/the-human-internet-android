# The Human Internet — Android App

Native Kotlin + Jetpack Compose app for "the human internet" — photo verification proving a photo was captured live by a real human. It is a port of the iOS app and **a second front end over the same backend**: nothing server-side lives here, and nothing server-side was changed to make it work.

**Sibling repos**, all talking to the same Supabase project (`xpjkgngifffzdaikjakw`):

- `../the-human-internet-app/` — the iOS app. **Its `CLAUDE.md` is the reference for the product, the database, and every shared contract.** Read it before changing anything that touches the backend; this file only records what is specific to Android.
- `../the-human-internet/` — the Next.js website (marketing + the public verification page).
- `../the-human-internet-backend/` — the signing Lambda and the Supabase Edge Functions.

## What's ported (core flow)

- **Sign-in** → **profile setup** → (identity verification passed through, see below) → **welcome** → main app. Progress persists to `users.onboarding_step` after every step.
- **Camera tab**: CameraX capture, front/back flip, shutter flash, tap-through thumbnail of the last photo. No gallery import, by design — it would defeat the "taken live" guarantee.
- **Upload pipeline** (`uploads/PhotoUploadQueue.kt`): optimistic and durable, a faithful port of the iOS queue — on-disk manifest as the source of truth, both watermark paths, 2-at-a-time processing, resume on launch, prune on sign-out, the same expiry limits.
- **Profile tab**: photo grid, long-press multi-select and batch delete, the pending/failed banner.
- **Photo detail**: the photo, Share, delete.
- **Sharing**: Reddit and X link intents, Messages with the link pre-filled, Instagram/Facebook via clipboard + image share, and the copyable verification link.
- **Settings** (minimal): username (read-only), privacy, Log Out.

## Not ported yet

Stripe Identity verification, the in-app verification page and `thehumaninternet://photo/{id}` deep links, Preview (as a fellow human / as a lurker), username editing, the verification-page editor, Account Settings (Photo Fingerprint switch, account deletion), the developer menu and its tools, the PP Neue Montreal font flag, and New Relic monitoring.

Until Stripe Identity is ported, onboarding's verify step is passed through exactly as a manual Skip is on iOS: `verification_status` stated as `unverified`, then on to the next step.

## Differences from iOS that matter

- **Sign in with Google, not Apple** (`auth/GoogleAuthService.kt`). It runs Supabase's OAuth flow in the browser and returns to `thehumaninternet://login-callback`. An Apple sign-in and a Google sign-in are the same account only if Supabase links them by verified email — an iOS user who chose "Hide My Email" gets a second, separate account on Android.
- **Signing requests are unattested.** iOS sends an App Attest assertion with every `sign-photo` call; Android has no equivalent yet. The equivalent is Play Integrity, and it needs a verifier added to the backend as well as client code here. **Do not set the `require_app_attest` flag to `all` until that exists — it would stop every Android upload.** Until then, anyone with a session token can get arbitrary bytes signed through this path, which is the hole App Attest closed on iOS.
- **`HumanUser.socialLinks` is raw JSON**, not a typed list. Every profile save writes the whole row back (as on iOS), and this build has no editor for the handles, so it passes them through untouched rather than risk dropping one it doesn't recognise.
- **Ids are lowercase UUID strings**, not a UUID type — that's how supabase-kt and Postgres both render them.
- **The provisional watermark is drawn into the decoded bitmap**, not overlaid as a separate view. Same artwork, same `markRect`.

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
  designsystem/         <- Theme, Components, PhotoWatermarker, RemotePhotoImage
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
- The share URL shapes (`ShareIntentTest`).
- The `sign-photo` headers in `RemotePhotoSigner`.

## Building and running

Open the folder in Android Studio, or from a terminal:

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug        # build
./gradlew :app:testDebugUnitTest    # unit tests
```

The emulator has a virtual camera, so capture works there, unlike the iOS Simulator.

## Known gaps / to do

1. **Open-source licence notices (not done — required before any public release).** The app bundles third-party code whose licences (Apache 2.0, MIT, SIL OFL) require their notices to ship with it: Kotlin, Jetpack/AndroidX and Compose, CameraX, supabase-kt, Ktor, kotlinx.serialization and coroutines, OkHttp, and the Inter Display font. It needs a "Licences" screen in Settings; Google's `oss-licenses` Gradle plugin can generate the list. Only the Inter licence text is in the repo so far (`licenses/`), and it is not yet shown in-app. **The iOS app has the same gap** (supabase-swift, c2pa-swift, the New Relic agent) — do both together.
2. **Play Integrity** for signing requests — see "Differences from iOS".
3. **Sign-in button artwork.** The button is plain text; Google's branding guidelines call for their logo on it.
4. **Uploads only run while the app is alive.** They resume on next launch, as on iOS, but Android can do better with WorkManager.
5. **Release build config**: no signing config, R8/minification off (the debug APK is ~85 MB largely because of the unshrunk Material icon set), no CI.
6. Everything under "Not ported yet".
