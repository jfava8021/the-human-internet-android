package com.thehumaninternet.app.verification

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import com.thehumaninternet.app.logging.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Proves to `sign-photo` that a signing request came from this app, as Google
 * Play distributes it, on a genuine Android device — via Google's Play
 * Integrity API. The Android counterpart of the iOS app's `AppAttestService`.
 *
 * For every photo it asks Google Play for a token bound to SHA-256 of the
 * **exact bytes being signed**, and sends it as one header. `sign-photo` has
 * Google decode it and checks the verdict before signing anything (the
 * backend's `_shared/playIntegrity.ts`). There is no per-install key to
 * register, unlike App Attest.
 *
 * **This never blocks an upload on its own.** Anything that goes wrong here —
 * no project number configured, no Google Play on the device, Google
 * unreachable — leaves the request unattested, and `sign-photo` decides:
 * it signs and logs while the `require_app_attest` flag is off for this user,
 * and refuses once it's on.
 *
 * A debug build, or any build on the emulator, still gets a token — but one
 * Google marks as unrecognized, which the server refuses while the flag is
 * on. Only a build installed from Google Play, on a real phone, passes; there
 * is deliberately no development exemption.
 *
 * What it proves is the request's origin, not the pixels': a rooted device
 * that still passes Google's checks can hook the capture path.
 */
object PlayIntegrityService {
    /** Header name — mirrored in `sign-photo`. */
    const val TOKEN_HEADER = "X-Play-Integrity-Token"

    /**
     * The **number** (not the id) of the Google Cloud project linked to this
     * app in Play Console → App integrity — "The Human Internet Android". Not
     * a secret. Set to 0 to turn Play Integrity off, which sends every
     * request unattested.
     */
    private val cloudProjectNumber: Long = 92493927984L

    private lateinit var appContext: Context

    /** Two photos sign concurrently; they must share one provider. */
    private val providerLock = Mutex()
    private var provider: StandardIntegrityTokenProvider? = null

    /** Called once from `Application.onCreate`. */
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * What a token is bound to: SHA-256 of the request body, base64url
     * without padding. Mirrored by `expectedRequestHash` in the backend's
     * `_shared/playIntegrity.ts` — the two must produce the same string for
     * the same bytes, which `PlayIntegrityRequestHashTest` pins.
     */
    fun requestHash(body: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(body))

    /**
     * Headers attesting to `body`, or none if this request has to go
     * unattested. Never throws — see the type's doc comment.
     */
    suspend fun headers(attesting: ByteArray): Map<String, String> {
        if (cloudProjectNumber == 0L) return emptyMap()
        return try {
            mapOf(TOKEN_HEADER to token(requestHash(attesting)))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.attestation.error("Play Integrity token request failed", error)
            emptyMap()
        }
    }

    private suspend fun token(requestHash: String): String {
        val request = StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build()
        return try {
            tokenProvider().request(request).await().token()
        } catch (error: StandardIntegrityException) {
            // Providers expire; Google says to prepare a fresh one and retry.
            if (error.errorCode != StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID) throw error
            providerLock.withLock { provider = null }
            tokenProvider().request(request).await().token()
        }
    }

    /**
     * The one-off warm-up Google's standard API requires before tokens can be
     * requested. Takes a few seconds the first time, then each token is fast.
     */
    private suspend fun tokenProvider(): StandardIntegrityTokenProvider = providerLock.withLock {
        provider ?: IntegrityManagerFactory.createStandard(appContext)
            .prepareIntegrityToken(
                PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(cloudProjectNumber).build()
            )
            .await()
            .also { provider = it }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }
}
