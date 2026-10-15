package com.thehumaninternet.app.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.thehumaninternet.app.designsystem.Spinner
import com.thehumaninternet.app.designsystem.Theme
import com.thehumaninternet.app.devmenu.DeveloperMenuSheet
import com.thehumaninternet.app.devmenu.ShakeDetector
import com.thehumaninternet.app.logging.Log
import com.thehumaninternet.app.models.AppState
import com.thehumaninternet.app.models.HumanUser
import com.thehumaninternet.app.models.LocalAppState
import com.thehumaninternet.app.onboarding.OnboardingFlow
import com.thehumaninternet.app.supabase.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Waits before each attempt: at once, then twice more before giving up. */
private val PROFILE_LOAD_DELAYS_MS = listOf(0L, 2_000L, 5_000L)

@Composable
fun RootScreen() {
    val appState = LocalAppState.current
    val scope = rememberCoroutineScope()
    var isRestoringSession by remember { mutableStateOf(true) }
    var profileLoadFailed by remember { mutableStateOf(false) }

    // Supabase persists the session across launches on its own and reports it
    // here. The same stream delivers a fresh sign-in (the browser returning
    // from Google) and, later, a session that went invalid by itself — so
    // this one collector is the only caller of `hydrate`.
    LaunchedEffect(Unit) {
        supabase.auth.sessionStatus.collect { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    val userID = status.session.user?.id
                    // Also emitted on every token refresh; only a change of
                    // identity needs the profile loaded.
                    if (userID != null && userID != appState.user.id) {
                        isRestoringSession = true
                        profileLoadFailed = !loadProfile(appState, userID)
                    }
                    isRestoringSession = false
                }

                is SessionStatus.NotAuthenticated -> {
                    // An explicit sign-out has already reset everything, in
                    // which case this is a no-op.
                    if (appState.user.id != null) appState.handleSessionInvalidated()
                    profileLoadFailed = false
                    isRestoringSession = false
                }

                // Still loading from storage, or a refresh that failed and
                // will be retried: nothing to act on yet.
                else -> Unit
            }
        }
    }

    // Coming back to the foreground is the moment a verification that
    // resolved while the app was away should become visible; nothing pushes
    // the webhook's write down to a running client.
    LifecycleResumeEffect(Unit) {
        val refresh = scope.launch { appState.refreshVerificationStatus() }
        onPauseOrDispose { refresh.cancel() }
    }

    when {
        isRestoringSession -> Box(
            Modifier.fillMaxSize().background(Theme.background),
            contentAlignment = Alignment.Center,
        ) { Spinner() }

        profileLoadFailed -> ProfileLoadErrorScreen(
            onRetry = {
                val userID = appState.user.id ?: return@ProfileLoadErrorScreen
                scope.launch {
                    isRestoringSession = true
                    profileLoadFailed = !loadProfile(appState, userID)
                    isRestoringSession = false
                }
            },
            onLogOut = {
                scope.launch {
                    try {
                        appState.signOut()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.auth.error("Sign-out failed", error)
                    }
                }
            },
        )

        appState.isOnboarded -> MainTabScreen()
        else -> OnboardingFlow()
    }

    var showDeveloperMenu by remember { mutableStateOf(false) }

    // `isAdmin` only becomes true after hydrate, so this is inert before
    // sign-in, and a non-admin's phone never listens to the accelerometer.
    ShakeDetector(enabled = appState.isAdmin) { showDeveloperMenu = true }

    if (showDeveloperMenu && appState.isAdmin) {
        DeveloperMenuSheet(onDismiss = { showDeveloperMenu = false })
    }
}

/**
 * A session only proves the identity is authenticated; hydrating their `users`
 * row is what says whether onboarding was ever finished. Retries a couple of
 * times first, so a launch on a flaky connection doesn't need a tap. Returns
 * whether it worked.
 *
 * On failure the user is **never** shown onboarding (BUG-1): a blank profile
 * there would be saved over their real one. Instead [RootScreen] shows
 * [ProfileLoadErrorScreen]. The blank user below keeps only the id, so Log out
 * can still prune this user's pending uploads; nothing can save it, because the
 * error screen is all that's on screen.
 */
private suspend fun loadProfile(appState: AppState, userID: String): Boolean {
    for (delayMs in PROFILE_LOAD_DELAYS_MS) {
        delay(delayMs)
        try {
            appState.hydrate(userID)
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.auth.error("Hydrate failed for $userID", error)
        }
    }
    appState.user = HumanUser(id = userID)
    appState.isOnboarded = false
    return false
}
