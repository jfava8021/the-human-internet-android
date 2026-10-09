package com.thehumaninternet.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.thehumaninternet.app.designsystem.HumanInternetTheme
import com.thehumaninternet.app.logging.Log
import com.thehumaninternet.app.models.LocalAppState
import com.thehumaninternet.app.root.RootScreen
import com.thehumaninternet.app.supabase.supabase
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is light-only, so the system bars always get dark icons.
        val bars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)

        // Only on a fresh launch: a recreated activity is handed the same
        // intent again, and a sign-in callback can only be redeemed once.
        if (savedInstanceState == null) handleAuthCallback(intent)

        val appState = (application as HumanInternetApp).appState
        setContent {
            HumanInternetTheme {
                CompositionLocalProvider(LocalAppState provides appState) {
                    RootScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthCallback(intent)
    }

    /**
     * Completes Sign in with Google when the browser returns to
     * `thehumaninternet://login-callback`. Success shows up on
     * `supabase.auth.sessionStatus`, which `RootScreen` is already watching.
     */
    private fun handleAuthCallback(intent: Intent) {
        supabase.handleDeeplinks(intent, onError = { error ->
            Log.auth.error("Sign-in callback failed", error)
        })
    }
}
