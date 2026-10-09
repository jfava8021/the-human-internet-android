package com.thehumaninternet.app.models

import com.thehumaninternet.app.logging.Log
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import java.security.SecureRandom
import java.time.Instant
import java.time.OffsetDateTime

/**
 * The JSON configuration every database row goes through.
 *
 * `encodeDefaults` is load-bearing: [HumanUser] is written back as a whole
 * row, exactly as on iOS, so a field left at its default must still be sent.
 * `explicitNulls = false` leaves a null `original_storage_path` out of the
 * upsert entirely, which is also what the iOS app does.
 */
val databaseJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * Shared decoding for the enums below, each of which mirrors a Postgres `text`
 * column whose permitted values are pinned by a CHECK constraint in the
 * database rather than by anything in this app.
 *
 * A value this build doesn't recognise logs and degrades to a declared
 * fallback instead of throwing: [com.thehumaninternet.app.models.AppState.hydrate]
 * decodes the user row immediately after sign-in, so a throw there locks the
 * user out of the app. Each fallback is chosen to claim *less* about a user
 * than the server said, never more.
 */
open class DatabaseEnumSerializer<T : Enum<T>>(
    private val name: String,
    private val values: List<T>,
    private val fallback: T,
    private val rawValue: (T) -> String,
) : KSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(rawValue(value))

    override fun deserialize(decoder: Decoder): T {
        val raw = decoder.decodeString()
        return values.firstOrNull { rawValue(it) == raw } ?: fallback.also {
            Log.auth.error("Unrecognised $name value '$raw' — falling back to '${rawValue(fallback)}'")
        }
    }
}

/** Values mirror the `users_privacy_check` constraint on `public.users`. */
@Serializable(with = PrivacyLevel.Serializer::class)
enum class PrivacyLevel(val rawValue: String) {
    PUBLIC("Public"),
    HUMANS_ONLY("Humans Only");

    /** Falls back to the *more* private of the two. */
    object Serializer : DatabaseEnumSerializer<PrivacyLevel>(
        "PrivacyLevel", entries, HUMANS_ONLY, PrivacyLevel::rawValue
    )
}

/**
 * Values mirror the `users_verification_status_check` constraint on
 * `public.users`. `UNVERIFIED` is the column default and a legitimate resting
 * state, since identity verification is optional; `IN_PROGRESS` means
 * "submitted to Stripe, awaiting the result".
 */
@Serializable(with = VerificationStatus.Serializer::class)
enum class VerificationStatus(val rawValue: String) {
    UNVERIFIED("unverified"),
    IN_PROGRESS("in_progress"),
    VERIFIED("verified"),
    FAILED("failed");

    /** A status this build can't read must never be mistaken for a verified human. */
    object Serializer : DatabaseEnumSerializer<VerificationStatus>(
        "VerificationStatus", entries, UNVERIFIED, VerificationStatus::rawValue
    )
}

/** Values mirror the `users_onboarding_step_check` constraint on `public.users`. */
@Serializable(with = OnboardingStep.Serializer::class)
enum class OnboardingStep(val rawValue: String) {
    PROFILE("profile"),
    VERIFY("verify"),
    WELCOME_HUMAN("welcome_human"),
    COMPLETED("completed");

    /**
     * Falls back to the *first* step: replaying onboarding is recoverable,
     * whereas reading an unknown step as completed would wave someone into
     * the app having skipped it.
     */
    object Serializer : DatabaseEnumSerializer<OnboardingStep>(
        "OnboardingStep", entries, PROFILE, OnboardingStep::rawValue
    )
}

/**
 * Maps to a row in the `users` table, minus the columns the client has no
 * business writing (`is_admin`, the Stripe-verified name, `watermark_enabled`
 * — each read separately, see `UserProfileRepository`). No SSN field exists
 * anywhere in this project, by design.
 *
 * Ids are lowercase UUID strings throughout this app, which is how both
 * Postgres and supabase-kt render them.
 */
