package com.thehumaninternet.app.logging

/** One logcat tag per area of the app, mirroring the iOS app's `Log` categories. */
object Log {
    val auth = Category("auth")
    val onboarding = Category("onboarding")
    val camera = Category("camera")
    val uploads = Category("uploads")
    val photos = Category("photos")
    val sharing = Category("sharing")
    val settings = Category("settings")

    class Category(name: String) {
        private val tag = "THI/$name"

        fun info(message: String) {
            android.util.Log.i(tag, message)
        }

        fun error(message: String, throwable: Throwable? = null) {
            android.util.Log.e(tag, message, throwable)
        }
    }
}
