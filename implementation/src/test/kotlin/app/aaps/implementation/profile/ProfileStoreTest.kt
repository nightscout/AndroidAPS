package app.aaps.implementation.profile

import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

internal class ProfileStoreTest : TestBaseWithProfile() {

    @Test
    fun getStartDateTest() {
        assertThat(getValidProfileStore().getStartDate()).isEqualTo(0)
    }

    @Test
    fun getDefaultProfileTest() {
        assertIs<PureProfile>(getValidProfileStore().getDefaultProfile())
    }

    @Test
    fun getDefaultProfileJsonTest() {
        assertThat(getValidProfileStore().getDefaultProfileJson()?.has("carbratio")).isTrue()
        assertThat(getInvalidProfileStore2().getDefaultProfileJson()).isNull()
    }

    @Test
    fun getDefaultProfileNameTest() {
        assertThat(getValidProfileStore().getDefaultProfileName()).isEqualTo(TESTPROFILENAME)
    }

    @Test
    fun getProfileListTest() {
        assertThat(getValidProfileStore().getProfileList()).hasSize(1)
    }

    @Test
    fun getSpecificProfileTest() {
        assertIs<PureProfile>(getValidProfileStore().getSpecificProfile(TESTPROFILENAME))
    }

    @Test
    fun allProfilesValidTest() {
        assertThat(getValidProfileStore().allProfilesValid).isTrue()
        assertThat(getInvalidProfileStore1().allProfilesValid).isFalse()
        assertThat(getInvalidProfileStore2().allProfilesValid).isFalse()
    }

    @Test
    fun allProfilesValidIgnoresPumpCompatibilityTest() {
        // The sync/storage gate uses semantic validity only. Make the (otherwise valid) profile
        // incompatible with the current pump — basal 1 U/h is below the pump's 2.0 minimum — and it
        // must STILL be valid, so a pump switch never silently freezes profile sync (#4872).
        testPumpPlugin.pumpDescription.basalMinimumRate = 2.0
        assertThat(getValidProfileStore().allProfilesValid).isTrue()
        // A semantically invalid profile (IC out of hard limits) is still rejected.
        assertThat(getInvalidProfileStore1().allProfilesValid).isFalse()
    }
}
