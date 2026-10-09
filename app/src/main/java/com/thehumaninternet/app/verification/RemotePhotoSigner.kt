package com.thehumaninternet.app.verification

import com.thehumaninternet.app.supabase.supabase
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

sealed class RemotePhotoSignerException(message: String) : Exception(message) {
    class EmptyResponse : RemotePhotoSignerException("sign-photo returned no data")

    /**
     * `sign-photo` answered a capture-pipeline request without confirming it
     * ran that pipeline — a backend that predates it, which signs the body
     * as-is. What came back is then the raw capture, signed, with no
     * watermark, and must never be uploaded as the shared photo.
     */
    class CapturePipelineNotConfirmed : RemotePhotoSignerException("sign-photo did not confirm the capture pipeline")
}

/**
 * The only C2PA signer. The signing key never touches this device: the
 * `sign-photo` Edge Function forwards to an AWS Lambda that signs with a key
 * held in KMS. There is no on-device fallback — if `sign-photo` is down,
 * uploads fail and retry.
 *
 * Each request carries a Play Integrity token over the exact bytes sent, when
 * this device can produce one — see [PlayIntegrityService].
 */
object RemotePhotoSigner {
    /** Request header opting into the capture pipeline, echoed back by a backend that ran it. */
    const val PIPELINE_HEADER = "X-Capture-Pipeline"
    const val CAPTURE_PIPELINE = "server-watermark-v1"

    /** Names the photo so `sign-photo` can file its signed capture under the photo's own path. */
    const val PHOTO_ID_HEADER = "X-Photo-Id"

    /**
     * Raw capture in, watermarked + signed photo out. Throws rather than ever
     * returning the raw capture back signed-but-unwatermarked.
     */
    suspend fun signCapture(rawData: ByteArray, photoID: String): ByteArray {
        val response = invoke(rawData) {
            header(PIPELINE_HEADER, CAPTURE_PIPELINE)
            header(PHOTO_ID_HEADER, photoID.lowercase())
        }
        if (response.headers[PIPELINE_HEADER] != CAPTURE_PIPELINE) {
            throw RemotePhotoSignerException.CapturePipelineNotConfirmed()
        }
        return response.nonEmptyBody()
    }

    /** JPEG in, the same JPEG signed as a `digitalCapture` out. */
    suspend fun sign(imageData: ByteArray): ByteArray = invoke(imageData).nonEmptyBody()

    private suspend fun invoke(body: ByteArray, configure: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
        val attestation = PlayIntegrityService.headers(attesting = body)
        return supabase.functions.invoke("sign-photo") {
            attestation.forEach { (name, value) -> header(name, value) }
            configure()
            contentType(ContentType.Application.OctetStream)
            setBody(body)
        }
    }

    private suspend fun HttpResponse.nonEmptyBody(): ByteArray =
        body<ByteArray>().also { if (it.isEmpty()) throw RemotePhotoSignerException.EmptyResponse() }
}
