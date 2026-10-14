package com.thehumaninternet.app.devmenu

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

    var skipC2PASigning by mutableStateOf(false)
        private set

    fun initialize(context: Context) {
        val stored = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        preferences = stored
        skipC2PASigning = stored.getBoolean(SKIP_C2PA_SIGNING, false)
    }

    fun saveSkipC2PASigning(isOn: Boolean) {
        skipC2PASigning = isOn
        preferences?.edit()?.putBoolean(SKIP_C2PA_SIGNING, isOn)?.apply()
    }
}
