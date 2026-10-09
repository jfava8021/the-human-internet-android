package com.thehumaninternet.app.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.thehumaninternet.app.auth.GoogleAuthService
import com.thehumaninternet.app.designsystem.Spinner
import com.thehumaninternet.app.designsystem.Theme
import com.thehumaninternet.app.logging.Log
import com.thehumaninternet.app.models.HumanUser
import com.thehumaninternet.app.models.LocalAppState
import com.thehumaninternet.app.models.OnboardingStep
import com.thehumaninternet.app.models.VerificationStatus
import com.thehumaninternet.app.supabase.UserProfileRepository
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The only place that knows the onboarding sequence. The individual screens
 * take callbacks and know nothing about navigation or Supabase.
 *
 * Which screen shows is read straight off `users.onboarding_step`, which is
 * saved after every step — so force-quitting mid-onboarding resumes at the
 * right screen on relaunch.
 */
@Composable
fun OnboardingFlow() {
    val appState = LocalAppState.current
    val scope = rememberCoroutineScope()
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var verifyAttempt by remember { mutableIntStateOf(0) }

    /**
     * Saves before moving on — if the save fails (e.g. a username conflict
     * picked up during the profile step), nothing changes and the user stays
     * where they are.
     */
    fun advance(to: OnboardingStep, updated: HumanUser = appState.user) {
        if (isBusy) return
        isBusy = true
        val next = updated.copy(onboardingStep = to)
        scope.launch {
            try {
                UserProfileRepository.upsert(next)
                appState.user = next
                if (to == OnboardingStep.COMPLETED) appState.isOnboarded = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage =
                    if (UserProfileRepository.isUsernameConflict(error)) "That username is already taken."
                    else "Couldn't save — please try again."
                Log.onboarding.error("Saving onboarding step failed", error)
            } finally {
                isBusy = false
            }
        }
    }

    val user = appState.user
    when {
        user.id == null -> WelcomeScreen(isBusy = isBusy, onSignIn = {
            isBusy = true
            scope.launch {
                try {
                    GoogleAuthService.signIn()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    errorMessage = error.localizedMessage ?: "Couldn't start sign-in."
                    Log.auth.error("Starting sign-in failed", error)
                } finally {
                    isBusy = false
                }
            }
        })

        user.onboardingStep == OnboardingStep.PROFILE -> ProfileSetupScreen(
            initialUsername = user.username,
            initialIconIndex = user.profileIconIndex,
            isBusy = isBusy,
            onNext = { username, iconIndex ->
                advance(OnboardingStep.VERIFY, user.copy(username = username, profileIconIndex = iconIndex))
            },
        )

        // Identity verification (Stripe Identity) isn't in this build yet, so
        // the step is passed through on exactly the terms a manual Skip takes
        // on iOS: `unverified` stated outright, then on to the next step. The
        // database reverts any other client-side change to that column, so
        // this can only ever write `unverified` over `unverified`.
        user.onboardingStep == OnboardingStep.VERIFY -> {
            Box(Modifier.fillMaxSize().background(Theme.background), contentAlignment = Alignment.Center) {
                Spinner()
            }
            LaunchedEffect(verifyAttempt) {
                advance(
                    OnboardingStep.WELCOME_HUMAN,
                    user.copy(verificationStatus = VerificationStatus.UNVERIFIED),
                )
            }
        }

        else -> WelcomeHumanScreen(onTakePhoto = { advance(OnboardingStep.COMPLETED) })
    }

    errorMessage?.let { message ->
        val dismiss: () -> Unit = {
            errorMessage = null
            // The verify step has no button to press again, so dismissing the
            // error is what retries it.
            verifyAttempt++
        }
        AlertDialog(
            onDismissRequest = dismiss,
            confirmButton = { TextButton(onClick = dismiss) { Text("OK") } },
            title = { Text("Something went wrong", style = Theme.font(18, FontWeight.SemiBold)) },
            text = { Text(message, style = Theme.font(14)) },
            containerColor = Theme.background,
        )
    }
}
