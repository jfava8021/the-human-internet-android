package com.thehumaninternet.app.supabase

import com.thehumaninternet.app.logging.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

/**
 * Remote, server-backed flags (`public.feature_flags`), shared with the iOS
 * app. Only the keys this build actually reads are listed; the rest are
 * documented in the iOS repo's `CLAUDE.md`.
 */
object FeatureFlagKey {
    /**
     * Whether the brand mark is burned in by the signing Lambda rather than on
     * device. On ⇒ `PhotoUploadQueue` sends the raw capture through
     * `sign-photo`'s capture pipeline. Off ⇒ watermark on device, then sign.
     */
    const val SERVER_SIDE_WATERMARK = "server_side_watermark"

    // Not read by this build yet; listed so the developer menu can show them.
    const val STRIPE_IDENTITY_VERIFICATION = "stripe_identity_verification"
    const val CUSTOM_VERIFICATION_PAGES = "custom_verification_pages"
    const val REQUIRE_APP_ATTEST = "require_app_attest"
    const val NEUE_FONT = "neue_font"

    /**
     * What a flag falls back to when the server has nothing this build can
     * use. Kept as a per-flag switch because the safe direction belongs to
     * what a flag guards, not to flags in general.
     */
    fun fallbackAudience(key: String): FeatureFlagAudience = when (key) {
        // The path that works against any backend.
        SERVER_SIDE_WATERMARK -> FeatureFlagAudience.OFF
        else -> FeatureFlagAudience.OFF
    }
}

/**
 * Who a flag is switched on for. Mirrors the `feature_flags_audience_check`
 * constraint on `public.feature_flags`.
 */
enum class FeatureFlagAudience(val rawValue: String, val label: String) {
    /** On for everyone. */
    ALL("all", "All"),

    /** On only for users whose `users.is_admin` is true. */
    ADMIN("admin", "Admin"),

    /** Off for everyone. */
    OFF("off", "Off");

    fun includes(isAdmin: Boolean): Boolean = when (this) {
        ALL -> true
        ADMIN -> isAdmin
        OFF -> false
    }

    companion object {
        fun fromRawValue(rawValue: String): FeatureFlagAudience? = entries.firstOrNull { it.rawValue == rawValue }
    }
}

object FeatureFlagRepository {
    /**
     * `audience` is decoded as a plain string and mapped afterwards, so an
     * audience this build doesn't recognise drops out of the map and the
     * per-flag fallback applies — and can't throw inside `AppState.hydrate`,
     * where a decoding error would take sign-in down with it.
     */
    @Serializable
    private data class Row(val key: String, val audience: String)

    suspend fun fetchAll(): Map<String, FeatureFlagAudience> {
        val rows = supabase.from("feature_flags")
            .select(Columns.list("key", "audience"))
            .decodeList<Row>()

        return buildMap {
            for (row in rows) {
                val audience = FeatureFlagAudience.fromRawValue(row.audience)
                if (audience == null) {
                    Log.settings.error("Unrecognised audience '${row.audience}' on feature flag ${row.key} — using this build's fallback")
                    continue
                }
                put(row.key, audience)
            }
        }
    }
}
