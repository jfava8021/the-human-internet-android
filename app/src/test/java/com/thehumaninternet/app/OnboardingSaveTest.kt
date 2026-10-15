package com.thehumaninternet.app

import com.thehumaninternet.app.models.HumanUser
import com.thehumaninternet.app.models.OnboardingStep
import com.thehumaninternet.app.models.PrivacyLevel
import com.thehumaninternet.app.models.VerificationStatus
import com.thehumaninternet.app.supabase.onboardingStepColumns
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingSaveTest {
    private val user = HumanUser(
        id = "00000000-0000-0000-0000-000000000001",
        username = "jordan",
        profileIconIndex = 3,
        privacy = PrivacyLevel.HUMANS_ONLY,
    )

    @Test
    fun profileStepWritesOnlyUsernameIconAndStep() {
        assertEquals(
            setOf("onboarding_step", "username", "profile_icon_index"),
            onboardingStepColumns(user, OnboardingStep.VERIFY).keys,
        )
    }

    @Test
    fun verifyStepWritesOnlyStatusAndStep() {
        assertEquals(
            setOf("onboarding_step", "verification_status"),
            onboardingStepColumns(user.copy(verificationStatus = VerificationStatus.UNVERIFIED), OnboardingStep.WELCOME_HUMAN).keys,
        )
    }

    @Test
    fun noStepWritesPrivacyOrSocialSettings() {
        for (step in OnboardingStep.entries) {
            val keys = onboardingStepColumns(user, step).keys
            for (protected in listOf("privacy", "show_identity", "social_links", "id")) {
                assert(protected !in keys) { "$step writes $protected" }
            }
        }
    }
}
