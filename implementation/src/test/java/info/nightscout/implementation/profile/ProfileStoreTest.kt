package info.nightscout.implementation.profile

import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.interfaces.profile.PureProfile
import org.junit.Assert
import org.junit.jupiter.api.Test

internal class ProfileStoreTest : TestBaseWithProfile() {

    @Test
    fun getStartDateTest() {
        Assert.assertEquals(0, getValidProfileStore().getStartDate())
    }

    @Test
    fun getDefaultProfileTest() {
        Assert.assertTrue(getValidProfileStore().getDefaultProfile() is PureProfile)
    }

    @Test
    fun getDefaultProfileJsonTest() {
        Assert.assertTrue(getValidProfileStore().getDefaultProfileJson()?.has("dia") ?: false)
        Assert.assertEquals(null, getInvalidProfileStore2().getDefaultProfileJson())
    }

    @Test
    fun getDefaultProfileNameTest() {
        Assert.assertEquals(TESTPROFILENAME, getValidProfileStore().getDefaultProfileName())
    }

    @Test
    fun getProfileListTest() {
        Assert.assertEquals(1, getValidProfileStore().getProfileList().size)
    }

    @Test
    fun getSpecificProfileTest() {
        Assert.assertTrue(getValidProfileStore().getSpecificProfile(TESTPROFILENAME) is PureProfile)
    }

    @Test
    fun allProfilesValidTest() {
        Assert.assertTrue(getValidProfileStore().allProfilesValid)
        Assert.assertFalse(getInvalidProfileStore1().allProfilesValid)
        Assert.assertFalse(getInvalidProfileStore2().allProfilesValid)
    }
}