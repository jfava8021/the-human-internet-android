package com.thehumaninternet.app

import android.app.Application
import com.thehumaninternet.app.models.AppState
import com.thehumaninternet.app.uploads.PhotoUploadQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Work that must outlive whichever screen started it — an upload in flight, a
 * capture being written to disk. Main-thread confined, like `AppState`.
 */
val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

class HumanInternetApp : Application() {
    /** Owned here rather than by the activity, so it survives the activity being recreated. */
    val appState = AppState()

    override fun onCreate() {
        super.onCreate()
        PhotoUploadQueue.initialize(this)
    }
}
