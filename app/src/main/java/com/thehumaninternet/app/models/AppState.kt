package com.thehumaninternet.app.models

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thehumaninternet.app.designsystem.PhotoThumbnailCache
import com.thehumaninternet.app.devmenu.DeveloperPreferences
import com.thehumaninternet.app.logging.Log
import com.thehumaninternet.app.supabase.FeatureFlagAudience
import com.thehumaninternet.app.supabase.FeatureFlagKey
import com.thehumaninternet.app.supabase.FeatureFlagRepository
import com.thehumaninternet.app.supabase.PhotoRepository
import com.thehumaninternet.app.supabase.UserProfileRepository
import com.thehumaninternet.app.supabase.supabase
import com.thehumaninternet.app.uploads.PhotoUploadQueue
import io.github.jan.supabase.auth.auth
import kotlin.coroutines.cancellation.CancellationException

/**
 * The one piece of shared app state, owned by the `Application` and handed to
 * the UI through [LocalAppState]. Views read it; services and repositories do
 * the side effects. Everything here is read and written on the main thread.
 */
class AppState {
    var isOnboarded by mutableStateOf(false)
    var user by mutableStateOf(HumanUser())
    var photos by mutableStateOf(listOf<VerifiedPhoto>())

    /**
     * Never sourced from [user] — see `UserProfileRepository.fetchIsAdmin` for
     * why admin status is kept out of the model that round-trips through upsert.
     */
    var isAdmin by mutableStateOf(false)

    /**
     * Remote feature flags, keyed by `FeatureFlagKey`. A key absent here means
     * the server had nothing this build could use for it, and
     * `FeatureFlagKey.fallbackAudience` decides what that means.
     */
    var featureFlags by mutableStateOf(mapOf<String, FeatureFlagAudience>())

    /**
     * Resolves a flag against this user. `ADMIN` reads [isAdmin], which
     * [hydrate] fetches *before* the flags, so the two are never out of step.
     */
    private fun isEnabled(key: String): Boolean = audience(key).includes(isAdmin)

    /**
     * What the server holds for a flag (or its fallback), as opposed to
     * whether it's on for this user. For an `ADMIN` flag the two differ.
     */
    fun audience(key: String): FeatureFlagAudience =
        featureFlags[key] ?: FeatureFlagKey.fallbackAudience(key)

    /**
     * Whether `PhotoUploadQueue` uploads photos unsigned, with no C2PA
     * manifest. The `isAdmin` check is what stops the per-device switch
     * outliving the admin who set it: a non-admin signing in on the same
     * phone must never inherit it.
     */
    val isC2PASigningSkipped: Boolean
        get() = isAdmin && DeveloperPreferences.skipC2PASigning

    /**
     * Whether new captures go through `sign-photo`'s capture pipeline (server
     * watermarks, capture kept as the C2PA ingredient) instead of being
     * watermarked on device.
     */
    val isServerSideWatermarkEnabled: Boolean
        get() = isEnabled(FeatureFlagKey.SERVER_SIDE_WATERMARK)

    /**
     * The user's Photo Fingerprint setting (`users.watermark_enabled`):
     * whether new captures get the brand mark burned into their pixels. Read
     * at processing time, so a pending upload follows whatever this says when
     * it's actually processed. There is no switch for it in this build yet;
     * the value set on iOS is honoured.
     */
    var isWatermarkEnabled by mutableStateOf(true)

    /**
     * Ids of photos in [photos] that are still being signed and/or uploaded,
     * or that failed and are waiting on a retry. Driven by `PhotoUploadQueue`.
     */
    var processingPhotoIDs by mutableStateOf(setOf<String>())
    var failedPhotoIDs by mutableStateOf(setOf<String>())

    /**
     * Photos whose only copy is still the raw capture — nothing has burned the
     * brand mark in yet. `RemotePhotoImage` draws a matching mark over these
     * so every photo looks watermarked from the moment it's taken.
     */
    var provisionalPhotoIDs by mutableStateOf(setOf<String>())

    fun uploadState(photoID: String): PhotoUploadState = when (photoID) {
        in processingPhotoIDs -> PhotoUploadState.PROCESSING
        in failedPhotoIDs -> PhotoUploadState.FAILED
        else -> PhotoUploadState.IDLE
    }

    /**
     * The explicit, user-initiated sign-out. Prunes this user's un-uploaded
     * photos from disk *before* the network call, so the local cleanup still
     * happens if signing out of Supabase fails and the next person on a shared
     * device can't recover them.
     */
    suspend fun signOut() {
        user.id?.let { PhotoUploadQueue.pruneOnSignOut(it) }
        supabase.auth.signOut()
        handleSessionInvalidated()
    }

    /**
     * The involuntary sign-out: a session revoked elsewhere, or a token
     * refresh that failed for good. Resets the same local state as [signOut]
     * but deliberately does **not** prune pending uploads — it's the same
     * person on the same device needing to re-authenticate, and the manifest
     * resumes once they do.
     */
    fun handleSessionInvalidated() {
        PhotoThumbnailCache.removeAll()
        isOnboarded = false
        user = HumanUser()
        photos = emptyList()
        processingPhotoIDs = emptySet()
        failedPhotoIDs = emptySet()
        provisionalPhotoIDs = emptySet()
        isAdmin = false
        featureFlags = emptyMap()
        isWatermarkEnabled = true
    }

    /**
     * Pulls the current `verification_status` down onto [user], leaving the
     * rest of the row alone. Best-effort: it runs on foregrounding, where a
     * failed read should cost nothing.
     */
    suspend fun refreshVerificationStatus() {
        val userID = user.id ?: return
        try {
            val status = UserProfileRepository.fetchVerificationStatus(userID)
            if (status != user.verificationStatus && user.id == userID) {
                Log.onboarding.info("Verification status changed to ${status.rawValue}")
                user = user.copy(verificationStatus = status)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.onboarding.error("Refreshing verification status failed", error)
        }
    }

    /**
     * The single place that turns "this identity is authenticated" into
     * "here's their profile, their photos, their flags, and whether
     * onboarding is done" — and resumes any interrupted uploads. Cold launch
     * and fresh sign-in both come through here; keep it that way.
     */
    suspend fun hydrate(userID: String) {
        val profile = UserProfileRepository.resolveOrCreate(userID)
        user = profile
        isOnboarded = profile.onboardingStep == OnboardingStep.COMPLETED
        isAdmin = UserProfileRepository.fetchIsAdmin(userID)
        featureFlags = FeatureFlagRepository.fetchAll()
        // Fail-soft on purpose: a failed read must not cost the user their
        // sign-in. Falls back to the column's own default.
        isWatermarkEnabled = try {
            UserProfileRepository.fetchWatermarkEnabled(userID)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.settings.error("Reading Photo Fingerprint setting failed", error)
            true
        }
        photos = PhotoRepository.fetchAll(userID)
        PhotoUploadQueue.resumePendingUploads(userID, this)
    }
}

val LocalAppState = compositionLocalOf<AppState> { error("AppState not provided") }
