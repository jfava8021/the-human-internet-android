package com.thehumaninternet.app.auth

import com.thehumaninternet.app.supabase.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google

/**
 * Sign in with Google — the only sign-in method on Android. (The iOS app's
 * only method is Sign in with Apple.)
 *
 * Runs Supabase's OAuth flow in the browser: Google authenticates the user,
 * Supabase creates or signs in the account, and the browser returns to
 * `thehumaninternet://login-callback`, which `MainActivity` hands back to
 * supabase-kt. Nothing Google-specific ships in the app — the client id and
 * secret live in the Supabase dashboard (Authentication > Providers > Google).
 *
 * **An Apple sign-in and a Google sign-in are the same account only if
 * Supabase links them**, which it does when both report the same verified
 * email. Someone who used "Hide My Email" with Apple gets a second, separate
 * account here.
 */
object GoogleAuthService {
    /**
     * Opens the browser and returns. The session itself arrives later, on
     * `supabase.auth.sessionStatus`.
     */
    suspend fun signIn() {
        supabase.auth.signInWith(Google)
    }
}
