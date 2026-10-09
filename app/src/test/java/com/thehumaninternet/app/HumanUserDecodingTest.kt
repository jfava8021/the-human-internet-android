package com.thehumaninternet.app

import com.thehumaninternet.app.models.HumanUser
import com.thehumaninternet.app.models.OnboardingStep
import com.thehumaninternet.app.models.PrivacyLevel
import com.thehumaninternet.app.models.VerificationStatus
import com.thehumaninternet.app.models.VerifiedPhoto
import com.thehumaninternet.app.models.databaseJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

/**
 * The user row is decoded immediately after sign-in, so anything that throws
 * here locks people out of the app. These pin the enums against the CHECK
 * constraints on `public.users` and the fallbacks for values this build
 * doesn't know.
 */
class HumanUserDecodingTest {
    private fun row(
        privacy: String = "Public",
        status: String = "unverified",
        step: String = "completed",
        extra: String = "",
    ) = """
        {"id":"a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d","username":"marina","profile_icon_index":2,
         "privacy":"$privacy","verification_status":"$status","onboarding_step":"$step",
         "show_identity":true,"social_links":[{"platform":"instagram","handle":"marina"}],
         "phone_number":"","is_admin":false,"created_at":"2026-08-06T12:00:00+00:00"$extra}
    """.trimIndent()

    private fun decode(json: String) = databaseJson.decodeFromString<HumanUser>(json)

    @Test
    fun everyValueTheDatabasePermitsDecodes() {
        // users_privacy_check
        assertEquals(PrivacyLevel.PUBLIC, decode(row(privacy = "Public")).privacy)
        assertEquals(PrivacyLevel.HUMANS_ONLY, decode(row(privacy = "Humans Only")).privacy)
        // users_verification_status_check
        assertEquals(VerificationStatus.UNVERIFIED, decode(row(status = "unverified")).verificationStatus)
        assertEquals(VerificationStatus.IN_PROGRESS, decode(row(status = "in_progress")).verificationStatus)
        assertEquals(VerificationStatus.VERIFIED, decode(row(status = "verified")).verificationStatus)
        assertEquals(VerificationStatus.FAILED, decode(row(status = "failed")).verificationStatus)
        // users_onboarding_step_check
        assertEquals(OnboardingStep.PROFILE, decode(row(step = "profile")).onboardingStep)
        assertEquals(OnboardingStep.VERIFY, decode(row(step = "verify")).onboardingStep)
        assertEquals(OnboardingStep.WELCOME_HUMAN, decode(row(step = "welcome_human")).onboardingStep)
        assertEquals(OnboardingStep.COMPLETED, decode(row(step = "completed")).onboardingStep)
    }

    @Test
    fun unknownValuesFallBackToClaimingLessNeverMore() {
        val user = decode(row(privacy = "Friends", status = "super_verified", step = "tutorial"))
        assertEquals(PrivacyLevel.HUMANS_ONLY, user.privacy)
        assertEquals(VerificationStatus.UNVERIFIED, user.verificationStatus)
        assertEquals(OnboardingStep.PROFILE, user.onboardingStep)
    }

    @Test
    fun aColumnThisBuildHasNeverHeardOfIsIgnored() {
        assertEquals("marina", decode(row(extra = ""","some_future_column":42""")).username)
    }

    @Test
    fun savingWritesTheWholeRowBackIncludingDefaultsAndUntouchedSocialLinks() {
        val original = databaseJson.parseToJsonElement(row()).jsonObject
        val saved = databaseJson.encodeToJsonElement(HumanUser.serializer(), decode(row()).copy(username = "renamed")).jsonObject

        assertEquals(
            setOf("id", "username", "profile_icon_index", "privacy", "verification_status", "onboarding_step", "show_identity", "social_links"),
            saved.keys,
        )
        // Handles set up on iOS must survive a save from this app untouched.
        assertEquals(original["social_links"], saved["social_links"])
        assertEquals(original["show_identity"], saved["show_identity"])

        // A brand-new row still states every default outright.
        val fresh = databaseJson.encodeToJsonElement(HumanUser.serializer(), HumanUser(id = "x")).jsonObject
        assertEquals(saved.keys, fresh.keys)
    }

    @Test
    fun aPhotoRowRoundTripsAndOmitsAMissingOriginal() {
        val json = """
            {"id":"0b5e7c1a-2d3f-4a6b-8c9d-0e1f2a3b4c5d","user_id":"a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
             "storage_path":"a/b.jpg","captured_at":"2026-08-06T12:34:56.123456+00:00",
             "verification_deep_link":"thehumaninternet://photo/x","short_code":"Ab3dEf9h","original_storage_path":null}
        """.trimIndent()
        val photo = databaseJson.decodeFromString<VerifiedPhoto>(json)
        assertEquals(Instant.parse("2026-08-06T12:34:56.123456Z"), photo.capturedAt)

        val saved = databaseJson.encodeToJsonElement(VerifiedPhoto.serializer(), photo).jsonObject
        assertFalse("original_storage_path" in saved)
    }
}
