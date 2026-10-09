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
import com.thehumaninternet.app.logging.Log
import com.thehumaninternet.app.models.AppState
import com.thehumaninternet.app.models.HumanUser
import com.thehumaninternet.app.models.LocalAppState
import com.thehumaninternet.app.onboarding.OnboardingFlow
import com.thehumaninternet.app.supabase.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun RootScreen() {
    val appState = LocalAppState.current
    val scope = rememberCoroutineScope()
    var isRestoringSession by remember { mutableStateOf(true) }

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
                        resolveProfile(appState, userID)
                    }
                    isRestoringSession = false
                }

                is SessionStatus.NotAuthenticated -> {
                    // An explicit sign-out has already reset everything, in
                    // which case this is a no-op.
                    if (appState.user.id != null) appState.handleSessionInvalidated()
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

        appState.isOnboarded -> MainTabScreen()
        else -> OnboardingFlow()
    }
}

/**
 * A session only proves the identity is authenticated — it doesn't say whether
 * onboarding was ever finished. Hydrating their `users` row (and photos) is
 * what makes `isOnboarded` reflect reality.
 */
private suspend fun resolveProfile(appState: AppState, userID: String) {
    try {
        appState.hydrate(userID)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.auth.error("Hydrate failed for $userID", error)
        appState.user = HumanUser(id = userID)
        appState.isOnboarded = false
    }
}
