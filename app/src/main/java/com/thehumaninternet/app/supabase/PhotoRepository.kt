package com.thehumaninternet.app.supabase

import com.thehumaninternet.app.models.VerifiedPhoto
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.time.Instant

object PhotoRepository {
    private const val BUCKET = "photos"

    /**
     * `photoID`/`capturedAt`/`shortCode` are supplied by the caller so
     * `PhotoUploadQueue` can retry an interrupted upload with the exact same
     * identity every time. Both the storage upload and the row write are
     * upserts, so calling this twice for the same photo is harmless.
     *
     * `imageData` is always the photo as it will be shared — never the
     * original capture. `hasOriginal` records that `sign-photo`'s capture
     * pipeline stored the signed capture in `photo-originals`.
     */
    suspend fun upload(
        imageData: ByteArray,
        photoID: String,
        userID: String,
        capturedAt: Instant,
        shortCode: String,
        hasOriginal: Boolean,
    ): VerifiedPhoto {
        val photo = VerifiedPhoto.create(photoID, userID, capturedAt, shortCode).let {
            if (hasOriginal) it.copy(originalStoragePath = VerifiedPhoto.originalStoragePath(userID, photoID)) else it
        }

        // Uploaded to the path the photo itself carries, so the object and the
        // row can't disagree about where the bytes live.
        supabase.storage.from(BUCKET).upload(photo.storagePath, imageData) {
            upsert = true
            contentType = ContentType.Image.JPEG
        }
        supabase.from("photos").upsert(photo) { onConflict = "id" }
        return photo
    }

    suspend fun fetchAll(userID: String): List<VerifiedPhoto> =
        supabase.from("photos")
            .select {
                filter { eq("user_id", userID) }
                order("captured_at", Order.DESCENDING)
            }
            .decodeList()

    suspend fun downloadImage(path: String): ByteArray =
        supabase.storage.from(BUCKET).downloadAuthenticated(path)

    /**
     * Deletes the database row first — that's what the public verification
     * page reads, so it's what actually kills the link. The Storage cleanup
     * after it is best-effort: a failure there leaves an orphaned, unreachable
     * file rather than a live link pointing at nothing. Don't reverse the order.
     *
     * The signed capture in `photo-originals` goes too, at a derived path: a
     * photo deleted mid-upload can already have one with no row pointing at it.
     */
    suspend fun delete(photo: VerifiedPhoto) = delete(listOf(photo))

    suspend fun delete(photos: List<VerifiedPhoto>) {
        if (photos.isEmpty()) return
        supabase.from("photos").delete { filter { isIn("id", photos.map { it.id }) } }
        runCatching { supabase.storage.from(BUCKET).delete(photos.map { it.storagePath }) }
        runCatching {
            supabase.storage.from(VerifiedPhoto.ORIGINALS_BUCKET)
                .delete(photos.map { VerifiedPhoto.originalStoragePath(it.userID, it.id) })
        }
    }
}
