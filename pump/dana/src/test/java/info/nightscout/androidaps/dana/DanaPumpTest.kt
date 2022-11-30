package info.nightscout.androidaps.dana

import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.interfaces.profile.ProfileInstantiator
import info.nightscout.pump.dana.DanaPump
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class DanaPumpTest : TestBaseWithProfile() {

    @Mock lateinit var sp: SP
    @Mock lateinit var profileInstantiator: ProfileInstantiator

    private lateinit var sut: DanaPump

    @BeforeEach
    fun setup() {
        sut = DanaPump(aapsLogger, sp, dateUtil, profileInstantiator)
    }

    @Test
    fun detectDanaRS() {
        sut.hwModel = 0x05
        Assert.assertTrue(sut.modelFriendlyName().contains("DanaRS"))
    }
}