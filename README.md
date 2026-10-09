# the human internet — Android

The Android app for [the human internet](https://the-human-internet.com): photos that carry proof they were taken live by a real person.

A Kotlin + Jetpack Compose port of the iOS app, running against the same backend. See [CLAUDE.md](CLAUDE.md) for what is and isn't ported, how it differs from iOS, and the dashboard setup sign-in needs.

## Build

Open the folder in Android Studio, or:

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
