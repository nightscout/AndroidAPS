package info.nightscout.implementation.profile

import com.google.common.truth.Truth.assertThat
import info.nightscout.interfaces.profile.PureProfile
import info.nightscout.sharedtests.TestBaseWithProfile
import org.junit.jupiter.api.Test

internal class ProfileStoreTest : TestBaseWithProfile() {

    @Test
    fun getStartDateTest() {
        assertThat(getValidProfileStore().getStartDate()).isEqualTo(0)
    }

    @Test
    fun getDefaultProfileTest() {
        assertThat(getValidProfileStore().getDefaultProfile()).isInstanceOf(PureProfile::class.java)
    }

    @Test
    fun getDefaultProfileJsonTest() {
        assertThat(getValidProfileStore().getDefaultProfileJson()?.has("dia")).isTrue()
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
        assertThat(getValidProfileStore().getSpecificProfile(TESTPROFILENAME)).isInstanceOf(PureProfile::class.java)
    }

    @Test
    fun allProfilesValidTest() {
        assertThat(getValidProfileStore().allProfilesValid).isTrue()
        assertThat(getInvalidProfileStore1().allProfilesValid).isFalse()
        assertThat(getInvalidProfileStore2().allProfilesValid).isFalse()
    }
}
