package com.thehumaninternet.app.supabase

import com.thehumaninternet.app.models.HumanUser
import com.thehumaninternet.app.models.OnboardingStep
import com.thehumaninternet.app.models.VerificationStatus
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object UserProfileRepository {
    suspend fun fetch(userID: String): HumanUser? =
        supabase.from("users")
            .select { filter { eq("id", userID) } }
            .decodeList<HumanUser>()
            .firstOrNull()

    suspend fun upsert(user: HumanUser) {
        supabase.from("users").upsert(user)
    }

    /**
     * Saves moving onboarding on to [to], writing only the columns the step
     * being left is about, never the whole row. A trip through onboarding
     * that shouldn't have happened therefore can't reset privacy, the social
     * handles, or anything else it doesn't own (BUG-1).
     */
    suspend fun saveOnboardingStep(user: HumanUser, to: OnboardingStep) {
        val userID = requireNotNull(user.id) { "Saving onboarding needs a signed-in user" }
        supabase.from("users").update(onboardingStepColumns(user, to)) {
            filter { eq("id", userID) }
        }
    }

    /**
     * Fetches the profile for `userID`, creating a fresh row at the profile
     * onboarding step if this identity has never signed in before.
     */
    suspend fun resolveOrCreate(userID: String): HumanUser {
        fetch(userID)?.let { return it }
        val fresh = HumanUser(id = userID, onboardingStep = OnboardingStep.PROFILE)
        upsert(fresh)
        return fresh
    }

    /**
     * `users_username_unique_idx` (case-insensitive, partial on non-empty
     * username) raises Postgres error 23505 on conflict.
     */
    fun isUsernameConflict(error: Throwable): Boolean =
        (error as? PostgrestRestException)?.code == "23505"

    /**
     * Reads back just `verification_status` — the only column on a user's own
     * row that changes without the app doing anything (`stripe-identity-webhook`
     * writes it with the service role). Narrow on purpose: assigning a whole
     * fetched row over `AppState.user` would stomp anything half-edited.
     */
    suspend fun fetchVerificationStatus(userID: String): VerificationStatus {
        @Serializable
        data class Row(@SerialName("verification_status") val verificationStatus: VerificationStatus)
        return selectColumn<Row>("verification_status", userID)?.verificationStatus
            ?: VerificationStatus.UNVERIFIED
    }

    /**
     * The Photo Fingerprint switch (`users.watermark_enabled`). Not a field on
     * [HumanUser], so a project without the column fails soft here instead of
     * breaking sign-in and every profile save.
     */
    suspend fun fetchWatermarkEnabled(userID: String): Boolean {
        @Serializable
        data class Row(@SerialName("watermark_enabled") val watermarkEnabled: Boolean)
        return selectColumn<Row>("watermark_enabled", userID)?.watermarkEnabled ?: true
    }

    /**
     * Deliberately not a field on [HumanUser]: that class round-trips through
     * [upsert], and `is_admin` must never be settable by writing the client's
     * own copy of its row back. A separate, narrow, read-only query instead.
     */
    suspend fun fetchIsAdmin(userID: String): Boolean {
        @Serializable
        data class Row(@SerialName("is_admin") val isAdmin: Boolean)
        return selectColumn<Row>("is_admin", userID)?.isAdmin ?: false
    }

    private suspend inline fun <reified Row : Any> selectColumn(column: String, userID: String): Row? =
        supabase.from("users")
            .select(Columns.list(column)) { filter { eq("id", userID) } }
            .decodeList<Row>()
            .firstOrNull()
}

/** The columns [UserProfileRepository.saveOnboardingStep] writes when moving on to [to]. */
internal fun onboardingStepColumns(user: HumanUser, to: OnboardingStep): JsonObject = buildJsonObject {
    put("onboarding_step", to.rawValue)
    when (to) {
        // Leaving profile setup.
        OnboardingStep.VERIFY -> {
            put("username", user.username)
            put("profile_icon_index", user.profileIconIndex)
        }
        // Leaving identity verification (passed through as a Skip in this build).
        OnboardingStep.WELCOME_HUMAN -> put("verification_status", user.verificationStatus.rawValue)
        OnboardingStep.PROFILE, OnboardingStep.COMPLETED -> Unit
    }
}
