package com.thehumaninternet.app.uploads

import android.content.Context
import androidx.annotation.MainThread
import com.thehumaninternet.app.applicationScope
import com.thehumaninternet.app.designsystem.PhotoWatermarker
import com.thehumaninternet.app.logging.Log
import com.thehumaninternet.app.models.AppState
import com.thehumaninternet.app.models.VerifiedPhoto
import com.thehumaninternet.app.supabase.PhotoRepository
import com.thehumaninternet.app.verification.RemotePhotoSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Persists a raw captured photo to disk *before* returning, so it survives a
 * force-quit, then drives watermarking + signing (if not already done) and the
 * Supabase upload in the background — the capture flow never blocks on any of
 * that.
 *
 * Watermarking + signing takes one of two paths, chosen per photo when it's
 * processed:
 * - **capture pipeline** (`server_side_watermark` on): the raw capture goes to
 *   `sign-photo`, which signs it, burns the mark into that signed capture
 *   server-side and signs the result with the capture as its C2PA parent
 *   ingredient — keeping the signed capture in `photo-originals` — and returns
 *   the watermarked photo;
 * - **on-device watermark** (flag off): [PhotoWatermarker] burns the mark in
 *   here and `sign-photo` signs those bytes as-is.
 *
 * Unless the user has turned Photo Fingerprint off: then neither path runs and
 * the raw capture is signed as-is.
 *
 * The on-disk manifest is the source of truth for "what's still pending";
 * `AppState.photos` / `processingPhotoIDs` / `failedPhotoIDs` are only an
 * in-memory reflection of it for the UI. `AppState.hydrate` calls
 * [resumePendingUploads] on every launch and sign-in.
 *
 * The manifest is only ever touched on the main thread, which is what keeps
 * its read-modify-write cycles from interleaving.
 */
object PhotoUploadQueue {
    @Serializable
    private data class PendingUpload(
        val id: String,
        val userID: String,
        val capturedAtMillis: Long,
        val shortCode: String,
        val isSigned: Boolean,
        /**
         * Set once the capture pipeline has stored this photo's signed capture
         * in `photo-originals`, so the upload — possibly after a relaunch —
         * records `original_storage_path` on the row.
         */
        val hasOriginal: Boolean,
    ) {
        val capturedAt: Instant get() = Instant.ofEpochMilli(capturedAtMillis)
    }

    /**
     * A foreign entry (belonging to a user who isn't the one hydrating) this
     * old is assumed abandoned by a session that ended before
     * [pruneOnSignOut] ran, and gets swept.
     */
    private val staleForeignEntryMaxAge = Duration.ofDays(7)

    /** Even a user's own permanently-failing upload must not sit on disk forever. */
    private val maxPendingAge = Duration.ofDays(30)
    private const val MAX_PENDING_COUNT = 200

    /**
     * How many photos may run the memory-heavy watermark + sign stage at once.
     * Each holds a full-resolution bitmap, and the shutter doesn't serialize
     * captures, so an unbounded burst of taps could run the app out of memory.
     */
    private val processingSlots = Semaphore(2)

    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var appContext: Context
    private val directory: File by lazy {
        File(appContext.filesDir, "PendingUploads").apply { mkdirs() }
    }
    private val manifestFile: File get() = File(directory, "manifest.json")

    /** Called once from `Application.onCreate`. */
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Writes the raw (not yet watermarked or signed) capture to disk and
     * appends it to the manifest before returning, then kicks off processing
     * and the upload in the background. Returns an optimistic photo the caller
     * can show immediately.
     */
    @MainThread
    suspend fun enqueue(imageData: ByteArray, userID: String, appState: AppState): VerifiedPhoto {
        val photoID = UUID.randomUUID().toString()
        // Whole milliseconds, so the manifest round-trips the exact value.
        val capturedAt = Instant.ofEpochMilli(System.currentTimeMillis())
        val shortCode = VerifiedPhoto.generateShortCode()

        withContext(Dispatchers.IO) { writeAtomically(imageFile(photoID), imageData) }
        saveManifest(
            loadManifest() + PendingUpload(photoID, userID, capturedAt.toEpochMilli(), shortCode, isSigned = false, hasOriginal = false)
        )
        if (appState.isWatermarkEnabled) {
            appState.provisionalPhotoIDs += photoID
        }

        drive(photoID, userID, capturedAt, shortCode, appState)

        return VerifiedPhoto.create(photoID, userID, capturedAt, shortCode)
    }

    /**
     * Resumes any uploads left pending by a prior force-quit or crash. Pending
     * photos are seeded into `appState.photos` here too, since
     * `PhotoRepository.fetchAll` only returns rows that already made it to the
     * database.
     */
    @MainThread
    fun resumePendingUploads(userID: String, appState: AppState) {
        sweep(currentUserID = userID)
        for (item in loadManifest().filter { it.userID == userID }) {
            if (appState.photos.none { it.id == item.id }) {
                appState.photos += VerifiedPhoto.create(item.id, item.userID, item.capturedAt, item.shortCode)
            }
            if (!item.isSigned && appState.isWatermarkEnabled) {
                appState.provisionalPhotoIDs += item.id
            }
            drive(item.id, item.userID, item.capturedAt, item.shortCode, appState)
        }
        appState.photos = appState.photos.sortedByDescending { it.capturedAt }
    }

    /** Manually re-attempts a specific upload that previously failed. */
    @MainThread
    fun retry(photoID: String, appState: AppState) {
        val item = loadManifest().firstOrNull { it.id == photoID } ?: return
        drive(item.id, item.userID, item.capturedAt, item.shortCode, appState)
    }

