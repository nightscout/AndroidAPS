package app.aaps.implementation.profile

import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
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
        assertIs<PureProfile>(getValidProfileStore().getSpecificProfile(TESTPROFILENAME))
    }

    @Test
    fun allProfilesValidTest() {
        assertThat(getValidProfileStore().allProfilesValid).isTrue()
        assertThat(getInvalidProfileStore1().allProfilesValid).isFalse()
        assertThat(getInvalidProfileStore2().allProfilesValid).isFalse()
    }
}
