package com.thehumaninternet.app.devmenu

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

/**
 * The developer tools' per-device switches. Per-device on purpose, so one
 * admin's testing can't strip signatures from another admin's uploads. Never
 * read these to decide behaviour; go through `AppState`, which ANDs in
 * `isAdmin`.
 */
object DeveloperPreferences {
    private const val FILE_NAME = "developer_tools"
    private const val SKIP_C2PA_SIGNING = "skipC2PASigning"

    private var preferences: SharedPreferences? = null
    private val skipC2PASigningState = mutableStateOf(false)

    /** Setting it also saves it to this phone. */
    var skipC2PASigning: Boolean
        get() = skipC2PASigningState.value
        set(isOn) {
            skipC2PASigningState.value = isOn
            preferences?.edit()?.putBoolean(SKIP_C2PA_SIGNING, isOn)?.apply()
        }

    fun initialize(context: Context) {
        val stored = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        preferences = stored
        // Straight to the state: loading the saved value shouldn't write it back.
        skipC2PASigningState.value = stored.getBoolean(SKIP_C2PA_SIGNING, false)
    }
}