    /**
     * The on-disk copy of a photo that's still pending upload, or null once
     * the upload has completed and the local copy was cleaned up.
     * `RemotePhotoImage` checks this first so a just-captured photo shows
     * instantly from disk.
     */
    suspend fun localImageData(photoID: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { imageFile(photoID).readBytes() }.getOrNull()
    }

    /**
     * Clears any local trace of a photo, whatever stage its upload was at.
     * Used when deleting a photo so a still-uploading or failed one can't
     * resurface later through a resumed upload.
     */
    @MainThread
    fun cancelPendingUpload(photoID: String, appState: AppState) {
        removeFromManifest(photoID)
        imageFile(photoID).delete()
        appState.processingPhotoIDs -= photoID
        appState.failedPhotoIDs -= photoID
        appState.provisionalPhotoIDs -= photoID
    }

    /**
     * Deletes every pending file and manifest entry owned by `userID`, so a
     * signed-out session leaves no un-uploaded photo behind for the next
     * person on a shared device.
     */
    @MainThread
    fun pruneOnSignOut(userID: String) {
        val (owned, others) = loadManifest().partition { it.userID == userID }
        owned.forEach { imageFile(it.id).delete() }
        saveManifest(others)
    }

    /** Enforces the staleness and count limits above. */
    private fun sweep(currentUserID: String) {
        val now = Instant.now()
        var (removed, kept) = loadManifest().partition { item ->
            val age = Duration.between(item.capturedAt, now)
            age > if (item.userID == currentUserID) maxPendingAge else staleForeignEntryMaxAge
        }

        if (kept.size > MAX_PENDING_COUNT) {
            val newestFirst = kept.sortedByDescending { it.capturedAtMillis }
            removed = removed + newestFirst.drop(MAX_PENDING_COUNT)
            kept = newestFirst.take(MAX_PENDING_COUNT)
        }

        if (removed.isEmpty()) return
        removed.forEach { imageFile(it.id).delete() }
        saveManifest(kept)
    }

    private fun drive(photoID: String, userID: String, capturedAt: Instant, shortCode: String, appState: AppState) {
        // Guards against a resume and a manual retry racing to process the
        // same photo — this only clears once signing *and* upload both finish
        // (or either fails), so it covers both stages.
        if (photoID in appState.processingPhotoIDs) return
        appState.processingPhotoIDs += photoID
        appState.failedPhotoIDs -= photoID

        applicationScope.launch {
            try {
                var imageData = withContext(Dispatchers.IO) { imageFile(photoID).readBytes() }

                // A missing manifest entry (e.g. raced with cancelPendingUpload)
                // shouldn't retrigger watermarking/signing.
                val isSigned = loadManifest().firstOrNull { it.id == photoID }?.isSigned ?: true
                if (!isSigned) {
                    processingSlots.withPermit {
                        // Decided per photo at processing time, so a resumed
                        // upload follows whatever the switches say now. Both
                        // marking paths put the mark in the pixels *before*
                        // the final signature, so the C2PA hash binding covers
                        // the exact bytes that get uploaded and shared.
                        var hasOriginal = false
                        val processedData = when {
                            // Photo Fingerprint off: the raw capture is signed
                            // exactly as shot, with nothing burned in.
                            !appState.isWatermarkEnabled -> RemotePhotoSigner.sign(imageData)

                            appState.isServerSideWatermarkEnabled -> {
                                hasOriginal = true
                                RemotePhotoSigner.signCapture(imageData, photoID)
                            }

                            else -> RemotePhotoSigner.sign(PhotoWatermarker.watermark(appContext, imageData))
                        }
                        // The final bytes are on disk before the provisional
                        // flag clears: a view that reloads on that change must
                        // find them there.
                        withContext(Dispatchers.IO) { writeAtomically(imageFile(photoID), processedData) }
                        markSigned(photoID, hasOriginal)
                        appState.provisionalPhotoIDs -= photoID
                        imageData = processedData
                    }
                }

                val hasOriginal = loadManifest().firstOrNull { it.id == photoID }?.hasOriginal ?: false
                val photo = PhotoRepository.upload(imageData, photoID, userID, capturedAt, shortCode, hasOriginal)
                removeFromManifest(photoID)
                imageFile(photoID).delete()
                appState.processingPhotoIDs -= photoID
                appState.photos = appState.photos.map { if (it.id == photoID) photo else it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                appState.processingPhotoIDs -= photoID
                appState.failedPhotoIDs += photoID
                Log.uploads.error("Upload failed for $photoID", error)
            }
        }
    }

    private fun markSigned(photoID: String, hasOriginal: Boolean) {
        saveManifest(
            loadManifest().map { if (it.id == photoID) it.copy(isSigned = true, hasOriginal = hasOriginal) else it }
        )
    }

    private fun imageFile(photoID: String) = File(directory, "${photoID.lowercase()}.jpg")

    private fun loadManifest(): List<PendingUpload> =
        runCatching { json.decodeFromString<List<PendingUpload>>(manifestFile.readText()) }.getOrDefault(emptyList())

    private fun saveManifest(manifest: List<PendingUpload>) {
        runCatching { writeAtomically(manifestFile, json.encodeToString(manifest).toByteArray()) }
    }

    private fun removeFromManifest(photoID: String) {
        saveManifest(loadManifest().filterNot { it.id == photoID })
    }

    /** Write-then-rename, so a crash mid-write can't leave a truncated file behind. */
    private fun writeAtomically(file: File, data: ByteArray) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(data)
        if (!temp.renameTo(file)) {
            temp.delete()
            throw java.io.IOException("Couldn't write ${file.name}")
        }
    }
}