@Serializable
data class HumanUser(
    val id: String? = null,
    val username: String = "",
    @SerialName("profile_icon_index") val profileIconIndex: Int = 0,
    val privacy: PrivacyLevel = PrivacyLevel.PUBLIC,
    @SerialName("verification_status") val verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
    @SerialName("onboarding_step") val onboardingStep: OnboardingStep = OnboardingStep.PROFILE,
    @SerialName("show_identity") val showIdentity: Boolean = false,
    /**
     * Kept as raw JSON rather than a typed list. This build has no
     * verification-page editor yet, so it never needs to look inside — but
     * every profile save writes the whole row back, and a typed list would
     * have to drop any platform it didn't recognise. Passing the value
     * through untouched means saving a username here can't erase handles
     * someone set up on iOS.
     */
    @SerialName("social_links") val socialLinks: JsonElement = JsonArray(emptyList()),
)

/**
 * Where a photo is in the capture → sign → upload pipeline, as far as the UI
 * is concerned. Derived by `AppState.uploadState(photoID)` rather than stored.
 */
enum class PhotoUploadState { IDLE, PROCESSING, FAILED }

/**
 * Maps 1:1 to a row in the `photos` table. No privacy field: signed-in app
 * users see full contents either way, and the signed-out web page joins to
 * the owner's `users.privacy`.
 */
@Serializable
data class VerifiedPhoto(
    val id: String,
    @SerialName("user_id") val userID: String,
    @SerialName("storage_path") val storagePath: String,
    @Serializable(with = InstantSerializer::class)
    @SerialName("captured_at") val capturedAt: Instant,
    /** `thehumaninternet://photo/{id}`, generated once and persisted. */
    @SerialName("verification_deep_link") val verificationDeepLink: String,
    /** 8-character code used for the short public link — see [generateShortCode]. */
    @SerialName("short_code") val shortCode: String,
    /**
     * Where this photo's signed capture lives in the private `photo-originals`
     * bucket. Null for photos watermarked on device, which have no separate
     * original.
     */
    @SerialName("original_storage_path") val originalStoragePath: String? = null,
) {
    /** Display form, without a scheme — shown in the share sheet. */
    val verificationLink: String get() = "$WEB_HOST/$shortCode"

    /** The link to actually share: tappable wherever it's pasted. */
    val verificationURL: String get() = "https://$verificationLink"

    companion object {
        /**
         * Host of the public web verification page. Must stay in step with the
         * website's `/[photoId]` route — links already shared can't be re-minted.
         */
        const val WEB_HOST = "the-human-internet.com"

        /** Custom URL scheme, shared with the iOS app. */
        const val DEEP_LINK_SCHEME = "thehumaninternet"
        const val DEEP_LINK_HOST = "photo"

        /** Private bucket `sign-photo` writes each signed capture to. */
        const val ORIGINALS_BUCKET = "photo-originals"

        private const val SHORT_CODE_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        private val random = SecureRandom()

        /**
         * The constructor every real photo goes through. `storagePath` and
         * `verificationDeepLink` are derived rather than passed in so the two
         * formats can't drift between the places a photo gets built.
         */
        fun create(id: String, userID: String, capturedAt: Instant, shortCode: String) = VerifiedPhoto(
            id = id,
            userID = userID,
            storagePath = storagePath(userID, id),
            capturedAt = capturedAt,
            verificationDeepLink = deepLink(id),
            shortCode = shortCode,
        )

        /**
         * Storage object path: `{user_id}/{photo_id}.jpg`, both lowercased to
         * match Postgres's uuid text rendering. The Storage RLS policy reads
         * ownership from this path.
         */
        fun storagePath(userID: String, photoID: String) = "${userID.lowercase()}/${photoID.lowercase()}.jpg"

        /** Same shape as [storagePath]; `sign-photo` builds it independently, so the two must stay identical. */
        fun originalStoragePath(userID: String, photoID: String) = storagePath(userID, photoID)

        fun deepLink(photoID: String) = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/${photoID.lowercase()}"

        /**
         * Base58 (Bitcoin alphabet, no 0/O/I/l), 8 characters. Must match the
         * `photos_short_code_format` CHECK constraint. Generated client-side so
         * it stays stable across retries of the same upload.
         */
        fun generateShortCode(): String = buildString {
            repeat(8) { append(SHORT_CODE_ALPHABET[random.nextInt(SHORT_CODE_ALPHABET.length)]) }
        }
    }
}

/** Reads Postgres `timestamptz` text (any offset) and writes ISO-8601 in UTC. */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = OffsetDateTime.parse(decoder.decodeString()).toInstant()
}
