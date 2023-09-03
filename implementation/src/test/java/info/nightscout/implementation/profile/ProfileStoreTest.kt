package info.nightscout.implementation.profile

import info.nightscout.interfaces.profile.PureProfile
import info.nightscout.sharedtests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ProfileStoreTest : TestBaseWithProfile() {

    @Test
    fun getStartDateTest() {
        Assertions.assertEquals(0, getValidProfileStore().getStartDate())
    }

    @Test
    fun getDefaultProfileTest() {
        Assertions.assertTrue(getValidProfileStore().getDefaultProfile() is PureProfile)
    }

    @Test
    fun getDefaultProfileJsonTest() {
        Assertions.assertTrue(getValidProfileStore().getDefaultProfileJson()?.has("dia") ?: false)
        Assertions.assertEquals(null, getInvalidProfileStore2().getDefaultProfileJson())
    }

    @Test
    fun getDefaultProfileNameTest() {
        Assertions.assertEquals(TESTPROFILENAME, getValidProfileStore().getDefaultProfileName())
    }

    @Test
    fun getProfileListTest() {
        Assertions.assertEquals(1, getValidProfileStore().getProfileList().size)
    }

    @Test
    fun getSpecificProfileTest() {
        Assertions.assertTrue(getValidProfileStore().getSpecificProfile(TESTPROFILENAME) is PureProfile)
    }

    @Test
    fun allProfilesValidTest() {
        Assertions.assertTrue(getValidProfileStore().allProfilesValid)
        Assertions.assertFalse(getInvalidProfileStore1().allProfilesValid)
        Assertions.assertFalse(getInvalidProfileStore2().allProfilesValid)
    }
}